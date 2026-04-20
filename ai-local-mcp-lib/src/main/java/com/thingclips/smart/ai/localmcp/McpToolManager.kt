package com.thingclips.smart.ai.localmcp

import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.bean.PayloadResponse
import com.thingclips.smart.ai.localmcp.tool.ToolRegistry

object McpToolManager {
    private const val TAG = "ai_local_mcp"

    internal var registry = ToolRegistry()
    internal var context: Context? = null

    fun handleToolList(json: JSONObject): PayloadResponse {
        val response = JSONObject()
        response["jsonrpc"] = json["jsonrpc"]
        response["id"] = json["id"]
        val result = JSONObject()
        result["tools"] = registry.asToolJsonArray()
        response["result"] = result
        Log.i(TAG, "handleToolList response: $response")
        return PayloadResponse(response.toJSONString())
    }

    suspend fun handleToolCall(json: JSONObject): PayloadResponse {
        val params = json.getJSONObject("params")
        if (params == null) {
            Log.w(TAG, "handleToolCall: params is null")
            return buildErrorResponse(json, -32602, "Invalid params", null)
        }
        val name = params.getString("name")
        val arguments = params.getJSONObject("arguments") ?: JSONObject()

        val ctx = context
        if (ctx == null) {
            Log.e(TAG, "handleToolCall: context is null")
            return buildErrorResponse(json, -32603, "Tool execution failed",
                JSONObject().apply {
                    put("tool_name", name)
                    put("reason", "Context not initialized")
                })
        }

        val tool = registry.find(name)
        if (tool == null) {
            Log.w(TAG, "handleToolCall: tool not found: $name")
            return buildErrorResponse(json, -32601, "Tool not found",
                JSONObject().apply {
                    put("tool_name", name)
                    put("reason", "No tool registered with name '$name'")
                })
        }

        return try {
            Log.i(TAG, "handleToolCall start: $name, args=$arguments")
            val result = tool.invoke(ctx, arguments)
            Log.i(TAG, "handleToolCall end: $name, result=$result")

            val contentArray = JSONArray()
            contentArray.add(result.toJson())

            val resultObj = JSONObject()
            resultObj["content"] = contentArray

            val response = JSONObject()
            response["jsonrpc"] = json["jsonrpc"]
            response["id"] = json["id"]
            response["result"] = resultObj

            PayloadResponse(response.toJSONString())
        } catch (e: Exception) {
            Log.e(TAG, "handleToolCall error: $name", e)
            buildErrorResponse(json, -32603, "Tool execution failed",
                JSONObject().apply {
                    put("tool_name", name)
                    put("reason", e.message ?: "Unknown error")
                })
        }
    }

    private fun buildErrorResponse(
        json: JSONObject,
        code: Int,
        message: String,
        data: JSONObject?
    ): PayloadResponse {
        val error = JSONObject()
        error["code"] = code
        error["message"] = message
        if (data != null) error["data"] = data

        val response = JSONObject()
        response["jsonrpc"] = json["jsonrpc"] ?: "2.0"
        response["id"] = json["id"]
        response["error"] = error

        Log.w(TAG, "buildErrorResponse: $response")
        return PayloadResponse(response.toJSONString())
    }
}
