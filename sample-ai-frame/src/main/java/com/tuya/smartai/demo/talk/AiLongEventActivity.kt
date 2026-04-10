package com.tuya.smartai.demo.talk

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tuya.smartai.demo.R
import com.tuya.smartai.demo.ai.AudioAmplitudeView
import com.tuya.smartai.demo.ai.ChatMessage
import org.json.JSONObject

/**
 * 设备身份版本的云端长 event 对话界面
 *
 * 与 devkit-ai-chat 的 AiLongEventActivity 功能对齐，
 * 差异仅在于使用设备身份（connectWithDevice）而非 App 身份连接。
 */
class AiLongEventActivity : AppCompatActivity(), AiLongEventEngine.LongEventListener {

    companion object {
        private const val TAG = "ai_stream_LongEvent"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        const val EXTRA_DEVICE_ID = "extra_device_id"

        fun start(context: Context, deviceId: String) {
            val intent = Intent(context, AiLongEventActivity::class.java)
            intent.putExtra(EXTRA_DEVICE_ID, deviceId)
            context.startActivity(intent)
        }
    }

    private lateinit var tvStatus: TextView
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var ivRecordButton: ImageView
    private lateinit var ivStopButton: ImageView
    private lateinit var audioAmplitudeView: AudioAmplitudeView

    private lateinit var chatAdapter: LongEventChatAdapter
    private val messageList = mutableListOf<ChatMessage>()

    private lateinit var aiLongEventEngine: AiLongEventEngine
    private lateinit var deviceId: String
    private var lastEventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_long_event)

        val devId = intent.getStringExtra(EXTRA_DEVICE_ID)
        if (devId.isNullOrEmpty()) {
            Toast.makeText(this, "Device ID is required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        deviceId = devId

        initViews()
        setupToolbar()
        setupChatRecyclerView()
        setupControls()

        aiLongEventEngine = AiLongEventEngine(this, deviceId, this)

        if (checkAndRequestAudioPermission()) {
            aiLongEventEngine.connect()
        }
    }

    override fun onPause() {
        super.onPause()
        aiLongEventEngine.stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        aiLongEventEngine.destroy()
    }

    // --- UI setup ---

    private fun initViews() {
        tvStatus = findViewById(R.id.tv_status)
        rvChatMessages = findViewById(R.id.rv_chat_messages)
        ivRecordButton = findViewById(R.id.iv_record_button)
        ivStopButton = findViewById(R.id.iv_stop_button)
        audioAmplitudeView = findViewById(R.id.audio_amplitude_view)

        setupStatusClickCopy()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setBackgroundColor(Color.parseColor("#1a1a2e"))
        toolbar.setTitleTextColor(Color.WHITE)
        supportActionBar?.apply {
            title = "AI Long Event (Device)"
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupChatRecyclerView() {
        chatAdapter = LongEventChatAdapter(this, messageList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = chatAdapter
        rvChatMessages.setBackgroundColor(Color.parseColor("#f8f9fa"))
    }

    private fun setupControls() {
        ivRecordButton.setOnClickListener {
            if (aiLongEventEngine.currentState == AiLongEventEngine.State.READY) {
                aiLongEventEngine.startRecording()
            } else {
                showToast("请等待系统就绪")
            }
        }
        ivStopButton.setOnClickListener { aiLongEventEngine.stopRecording() }

        setupButtonStyle(ivRecordButton, Color.parseColor("#4CAF50"))
        setupButtonStyle(ivStopButton, Color.parseColor("#f44336"))
    }

    private fun setupButtonStyle(button: ImageView, color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(4, Color.WHITE)
        }
        button.background = drawable
        button.elevation = 8f
    }

    private fun setupStatusClickCopy() {
        tvStatus.setOnClickListener { copySessionAndEventIds() }
        tvStatus.setPadding(tvStatus.paddingLeft, 16, tvStatus.paddingRight, 16)
        tvStatus.minHeight = 48
    }

    // --- User interaction ---

    private fun updateUiForState(state: AiLongEventEngine.State) {
        when (state) {
            AiLongEventEngine.State.IDLE -> {
                tvStatus.text = "未连接"
                tvStatus.setTextColor(Color.parseColor("#666666"))
            }
            AiLongEventEngine.State.CONNECTING -> {
                tvStatus.text = "连接中..."
                tvStatus.setTextColor(Color.parseColor("#2196F3"))
            }
            AiLongEventEngine.State.CREATING_SESSION -> {
                tvStatus.text = "创建会话中..."
                tvStatus.setTextColor(Color.parseColor("#FF9800"))
            }
            AiLongEventEngine.State.READY -> {
                tvStatus.text = "就绪 - 点击录音按钮开始"
                tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
            AiLongEventEngine.State.RECORDING -> {
                tvStatus.text = "录音中..."
                tvStatus.setTextColor(Color.parseColor("#f44336"))
            }
            AiLongEventEngine.State.ERROR -> {
                tvStatus.text = "错误"
                tvStatus.setTextColor(Color.parseColor("#f44336"))
            }
        }

        val isReady = state == AiLongEventEngine.State.READY
        val isRecording = state == AiLongEventEngine.State.RECORDING

        ivRecordButton.isEnabled = isReady
        ivRecordButton.alpha = if (isReady) 1.0f else 0.5f
        ivStopButton.isEnabled = isRecording
        ivStopButton.alpha = if (isRecording) 1.0f else 0.5f
        audioAmplitudeView.visibility = if (isRecording) View.VISIBLE else View.GONE
    }

    private fun addMessage(message: ChatMessage) {
        messageList.add(message)
        chatAdapter.notifyItemInserted(messageList.size - 1)
        rvChatMessages.scrollToPosition(messageList.size - 1)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun copySessionAndEventIds() {
        try {
            val currentEventId = aiLongEventEngine.getCurrentEventId()
            val eventIdToCopy = currentEventId?.takeIf { it.isNotEmpty() } ?: lastEventId

            val json = JSONObject().apply {
                put("sessionId", aiLongEventEngine.getSessionId() ?: "")
                put("eventId", eventIdToCopy ?: "")
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Session and Event IDs", json.toString(2)))
            showToast("已复制到剪贴板")
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed", e)
        }
    }

    // --- Permission ---

    private fun checkAndRequestAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                aiLongEventEngine.connect()
            } else {
                showToast("需要录音权限")
                finish()
            }
        }
    }

    // --- AiLongEventEngine.LongEventListener ---

    override fun onStateChanged(newState: AiLongEventEngine.State) {
        runOnUiThread { updateUiForState(newState) }
    }

    override fun onRecordingStarted() {
        runOnUiThread {
            addMessage(ChatMessage(text = "🎙️ 开始录音", isSentByUser = true, messageType = ChatMessage.MessageType.TEXT))
        }
    }

    override fun onRecordingStopped() {
        runOnUiThread {
            addMessage(ChatMessage(text = "⏹️ 停止录音", isSentByUser = true, messageType = ChatMessage.MessageType.TEXT))
        }
    }

    override fun onNlgInterrupted() {
        runOnUiThread {
            addMessage(ChatMessage(text = "⚡ 语音被中断", isSentByUser = true, messageType = ChatMessage.MessageType.TEXT))
        }
    }

    override fun onAsrResult(text: String, bizId: String) {
        runOnUiThread {
            addMessage(ChatMessage(text = text, isSentByUser = true, messageType = ChatMessage.MessageType.VOICE_TO_TEXT))
        }
    }

    override fun onNlgResult(eventId: String?, json: String) {
        runOnUiThread {
            if (!eventId.isNullOrEmpty()) lastEventId = eventId

            try {
                val jsonObject = JSONObject(json)
                val dataObject = jsonObject.optJSONObject("data") ?: return@runOnUiThread
                val bizId = jsonObject.optString("bizId")
                val content = dataObject.optString("content")
                val appendMode = dataObject.optString("appendMode", "")

                if (TextUtils.equals(appendMode, "append")) {
                    for (i in messageList.indices.reversed()) {
                        val msg = messageList[i]
                        if (!msg.isSentByUser && msg.bizId == bizId
                            && msg.messageType == ChatMessage.MessageType.NLG_TEXT
                        ) {
                            msg.text = (msg.text ?: "") + content
                            chatAdapter.notifyItemChanged(i)
                            rvChatMessages.scrollToPosition(messageList.size - 1)
                            return@runOnUiThread
                        }
                    }
                }

                addMessage(
                    ChatMessage(text = content, isSentByUser = false, messageType = ChatMessage.MessageType.NLG_TEXT, bizId = bizId)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Parse NLG result failed", e)
            }
        }
    }

    override fun onAudioAmplitudeUpdate(amplitudes: DoubleArray) {
        runOnUiThread {
            if (audioAmplitudeView.visibility == View.VISIBLE) {
                audioAmplitudeView.setAmplitudes(amplitudes)
            }
        }
    }

    override fun onAudioPlaybackStateChanged(
        state: AiLongEventEngine.PlaybackState, code: Int, msg: String?
    ) {
        runOnUiThread {
            when (state) {
                AiLongEventEngine.PlaybackState.STARTED -> Log.d(TAG, "Audio playback started")
                AiLongEventEngine.PlaybackState.FINISHED -> Log.d(TAG, "Audio playback finished")
                AiLongEventEngine.PlaybackState.ERROR -> {
                    Log.e(TAG, "Audio playback error: code=$code, msg=$msg")
                    showToast("音频播放错误: $msg")
                }
            }
        }
    }

    override fun onError(errorMessage: String) {
        runOnUiThread {
            showToast(errorMessage)
            Log.e(TAG, "Engine error: $errorMessage")
        }
    }
}
