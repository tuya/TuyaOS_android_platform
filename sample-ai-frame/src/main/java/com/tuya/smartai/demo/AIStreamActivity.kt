package com.tuya.smartai.demo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.thingclips.sdk.aistream.ConnectCallback
import com.thingclips.sdk.aistream.EventStartCallback
import com.thingclips.sdk.aistream.SessionCallback
import com.thingclips.sdk.aistream.StreamResultCallback
import com.thingclips.sdk.aistream.ThingAiStreamListener
import com.thingclips.sdk.aistream.audio.AudioPlayCallback
import com.thingclips.sdk.aistream.bean.RecordParams
import com.thingclips.sdk.aistream.bean.RecordParams.Companion.SYSTEM_MODE_DEFAULT
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
import com.tuya.smartai.demo.ai.AiChatActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AIStreamActivity : AppCompatActivity() {

    private lateinit var aiStream: IThingAiStream
    private lateinit var statusTextView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var createSessionButton: Button
    private lateinit var closeSessionButton: Button
    private lateinit var sendAudioButton: Button
    private lateinit var stopAudioButton: Button
    private lateinit var stopTtsButton: Button
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var imageView: ImageView
    private var currentSessionId: String? = null
    private var deviceId: String? = null

    private var isRecording = false
    private var mEventId: String? = null

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecordingAudio = AtomicBoolean(false)
    private var audioDataStream: ByteArrayOutputStream? = null
    private var audioEventId: String? = null
    private var isEventStarted = false
    private var isFirstFrameSent = false
    private var pendingAudioData: ByteArrayOutputStream? = null
    private var bufferSize = 0
    private var packageCount = 0
    private var isGenerateImageSession = false
    private var lastFilePath: String? = null

    private val listener = object : ThingAiStreamListener {
        override fun onConnectStateChanged(connectionId: String, state: Int, errorCode: Int) {
            Log.i(TAG, "连接状态变更: connectionId=$connectionId, state=$state, errorCode=$errorCode")
            addLog("连接状态变更: connectionId=$connectionId, state=$state, errorCode=$errorCode")
            runOnUiThread {
                statusTextView.text = if (state == Constants.ConnectState.CONNECTED) {
                    "已连接: $connectionId"
                } else {
                    "已断开连接: $errorCode"
                }
            }
        }

        override fun onSessionStateChanged(sessionId: String, state: Int, errorCode: Int) {
            Log.i(TAG, "会话状态变更: sessionId=$sessionId, state=$state, errorCode=$errorCode")
            addLog("会话状态变更: sessionId=$sessionId, state=$state, errorCode=$errorCode")
            currentSessionId = sessionId
            runOnUiThread {
                statusTextView.text = when (state) {
                    Constants.SessionState.CREATE_SUCCESS -> {"会话创建成功: $sessionId"}
                    Constants.SessionState.CREATE_FAILED -> "会话创建失败: $errorCode"
                    Constants.SessionState.CLOSED_BY_SERVER -> {
                        currentSessionId = null
                        "会话被服务器关闭: $sessionId"
                    }
                    else -> statusTextView.text
                }
            }
            if(Constants.SessionState.CREATE_SUCCESS == state){
                preInitAudioRecorder()
            }
        }

        override fun onAudioReceived(audioData: StreamAudio) {
            val payload = audioData.payload
            if (audioData.streamFlag == Constants.StreamFlag.END || payload == null) return
            Log.i(TAG, "收到音频数据: ${payload.size} 字节, streamFlag=${audioData.streamFlag}")
            if (audioData.streamFlag == Constants.StreamFlag.START) {
                aiStream.startPlayAudio(audioData, object : AudioPlayCallback {
                    override fun onPlayStart() {
                        Log.i(TAG, "onPlayStart: ")
                        addLog("=====onPlayStart: ")
                    }

                    override fun onPlayFinish() {
                        Log.i(TAG, "onPlayFinish: ")
                        addLog("=====onPlayFinish: ")
                    }

                    override fun onPlayError(errorCode: Int, errorMessage: String) {
                        Log.e(TAG, "onPlayError() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
                        addLog("=====onPlayError: $errorCode,msg: $errorMessage")
                    }
                })
            }
        }

        override fun onVideoReceived(videoData: StreamVideo) {
            val size = videoData.payload?.size ?: 0
            Log.i(TAG, "收到视频数据: $size 字节")
            addLog("收到视频数据: $size 字节")
        }

        override fun onImageReceived(imageData: StreamImage) {
            if (imageData.streamFlag == Constants.StreamFlag.END) {
                Log.e(TAG, "=======收到结束包 filePath ： ${imageData.filePath}, ${imageData.format},streamFlag:${imageData.streamFlag}")
                return
            }
            val imgSize = imageData.payload?.size ?: 0
            Log.i(TAG, "收到图像数据: $imgSize 字节 filePath ： ${imageData.filePath}, ${imageData.format}")
            addLog("收到图像数据: $imgSize 字节,filePath: ${imageData.filePath},imageUrl ${imageData.imageUrl},format: ${imageData.format},streamFlag:${imageData.streamFlag}")
            displayImage(imageData.filePath)
        }

        override fun onFileReceived(fileData: StreamFile) {
            Log.i(TAG, "收到文件数据: ${fileData.fileName}")
            addLog("收到文件数据: ${fileData.fileName}")
        }

        override fun onTextReceived(textData: StreamText) {
            Log.i(TAG, "收到文本数据: ${textData.text}")
            parseTextFromJson(textData.text)?.let { parsedData ->
                addLog(">>>>${parsedData.bizType}: ${parsedData.eof}->${parsedData.text}")
            }
        }

        override fun onEventReceived(event: StreamEvent) {
            Log.i(TAG, "收到事件: $event")
            addLog("收到事件: $event")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startEventAndRecord()
            } else {
                Toast.makeText(this, "录音权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai)

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)

        ThingStreamManager.getInstance().enableDebugLog(true)
        aiStream = ThingAIOS.getInstance().aiStream
        statusTextView = findViewById(R.id.statusTextView)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        createSessionButton = findViewById(R.id.createSessionButton)
        closeSessionButton = findViewById(R.id.closeSessionButton)
        sendAudioButton = findViewById(R.id.sendAudio)
        stopAudioButton = findViewById(R.id.stopAudio)
        stopTtsButton = findViewById(R.id.stopTtsButton)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        imageView = findViewById(R.id.imageView)

        aiStream.setStreamListener(listener)
        addLog("AIStreamActivity 初始化完成，设备ID: $deviceId")

        findViewById<View>(R.id.createSessionButton2).setOnClickListener { createSession2() }
        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { disconnect() }
        createSessionButton.setOnClickListener { createSession() }
        closeSessionButton.setOnClickListener { closeSession() }
        sendAudioButton.setOnClickListener {
            if (!isRecordingAudio.get()) {
                startEventAndRecord()
            } else {
                addLog("正在录音中，请先停止录音")
                Toast.makeText(this, "正在录音中，请先停止录音", Toast.LENGTH_SHORT).show()
            }
        }
        stopAudioButton.setOnClickListener { stopEventAndRecord() }
        findViewById<View>(R.id.sendText).setOnClickListener { startTextEventSample() }
        findViewById<View>(R.id.sendImage).setOnClickListener { startImageEventSample() }
        stopTtsButton.setOnClickListener { aiStream.stopPlayAudio() }
    }

    private fun connect() {
        val devId = deviceId ?: return
        addLog("开始连接设备: $devId")
        aiStream.connectWithDevice(devId, object : ConnectCallback {
            override fun onSuccess(connectionId: String) {
                addLog("连接成功: connectionId=$connectionId")
                runOnUiThread { statusTextView.text = "连接成功：connectionId： $connectionId" }
            }

            override fun onError(code: Int, error: String) {
                addLog("连接失败: code=$code, error=$error")
                runOnUiThread {
                    statusTextView.text = "连接失败: $error"
                    Toast.makeText(this@AIStreamActivity, "连接失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun disconnect() {
        addLog("断开连接")
        isRecording = false
        aiStream.disconnect()
    }

    private fun preInitAudioRecorder() {

        val params = RecordParams.Builder()
            .playMode(true)
            .systemMode(SYSTEM_MODE_DEFAULT)
            .build()
        Log.i(TAG, "Pre-initializing audio recorder after session created")
        aiStream.initAudioRecorder(params, object : StreamResultCallback {
            override fun onSuccess() {
                Log.i(TAG, "Audio recorder pre-initialized successfully")
            }
            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "Audio recorder pre-init failed: $errorMessage")
            }
        })
    }

    private fun createSession2() {
        if (!currentSessionId.isNullOrEmpty()) {
            Toast.makeText(this, "Demo只维护一个 session，请先关闭其他的", Toast.LENGTH_SHORT).show()
            return
        }

        addLog("创建会话2: $deviceId")
        aiStream.createSession(deviceId, "generate_image", null, object : SessionCallback {
            override fun onSuccess(sessionId: String, sendDataChannels: Map<String, Int>, revDataChannels: Map<String, Int>) {
                Log.i(TAG, "会话创建成功: $sessionId, sendDataCodes: $sendDataChannels, revDataCodes: $revDataChannels")
                isGenerateImageSession = true
                runOnUiThread {
                    statusTextView.text = "会话创建成功: $sessionId"
                    addLog("会话创建成功: 【generate_image】 $sessionId")
                    Toast.makeText(this@AIStreamActivity, "会话创建成功", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(errorCode: Int, message: String) {
                runOnUiThread {
                    statusTextView.text = "会话创建失败: $message"
                    addLog("会话创建失败: $message")
                    Toast.makeText(this@AIStreamActivity, "会话创建失败", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun createSession() {
        if (!currentSessionId.isNullOrEmpty()) {
            Toast.makeText(this, "Demo只维护一个 session，请先关闭其他的", Toast.LENGTH_SHORT).show()
            return
        }

        addLog("创建会话1: $deviceId")

        try {
            val solution = JSONObject().put("value", "generate_image")
            val customParam = JSONObject().put("custom.solution", solution)
            val ttsConfig = JSONObject().apply {
                put("container", "")
                put("channels", 1)
                put("bitDepth", "16")
                put("bitRate", "32000")
                put("format", "mp3")
                put("sampleRate", 16000)
            }
            val sessionAttributes = JSONObject().apply {
                put("custom.param", customParam)
                put("tts.order.supports", JSONArray().put(ttsConfig))
            }
            val customAttribute = JSONObject().put("sessionAttributes", sessionAttributes).toString()
            addLog("使用自定义参数: $customAttribute")

            aiStream.createSession(deviceId, "", customAttribute, object : SessionCallback {
                override fun onSuccess(sessionId: String, sendDataChannels: Map<String, Int>, revDataChannels: Map<String, Int>) {
                    Log.i(TAG, "会话创建成功: $sessionId, sendDataCodes: $sendDataChannels, revDataCodes: $revDataChannels")
                    isGenerateImageSession = false
                    runOnUiThread {
                        statusTextView.text = "会话创建成功: $sessionId"
                        addLog("会话创建成功: 【】 $sessionId")
                        Toast.makeText(this@AIStreamActivity, "会话创建成功", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(errorCode: Int, message: String) {
                    runOnUiThread {
                        statusTextView.text = "会话创建失败: $message"
                        addLog("会话创建失败: $message")
                        Toast.makeText(this@AIStreamActivity, "会话创建失败", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: JSONException) {
            Log.e(TAG, "构建 userData JSON 失败", e)
            addLog("构建 userData JSON 失败: ${e.message}")
            runOnUiThread {
                statusTextView.text = "构建 userData 失败"
                Toast.makeText(this, "构建 userData 失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun closeSession() {
        val sessionId = currentSessionId
        if (sessionId.isNullOrEmpty()) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show()
            return
        }
        aiStream.closeSession(sessionId, object : StreamResultCallback {
            override fun onSuccess() {
                currentSessionId = null
                runOnUiThread {
                    addLog("session会话 已关闭")
                    statusTextView.text = "session会话 已关闭"
                }
            }

            override fun onError(code: Int, msg: String) {
                currentSessionId = null
                addLog("session会话关闭失败")
                runOnUiThread { statusTextView.text = "session 关闭失败 $msg" }
            }
        })
    }

    private fun startTextEventSample() {
        val sessionId = currentSessionId
        if (sessionId.isNullOrEmpty()) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show()
            return
        }
        if (!aiStream.isConnected(Constants.ClientType.DEVICE, deviceId)) {
            Toast.makeText(this, "没有活动的会话q", Toast.LENGTH_SHORT).show()
            return
        }

        val textData = StreamText().apply {
            text = if (isGenerateImageSession) {
                "Please generate an image of two children chasing each other on the beach."
            } else {
                "给我生成一个 200字 解读李白将近酒的解析"
            }
        }

        val options = EventStartOptions.Builder(sessionId).build()
        aiStream.sendEventStart(options, object : EventStartCallback {
            override fun onSuccess(eventId: String) {
                addLog("<---text:${textData.text}")
                aiStream.sendTextData(sessionId, textData, object : StreamResultCallback {
                    override fun onSuccess() = finalAudioEvent(eventId)
                    override fun onError(errorCode: Int, errorMessage: String) = finalAudioEvent(eventId)
                })
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "onError() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
                addLog("onError() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
            }
        })
    }

    private fun startEventAndRecord() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            return
        }
        if (currentSessionId.isNullOrEmpty()) {
            addLog("请先创建会话")
            Toast.makeText(this, "请先创建会话", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRecordingAudio.get()) {
            addLog("已经在录音中")
            return
        }

        try {
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                addLog("AudioRecord初始化失败")
                return
            }

            audioDataStream = ByteArrayOutputStream()
            pendingAudioData = ByteArrayOutputStream()
            isEventStarted = false
            isFirstFrameSent = false
            packageCount = 0
            addLog("录音初始化完成: pendingAudioData=$pendingAudioData")
            Log.d(TAG, "录音初始化完成: pendingAudioData=$pendingAudioData")

            audioRecord?.startRecording()
            isRecordingAudio.set(true)
            addLog("开始录音...")
            Toast.makeText(this, "开始录音", Toast.LENGTH_SHORT).show()

            recordingThread = Thread(::recordingLoop, "RecordingThread").also { it.start() }
            sendAudioEventStart()
        } catch (e: Exception) {
            addLog("启动录音失败: ${e.message}")
            Log.e(TAG, "启动录音失败", e)
        }
    }

    private fun sendAudioEventStart() {
        val sessionId = currentSessionId ?: return
        val options = EventStartOptions.Builder(sessionId).build()
        aiStream.sendEventStart(options, object : EventStartCallback {
            override fun onSuccess(eventId: String) {
                audioEventId = eventId
                isEventStarted = true
                addLog("-------> 音频事件开始，eventId: $eventId")
                sendPendingAudioData()
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                addLog("音频事件开始失败: $errorCode, $errorMessage")
                Log.e(TAG, "音频事件开始失败: $errorCode, $errorMessage")
            }
        })
    }

    private fun sendPendingAudioData() {
        val pending = pendingAudioData
        if (pending != null && pending.size() > 0) {
            val cachedData = pending.toByteArray()
            sendAudioStartDataToStream(cachedData, cachedData.size)
            try {
                pending.close()
                pendingAudioData = ByteArrayOutputStream()
            } catch (e: Exception) {
                Log.e(TAG, "清空音频缓存失败", e)
            }
        } else {
            Log.i(TAG, "sendPendingAudioData 缓存为空，不需要使用缓存")
        }
    }

    private fun recordingLoop() {
        val buffer = ByteArray(bufferSize)
        while (isRecordingAudio.get()) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (bytesRead <= 0) continue

                audioDataStream?.write(buffer, 0, bytesRead)

                when {
                    isEventStarted && isFirstFrameSent -> sendAudioDataToStream(buffer, bytesRead)
                    isEventStarted -> {
                        val pending = pendingAudioData
                        if (pending == null || pending.size() == 0) {
                            sendAudioStartDataToStream(buffer, bytesRead)
                        } else {
                            Log.e(TAG, "ignore buffer length : ${buffer.size}")
                        }
                    }
                    else -> {
                        pendingAudioData?.write(buffer, 0, bytesRead)
                        Log.d(TAG, "音频数据已缓存，当前缓存大小: ${pendingAudioData?.size()} 字节")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "录音循环出错", e)
                break
            }
        }
    }

    private fun sendAudioStartDataToStream(audioData: ByteArray, length: Int) {
        try {
            val validData = audioData.copyOf(length)
            val audio = StreamAudio().apply {
                payload = validData
                timestamp = System.currentTimeMillis()
                codecType = Constants.AudioCodec.PCM
                sampleRate = SAMPLE_RATE
                channels = Constants.AudioChannel.CHANNEL_MONO
                bitDepth = 16
                streamFlag = Constants.StreamFlag.START
            }
            addLog("发送首帧数据: data: $length 字节")
            val sessionId = currentSessionId ?: return
            aiStream.sendAudioData(sessionId, audio, object : StreamResultCallback {
                override fun onSuccess() {
                    isFirstFrameSent = true
                    addLog("-----> Start -----首帧音频数据发送成功，开始发送后续数据")
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    addLog("首帧音频数据发送失败: $errorCode, $errorMessage")
                    Log.e(TAG, "首帧音频数据发送失败: $errorCode, $errorMessage")
                }
            })
        } catch (e: Exception) {
            addLog("发送首帧音频数据出错: ${e.message}")
            Log.e(TAG, "发送首帧音频数据出错", e)
        }
    }

    private fun sendAudioDataToStream(audioData: ByteArray, length: Int) {
        try {
            val validData = audioData.copyOf(length)
            val audio = StreamAudio().apply {
                payload = validData
                timestamp = System.currentTimeMillis()
                codecType = Constants.AudioCodec.PCM
                sampleRate = SAMPLE_RATE
                channels = Constants.AudioChannel.CHANNEL_MONO
                bitDepth = 16
                streamFlag = Constants.StreamFlag.IN_PROGRESS
            }
            packageCount++
            val sessionId = currentSessionId ?: return
            aiStream.sendAudioData(sessionId, audio, object : StreamResultCallback {
                override fun onSuccess() {}
                override fun onError(errorCode: Int, errorMessage: String) {
                    Log.e(TAG, "发送音频数据失败: $errorCode, $errorMessage")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "发送音频数据出错", e)
        }
    }

    private fun stopEventAndRecord() {
        if (!isRecordingAudio.get()) {
            addLog("当前没有在录音")
            return
        }

        try {
            isRecordingAudio.set(false)
            audioRecord?.run { stop(); release() }
            audioRecord = null

            recordingThread?.takeIf { it.isAlive }?.interrupt()
            recordingThread = null

            audioDataStream?.close()
            audioDataStream = null
            pendingAudioData?.close()
            pendingAudioData = null

            addLog("录音结束")
            Toast.makeText(this, "录音结束", Toast.LENGTH_SHORT).show()
            sendAudioEventEnd()
        } catch (e: Exception) {
            addLog("停止录音失败: ${e.message}")
            Log.e(TAG, "停止录音失败", e)
        }
    }

    private fun sendAudioEventEnd() {
        if (audioEventId.isNullOrEmpty()) {
            addLog("音频事件ID为空，无法发送结束事件")
            return
        }
        sendFinalAudioData()
    }

    private fun finalAudioEvent(eventId: String) {
        val sessionId = currentSessionId ?: return
        aiStream.sendEventEnd(eventId, sessionId, null, object : StreamResultCallback {
            override fun onSuccess() {
                addLog("事件发送完成")
                audioEventId = null
                isEventStarted = false
                isFirstFrameSent = false
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                addLog("事件发送完成: $errorCode, $errorMessage")
                audioEventId = null
                isEventStarted = false
                isFirstFrameSent = false
            }
        })
    }

    private fun sendFinalAudioData() {
        try {
            val audio = StreamAudio().apply {
                payload = byteArrayOf(0x00)
                timestamp = System.currentTimeMillis()
                codecType = Constants.AudioCodec.PCM
                sampleRate = SAMPLE_RATE
                channels = Constants.AudioChannel.CHANNEL_MONO
                bitDepth = 16
                streamFlag = Constants.StreamFlag.END
            }
            val sessionId = currentSessionId ?: return
            aiStream.sendAudioData(sessionId, audio, object : StreamResultCallback {
                override fun onSuccess() {
                    addLog("----> END ----- 事件发送完成,package Count: $packageCount")
                    audioEventId?.let { finalAudioEvent(it) }
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    addLog("发送音频数据失败: $errorCode, $errorMessage")
                    Log.e(TAG, "onError() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
                    audioEventId?.let { finalAudioEvent(it) }
                }
            })
        } catch (e: Exception) {
            addLog("发送最终音频数据出错: ${e.message}")
        }
    }

    private fun isImageUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val lower = url.lowercase()
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                || lower.contains(".gif") || lower.contains(".webp") || lower.contains(".bmp")
                || lower.contains("image") || lower.contains("/img")
                || lower.contains("tos-cn") || lower.contains("volces.com")
    }

    private fun parseTextFromJson(jsonText: String?): TextData? {
        return try {
            val jsonObject = JSONObject(jsonText ?: return null)
            val textData = TextData()

            textData.bizId = jsonObject.optString("bizId", null)
            textData.bizType = jsonObject.optString("bizType", null)
            textData.eof = jsonObject.optInt("eof", 0)

            if (jsonObject.has("data")) {
                when (val dataValue = jsonObject.get("data")) {
                    is String -> {
                        if (isImageUrl(dataValue)) {
                            textData.text = "[图片URL]"
                            textData.imageUrl = dataValue
                            runOnUiThread {
                                displayImage(dataValue)
                                addLog("收到图片: ${dataValue.take(80)}...")
                            }
                        } else {
                            textData.text = dataValue
                        }
                    }
                    is JSONObject -> {
                        textData.text = dataValue.optString("text", null)
                        dataValue.optString("content", "").takeIf { it.isNotEmpty() }?.let {
                            textData.text = it
                        }
                        parseImagesFromData(dataValue, textData)
                    }
                }
            }
            textData
        } catch (e: JSONException) {
            null
        }
    }

    private fun parseImagesFromData(dataObject: JSONObject, textData: TextData) {
        if (!dataObject.has("images")) return
        try {
            val imagesArray = dataObject.getJSONArray("images")
            for (i in 0 until imagesArray.length()) {
                val imageObj = imagesArray.getJSONObject(i)
                val imageUrl = imageObj.optString("url", null) ?: continue
                textData.text = "[图片URL: $imageUrl]"
                textData.imageUrl = imageUrl
                runOnUiThread { displayImage(imageUrl) }
            }
        } catch (e: JSONException) {
            val imagesObject = dataObject.optJSONObject("images") ?: return
            val urlArray = imagesObject.optJSONArray("url") ?: return
            if (urlArray.length() > 0) {
                val imageUrl = urlArray.getString(0)
                textData.text = "[图片URL: $imageUrl]"
                textData.imageUrl = imageUrl
                runOnUiThread { displayImage(imageUrl) }
            }
        }
    }

    private fun addLog(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            var newText = logTextView.text.toString() + logEntry
            val lines = newText.split("\n")
            if (lines.size > 1000) {
                newText = lines.takeLast(800).joinToString("\n")
            }
            logTextView.text = newText
            logScrollView.post {
                try { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) } catch (_: Exception) {}
            }
        }
    }

    private fun startImageEventSample() {
        val sessionId = currentSessionId
        if (sessionId.isNullOrEmpty()) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show()
            return
        }
        if (!aiStream.isConnected(Constants.ClientType.DEVICE, deviceId)) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show()
            return
        }

        val options = EventStartOptions.Builder(sessionId).build()
        aiStream.sendEventStart(options, object : EventStartCallback {
            override fun onSuccess(eventId: String) {
                val filePath = lastFilePath
                if (filePath.isNullOrEmpty()) return
                addLog("<---current: image: $filePath")
                displayImage(filePath)
                aiStream.sendImageData(sessionId, filePath, null, object : StreamResultCallback {
                    override fun onSuccess() {
                        val textData = StreamText().apply {
                            text = if (isGenerateImageSession) {
                                "Transform this photo into a sketch style"
                            } else {
                                "描述一下这个照片"
                            }
                        }
                        addLog("<---text: ${textData.text}")
                        aiStream.sendTextData(sessionId, textData, object : StreamResultCallback {
                            override fun onSuccess() = finalAudioEvent(eventId)
                            override fun onError(errorCode: Int, errorMessage: String) = finalAudioEvent(eventId)
                        })
                    }

                    override fun onError(errorCode: Int, errorMessage: String) {}
                })
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                Log.e(TAG, "onError() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
                addLog("onError() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
            }
        })
    }

    private fun displayImage(filePath: String?) {
        Log.d(TAG, "displayImage: filePath = [$filePath]")
        if (filePath.isNullOrEmpty()) {
            imageView.visibility = View.GONE
            return
        }
        lastFilePath = filePath
        imageView.visibility = View.VISIBLE
        Glide.with(this)
            .load(filePath)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(imageView)
    }

    private fun loadImageWithBitmapFactory(filePath: String, maxWidth: Int, maxHeight: Int) {
        try {
            val imageFile = File(filePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file does not exist: $filePath")
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                return
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            if (imageWidth <= 0 || imageHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${imageWidth}x$imageHeight")
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                return
            }
            Log.d(TAG, "Image original size: ${imageWidth}x$imageHeight")

            val sampleSize = calculateInSampleSize(imageWidth, imageHeight, maxWidth, maxHeight)
            Log.d(TAG, "Calculated sample size: $sampleSize")

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeFile(filePath, options)
            if (bitmap != null) {
                Log.d(TAG, "Bitmap loaded successfully: ${bitmap.width}x${bitmap.height}")
                imageView.setImageBitmap(bitmap)
            } else {
                Log.e(TAG, "Failed to decode bitmap from file: $filePath")
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image with BitmapFactory", e)
            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
        }
    }

    private fun calculateInSampleSize(imageWidth: Int, imageHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (imageHeight > reqHeight || imageWidth > reqWidth) {
            val halfHeight = imageHeight / 2
            val halfWidth = imageWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onDestroy() {
        super.onDestroy()
        aiStream.releaseAudioPlayer()
        aiStream.destroy()
    }

    companion object {
        const val TAG = TAG_PREFIX + "AiActivity"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        @JvmStatic
        fun start(context: Context, deviceId: String?) {
            context.startActivity(Intent(context, AIStreamActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_ID, deviceId)
            })
        }
    }
}
