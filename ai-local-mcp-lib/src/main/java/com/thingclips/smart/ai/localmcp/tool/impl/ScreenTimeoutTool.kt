package com.thingclips.smart.ai.localmcp.tool.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.ai.localmcp.tool.McpTool
import com.thingclips.smart.ai.localmcp.tool.ToolResult

/**
 * 屏幕超时设置工具 —— 设置/查询屏幕自动息屏时间
 *
 * 工具名称: device.screen.set_timeout
 *
 * 功能说明：
 * 通过 Settings.System.SCREEN_OFF_TIMEOUT 设置或查询屏幕自动锁定时间。
 * 当用户说"屏幕常亮"、"设置息屏时间"、"多久自动锁屏"时触发。
 *
 * 参数说明：
 * - timeout_ms (可选) 息屏超时时间（毫秒）：
 *   - 15000   = 15秒
 *   - 30000   = 30秒
 *   - 60000   = 1分钟
 *   - 120000  = 2分钟
 *   - 300000  = 5分钟
 *   - 600000  = 10分钟
 *   - 不传此参数则为查询当前设置
 *
 * 调用示例：
 * ```json
 * {"timeout_ms": 300000}
 * {}
 * ```
 * 返回示例：
 * ```json
 * {"type": "text", "status": "success", "text": "Screen timeout set to 300s"}
 * {"type": "text", "status": "success", "text": "Current screen timeout: 60s"}
 * ```
 *
 * 注意：设置息屏时间需要 WRITE_SETTINGS 权限，未授权时会自动跳转授权页面
 */
class ScreenTimeoutTool : McpTool {
    private val TAG = "ai_local_mcp"

    override val name = "device.screen.set_timeout"
    override val description = "Set or query how long the screen stays on before auto-lock. Use when user says 'keep screen on', 'set screen timeout', 'auto-lock time'. Provide timeout_ms in milliseconds."
    override val inputSchema: JSONObject
        get() = JSONObject.parseObject("""
            {
              "type": "object",
              "properties": {
                "timeout_ms": {
                  "type": "integer",
                  "description": "Screen timeout in milliseconds. Common values: 15000, 30000, 60000, 120000, 300000, 600000. Omit to query current setting."
                }
              }
            }
        """.trimIndent())

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        return try {
            val timeoutMs = args.getInteger("timeout_ms")

            if (timeoutMs == null) {
                val currentMs = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    60000
                )
                val seconds = currentMs / 1000
                Log.i(TAG, "device.screen.set_timeout: query result=${seconds}s")
                ToolResult(status = "success", message = "Current screen timeout: ${seconds}s")
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                    Log.w(TAG, "device.screen.set_timeout: WRITE_SETTINGS not granted, redirecting")
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return ToolResult(status = "error", message = "WRITE_SETTINGS permission not granted. Opening settings for authorization.")
                }
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    timeoutMs
                )
                val seconds = timeoutMs / 1000
                Log.i(TAG, "device.screen.set_timeout: set to ${seconds}s")
                ToolResult(status = "success", message = "Screen timeout set to ${seconds}s")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "device.screen.set_timeout: SecurityException", e)
            ToolResult(status = "error", message = "Permission denied: WRITE_SETTINGS permission required")
        } catch (e: Exception) {
            Log.e(TAG, "device.screen.set_timeout: failed", e)
            ToolResult(status = "error", message = "Failed to set screen timeout: ${e.message}")
        }
    }
}
