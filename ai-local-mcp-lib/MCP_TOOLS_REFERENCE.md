# 本地 MCP 工具能力说明

本文档描述设备端支持的本地 MCP 工具集。AI 助手在对话中根据用户意图，选择合适的工具调用，实现对设备的本地控制。

---

## 工具调用流程

```
用户语音/文字输入
    ↓
AI 理解用户意图
    ↓
AI 选择匹配的 tool，生成 JSON 参数
    ↓
设备端执行 tool，返回执行结果
    ↓
AI 根据执行结果，用自然语言回复用户
```

**关键原则：**
- 一次对话可以调用零个或多个工具
- 工具调用发生在设备本地，不经过云端转发
- 工具返回结构化结果（成功/失败+描述），AI 需要将结果转化为自然语言回复用户
- 当用户意图不明确时，AI 应先追问确认，而不是猜测调用

---

## 工具列表

### 1. device.audio.set_volume

**作用：** 控制设备音量，包括查询当前音量、设置到指定值、音量增大或减小。

**支持的音频流：** 媒体音量(music)、铃声音量(ring)、通知音量(notification)。默认操作媒体音量。

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "把声音调大一点" | device.audio.set_volume | `{"action": "up"}` |
| "声音太吵了，小声点" | device.audio.set_volume | `{"action": "down"}` |
| "把音量设到50" | device.audio.set_volume | `{"action": "set", "level": 50}` |
| "静音" | device.audio.set_volume | `{"action": "set", "level": 0}` |
| "音量最大" | device.audio.set_volume | `{"action": "set", "level": 100}` |
| "现在音量多少" | device.audio.set_volume | `{"action": "get"}` |
| "把铃声调大" | device.audio.set_volume | `{"action": "up", "stream": "ring"}` |

**执行结果示例：**
- 成功：`"Volume set to 50%"` → AI 回复："好的，已经把音量调到50%了。"
- 成功：`"Volume raised. Current: 73%"` → AI 回复："已经调大了，现在音量是73%。"

**不应调用此工具的场景：** 用户说"设个闹钟"、"定个提醒"——这些应该用 device.alarm.set。

---

### 2. device.alarm.set

**作用：** 在系统闹钟应用中创建闹钟。支持指定时间、标签和按星期重复。

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "明早8点叫我" | device.alarm.set | `{"hour": 8, "minute": 0, "message": "起床"}` |
| "设个下午3点半的闹钟" | device.alarm.set | `{"hour": 15, "minute": 30}` |
| "每个工作日早上7点叫我起床" | device.alarm.set | `{"hour": 7, "minute": 0, "message": "起床", "days": "MON,TUE,WED,THU,FRI"}` |
| "每天晚上10点提醒我吃药" | device.alarm.set | `{"hour": 22, "minute": 0, "message": "吃药", "days": "MON,TUE,WED,THU,FRI,SAT,SUN"}` |
| "周末早上9点设个闹钟" | device.alarm.set | `{"hour": 9, "minute": 0, "days": "SAT,SUN"}` |

**执行结果示例：**
- 成功：`"Alarm set for 08:00"` → AI 回复："好的，明早8点的闹钟已经设好了。"

**注意：**
- hour 使用24小时制（0-23）
- days 参数使用英文缩写：MON, TUE, WED, THU, FRI, SAT, SUN
- 不传 days 表示单次闹钟
- 用户说"3点"时，AI 需要根据上下文判断是凌晨3点(3)还是下午3点(15)

**不应调用此工具的场景：** 用户说"帮我安排个会议"、"下周三下午开会"——这些是日程事件，应该用 device.calendar.create_event。

---

### 3. device.app.open

**作用：** 按分类启动设备上已安装的应用。分类与具体 App 的映射由业务侧预配置。

**当前支持的分类：**

| 分类 | 含义 | 用户可能的表述 |
|------|------|--------------|
| music | 音乐类 App | 听歌、放首歌、播放音乐、打开音乐 |
| movie | 长视频/影视类 App | 看电影、追剧、看电视剧 |
| video | 短视频类 App | 刷短视频、看视频、刷抖音、刷快手 |
| office | 办公类 App | 办公、打开文档、开个会议 |
| im | 即时通讯/聊天类 App | 发消息、聊天、打开微信、打开QQ |

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "我想听歌" | device.app.open | `{"category": "music"}` |
| "打开微信" | device.app.open | `{"category": "im"}` |
| "我想刷会儿短视频" | device.app.open | `{"category": "video"}` |
| "帮我打开办公软件" | device.app.open | `{"category": "office"}` |
| "我想看个电影" | device.app.open | `{"category": "movie"}` |

**执行结果示例：**
- 成功：`"Opened 网易云音乐"` → AI 回复："已经帮你打开网易云音乐了，享受音乐吧。"
- 失败：`"网易云音乐 is not installed"` → AI 回复："抱歉，设备上没有安装音乐应用。"
- 失败：`"Unknown category: game"` → AI 回复："抱歉，暂时不支持打开这类应用。"

**不应调用此工具的场景：** 用户说"把音量调大"、"设个闹钟"——这些是设备设置操作，不是打开 App。

---

### 4. device.app.close

**作用：** 按分类关闭正在运行的应用。分类与 device.app.open 一致。

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "关掉音乐" | device.app.close | `{"category": "music"}` |
| "把视频关了" | device.app.close | `{"category": "video"}` |
| "退出微信" | device.app.close | `{"category": "im"}` |

**执行结果示例：**
- 成功：`"Closed 网易云音乐"` → AI 回复："好的，已经帮你关掉音乐了。"

**不应调用此工具的场景：** 用户说"静音"——应使用 device.audio.set_volume 而不是关闭 App。

---

### 5. device.notification.control

**作用：** 管理设备的免打扰(DND)模式，包括查询当前状态、切换模式、跳转通知设置页面。

**免打扰模式说明：**

| 模式 | 效果 |
|------|------|
| off | 关闭免打扰，所有通知正常接收 |
| priority | 仅允许星标联系人等优先通知 |
| alarms | 仅允许闹钟响铃，其他通知全部静默 |
| total_silence | 完全静音，包括闹钟在内全部屏蔽 |

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "开启免打扰" | device.notification.control | `{"action": "toggle_dnd", "dnd_mode": "total_silence"}` |
| "关闭免打扰" | device.notification.control | `{"action": "toggle_dnd", "dnd_mode": "off"}` |
| "只允许闹钟通知" | device.notification.control | `{"action": "toggle_dnd", "dnd_mode": "alarms"}` |
| "现在是免打扰模式吗" | device.notification.control | `{"action": "query"}` |
| "打开通知设置" | device.notification.control | `{"action": "open_settings"}` |
| "睡觉了，别打扰我" | device.notification.control | `{"action": "toggle_dnd", "dnd_mode": "total_silence"}` |
| "我要开会，除了重要电话别打扰" | device.notification.control | `{"action": "toggle_dnd", "dnd_mode": "priority"}` |

**执行结果示例：**
- 成功：`"Do Not Disturb set to: total_silence"` → AI 回复："好的，已经开启完全静音模式，不会有通知打扰你了。"
- 查询：`"{\"notifications_enabled\":true,\"dnd_mode\":\"off\"}"` → AI 回复："当前通知是正常接收的，没有开启免打扰。"
- 权限不足：`"DND policy access not granted. Opening settings for permission."` → AI 回复："需要你授权免打扰权限，已经帮你打开了设置页面。"

---

### 6. device.calendar.create_event

**作用：** 在系统日历中创建事件/日程，支持设置标题、时间段、描述和提前提醒。

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "帮我建个日程，下午3点团队周会" | device.calendar.create_event | `{"title": "团队周会", "begin_time": <下午3点的epoch毫秒>}` |
| "明天上午10点到11点有个产品评审" | device.calendar.create_event | `{"title": "产品评审", "begin_time": <明天10点>, "end_time": <明天11点>}` |
| "记一下后天的牙医预约，提前30分钟提醒我" | device.calendar.create_event | `{"title": "牙医预约", "begin_time": <后天时间>, "reminder_minutes": 30}` |
| "下周五下午茶聚会，备注带蛋糕" | device.calendar.create_event | `{"title": "下午茶聚会", "begin_time": <下周五时间>, "description": "带蛋糕"}` |

**执行结果示例：**
- 成功：`"Calendar event created: 团队周会 at 2026-04-17 15:00"` → AI 回复："好的，下午3点的团队周会日程已经创建好了。"

**注意：**
- begin_time 和 end_time 是 epoch 毫秒时间戳，AI 需要根据当前时间和用户描述计算
- 不传 end_time 默认持续1小时
- 不传 reminder_minutes 默认提前10分钟提醒

**与 device.alarm.set 的区分规则：**
- 闹钟：简单的时间点提醒，每天重复，"叫我"、"提醒我起床" → device.alarm.set
- 日程：有具体事项的事件安排，"开会"、"预约"、"安排" → device.calendar.create_event

---

### 7. device.screen.set_timeout

**作用：** 设置或查询屏幕自动息屏（锁屏）的超时时间。

**常用超时值：**
- 15000ms = 15秒
- 30000ms = 30秒
- 60000ms = 1分钟
- 120000ms = 2分钟
- 300000ms = 5分钟
- 600000ms = 10分钟

**典型用例：**

| 用户说 | AI 应调用 | 参数 |
|--------|----------|------|
| "屏幕别那么快灭" | device.screen.set_timeout | `{"timeout_ms": 300000}` |
| "设置5分钟自动锁屏" | device.screen.set_timeout | `{"timeout_ms": 300000}` |
| "屏幕常亮" | device.screen.set_timeout | `{"timeout_ms": 600000}` |
| "息屏时间设短一点" | device.screen.set_timeout | `{"timeout_ms": 30000}` |
| "现在多久自动息屏" | device.screen.set_timeout | `{}` |

**执行结果示例：**
- 成功：`"Screen timeout set to 300s"` → AI 回复："好的，屏幕会在5分钟无操作后自动息屏。"
- 查询：`"Current screen timeout: 60s"` → AI 回复："当前屏幕是1分钟没操作就自动息屏。"
- 权限不足：`"WRITE_SETTINGS permission not granted. Opening settings for authorization."` → AI 回复："需要授权修改系统设置的权限，已帮你打开授权页面。"

---

## 工具选择决策参考

当用户意图可能匹配多个工具时，按以下规则判断：

| 用户意图关键词 | 正确工具 | 易混淆工具 |
|--------------|---------|-----------|
| 音量、声音大小、静音、响一点 | device.audio.set_volume | ~~device.notification.control~~ |
| 闹钟、叫我、每天提醒、起床 | device.alarm.set | ~~device.calendar.create_event~~ |
| 开会、日程、预约、安排事项 | device.calendar.create_event | ~~device.alarm.set~~ |
| 免打扰、勿扰、别打扰、通知 | device.notification.control | ~~device.audio.set_volume~~ |
| 打开xx应用、听歌、看视频 | device.app.open | ~~device.audio.set_volume~~ |
| 关掉xx应用、退出、关闭App | device.app.close | ~~device.audio.set_volume~~ |
| 息屏、锁屏时间、屏幕常亮 | device.screen.set_timeout | — |

---

## 统一返回格式

所有工具返回结构：

```json
{
  "type": "text",
  "status": "success" | "error",
  "text": "具体的执行结果描述"
}
```

AI 收到返回后应该：
- **status=success**：用自然语言告知用户操作已完成，可附带结果数据
- **status=error**：用自然语言告知用户操作失败的原因，并给出建议（如需要授权、App未安装等）
