# IPhotoFrame 接口文档

## 功能概览

### IPhotoFrame 接口方法

| 功能模块 | 方法名 | 功能描述 | 备注 |
|---------|--------|---------|---------|
| 初始化 | `initFrameZone(FrameZoneConfig, ApiCallback)` | 初始化相框配置，使用默认文件存储路径。 | 初始化时机为设备被激活后，请参考 demo 中时机。 |
| 初始化 | `initFrameZone(FrameZoneConfig, String, ApiCallback)` | 初始化相框配置，支持自定义文件存储路径 |  |
| 事件监听 | `registerMediaUploadListener(IPhotoFrameMediaUpdateListener)` | 注册文件上传/下载监听器 |  |
| 事件监听 | `unregisterMediaUploadListener()` | 注销文件上传/下载监听器 |  |
| 查询 | `getAllFileInfos()` | 获取所有文件信息列表 |  |
| 查询 | `getPhotoInfoByName(String)` | 通过文件名获取指定文件信息 |  |
| 添加 | `addLocalPhoto(List<PhotoInfo>)` | 添加本地照片到相框 | 用于添加 SD 卡等本地图片 |
| 删除 | `deletePhoto(String)` | 删除指定相框文件 |  |
| 删除 | `deleteAllPhotos()` | 删除所有相框文件 |  |

### IPhotoFrameMediaUpdateListener 监听器回调

| 功能模块 | 回调方法 | 触发时机 | 参数说明 |
|---------|---------|---------|---------|
| 上传监听 | `onMediaAppStartUpload(StartEventData)` | App 开始上传文件到云端时 | StartEventData - 包含 sid、文件前缀、文件数量等 |
| 上传监听 | `onMediaAppUploaded(UploadedEventData)` | App 单个文件上传到云端完成时 | UploadedEventData - 单个文件上传完成信息 |
| 下载监听 | `onMediaDeviceDownloaded(UploadedEventData, String)` | 相框设备从云端下载文件完成时 | UploadedEventData - 文件信息<br>String - 本地存储路径 |
| 失败监听 | `onMediaUploadFailed(String, String, String)` | 文件上传或下载失败时 | sid - 会话ID<br>code - 错误码<br>errorMessage - 错误信息 |
| 完成监听 | `onMediaAllUploadFinished(UploadFinishedEventData)` | 本次上传的所有文件都处理完成时 | UploadFinishedEventData - 上传完成统计信息 |
| 删除监听 | `onMediaAppDelete(DeleteEventData)` | App 删除文件时 | DeleteEventData - 删除事件信息 |

---

## 接口详情

### 1. 初始化相框功能

#### 1.1 基础初始化

```java
void initFrameZone(FrameZoneConfig config, ApiCallback<Void> callback)
```

**功能说明**  
使用默认文件存储路径初始化相框功能，配置设备参数和文件限制。 默认地址：/data/data/<你的应用包名>/files

**参数说明**
- `config`: FrameZoneConfig - 相框配置对象，包含设备ID、分辨率、文件格式限制等
- `callback`: ApiCallback<Void> - 初始化结果回调

**使用示例**

```java
FrameZoneConfig config = FrameZoneConfig.builder()
    .devId("your_device_id")
    .resolution("1920*1080")
    .allowFormats(new String[]{"png", "jpg", "mp4"})
    .maxFc(20)
    .maxFs(20971520);  // 20MB

photoFrame.initFrameZone(config, new ApiCallback<Void>() {
    @Override
    public void onSuccess(Void result) {
        // 初始化成功
    }

    @Override
    public void onError(String code, String message) {
        // 初始化失败
    }
});
```

---

#### 1.2 自定义存储路径初始化

```java
void initFrameZone(FrameZoneConfig config, String fileBaseDirPath, ApiCallback<Void> callback)
```

**功能说明**  
支持自定义文件存储路径的相框初始化方法。

**参数说明**
- `config`: FrameZoneConfig - 相框配置对象
- `fileBaseDirPath`: String - 自定义文件存储基础路径
- `callback`: ApiCallback<Void> - 初始化结果回调

**使用示例**

```java
String customPath = "/sdcard/custom_photo_frame/";
FrameZoneConfig config = FrameZoneConfig.builder()
    .devId("your_device_id")
    .resolution("1920*1080")
    .maxFc(50);

photoFrame.initFrameZone(config, customPath, new ApiCallback<Void>() {
    @Override
    public void onSuccess(Void result) {
        // 初始化成功
    }

    @Override
    public void onError(String code, String message) {
        // 初始化失败
    }
});
```

---

### 2. 监听器管理

#### 2.1 注册文件上传监听

```java
void registerMediaUploadListener(IPhotoFrameMediaUpdateListener listener)
```

**功能说明**  
注册文件上传/下载事件监听器，监听文件的上传、下载、删除等事件。

**参数说明**
- `listener`: IPhotoFrameMediaUpdateListener - 媒体文件更新监听器

**监听器回调说明**

| 回调方法 | 触发时机 | 参数说明 |
|---------|---------|---------|
| `onMediaAppStartUpload` | App 开始上传文件时 | StartEventData - 包含 sid、文件前缀、文件数量等 |
| `onMediaAppUploaded` | App 上传文件完成 | UploadedEventData - 单个文件上传完成信息 |
| `onMediaDeviceDownloaded` | 相框设备下载文件完成 | UploadedEventData, String - 文件信息和本地路径 |
| `onMediaUploadFailed` | 文件上传/下载失败 | sid, code, errorMessage - 失败信息 |
| `onMediaAllUploadFinished` | 所有文件上传完成 | UploadFinishedEventData - 完成统计信息 |
| `onMediaAppDelete` | App 删除文件 | DeleteEventData - 删除事件信息 |

**使用示例**

```java
photoFrame.registerMediaUploadListener(new IPhotoFrameMediaUpdateListener() {
    @Override
    public void onMediaAppStartUpload(StartEventData event) {
        Log.d(TAG, "开始上传，sid: " + event.getSid() + ", 文件数: " + event.getFileCount());
    }

    @Override
    public void onMediaAppUploaded(UploadedEventData event) {
        Log.d(TAG, "文件上传完成: " + event.getRealFn());
    }

    @Override
    public void onMediaDeviceDownloaded(UploadedEventData event, String filePath) {
        Log.d(TAG, "设备下载完成: " + event.getRealFn() + ", 路径: " + filePath);
    }

    @Override
    public void onMediaUploadFailed(String sid, String code, String errorMessage) {
        Log.e(TAG, "上传失败 - sid: " + sid + ", 错误: " + errorMessage);
    }

    @Override
    public void onMediaAllUploadFinished(UploadFinishedEventData event) {
        Log.d(TAG, "所有文件上传完成");
    }

    @Override
    public void onMediaAppDelete(DeleteEventData event) {
        Log.d(TAG, "文件被删除");
    }
});
```

---

#### 2.2 注销文件上传监听

```java
void unregisterMediaUploadListener()
```

**功能说明**  
注销之前注册的媒体文件更新监听器。

**使用示例**

```java
photoFrame.unregisterMediaUploadListener();
```

---

### 3. 查询功能

#### 3.1 获取所有文件信息

```java
@NonNull
List<PhotoInfo> getAllFileInfos()
```

**功能说明**  
获取相框中所有文件的信息列表，包括图片和视频。

**返回值**
- `List<PhotoInfo>`: 文件信息列表，不会返回 null（可能返回空列表）

**使用示例**

```java
List<PhotoInfo> allPhotos = photoFrame.getAllFileInfos();
for (PhotoInfo photo : allPhotos) {
    Log.d(TAG, "文件名: " + photo.getRealFn() + 
               ", 路径: " + photo.getLocalPath() + 
               ", 类型: " + (photo.getFileType() == 0 ? "图片" : "视频"));
}
```

---

#### 3.2 通过文件名获取文件信息

```java
PhotoInfo getPhotoInfoByName(String realFn)
```

**功能说明**  
根据文件名（realFn）获取指定文件的详细信息。

**参数说明**
- `realFn`: String - 文件的唯一标识名称

**返回值**
- `PhotoInfo`: 文件信息对象，如果文件不存在则返回 null

**使用示例**

```java
PhotoInfo photo = photoFrame.getPhotoInfoByName("photo_12345.jpg");
if (photo != null) {
    Log.d(TAG, "找到文件: " + photo.getTitle());
    Log.d(TAG, "本地路径: " + photo.getLocalPath());
    Log.d(TAG, "时间戳: " + photo.getTimestamp());
} else {
    Log.d(TAG, "文件不存在");
}
```

---

### 4. 添加本地照片

#### 4.1 添加本地照片到相框

```java
void addLocalPhoto(List<PhotoInfo> infos)
```

**功能说明**  
将本地照片（如 SD 卡、相册中的图片）添加到相框系统中进行管理。添加后可通过 `getAllFileInfos()` 查询。

**参数说明**
- `infos`: List<PhotoInfo> - 要添加的照片信息列表

**注意事项**
- `realFn`（文件名）为必填字段，作为唯一标识
- `localPath`（本地路径）为必填字段
- `source` 由调用方自行设置，可使用 `SOURCE_LOCAL`、`SOURCE_CLOUD` 或自定义值
- 如果 `timestamp` 未设置，会自动使用当前时间

**使用示例**

```java
// 创建本地照片信息
PhotoInfo photo1 = new PhotoInfo();
photo1.setRealFn("local_001.jpg");                    // 必填：唯一标识
photo1.setLocalPath("/sdcard/DCIM/Camera/IMG_001.jpg"); // 必填：本地路径
photo1.setTitle("我的照片");                           // 可选
photo1.setFileType(0);                                // 可选：0=图片, 1=视频
photo1.setSource(PhotoInfo.SOURCE_LOCAL);             // 可选：设置来源

PhotoInfo photo2 = new PhotoInfo();
photo2.setRealFn("local_002.mp4");
photo2.setLocalPath("/sdcard/Movies/video.mp4");
photo2.setFileType(1);
photo2.setSource(PhotoInfo.SOURCE_LOCAL);

// 批量添加
List<PhotoInfo> localPhotos = new ArrayList<>();
localPhotos.add(photo1);
localPhotos.add(photo2);

photoFrame.addLocalPhoto(localPhotos);
```

---

### 5. 删除功能

#### 5.1 删除指定文件

```java
void deletePhoto(String realFn)
```

**功能说明**  
根据文件名删除相框中的指定文件（图片或视频）。

**参数说明**
- `realFn`: String - 要删除的文件名（唯一标识）

**使用示例**

```java
photoFrame.deletePhoto("photo_12345.jpg");
```

---

#### 5.2 删除所有文件

```java
void deleteAllPhotos()
```

**功能说明**  
删除相框中的所有文件，包括图片和视频。

**注意事项**
- 此操作不可逆，请谨慎使用
- 建议在执行前向用户确认

**使用示例**

```java
// 建议添加确认对话框
new AlertDialog.Builder(context)
    .setTitle("确认删除")
    .setMessage("确定要删除所有相框文件吗？")
    .setPositiveButton("确定", (dialog, which) -> {
        photoFrame.deleteAllPhotos();
    })
    .setNegativeButton("取消", null)
    .show();
```

---

## 数据模型说明

### FrameZoneConfig - 相框配置类

使用 Builder 模式构建配置对象。

**字段说明**

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| devId | String | - | 设备ID（必填） |
| resolution | String | "1024*600" | 屏幕分辨率 |
| allowFormats | String[] | ["png", "jpg", "mp4"] | 允许的文件格式（仅支持 png、jpg、mp4） |
| maxFc | int | 10 | 最大文件数量 |
| maxFs | long | 10485760 | 最大文件大小（字节，默认10MB） |

**构建示例**

```java
FrameZoneConfig config = FrameZoneConfig.builder()
    .devId("device_123456")                          // 必填
    .resolution("1920*1080")                         // 可选
    .allowFormats(new String[]{"png", "jpg"})        // 可选
    .maxFc(30)                                       // 可选
    .maxFs(20 * 1024 * 1024);                       // 可选，20MB
```

**格式限制**
- 仅支持 png、jpg、mp4 三种格式
- 传入不支持的格式会抛出 `IllegalArgumentException`

---

### PhotoInfo - 文件信息类

存储单个文件的详细信息。

**常量定义**

| 常量名 | 值 | 说明 |
|--------|-----|------|
| SOURCE_CLOUD | 0 | 来源：云端（App 上传） |
| SOURCE_LOCAL | 1 | 来源：本地（SD 卡等） |

**字段说明**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| realFn | String | 文件名（唯一标识） |
| localPath | String | 文件本地存储路径 |
| title | String | 文件标题（可选） |
| meta | String | 文件描述信息（可选） |
| timestamp | long | 文件时间戳（毫秒） |
| centerX | int | 图片中心点 X 坐标（仅图片类型有效） |
| centerY | int | 图片中心点 Y 坐标（仅图片类型有效） |
| fileType | int | 文件类型：0-图片，1-视频 |
| prefix | String | 文件前缀名称 |
| source | int | 来源：0-云端（默认），1-本地 |
| extra | String | 扩展字段，可存储 JSON 或自定义数据 |

**使用示例**

```java
PhotoInfo photo = photoFrame.getPhotoInfoByName("photo_001.jpg");
if (photo != null) {
    String fileName = photo.getRealFn();         // 文件名
    String path = photo.getLocalPath();          // 本地路径
    int type = photo.getFileType();              // 0:图片 1:视频
    int source = photo.getSource();              // 0:云端 1:本地
    
    if (type == 0) {
        // 图片类型
        int x = photo.getCenterX();
        int y = photo.getCenterY();
        Log.d(TAG, "图片中心点: (" + x + ", " + y + ")");
    }
}
```

**根据来源过滤照片**

```java
List<PhotoInfo> allPhotos = photoFrame.getAllFileInfos();
List<PhotoInfo> cloudPhotos = new ArrayList<>();
List<PhotoInfo> localPhotos = new ArrayList<>();

for (PhotoInfo photo : allPhotos) {
    if (photo.getSource() == PhotoInfo.SOURCE_CLOUD) {
        cloudPhotos.add(photo);
    } else {
        localPhotos.add(photo);
    }
}

Log.d(TAG, "云端照片: " + cloudPhotos.size() + " 张");
Log.d(TAG, "本地照片: " + localPhotos.size() + " 张");
```

---

### IPhotoFrameMediaUpdateListener - 媒体更新监听器

监听文件上传、下载、删除等事件的回调接口。

**回调方法详情**

#### onMediaAppStartUpload
```java
void onMediaAppStartUpload(StartEventData event)
```
- **触发时机**: App 开始上传文件到云端时
- **参数**: StartEventData - 包含上传会话 ID、文件前缀、文件数量等信息

#### onMediaAppUploaded
```java
void onMediaAppUploaded(UploadedEventData event)
```
- **触发时机**: 单个文件上传到云端完成时
- **参数**: UploadedEventData - 包含文件的详细信息

#### onMediaDeviceDownloaded
```java
void onMediaDeviceDownloaded(UploadedEventData event, String filePath)
```
- **触发时机**: 相框设备从云端下载文件完成时
- **参数**: 
  - event - 文件信息
  - filePath - 本地存储路径

#### onMediaUploadFailed
```java
void onMediaUploadFailed(String sid, String code, String errorMessage)
```
- **触发时机**: 文件上传或下载失败时
- **参数**: 
  - sid - 会话ID
  - code - 错误码
  - errorMessage - 错误信息

#### onMediaAllUploadFinished
```java
void onMediaAllUploadFinished(UploadFinishedEventData event)
```
- **触发时机**: 本次上传的所有文件都处理完成时
- **参数**: UploadFinishedEventData - 上传完成统计信息

#### onMediaAppDelete
```java
void onMediaAppDelete(DeleteEventData event)
```
- **触发时机**: App 删除文件时。sdk 会移除内部存储记录 以及文件地址。业务层需要处理正在展示的照片是否需要删除。
- **参数**: DeleteEventData - 删除事件的详细信息

---

## 使用流程示例

### 完整使用示例

```java
请参考sample 中的示例
```

---

## 版本历史

| 版本 | 日期 | 更新内容 |
|------|------|---------|
| 1.0.0 | 2025-11-13 | 初始版本，包含完整的相框管理功能。 |
| 1.1.0 | 2025-12-05 | 新增 `addLocalPhoto` 方法，支持添加本地照片；`PhotoInfo` 新增 `source` 字段区分云端/本地来源。 |

---

## 技术支持

如有任何问题，请联系技术支持团队或查阅完整的 SDK 文档。

