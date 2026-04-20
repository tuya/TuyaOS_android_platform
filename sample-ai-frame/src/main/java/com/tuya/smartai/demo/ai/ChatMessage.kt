package com.tuya.smartai.demo.ai

import android.net.Uri

data class ChatMessage(
    var text: String? = null,
    val imageUri: Uri? = null,
    var imageUrl: String? = null,
    val isSentByUser: Boolean,
    val messageType: MessageType,
    var bizId: String? = null,
    var isPlaceholder: Boolean = false,
    var placeholderIndex: Int = 0,
    var mcpToolName: String? = null,
    var mcpArgs: String? = null,
    var mcpResultStatus: String? = null
) {
    val timestamp: Long = System.currentTimeMillis()

    enum class MessageType {
        TEXT, IMAGE, VOICE_TO_TEXT, NLG_TEXT, NLG_IMAGE, NLG_IMAGE_PLACEHOLDER,
        MCP_TOOL_CALL,
        MCP_TOOL_RESULT
    }
}