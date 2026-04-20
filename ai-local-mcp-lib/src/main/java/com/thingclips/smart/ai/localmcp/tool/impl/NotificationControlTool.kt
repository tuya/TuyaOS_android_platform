package com.thingclips.smart.ai.localmcp.tool.impl

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult

/**
 * 通知管理工具 —— 管理免打扰模式和通知设置
 *
 * 工具名称: device.notification.control
 *
 * 功能说明：
 * 通过 NotificationManager 查询/切换免打扰(DND)模式，或跳转通知设置页面。
 * 当用户说"开启免打扰"、"关闭通知"、"打开通知设置"时触发。
 *
 * 参数说明：
 * - action   (必填) 操作类型：
 *   - "query"         查询当前通知和 DND 状态
 *   - "open_settings"  跳转到应用通知设置页面
 *   - "toggle_dnd"    切换免打扰模式（需配合 dnd_mode）
 * - dnd_mode (可选) 免打扰模式，仅在 action="toggle_dnd" 时使用：
 *   - "off"           关闭免打扰
 *   - "priority"      仅允许优先通知
 *   - "alarms"        仅允许闹钟
 *   - "total_silence" 完全静音
 *
 * 调用示例：
 * ```json
 * {"action": "query"}
 * {"action": "toggle_dnd", "dnd_mode": "priority"}
 * {"action": "open_settings"}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "{\"notifications_enabled\":true,\"dnd_mode\":\"off\"}"}
 * {"type": "text", "status": "success", "text": "Do Not Disturb set to: priority"}
 * ```
 *
 * 注意：切换 DND 需要 NOTIFICATION_POLICY_ACCESS 权限，未授权时会自动跳转授权页面
 */
class NotificationControlTool : McpTool {
    private val TAG = "ai_local_mcp"

    override val name = "device.notification.control"
    override val description = "Manage Do Not Disturb mode and notification settings. Use when user says 'turn on/off DND', 'mute notifications', 'open notification settings'."
    override val inputSchema: JSONObject
        get() = JSONObject.parseObject("""
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "enum": ["query", "open_settings", "toggle_dnd"],
                  "description": "Notification action type"
                },
                "dnd_mode": {
                  "type": "string",
                  "enum": ["off", "priority", "alarms", "total_silence"],
                  "description": "Do Not Disturb mode (used with toggle_dnd action)"
                }
              },
              "required": ["action"]
            }
        """.trimIndent())

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        val action = args.getString("action")
            ?: return ToolResult(status = "error", message = "Missing required argument: action")

        Log.i(TAG, "device.notification.control: action=$action")

        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            when (action.lowercase()) {
                "query" -> {
                    val notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        notificationManager.areNotificationsEnabled()
                    } else {
                        true
                    }
                    val dndFilter = notificationManager.currentInterruptionFilter
                    val dndStatus = when (dndFilter) {
                        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
                        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
                        NotificationManager.INTERRUPTION_FILTER_NONE -> "total_silence"
                        else -> "unknown"
                    }
                    val result = JSONObject()
                    result["notifications_enabled"] = notificationsEnabled
                    result["dnd_mode"] = dndStatus
                    Log.i(TAG, "device.notification.control: query result=$result")
                    ToolResult(status = "success", message = result.toJSONString())
                }

                "open_settings" -> {
                    Log.i(TAG, "device.notification.control: opening settings")
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(status = "success", message = "Opened notification settings")
                }

                "toggle_dnd" -> {
                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        Log.w(TAG, "device.notification.control: DND policy not granted, redirecting")
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        return ToolResult(status = "error", message = "DND policy access not granted. Opening settings for permission.")
                    }
                    val dndMode = args.getString("dnd_mode")?.lowercase() ?: "off"
                    val filter = when (dndMode) {
                        "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
                        "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                        "total_silence" -> NotificationManager.INTERRUPTION_FILTER_NONE
                        else -> return ToolResult(status = "error", message = "Invalid dnd_mode: $dndMode. Use: off, priority, alarms, total_silence")
                    }
                    notificationManager.setInterruptionFilter(filter)
                    Log.i(TAG, "device.notification.control: DND set to $dndMode")
                    ToolResult(status = "success", message = "Do Not Disturb set to: $dndMode")
                }

                else -> ToolResult(status = "error", message = "Invalid action: $action. Use: query, open_settings, toggle_dnd")
            }
        } catch (e: Exception) {
            Log.e(TAG, "device.notification.control: failed", e)
            ToolResult(status = "error", message = "Failed to manage notifications: ${e.message}")
        }
    }
}
