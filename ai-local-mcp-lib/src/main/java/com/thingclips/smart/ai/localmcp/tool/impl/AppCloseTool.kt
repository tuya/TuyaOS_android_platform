package com.thingclips.smart.ai.localmcp.tool.impl

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult

/**
 * 应用关闭工具 —— 按分类强制停止正在运行的 App
 *
 * 工具名称: device.app.close
 *
 * 功能说明：
 * 与 [AppOpenTool] 共享分类映射，通过 ActivityManager.killBackgroundProcesses 关闭目标应用。
 * 当用户说"关掉音乐"、"退出视频"、"关闭微信"时触发。
 *
 * 参数说明：
 * - category (必填) 应用分类，与 device.app.open 相同（如 "music"、"video"、"im"）
 *
 * 调用示例：
 * ```json
 * {"category": "music"}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "Closed 网易云音乐"}
 * ```
 *
 * 注意：需要 KILL_BACKGROUND_PROCESSES 权限
 */
class AppCloseTool(
    private val appOpenTool: AppOpenTool
) : McpTool {
    private val TAG = "ai_local_mcp"

    override val name = "device.app.close"

    override val description: String
        get() = "Force-stop/close a running app by category. Same categories as device.app.open. " +
            "Use when user says 'close music app', 'stop the video', 'exit WeChat', 'quit office app'. " +
            "Match user intent to the closest category. Do NOT use for muting volume or canceling alarms."

    override val inputSchema: JSONObject
        get() {
            val schema = JSONObject()
            schema["type"] = "object"
            val props = JSONObject()
            val categoryProp = JSONObject()
            categoryProp["type"] = "string"
            categoryProp["description"] = "App category to close (e.g. music, video, im)"
            props["category"] = categoryProp
            schema["properties"] = props
            schema["required"] = listOf("category")
            return schema
        }

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        return try {
            val category = args.getString("category")
            if (category.isNullOrBlank()) {
                return ToolResult(status = "error", message = "Missing required argument: category")
            }

            val app = appOpenTool.getApp(category)
            if (app == null) {
                Log.w(TAG, "device.app.close: unknown category '$category'")
                return ToolResult(status = "error", message = "Unknown category: $category")
            }

            Log.i(TAG, "device.app.close: killing ${app.label} (${app.packageName})")
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(app.packageName)
            ToolResult(status = "success", message = "Closed ${app.label}")
        } catch (e: Exception) {
            Log.e(TAG, "device.app.close: failed", e)
            ToolResult(status = "error", message = "Failed to close app: ${e.message}")
        }
    }
}
