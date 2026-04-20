package com.tuya.smartai.demo.talk

enum class State {
    IDLE, CONNECTING, CREATING_SESSION, READY, RECORDING, ERROR
}

enum class PlaybackState {
    STARTED, FINISHED, ERROR
}

/**
 * MCP 工具调用信息，用于 UI 展示和弹窗确认
 */
data class McpToolCallInfo(
    val toolName: String,
    val argsJson: String,
    val onResult: (success: Boolean) -> Unit
)

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

    fun onMcpToolCall(info: McpToolCallInfo)
    fun onMcpToolResult(toolName: String, argsJson: String, status: String, message: String)
}

/**
 * Engine 公共接口，Activity 面向此接口编程，可快速切换实现
 */
interface ILongEventEngine {
    val currentState: State
    fun connect()
    fun startRecording()
    fun stopRecording()
    fun stopPlayAudio()
    fun closeEvent()
    fun getSessionId(): String?
    fun getCurrentEventId(): String?
    fun destroy()
}
