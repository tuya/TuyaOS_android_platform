package com.thingclips.smart.ai.localmcp.tool

import com.alibaba.fastjson.JSONObject

/**
 * MCP 工具执行结果
 *
 * @param type    结果类型，默认 "text"
 * @param status  执行状态："success" 表示成功，"error" 表示失败
 * @param message 结果描述信息，如 "Alarm set for 08:30" 或错误原因
 * @param data    附加数据（可选），用于返回结构化信息
 *
 * 示例：
 * ```kotlin
 * // 成功
 * ToolResult(status = "success", message = "Volume set to 80%")
 * // 失败
 * ToolResult(status = "error", message = "Missing required argument: hour")
 * ```
 */
data class ToolResult(
    val type: String = "text",
    val status: String,
    val message: String? = null,
    val data: JSONObject? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json["type"] = type
        json["status"] = status
        if (message != null) json["text"] = message
        if (data != null) json["data"] = data
        return json
    }
}
