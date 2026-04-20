package com.thingclips.smart.ai.localmcp

import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool

object McpLocalManager {
    private const val TAG = "ai_local_mcp"

    fun init(context: Context) {
        McpToolManager.context = context.applicationContext
        Log.i(TAG, "McpLocalManager initialized")
    }

    fun register(vararg tools: McpTool) {
        tools.forEach { tool ->
            Log.i(TAG, "register: ${tool.name}")
        }
        McpToolManager.registry.register(*tools)
    }

    fun unregister(name: String) {
        Log.i(TAG, "unregister: $name")
        McpToolManager.registry.unregister(name)
    }

    suspend fun handlePayload(payload: String): String? {
        return McpManager.handlePayload(payload)?.response
    }

    fun buildUserData(): String? {
        if (McpToolManager.registry.isEmpty()) {
            Log.w(TAG, "buildUserData: no tools registered, returning null")
            return null
        }
        val deviceMcp = JSONObject().apply {
            put("mcpVersion", McpManager.MCP_VERSION)
            put("mcpTools", McpToolManager.registry.asToolJsonArray())
            put("supportCustomMCP", true)
        }
        val sessionAttributes = JSONObject().apply {
            put("deviceMcp", deviceMcp)
        }
        val userData = JSONObject().apply {
            put("sessionAttributes", sessionAttributes)
        }
        val result = userData.toJSONString()
        Log.i(TAG, "buildUserData: $result")
        return result
    }
}
