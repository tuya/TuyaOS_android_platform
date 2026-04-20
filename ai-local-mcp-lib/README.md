# ai-local-mcp-lib

Android 本地 MCP 工具库，用于把 App 侧本地能力以 `deviceMcp` 的形式上报给 AI 会话，并通过 MCP 协议完成 `initialize`、`tools/list`、`tools/call`。

当前模块适合这类场景：
- 本地设置控制：音量、闹钟、通知、锁屏时长、日程
- 本地 App 控制：按业务定义的分类打开/关闭 App
- 业务需要按需注册工具，而不是全部默认暴露
- 业务希望后续自行扩展新的 MCP Tool

---

## 1. 整体接入流程

```text
业务 Activity/Engine
    │
    ├─ 1. McpLocalManager.init(context)
    ├─ 2. McpLocalManager.register(tool1, tool2, ...)
    ├─ 3. createSession(..., McpLocalManager.buildUserData(), ...)
    │
云端 Session 创建
    │
    ├─ 4. 云端读取 userData.sessionAttributes.deviceMcp
    ├─ 5. 云端发 MCP initialize
    ├─ 6. 云端发 MCP tools/list
    └─ 7. 云端发 MCP tools/call
            │
            └─ App 收到 MCP event → McpLocalManager.handlePayload(payload)
                              │
                              └─ ToolRegistry 路由到对应 Tool.invoke()
```

在当前 demo 中：
- `AiLongEventActivity.java` 负责初始化和注册 tool
- `AiLongEventEngine.kt` 负责 session 创建、MCP event 收发

---

## 2. 核心类说明

### `McpLocalManager`

对业务暴露的唯一入口：

```kotlin
McpLocalManager.init(context)
McpLocalManager.register(tool1, tool2)
val userData = McpLocalManager.buildUserData()
val response = McpLocalManager.handlePayload(payload)
```

职责：
- 保存 application context
- 注册/反注册工具
- 生成 session 所需 `deviceMcp`
- 处理云端发来的 MCP 请求

### `McpManager`

负责协议路由：
- `initialize`
- `notifications/initialized`
- `tools/list`
- `tools/call`

### `ToolRegistry`

负责维护工具列表，并把工具信息转换成 `mcpTools`：
- `name`
- `description`
- `inputSchema`

### `McpTool`

所有自定义工具都实现这个接口：

```kotlin
interface McpTool {
    val name: String
    val description: String
    val inputSchema: JSONObject
    suspend fun invoke(context: Context, args: JSONObject): ToolResult
}
```

---

## 3. Session 接入方式

在创建 session 时，把 `buildUserData()` 作为 `userData` 传给基座。

参考：`devkit-ai-chat/.../AiLongEventEngine.kt`

```kotlin
aiStream.createSession(params, McpLocalManager.buildUserData(), callback)
```

当前生成的结构如下：

```json
{
  "sessionAttributes": {
    "deviceMcp": {
      "mcpVersion": "1.0.0",
      "mcpTools": [
        {
          "name": "device.audio.set_volume",
          "description": "...",
          "inputSchema": { }
        }
      ],
      "supportCustomMCP": true
    }
  }
}
```

说明：
- `mcpTools` 来自当前已注册的 tool
- 未注册的 tool 不会上报
- 这是按需配置的，不是全量默认注册

---

## 4. MCP event 处理方式

在接收 `MCP_CMD` 事件后，把 payload 交给 `McpLocalManager.handlePayload()`：

```kotlin
val response = McpLocalManager.handlePayload(payloadString)
if (response != null) {
    sendMcpResponse(sessionId, eventId, response)
}
```

当前已支持：
- `initialize` → 返回 serverInfo / capabilities
- `tools/list` → 返回当前注册工具列表
- `tools/call` → 路由到本地 tool
- 未知 method / 执行异常 → 返回标准 JSON-RPC error

---

## 5. 当前内置工具

| Tool Name | 说明 |
|---|---|
| `device.audio.set_volume` | 音量查询 / 设置 / 增减 |
| `device.alarm.set` | 设置闹钟 |
| `device.notification.control` | 通知 / 勿扰 |
| `device.screen.set_timeout` | 屏幕超时 |
| `device.calendar.create_event` | 创建日程 |
| `device.app.open` | 按分类打开 App |
| `device.app.close` | 按分类关闭 App |

---

## 6. App 打开/关闭的配置方式

当前 `device.app.open` / `device.app.close` 不再依赖 SP，也不从工具参数中传 `preferred_apps`。

改为：**创建 tool 时直接注入 category → app 映射**。

```java
Map<String, AppEntry> defaultApps = new HashMap<>();
defaultApps.put("music", new AppEntry("com.netease.cloudmusic", "网易云音乐"));
defaultApps.put("movie", new AppEntry("com.youku.phone", "优酷"));
defaultApps.put("video", new AppEntry("com.ss.android.ugc.aweme", "抖音"));
defaultApps.put("office", new AppEntry("cn.wps.moffice_eng", "WPS"));
defaultApps.put("im", new AppEntry("com.tencent.mm", "微信"));

AppOpenTool appOpenTool = new AppOpenTool(defaultApps);
AppCloseTool appCloseTool = new AppCloseTool(appOpenTool);
```

### 支持的默认分类

| category | 含义 |
|---|---|
| `music` | 音乐类 App |
| `movie` | 长视频 / 影视类 App |
| `video` | 短视频类 App |
| `office` | 办公类 App |
| `im` | IM / 聊天类 App |

### 运行时更新映射

如果业务想在登录后、配置下发后、设置页保存后更新映射：

```kotlin
appOpenTool.updateApps(newApps)
```

`AppCloseTool` 复用 `AppOpenTool` 的映射，所以更新一次即可同时对 open/close 生效。

---

## 7. 业务接入示例

参考：`devkit-ai-chat/.../AiLongEventActivity.java`

```java
McpLocalManager.INSTANCE.init(this);

Map<String, AppEntry> defaultApps = new HashMap<>();
defaultApps.put("music", new AppEntry("com.netease.cloudmusic", "网易云音乐"));
defaultApps.put("movie", new AppEntry("com.youku.phone", "优酷"));
defaultApps.put("video", new AppEntry("com.ss.android.ugc.aweme", "抖音"));
defaultApps.put("office", new AppEntry("cn.wps.moffice_eng", "WPS"));
defaultApps.put("im", new AppEntry("com.tencent.mm", "微信"));

AppOpenTool appOpenTool = new AppOpenTool(defaultApps);
AppCloseTool appCloseTool = new AppCloseTool(appOpenTool);

McpLocalManager.INSTANCE.register(
    new VolumeControlTool(),
    new AlarmSetTool(),
    new NotificationControlTool(),
    new ScreenTimeoutTool(),
    new ScheduleCreateTool(),
    appOpenTool,
    appCloseTool
);
```

---

## 8. 如何扩展新工具

新增一个本地能力，推荐按下面步骤做。

### 第一步：实现 `McpTool`

```kotlin
class FlashLightTool : McpTool {
    override val name = "device.flashlight.control"

    override val description = "Turn flashlight on or off. Use when user says turn on flashlight or close flashlight."

    override val inputSchema: JSONObject
        get() = JSONObject.parseObject("""
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "enum": ["on", "off"]
                }
              },
              "required": ["action"]
            }
        """.trimIndent())

    override suspend fun invoke(context: Context, args: JSONObject): ToolResult {
        val action = args.getString("action")
            ?: return ToolResult(status = "error", message = "Missing required argument: action")

        return try {
            // do something
            ToolResult(status = "success", message = "Flashlight $action")
        } catch (e: Exception) {
            ToolResult(status = "error", message = "Failed: ${e.message}")
        }
    }
}
```

### 第二步：注册工具

```kotlin
McpLocalManager.register(FlashLightTool())
```

### 第三步：重新创建 session 或重新进入页面验证

因为 `mcpTools` 是在 `buildUserData()` 时上报的，所以：
- 新工具要在 `createSession()` 之前注册
- 如果 session 已经创建完成，再注册不会自动同步到当前 session
- 这类变更通常需要重建 session

---

## 9. 二次开发建议

### 9.1 Tool 命名规范

建议统一使用 `device.xxx.yyy` 风格：
- `device.audio.set_volume`
- `device.alarm.set`
- `device.app.open`

优点：
- 与设备 MCP 文档一致
- 大模型更容易按领域理解工具能力
- 后续平台覆盖 description / params 时更稳定

### 9.2 description 要写清楚“什么时候用 / 什么时候不用”

这点非常关键。description 不是写给人看的，而是写给大模型做 tool selection 的。

推荐包含三部分：
1. 这个工具做什么
2. 用户说哪些话时应该调用
3. 哪些场景不要调用，避免和其他 tool 混淆

例如：

```text
Use this tool when user wants to open any app, listen to music, watch videos, chat, or do office work.
Match user intent to the closest category.
Do NOT use this tool for device settings like volume or alarms.
```

### 9.3 inputSchema 尽量简单稳定

建议：
- 参数命名直观
- required 尽量少但必须明确
- enum 尽量收敛，避免自由输入过多
- 大模型更容易稳定输出结构化参数

### 9.4 Tool 内部不要依赖页面状态

建议工具只依赖：
- application context
- 系统 API
- 业务注入配置

不要把 tool 设计成依赖某个 Activity 实例，否则复用性会很差。

### 9.5 配置型工具优先用“构造注入 + update”

像 `AppOpenTool` 这种带业务配置的工具，推荐：
- 构造时注入初始配置
- 提供 `updateXxx()` 方法支持运行时更新

这样更适合：
- 登录后拉取用户配置
- 灰度下发
- 设置页保存后即时生效

---

## 10. 常见问题

### Q1：为什么注册了 tool，但云端没看到？

先检查：
1. 是否在 `createSession()` 前调用了 `McpLocalManager.register()`
2. `buildUserData()` 日志里 `mcpTools` 是否有数据
3. 是否复用了老 session

### Q2：为什么修改了 description / schema，但云端还是旧的？

因为 `deviceMcp` 是 session 创建时上报的。修改后需要：
- 重建 session
或
- 重新进入页面重新建 session

### Q3：为什么 tool 被大模型误调用？

优先检查：
- description 是否写清触发场景
- 是否缺少负面约束（Do NOT use for ...）
- 是否和其他 tool 名称/描述重叠太多

### Q4：App 打开配置如何替换成业务自己的？

直接在业务层构造自己的映射：

```kotlin
val apps = mapOf(
    "music" to AppEntry("your.package.music", "你的音乐App"),
    "im" to AppEntry("your.package.im", "你的IM App")
)
val appOpenTool = AppOpenTool(apps)
```

---

## 11. 建议的联调顺序

1. 先只注册 1 个最简单的 tool（如音量）
2. 确认 `buildUserData()` 中 `mcpTools` 正常
3. 确认云端能走 `initialize` / `tools/list`
4. 再逐步增加其他 tool
5. 最后再优化 description 和 prompt，提高 tool selection 准确率

---

## 12. 相关文件

### MCP 模块
- `McpLocalManager.kt`
- `McpManager.kt`
- `McpToolManager.kt`
- `tool/McpTool.kt`
- `tool/ToolRegistry.kt`
- `tool/ToolResult.kt`
- `tool/impl/*.kt`

### Demo 接入参考
- `devkit-ai-chat/.../AiLongEventActivity.java`
- `devkit-ai-chat/.../AiLongEventEngine.kt`
