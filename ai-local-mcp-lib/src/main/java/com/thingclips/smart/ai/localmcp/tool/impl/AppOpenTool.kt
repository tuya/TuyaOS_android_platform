package com.thingclips.smart.ai.localmcp.tool.impl

import android.content.Context
import android.content.Intent
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult

data class AppEntry(val packageName: String, val label: String)

/**
 * 应用启动工具 —— 按分类打开手机上已安装的 App
 *
 * 工具名称: device.app.open
 *
 * 功能说明：
 * 根据预注册的分类映射表，通过 category 匹配对应 App 并启动。
 * 当用户说"打开音乐"、"我想看视频"、"打开微信"时触发。
 *
 * 分类映射示例（通过 [updateApps] 动态更新）：
 * - music → 网易云音乐 (com.netease.cloudmusic)
 * - video → 抖音 (com.ss.android.ugc.aweme)
 * - im    → 微信 (com.tencent.mm)
 *
 * 参数说明：
 * - category (必填) 应用分类，如 "music"、"video"、"im"、"office"、"movie"
 *
 * 调用示例：
 * ```json
 * {"category": "music"}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "Opened 网易云音乐"}
 * ```
 */
class AppOpenTool(
    categoryApps: Map<String, AppEntry> = emptyMap()
) : McpTool {
    private val TAG = "ai_local_mcp"

    @Volatile
    private var appRegistry: Map<String, AppEntry> = categoryApps.toMap()

    fun updateApps(categoryApps: Map<String, AppEntry>) {
        appRegistry = categoryApps.toMap()
        Log.i(TAG, "device.app.open: appRegistry updated, categories=${appRegistry.keys}")
    }

    fun getApp(category: String): AppEntry? = appRegistry[category]

    override val name = "device.app.open"

    override val description: String
        get() {
            val categoryHints = mapOf(
                "music" to "听歌/播放音乐/放首歌",
                "movie" to "看电影/追剧/看电视剧",
                "video" to "刷短视频/看视频/刷抖音/刷快手",
                "office" to "办公/打开文档/开个会议",
                "im" to "发消息/聊天/打开微信/打开QQ"
            )
            val lines = appRegistry.entries.joinToString("; ") { (cat, app) ->
                val hint = categoryHints[cat] ?: cat
                "$cat → ${app.label} (triggers: $hint)"
            }
            return "Launch a phone app by category. Category mapping: [$lines]. " +
                "Use this tool when user wants to open any app, listen to music, watch videos, chat, or do office work. " +
                "Match user intent to the closest category. Do NOT use this tool for device settings like volume or alarms."
        }

    override val inputSchema: JSONObject
        get() {
            val categoryEnum = appRegistry.keys.toList()
            val schema = JSONObject()
            schema["type"] = "object"

            val props = JSONObject()
            val categoryProp = JSONObject()
            categoryProp["type"] = "string"
            categoryProp["enum"] = categoryEnum
            categoryProp["description"] = "App category: ${categoryEnum.joinToString(", ")}"
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

            val app = appRegistry[category]
            if (app == null) {
                Log.w(TAG, "device.app.open: unknown category '$category'")
                return ToolResult(status = "error", message = "Unknown category: $category. Available: ${appRegistry.keys}")
            }

            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent == null) {
                Log.w(TAG, "device.app.open: not installed: ${app.label} (${app.packageName})")
                return ToolResult(status = "error", message = "${app.label} is not installed")
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "device.app.open: launched ${app.label} (${app.packageName}) via category=$category")
            ToolResult(status = "success", message = "Opened ${app.label}")
        } catch (e: Exception) {
            Log.e(TAG, "device.app.open: failed", e)
            ToolResult(status = "error", message = "Failed to open app: ${e.message}")
        }
    }
}
