package com.thingclips.smart.ai.localmcp.tool

import android.content.Context
import com.alibaba.fastjson.JSONObject

/**
 * MCP 工具接口 —— 所有本地 MCP 工具的基类
 *
 * 每个工具需要实现以下属性和方法：
 * - [name]        工具唯一标识，如 "device.alarm.set"
 * - [description] 工具功能描述，供 AI 模型理解何时调用此工具
 * - [inputSchema] JSON Schema，定义工具接受的输入参数结构
 * - [invoke]      工具的实际执行逻辑，返回 [ToolResult]
 */
interface McpTool {
    val name: String
    val description: String
    val inputSchema: JSONObject
    suspend fun invoke(context: Context, args: JSONObject): ToolResult
}
