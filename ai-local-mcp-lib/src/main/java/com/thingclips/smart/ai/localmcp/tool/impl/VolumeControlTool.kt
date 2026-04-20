package com.thingclips.smart.ai.localmcp.tool.impl

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult

/**
 * 音量控制工具 —— 调节/查询设备音量
 *
 * 工具名称: device.audio.set_volume
 *
 * 功能说明：
 * 通过 AudioManager 控制设备的媒体、铃声或通知音量。
 * 当用户说"声音大一点"、"把音量调到50"、"静音"、"当前音量多少"时触发。
 *
 * 参数说明：
 * - action (必填) 操作类型：
 *   - "get"  查询当前音量
 *   - "set"  设置绝对音量（需配合 level）
 *   - "up"   音量增大一格
 *   - "down" 音量减小一格
 * - level  (可选) 目标音量 0-100，仅在 action="set" 时使用
 * - stream (可选) 音频流类型："music"(默认)、"ring"、"notification"
 *
 * 调用示例：
 * ```json
 * {"action": "set", "level": 80, "stream": "music"}
 * {"action": "up"}
 * {"action": "get"}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "Volume set to 80%"}
 * {"type": "text", "status": "success", "text": "Current volume: 60%"}
 * ```
 */
class VolumeControlTool : McpTool {
    private val TAG = "ai_local_mcp"

    override val name = "device.audio.set_volume"
    override val description = "Adjust or query device media/ringtone volume level. Use ONLY when user explicitly asks to change volume (louder, quieter, mute, set volume). Do NOT use for alarms, timers or scheduling."
    override val inputSchema: JSONObject
        get() = JSONObject.parseObject("""
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "enum": ["get", "set", "up", "down"],
                  "description": "Volume action: get, set, up, down"
                },
                "level": {
                  "type": "integer",
                  "description": "Absolute volume level 0-100 (used with 'set' action)"
                },
                "stream": {
                  "type": "string",
                  "enum": ["music", "ring", "notification"],
                  "description": "Audio stream type, default: music"
                }
              },
              "required": ["action"]
            }
        """.trimIndent())

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val streamType = when (args.getString("stream")?.lowercase()) {
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_MUSIC
            }
            val action = args.getString("action") ?: "get"

            Log.i(TAG, "device.audio.set_volume: action=$action, stream=${args.getString("stream")}")

            when (action.lowercase()) {
                "set" -> {
                    val level = args.getInteger("level")
                        ?: return ToolResult(status = "error", message = "Missing required argument: level")
                    val maxVolume = audioManager.getStreamMaxVolume(streamType)
                    val actual = (level.coerceIn(0, 100) * maxVolume) / 100
                    audioManager.setStreamVolume(streamType, actual, AudioManager.FLAG_SHOW_UI)
                    Log.i(TAG, "device.audio.set_volume: set to $level%")
                    ToolResult(status = "success", message = "Volume set to $level%")
                }
                "up" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    val current = audioManager.getStreamVolume(streamType)
                    val max = audioManager.getStreamMaxVolume(streamType)
                    val percentage = if (max > 0) (current * 100) / max else 0
                    Log.i(TAG, "device.audio.set_volume: raised, current=$percentage%")
                    ToolResult(status = "success", message = "Volume raised. Current: $percentage%")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    val current = audioManager.getStreamVolume(streamType)
                    val max = audioManager.getStreamMaxVolume(streamType)
                    val percentage = if (max > 0) (current * 100) / max else 0
                    Log.i(TAG, "device.audio.set_volume: lowered, current=$percentage%")
                    ToolResult(status = "success", message = "Volume lowered. Current: $percentage%")
                }
                else -> {
                    val current = audioManager.getStreamVolume(streamType)
                    val max = audioManager.getStreamMaxVolume(streamType)
                    val percentage = if (max > 0) (current * 100) / max else 0
                    Log.i(TAG, "device.audio.set_volume: query result=$percentage%")
                    ToolResult(status = "success", message = "Current volume: $percentage%")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "device.audio.set_volume: failed", e)
            ToolResult(status = "error", message = "Failed to control volume: ${e.message}")
        }
    }
}
