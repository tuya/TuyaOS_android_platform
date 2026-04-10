package com.tuya.smartai.demo

data class TextData(
    var bizId: String? = null,
    var bizType: String? = null,
    var eof: Int = 0,
    var text: String? = null,
    var imageUrl: String? = null
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrEmpty()
}
