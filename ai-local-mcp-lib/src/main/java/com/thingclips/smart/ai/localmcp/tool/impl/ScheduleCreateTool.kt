package com.thingclips.smart.ai.localmcp.tool.impl

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 日程创建工具 —— 在系统日历中创建事件
 *
 * 工具名称: device.calendar.create_event
 *
 * 功能说明：
 * 通过系统日历 Intent 创建日程/会议事件，支持设置标题、时间、描述和提前提醒。
 * 当用户说"帮我建个日程"、"安排一个会议"、"下周三下午3点提醒我开会"时触发。
 * 注意：仅用于日历事件，如果用户想设定时闹钟应使用 device.alarm.set。
 *
 * 参数说明：
 * - title            (必填) 事件标题，如 "团队周会"
 * - description      (可选) 事件描述/备注
 * - begin_time       (可选) 开始时间，epoch 毫秒；不填则为当前时间
 * - end_time         (可选) 结束时间，epoch 毫秒；不填则为开始时间 +1 小时
 * - reminder_minutes (可选) 提前提醒分钟数，默认 10 分钟
 *
 * 调用示例：
 * ```json
 * {"title": "团队周会", "begin_time": 1713340800000, "end_time": 1713344400000, "reminder_minutes": 15}
 * {"title": "牙医预约", "description": "带上病历本"}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "Calendar event created: 团队周会 at 2026-04-17 14:00"}
 * ```
 */
class ScheduleCreateTool : McpTool {
    private val TAG = "ai_local_mcp"

    override val name = "device.calendar.create_event"
    override val description = "Create a calendar event or schedule. Use when user says 'add an event', 'schedule a meeting', 'put on my calendar'. NOT for alarms or reminders at a specific time — use device.alarm.set for that."
    override val inputSchema: JSONObject
        get() = JSONObject.parseObject("""
            {
              "type": "object",
              "properties": {
                "title": {
                  "type": "string",
                  "description": "Event title"
                },
                "description": {
                  "type": "string",
                  "description": "Event description or notes"
                },
                "begin_time": {
                  "type": "integer",
                  "description": "Event start time as epoch milliseconds"
                },
                "end_time": {
                  "type": "integer",
                  "description": "Event end time as epoch milliseconds. Default: begin_time + 1 hour"
                },
                "reminder_minutes": {
                  "type": "integer",
                  "description": "Minutes before event to show reminder. Default: 10"
                }
              },
              "required": ["title"]
            }
        """.trimIndent())

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        val title = args.getString("title")
            ?: return ToolResult(status = "error", message = "Missing required argument: title")

        Log.i(TAG, "device.calendar.create_event: title=$title")

        return try {
            val beginMillis = args.getLong("begin_time") ?: System.currentTimeMillis()
            val endMillis = args.getLong("end_time") ?: (beginMillis + 3600000L)
            val reminderMinutes = args.getInteger("reminder_minutes") ?: 10

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                args.getString("description")?.let {
                    putExtra(CalendarContract.Events.DESCRIPTION, it)
                }
                putExtra(CalendarContract.Reminders.MINUTES, reminderMinutes)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val timeStr = dateFormat.format(beginMillis)
            Log.i(TAG, "device.calendar.create_event: event created at $timeStr")
            ToolResult(status = "success", message = "Calendar event created: $title at $timeStr")
        } catch (e: Exception) {
            Log.e(TAG, "device.calendar.create_event: failed", e)
            ToolResult(status = "error", message = "Failed to create calendar event: ${e.message}")
        }
    }
}
