package com.thingclips.smart.ai.localmcp.tool

import android.util.Log
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject

/**
 * MCP 工具注册中心 —— 管理所有已注册的 [McpTool]
 *
 * 负责工具的注册、注销、查找，以及将工具列表序列化为 JSON 数组供协议上报。
 *
 * 示例：
 * ```kotlin
 * val registry = ToolRegistry()
 * registry.register(AlarmSetTool(), VolumeControlTool())
 * val json = registry.asToolJsonArray() // 上报给云端
 * val tool = registry.find("device.alarm.set") // 根据名称查找工具
 * ```
 */
class ToolRegistry {
    private val TAG = "ai_local_mcp"
    private val tools = mutableListOf<McpTool>()

    fun register(vararg mcpTools: McpTool) {
        mcpTools.forEach { tool ->
            Log.i(TAG, "register tool: ${tool.name}")
            tools.add(tool)
        }
    }

    fun unregister(name: String) {
        tools.removeAll { it.name == name }
    }

    fun asToolJsonArray(): JSONArray {
        val array = JSONArray()
        for (tool in tools) {
            val item = JSONObject()
            item.put("name", tool.name)
            item.put("description", tool.description)
            item.put("inputSchema", tool.inputSchema)
            array.add(item)
        }
        return array
    }

    fun find(name: String): McpTool? = tools.firstOrNull { it.name == name }

    fun isEmpty(): Boolean = tools.isEmpty()

    fun getToolNames(): List<String> = tools.map { it.name }
}
