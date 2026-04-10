package com.tuya.smartai.demo.ai

import android.net.Uri

data class ChatMessage(
    var text: String? = null,
    val imageUri: Uri? = null, // For local images to be sent
    var imageUrl: String? = null, // For received images (URL), now mutable for placeholder replacement
    val isSentByUser: Boolean,
    val messageType: MessageType,
    var bizId: String? = null, // For NLG messages, to handle append
    var isPlaceholder: Boolean = false, // 是否为占位图
    var placeholderIndex: Int = 0 // 占位图索引（当一次生成多张图时）
) {
    val timestamp: Long = System.currentTimeMillis()

    enum class MessageType {
        TEXT, IMAGE, VOICE_TO_TEXT, NLG_TEXT, NLG_IMAGE, NLG_IMAGE_PLACEHOLDER
    }
}