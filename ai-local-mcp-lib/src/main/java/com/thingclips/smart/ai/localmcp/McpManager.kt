package com.thingclips.smart.ai.localmcp

import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.bean.PayloadResponse

object McpManager {
    private const val TAG = "ai_local_mcp"

    private const val METHOD_INIT = "initialize"
    private const val METHOD_INIT_SUCCESS = "notifications/initialized"
    private const val METHOD_TOOL_LIST = "tools/list"
    private const val METHOD_TOOL_CALL = "tools/call"

    const val MCP_NAME = "app-local-server"
    const val MCP_VERSION = "1.0.0"

    suspend fun handlePayload(payload: String): PayloadResponse? {
        return try {
            Log.i(TAG, "handlePayload: $payload")
            val json = JSONObject.parseObject(payload)
            val method = json.getString("method")

            when (method) {
                METHOD_INIT -> handleInit(json)
                METHOD_INIT_SUCCESS -> null
                METHOD_TOOL_LIST -> McpToolManager.handleToolList(json)
                METHOD_TOOL_CALL -> McpToolManager.handleToolCall(json)
                else -> {
                    Log.w(TAG, "unknown method: $method")
                    buildMethodNotFoundError(json, method)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handlePayload error: ${e.message}")
            null
        }
    }

    private fun handleInit(json: JSONObject): PayloadResponse {
        val protocolVersion = json.getJSONObject("params")?.getString("protocolVersion") ?: "2025-06-18"

        val tools = JSONObject()
        tools["listChanged"] = true

        val capabilities = JSONObject()
        capabilities["tools"] = tools
        capabilities["resources"] = JSONObject()

        val serverInfo = JSONObject()
        serverInfo["name"] = MCP_NAME
        serverInfo["version"] = MCP_VERSION

        val result = JSONObject()
        result["protocolVersion"] = protocolVersion
        result["capabilities"] = capabilities
        result["serverInfo"] = serverInfo

        val response = JSONObject()
        response["jsonrpc"] = json["jsonrpc"] ?: "2.0"
        response["id"] = json["id"]
        response["result"] = result

        Log.i(TAG, "handleInit response: $response")
        return PayloadResponse(response.toJSONString())
    }

    private fun buildMethodNotFoundError(json: JSONObject, method: String?): PayloadResponse {
        val error = JSONObject()
        error["code"] = -32601
        error["message"] = "Method not found: $method"

        val response = JSONObject()
        response["jsonrpc"] = json["jsonrpc"] ?: "2.0"
        response["id"] = json["id"]
        response["error"] = error

        Log.w(TAG, "buildMethodNotFoundError: $response")
        return PayloadResponse(response.toJSONString())
    }
}
