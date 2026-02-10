# SDK使用文档 4-产测文档



## 1. 产测系统概述

### 1.1 系统功能

涂鸦相框产测授权系统，用于生产测试环节的设备授权管理，支持：

✅ **授权管理** - 授权码分配、使用、回收全流程管理  
✅ **状态可视** - Excel文件实时标记使用状态，产测人员可见  
✅ **安全存储** - 授权信息加密存储，多重备份防止丢失  
✅ **异常处理** - 智能识别各种异常情况并给出提示  



## 2. 集成产测模块

### 2.1 集成步骤

#### 步骤1: 添加依赖

在 `app/build.gradle` 中添加：

```groovy
// ==================== thingiotsdk-licenseprovision（可选）
 // 如需授权烧录能力，添加以下依赖：
 implementation "com.thingclips.smart:thingiotsdk-licenseprovision:x.x.x" //获取最新版

//依赖的三方库
 implementation 'androidx.security:security-crypto:1.0.0'//授权码存储用到的加密库
 implementation 'androidx.documentfile:documentfile:1.0.1'
 implementation('com.github.SUPERCILEX.poi-android:poi:3.17') {
     exclude group: 'stax', module: 'stax-api'
     exclude group: 'com.bea.xml', module: 'jsr173-ri'
     exclude group: 'org.codehaus.woodstox', module: 'stax2-api'
 }
```



#### 步骤2: 集成逻辑

在 `MainActivity` 中：
见使用方式

- 推荐流程

- 判断是否具有授权码，若无，直接弹出
- 若有授权码，判断是否有插入 SD卡，且 SD 卡具有烧录能力，此场景可能用于回收场景，也需要弹。



##### 监听授权

```java 
   // 初始化授权烧录模块
        LicenseProvisionManager.getInstance()
                .init(this)
                .enableAutoLaunch(false)  // 启用SD卡自动弹出授权页面
                .setOnLicenseChangeListener(new LicenseProvisionManager.OnLicenseChangeListener() {
                    @Override
                    public void onLicenseGranted(LicenseInfo license) {
                        TLog.i(TAG, "授权码已授予: uuid=" + license.uuid);
                        if (!ioTSDKManager.isInitialized()) {
                            Toast.makeText(MainActivity.this, "授权成功，正在初始化SDK...", Toast.LENGTH_SHORT).show();
                            initSDK();
                        }
                    }

                    @Override
                    public void onLicenseRevoked() {
                        TLog.i(TAG, "授权码已回收");
                    }
                })
                .start();
```

##### 判断当前是否已经授权过 弹出授权流程

```java
    LicenseInfo info = LicenseProvisionManager.getInstance().getLicense();

        if (info != null) {
           // 已授权，可以不用进入授权模块
           initSDK()；
           // 若具有授权的 sdk ，则可能用于回收，弹出授权码系统
            if (LicenseProvisionManager.getInstance().hasExternalSDCardWithLicenseFile()) {
                showLicenseGuideDialog();
            }
        }else{
						// 进入授权模块 ，后续结果通过监听回调
           LicenseProvisionManager.getInstance().showProvisionPage();
        }
```







## 3. Excel授权文件制作

### 4.1 文件要求

**位置**：创建SD卡根目录/tuya_frame_license/

**格式**：将授权excel 文件放到tuya_frame_license即可。

**数量**：文件夹中有且仅有一个Excel文件

