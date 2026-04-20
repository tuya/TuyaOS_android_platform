package com.thingclips.smart.ai.localmcp.tool.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult
import java.util.Calendar

/**
 * 闹钟设置工具 —— 设置系统闹钟/提醒
 *
 * 工具名称: device.alarm.set
 *
 * 功能说明：
 * 通过系统 AlarmClock Intent 设置闹钟，支持指定时间、标签和重复日期。
 * 当用户说"设个闹钟"、"明天早上8点叫我"、"每天7点提醒我"时触发。
 *
 * 参数说明：
 * - hour    (必填) 闹钟小时，24小时制 (0-23)
 * - minute  (必填) 闹钟分钟 (0-59)
 * - message (可选) 闹钟标签，如 "起床上班"
 * - days    (可选) 重复日期，逗号分隔：MON,TUE,WED,THU,FRI,SAT,SUN；不填则为单次闹钟
 *
 * 调用示例：
 * ```json
 * {"hour": 8, "minute": 30, "message": "起床上班", "days": "MON,TUE,WED,THU,FRI"}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "Alarm set for 08:30"}
 * ```
 */
class AlarmSetTool : McpTool {
    private val TAG = "ai_local_mcp"

    override val name = "device.alarm.set"
    override val description = "Set a wake-up alarm or reminder at a specific time. Use when user says 'set an alarm', 'wake me up at', 'remind me at [time]'. Requires hour (0-23) and minute (0-59). This is NOT for volume control."
    override val inputSchema: JSONObject
        get() = JSONObject.parseObject("""
            {
              "type": "object",
              "properties": {
                "hour": {
                  "type": "integer",
                  "description": "Alarm hour in 24-hour format (0-23)"
                },
                "minute": {
                  "type": "integer",
                  "description": "Alarm minute (0-59)"
                },
                "message": {
                  "type": "string",
                  "description": "Alarm label or message"
                },
                "days": {
                  "type": "string",
                  "description": "Repeat days comma-separated: MON,TUE,WED,THU,FRI,SAT,SUN. Empty for one-time alarm"
                }
              },
              "required": ["hour", "minute"]
            }
        """.trimIndent())

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        val hour = args.getInteger("hour")
            ?: return ToolResult(status = "error", message = "Missing required argument: hour")
        val minute = args.getInteger("minute")
            ?: return ToolResult(status = "error", message = "Missing required argument: minute")

        return try {
            Log.i(TAG, "device.alarm.set: hour=$hour, minute=$minute")
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                args.getString("message")?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                putExtra(AlarmClock.EXTRA_VIBRATE, true)
                args.getString("days")?.let { daysStr ->
                    val daysList = ArrayList<Int>()
                    daysStr.split(",").forEach { day ->
                        val calendarDay = when (day.trim().uppercase()) {
                            "MON" -> Calendar.MONDAY
                            "TUE" -> Calendar.TUESDAY
                            "WED" -> Calendar.WEDNESDAY
                            "THU" -> Calendar.THURSDAY
                            "FRI" -> Calendar.FRIDAY
                            "SAT" -> Calendar.SATURDAY
                            "SUN" -> Calendar.SUNDAY
                            else -> null
                        }
                        if (calendarDay != null) daysList.add(calendarDay)
                    }
                    if (daysList.isNotEmpty()) putExtra(AlarmClock.EXTRA_DAYS, daysList)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formattedTime = String.format("%02d:%02d", hour, minute)
            Log.i(TAG, "device.alarm.set: set for $formattedTime")
            ToolResult(status = "success", message = "Alarm set for $formattedTime")
        } catch (e: Exception) {
            Log.e(TAG, "device.alarm.set: failed", e)
            ToolResult(status = "error", message = "Failed to set alarm: ${e.message}")
        }
    }
}
