package com.tuya.smartai.demo.talk

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.thingclips.sdk.aistream.AudioAmplitudesCallback
import com.thingclips.sdk.aistream.ConnectCallback
import com.thingclips.sdk.aistream.EventStartCallback
import com.thingclips.sdk.aistream.SessionCallback
import com.thingclips.sdk.aistream.StreamResultCallback
import com.thingclips.sdk.aistream.ThingAiStreamConstant
import com.thingclips.sdk.aistream.ThingAiStreamListener
import com.thingclips.sdk.aistream.audio.AudioDetectManager
import com.thingclips.sdk.aistream.audio.AudioPlayCallback
import com.thingclips.sdk.aistream.bean.RecordParams
import com.thingclips.sdk.aistream.bean.RecordParams.Companion.SYSTEM_MODE_DEFAULT
import com.thingclips.sdk.aistream.helper.EventStartOptions
import com.thingclips.smart.ai.localmcp.McpLocalManager
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
import com.tuya.smartai.demo.TAG_PREFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 设备身份版本的云端长 event 引擎
 *
 * 仿照 devkit-ai-chat 的 AiLongEventEngine 实现，核心差异仅在连接和会话创建方式：
 * - 使用 connectWithDevice(deviceId) 而非 connectWithApp()
 * - 使用 createSession(deviceId, ...) 而非 createSession(AgentTokenRequestParams, ...)
 *
 * 录音方式与参考实现完全一致：
 * - 使用 AudioDetectManager.startDetector() 启动录音
 * - 通过 AudioDetectionListener.onStreamAudioData 获取音频数据
 * - 通过 aiStream.sendAudioData() 手动发送音频
 * - 只保留 AEC 模型，不使用本地 VAD
 */
class AiLongEventEngine(
    private val context: Context,
    private val deviceId: String,
    private val longEventListener: LongEventListener
) : ILongEventEngine {
    companion object {
        private const val TAG = TAG_PREFIX + "LongEventEngine"
        const val AMPLITUDES_LENGTH = 50
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val aiStream: IThingAiStream = ThingAIOS.getInstance().getAiStream()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var sessionId: String? = null
    private var currentEventId: String? = null

    @Volatile
    private var isCreatingEvent = false

    @Volatile
    private var isRecording = false

    override var currentState: State = State.IDLE
        private set

    init {
        ThingStreamManager.getInstance().enableDebugLog(true)
    }

    // --- Public API ---

    override fun connect() {
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

    override fun startRecording() {
        if (currentState != State.READY) return
        if (currentEventId.isNullOrEmpty()) {
            createLongEvent()
        } else {
            startAudioRecording()
        }
    }

    override fun stopRecording() {
        if (currentState != State.RECORDING) return

        isRecording = false
        Log.i(TAG, "Stop recording")
        AudioDetectManager.getInstance().stopRecord()
        setState(State.READY)
        longEventListener.onRecordingStopped()
    }

    override fun stopPlayAudio() {
        aiStream.stopPlayAudio()
    }

    override fun closeEvent() {
        val eventToClose = currentEventId ?: return
        Log.i(TAG, "Close long event: $eventToClose")
        sessionId?.let { session ->
            aiStream.sendEventEnd(eventToClose, session, null, null)
            currentEventId = null
        }
    }

    override fun getSessionId(): String? = sessionId
    override fun getCurrentEventId(): String? = currentEventId

    override fun destroy() {
        stopRecording()
        stopPlayAudio()
        closeEvent()
        sessionId?.let { aiStream.closeSession(it, null) }
        audioHandlerThread.quitSafely()
        aiStream.destroy()
        mainHandler.removeCallbacksAndMessages(null)
        AudioDetectManager.getInstance().destroyDetector()
        scope.cancel()
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
        aiStream.createSession(deviceId, "", McpLocalManager.buildUserData(), object : SessionCallback {
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

        setState(State.RECORDING)
        isRecording = true
        longEventListener.onRecordingStarted()
        aiStream.registerRecordAmplitudesCallback(AMPLITUDES_LENGTH, audioAmplitudesCallback)
        val params = RecordParams.Builder()
            .sampleRate(16000)
            .enableANC(true)
            .enableRnnoise(true)
            .ancLevel(2)
            .systemMode(SYSTEM_MODE_DEFAULT)
            .playMode(true)
            .build()

        Log.i(TAG, "Starting AudioDetectManager with ANC/Rnnoise + device audio mode")
        AudioDetectManager.getInstance().startDetector(params, audioDetectionListener)
    }

    // --- Data parsing ---

    @Suppress("UNUSED_PARAMETER")
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

    private val confirmableTools = setOf("device.app.open", "device.app.close")

    private fun handleMcpEvent(sessionId: String, eventId: String, payload: ByteArray?) {
        if (payload == null) {
            Log.w(TAG, "handleMcpEvent: payload is null")
            return
        }
        scope.launch {
            try {
                val payloadString = String(payload, Charsets.UTF_8)
                Log.i(TAG, "handleMcpEvent payload: $payloadString")

                val json = JSONObject(payloadString)
                val method = json.optString("method")
                val params = json.optJSONObject("params")
                val toolName = params?.optString("name") ?: ""
                val arguments = params?.optJSONObject("arguments")
                val argsStr = arguments?.toString() ?: "{}"

                if (method == "tools/call" && toolName in confirmableTools) {
                    longEventListener.onMcpToolCall(McpToolCallInfo(
                        toolName = toolName,
                        argsJson = argsStr,
                        onResult = { success ->
                            scope.launch {
                                val status = if (success) "success" else "error"
                                val msg = if (success) "$toolName executed" else "$toolName failed"
                                val toolResult = buildMcpToolResponse(json, status, msg)
                                longEventListener.onMcpToolResult(toolName, argsStr, status, msg)
                                sendMcpResponse(sessionId, eventId, toolResult)
                            }
                        }
                    ))
                    return@launch
                }

                if (method == "tools/call" && toolName.isNotEmpty()) {
                    mainHandler.post {
                        longEventListener.onMcpToolResult(toolName, argsStr, "executing", "")
                    }
                }

                val response = McpLocalManager.handlePayload(payloadString)
                if (response != null) {
                    if (method == "tools/call" && toolName.isNotEmpty()) {
                        val resultStatus = parseResultStatus(response)
                        val resultMsg = parseResultMessage(response)
                        mainHandler.post {
                            longEventListener.onMcpToolResult(toolName, argsStr, resultStatus, resultMsg)
                        }
                    }
                    sendMcpResponse(sessionId, eventId, response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleMcpEvent error", e)
            }
        }
    }

    private fun buildMcpToolResponse(originalJson: JSONObject, status: String, message: String): String {
        val contentItem = JSONObject().apply {
            put("type", "text")
            put("status", status)
            put("text", message)
        }
        val contentArray = org.json.JSONArray().apply { put(contentItem) }
        val result = JSONObject().apply { put("content", contentArray) }
        return JSONObject().apply {
            put("jsonrpc", originalJson.optString("jsonrpc", "2.0"))
            put("id", originalJson.opt("id"))
            put("result", result)
        }.toString()
    }

    private fun parseResultStatus(response: String): String {
        return try {
            val json = JSONObject(response)
            val content = json.optJSONObject("result")?.optJSONArray("content")
            val first = content?.optJSONObject(0)
            first?.optString("status", "success") ?: "success"
        } catch (e: Exception) { "success" }
    }

    private fun parseResultMessage(response: String): String {
        return try {
            val json = JSONObject(response)
            val content = json.optJSONObject("result")?.optJSONArray("content")
            val first = content?.optJSONObject(0)
            first?.optString("text", "") ?: ""
        } catch (e: Exception) { "" }
    }

    private fun sendMcpResponse(sessionId: String, eventId: String, response: String) {
        Log.i(TAG, "sendMcpResponse: $response")
        val streamEvent = StreamEvent.Builder(sessionId, eventId, Constants.EventType.MCP_CMD)
            .payload(response.toByteArray())
            .build()
        aiStream.sendEvent(streamEvent, object : StreamResultCallback {
            override fun onSuccess() {
                Log.i(TAG, "sendMcpResponse success")
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "sendMcpResponse error: $errorCode, $errorMessage")
            }
        })
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
                createSession()
            }
        }

        override fun onTextReceived(data: StreamText) {
            if (data.text.isNullOrEmpty()) return
            parseAndProcessText(data.sessionId, data.text)
        }

        override fun onAudioReceived(data: StreamAudio) {
            if (data.streamFlag == Constants.StreamFlag.START) {
                aiStream.startPlayAudio(true,data, audioPlayCallback)
            }
        }

        override fun onVideoReceived(data: StreamVideo) {}

        override fun onEventReceived(event: StreamEvent) {
            Log.i(TAG, "Event: ${event.eventId} type=${event.eventType}")
            when (event.eventType) {
                Constants.EventType.MCP_CMD -> {
                    handleMcpEvent(event.sessionId, event.eventId, event.payload)
                }
                Constants.EventType.CHAT_BREAK -> {
                    Log.i(TAG, "Chat break received, interrupting NLG playback")
                    aiStream.stopPlayAudio()
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

    private val audioHandlerThread = HandlerThread("AudioControlThread").apply { start() }
    private val audioHandler = Handler(audioHandlerThread.looper)

    private val audioDetectionListener = object : AudioDetectManager.AudioDetectionListener {
        override fun onVoiceDetected() {}

        override fun onStreamAudioData(streamAudio: StreamAudio) {
            if (!isRecording) return
            val currentEvent = currentEventId
            val currentSession = sessionId
            if (currentEvent != null && currentSession != null) {
                aiStream.sendAudioData(currentSession, streamAudio, null)
            }
        }

        override fun onVoiceData(voice: ByteArray?, voiceLength: Int, pcmType: Int) {
            if (voice != null && voiceLength > 0 && isRecording) {
                handleAudioAmplitudes(voice)
            }
        }

        override fun onVoiceEnd() {}

        override fun onVoiceDetectError(error: Int, errorMessage: String?) {
            Log.e(TAG, "Audio detection error: $errorMessage")
            mainHandler.post {
                longEventListener.onError("Audio detection error: $errorMessage")
                stopRecording()
            }
        }
    }

    private fun handleAudioAmplitudes(voice: ByteArray) {
        val frequencyMagnitudes = ThingStreamManager.getInstance().getFrequencyMagnitudesNative(
            voice, 0, voice.size,
            Constants.AudioSampleRate.SAMPLE_RATE_16000,
            ThingAiStreamConstant.DEFAULT_BIT_DEPTH,
            ThingAiStreamConstant.DEFAULT_FFT_SIZE,
            AMPLITUDES_LENGTH
        )
        audioAmplitudesCallback.onSuccess(frequencyMagnitudes)
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

    // --- Model file helper ---

    private fun getModelPath(modelFileName: String): String? {
        val modelFile = File(context.filesDir, modelFileName)
        if (!modelFile.exists()) {
            Log.d(TAG, "Copying AEC model from assets: $modelFileName")
            try {
                context.assets.open(modelFileName).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                Log.d(TAG, "Model copied to ${modelFile.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Error copying model from assets: $modelFileName", e)
                return null
            }
        }
        return modelFile.absolutePath
    }
}
