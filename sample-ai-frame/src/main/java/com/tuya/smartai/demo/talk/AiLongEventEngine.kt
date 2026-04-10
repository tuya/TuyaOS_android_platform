package com.tuya.smartai.demo.talk

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.thingclips.sdk.aistream.AudioAmplitudesCallback
import com.thingclips.sdk.aistream.ConnectCallback
import com.thingclips.sdk.aistream.EventStartCallback
import com.thingclips.sdk.aistream.SessionCallback
import com.thingclips.sdk.aistream.StreamResultCallback
import com.thingclips.sdk.aistream.ThingAiStreamListener
import com.thingclips.sdk.aistream.audio.AudioPlayCallback
import com.thingclips.sdk.aistream.bean.RecordParams
import com.thingclips.sdk.aistream.helper.EventStartOptions
import com.thingclips.smart.ai.stream.IThingAiStream
import com.thingclips.smart.ai.stream.ThingAIOS
import com.thingclips.smart.android.aistream.Constants
import com.thingclips.smart.android.aistream.ThingStreamManager
import com.thingclips.smart.android.aistream.data.StreamAudio
import com.thingclips.smart.android.aistream.data.StreamEvent
import com.thingclips.smart.android.aistream.data.StreamFile
import com.thingclips.smart.android.aistream.data.StreamImage
import com.thingclips.smart.android.aistream.data.StreamText
import com.thingclips.smart.android.aistream.data.StreamVideo
import android.util.Log
import com.tuya.smartai.demo.TAG_PREFIX
import org.json.JSONObject

/**
 * 设备身份版本的云端长 event 引擎
 *
 * 与 devkit-ai-chat 的 AiLongEventEngine 的核心差异：
 * - 使用 connectWithDevice(deviceId) 而非 connectWithApp()
 * - 使用 createSession(deviceId, ...) 而非 createSession(AgentTokenRequestParams, ...)
 * - 使用高层 initAudioRecorder / startRecordAndSendAudioData API 录音
 *
 * 长 event 特点：
 * - 只创建一次 event，持续向云端投递音频
 * - 不需要本地 VAD，由云端负责语音活动检测
 * - 用户手动控制录音的开始和停止
 */
class AiLongEventEngine(
    private val context: Context,
    private val deviceId: String,
    private val longEventListener: LongEventListener
) {
    companion object {
        private const val TAG = TAG_PREFIX + "LongEventEngine"
        const val AMPLITUDES_LENGTH = 50
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val aiStream: IThingAiStream = ThingAIOS.getInstance().getAiStream()

    private var sessionId: String? = null
    private var currentEventId: String? = null
    private var recorderInitialized = false

    @Volatile
    private var isCreatingEvent = false

    @Volatile
    private var isRecording = false

    var currentState: State = State.IDLE
        private set

    init {
        ThingStreamManager.getInstance().enableDebugLog(true)
    }

    enum class State {
        IDLE, CONNECTING, CREATING_SESSION, READY, RECORDING, ERROR
    }

    enum class PlaybackState {
        STARTED, FINISHED, ERROR
    }

    interface LongEventListener {
        fun onStateChanged(newState: State)
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun onNlgInterrupted()
        fun onAsrResult(text: String, bizId: String)
        fun onNlgResult(eventId: String?, json: String)
        fun onAudioAmplitudeUpdate(amplitudes: DoubleArray)
        fun onAudioPlaybackStateChanged(state: PlaybackState, code: Int, msg: String?)
        fun onError(errorMessage: String)
    }

    // --- Public API ---

    fun connect() {
        aiStream.setStreamListener(aiStreamListener)
        if (aiStream.isConnected(Constants.ClientType.DEVICE, deviceId)) {
            createSession()
            return
        }
        setState(State.CONNECTING)
        aiStream.connectWithDevice(deviceId, object : ConnectCallback {
            override fun onSuccess(connectionId: String) { /* handled by listener */ }
            override fun onError(errorCode: Int, errorMessage: String) {
                setState(State.ERROR)
                longEventListener.onError("Connection failed: $errorMessage")
            }
        })
    }

    fun startRecording() {
        if (currentState != State.READY) return
        if (currentEventId.isNullOrEmpty()) {
            createLongEvent()
        } else {
            startAudioRecording()
        }
    }

    fun stopRecording() {
        if (currentState != State.RECORDING || !isRecording) return

        Log.i(TAG, "Stop recording")
        isRecording = false

        val session = sessionId ?: run {
            setState(State.READY)
            longEventListener.onRecordingStopped()
            return
        }

        aiStream.unregisterRecordAmplitudesCallback()
        val callback: StreamResultCallback = object : StreamResultCallback {
            override fun onSuccess() {
                Log.i(TAG, "stopRecordAndSendAudioData success")
            }
            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "stopRecordAndSendAudioData error: $errorMessage")
            }
        }
        aiStream.stopRecordAndSendAudioData(session, null, null, callback)

        setState(State.READY)
        longEventListener.onRecordingStopped()
    }

    fun stopPlayAudio() {
        aiStream.stopPlayAudio()
    }

    fun closeEvent() {
        val eventToClose = currentEventId ?: return
        Log.i(TAG, "Close long event: $eventToClose")
        sessionId?.let { session ->
            aiStream.sendEventEnd(eventToClose, session, null, null)
            currentEventId = null
        }
    }

    fun getSessionId(): String? = sessionId
    fun getCurrentEventId(): String? = currentEventId

    fun destroy() {
        if (isRecording) {
            isRecording = false
            sessionId?.let {
                aiStream.stopRecordAndSendAudioData(it, null, null, null as StreamResultCallback?)
            }
            aiStream.unregisterRecordAmplitudesCallback()
        }
        stopPlayAudio()
        closeEvent()
        sessionId?.let { aiStream.closeSession(it, null) }
        aiStream.destroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    // --- State & Session ---

    private fun setState(newState: State) {
        if (currentState != newState) {
            currentState = newState
            longEventListener.onStateChanged(newState)
        }
    }

    private fun createSession() {
        if (!sessionId.isNullOrEmpty()) {
            setState(State.READY)
            return
        }
        setState(State.CREATING_SESSION)

        aiStream.createSession(deviceId, "", null, object : SessionCallback {
            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "Session creation error: $errorMessage")
                setState(State.ERROR)
                longEventListener.onError("Session creation failed: $errorMessage")
            }

            override fun onSuccess(
                sessionId: String,
                sendDataChannels: Map<String, Int>,
                revDataChannels: Map<String, Int>
            ) {
                this@AiLongEventEngine.sessionId = sessionId
                Log.i(TAG, "Session created: $sessionId")
                setState(State.READY)
            }
        })
    }

    // --- Event lifecycle ---

    private fun createLongEvent() {
        if (isCreatingEvent || !currentEventId.isNullOrEmpty()) {
            Log.w(TAG, "Active or creating event exists, skip")
            return
        }
        isCreatingEvent = true

        if (sessionId.isNullOrEmpty()) {
            Log.e(TAG, "Session ID is empty, cannot create event")
            isCreatingEvent = false
            createSession()
            return
        }
        val options = EventStartOptions.Builder(sessionId!!)
            .enableVad(true)
            .enableInterrupt(true)
            .build()
        aiStream.sendEventStart(options, object : EventStartCallback {
            override fun onSuccess(eventId: String) {
                Log.i(TAG, "Long event created: $eventId")
                currentEventId = eventId
                isCreatingEvent = false
                startAudioRecording()
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                isCreatingEvent = false
                Log.e(TAG, "Failed to create long event: $errorMessage")
                longEventListener.onError("Failed to create event: $errorMessage")
            }
        })
    }

    private fun startAudioRecording() {
        if (isRecording) return
        val session = sessionId ?: return

        if (!recorderInitialized) {
            initRecorderThenStart(session)
        } else {
            doStartRecording(session)
        }
    }

    private fun initRecorderThenStart(session: String) {
        
        aiStream.initAudioRecorder(RecordParams.Builder().build(), object : StreamResultCallback {
            override fun onSuccess() {
                recorderInitialized = true
                doStartRecording(session)
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "initAudioRecorder failed: $errorMessage")
                longEventListener.onError("初始化录音器失败: $errorMessage")
            }
        })
    }

    private fun doStartRecording(session: String) {
        setState(State.RECORDING)
        isRecording = true
        longEventListener.onRecordingStarted()
        aiStream.registerRecordAmplitudesCallback(AMPLITUDES_LENGTH, audioAmplitudesCallback)

        aiStream.startRecordAndSendAudioData(session, null, null, object : StreamResultCallback {
            override fun onSuccess() {
                Log.d(TAG, "startRecordAndSendAudioData success")
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "startRecordAndSendAudioData failed: $errorCode $errorMessage")
                isRecording = false
                aiStream.unregisterRecordAmplitudesCallback()
                setState(State.READY)
                longEventListener.onError("录音启动失败: $errorMessage")
            }
        })
    }

    // --- Data parsing ---

    private fun parseAndProcessText(sessionId: String?, jsonText: String) {
        try {
            Log.d(TAG, "Received: $jsonText")
            val jsonObject = JSONObject(jsonText)
            val bizType = jsonObject.optString("bizType")
            val bizId = jsonObject.optString("bizId")
            val eof = jsonObject.optInt("eof", 0)
            val data = jsonObject.optJSONObject("data") ?: return

            when {
                "ASR".equals(bizType, ignoreCase = true) -> {
                    val asrText = data.optString("text", "").trim()
                    if (asrText.isNotEmpty()) aiStream.stopPlayAudio()
                    if (asrText.isNotEmpty() && eof == 1) {
                        longEventListener.onAsrResult(asrText, bizId)
                    }
                }
                "NLG".equals(bizType, ignoreCase = true) -> {
                    longEventListener.onNlgResult(currentEventId, jsonText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse text", e)
        }
    }

    // --- Callbacks ---

    private val aiStreamListener = object : ThingAiStreamListener {
        override fun onConnectStateChanged(connectionId: String, state: Int, errorCode: Int) {
            if (state == Constants.ConnectState.CONNECTED) {
                createSession()
            } else {
                setState(State.IDLE)
                sessionId = null
            }
        }

        override fun onSessionStateChanged(sessionId: String, state: Int, errorCode: Int) {
            Log.d(TAG, "Session $sessionId state=$state")
            if (state == Constants.SessionState.CLOSED_BY_SERVER ||
                state == Constants.SessionState.AGENT_TOKEN_EXPIRED
            ) {
                longEventListener.onError("Session closed, reinitializing")
                setState(State.IDLE)
                this@AiLongEventEngine.sessionId = null
                currentEventId = null
                recorderInitialized = false
                createSession()
            }
        }

        override fun onTextReceived(data: StreamText) {
            if (data.text.isNullOrEmpty()) return
            parseAndProcessText(data.sessionId, data.text)
        }

        override fun onAudioReceived(data: StreamAudio) {
            if (data.streamFlag == Constants.StreamFlag.START) {
                aiStream.startPlayAudio(data, audioPlayCallback)
            }
        }

        override fun onVideoReceived(data: StreamVideo) {
            
        }
        override fun onEventReceived(event: StreamEvent) {
            Log.i(TAG, "Event: ${event.eventId} type=${event.eventType}")
            when (event.eventType) {
                Constants.EventType.CHAT_BREAK -> {
                    Log.i(TAG, "Chat break received, interrupting NLG playback")
                    longEventListener.onNlgInterrupted()
                }
                Constants.EventType.END -> {
                    Log.i(TAG, "Event ${event.eventId} ended")
                }
            }
        }
        override fun onFileReceived(data: StreamFile) {}
        override fun onImageReceived(data: StreamImage) {}
    }

    private val audioAmplitudesCallback = object : AudioAmplitudesCallback {
        override fun onSuccess(amplitudes: DoubleArray?) {
            if (amplitudes != null) longEventListener.onAudioAmplitudeUpdate(amplitudes)
        }
        override fun onError(errorCode: Int, errorMessage: String) {
            Log.w(TAG, "Amplitude error: $errorMessage")
        }
    }

    private val audioPlayCallback = object : AudioPlayCallback {
        override fun onPlayStart() {
            longEventListener.onAudioPlaybackStateChanged(PlaybackState.STARTED, 0, null)
        }
        override fun onPlayFinish() {
            longEventListener.onAudioPlaybackStateChanged(PlaybackState.FINISHED, 0, null)
        }
        override fun onPlayError(errorCode: Int, errorMessage: String) {
            longEventListener.onAudioPlaybackStateChanged(PlaybackState.ERROR, errorCode, errorMessage)
        }
    }
}
