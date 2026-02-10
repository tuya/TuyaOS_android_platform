package com.tuya.smartai.sample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.tuya.smartai.iot_sdk.DPEvent;
import com.tuya.smartai.iot_sdk.IIoTManager;
import com.tuya.smartai.iot_sdk.IoTSDKManager;
import com.tuya.smartai.iot_sdk.OpenCode;
import com.tuya.smartai.iot_sdk.ThingOS;
import com.tuya.smartai.iot_sdk.core.IoTCallback;
import com.tuya.smartai.iot_sdk.core.IoTParams;
import com.tuya.smartai.iot_sdk.model.ResetType;
import com.tuya.smartai.iot_sdk.utils.TLog;
import com.tuya.smartai.sample.event.IoTEvent;
import com.tuya.smartai.sample.event.IoTEventBus;

/**
 * IoT 后台服务
 * 用于接收和处理远程 DP 控制指令
 * 通过 EventBus 与 Activity 通信
 */
public class IoTService extends Service implements IoTEventBus.EventListener {
    private static final String TAG = "IoTService";
    private static final String CHANNEL_ID = "iot_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private IIoTManager ioTSDKManager;
    private IoTEventBus eventBus;
    private TVControlManager tvControlManager;
    private android.widget.Toast singletonToast;  // 单例 Toast
    private android.os.Handler mainHandler;
    
    // 保存当前 MQTT 状态
    private int currentMqttStatus = IoTSDKManager.STATUS_OFFLINE;

    private static final String PRODUCT_ID = "figevyadbapdki1f"; // 产品ID
    private static final String UUID = "uuidb093dd8d8e8e1261"; // 授权码中的uuid
    private static final String AUTH_KEY = "7rX8vezQpw72u5EB0utD9lkPcsYfu8AL"; // 授权码中的authKey
    private static final String VERSION = "1.0.0"; // 设备版本号

    @Override
    public void onCreate() {
        super.onCreate();
        TLog.i(TAG, "IoTService onCreate");
        
        // 获取事件总线实例并注册监听
        eventBus = IoTEventBus.getInstance();
        eventBus.register(this);
        
        // 创建前台服务通知
        createNotificationChannel();
        // Android 12+ 需要指定前台服务类型
        if (Build.VERSION.SDK_INT >= 31) { // Build.VERSION_CODES.S = 31
            startForeground(NOTIFICATION_ID, createNotification("IoT 服务启动中..."), 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, createNotification("IoT 服务启动中..."));
        }
        
        // 发送服务启动事件
        eventBus.post(IoTEvent.serviceStarted());
        
        // 初始化 IoT SDK
        initSDK();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TLog.i(TAG, "IoTService onStartCommand");
        return START_STICKY; // 服务被杀死后自动重启
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 不需要绑定，返回 null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TLog.i(TAG, "IoTService onDestroy");
        if (eventBus != null) {
            eventBus.unregister(this);
        }
        // 销毁 TVControlManager
        if (tvControlManager != null) {
            tvControlManager.destroy();
            tvControlManager = null;
        }
    }
    
    /**
     * 监听 EventBus 事件（来自 Activity 的请求）
     */
    @Override
    public void onEvent(IoTEvent event) {
        if (event.getType() == IoTEvent.Type.REQUEST_STATUS) {
            TLog.i(TAG, "收到状态请求，发送当前 MQTT 状态: " + currentMqttStatus);
            // 重新发送当前 MQTT 状态
            String statusStr = getMqttStatusStr(currentMqttStatus);
            eventBus.post(IoTEvent.mqttStatusChanged(currentMqttStatus, statusStr));
        }
    }

    private void initSDK() {
        TLog.i(TAG, "后台服务初始化 IoT SDK...");

        ioTSDKManager = ThingOS.getInstance().getIoTSDKManager();

        // 如果已经初始化，说明已经配网成功
        if (ioTSDKManager.isInitialized()) {
            String deviceId = ioTSDKManager.getDeviceId();
            TLog.i(TAG, "IoT SDK 已经初始化，设备已配网，DeviceId: " + deviceId);
            updateNotification("设备已就绪，等待指令");
            
            // 初始化 TVControlManager
            initTVControlManager();
            
            // 发送已配网事件，携带设备ID
            eventBus.post(IoTEvent.serviceReady(true));
            eventBus.post(IoTEvent.deviceActive()); // 发送设备激活事件
            
            // 如果有设备ID，单独发送设备ID事件
            if (deviceId != null && !deviceId.isEmpty()) {
                eventBus.post(new IoTEvent(IoTEvent.Type.SERVICE_READY, deviceId, "DeviceId: " + deviceId));
            }
            return;
        }

        // 未配网，需要进行初始化和配网
        TLog.i(TAG, "设备未配网，开始初始化 SDK");
        eventBus.post(IoTEvent.serviceReady(false));

        IoTParams params = new IoTParams.Builder()
                .addMode(IoTParams.Mode.MODE_QR) // 二维码配网模式
                .productId(PRODUCT_ID)
                .uuid(UUID)
                .authKey(AUTH_KEY)
                .version(VERSION)
                .enableHotReset(true) // 启用热重置
                .ioTCallback(mIotCallback)
                .build();

        int result = ioTSDKManager.initSDK(params);

        if (result == OpenCode.CODE_OK) {
            TLog.i(TAG, "IoT SDK 初始化成功，等待配网");
            updateNotification("等待配网");
            eventBus.post(IoTEvent.sdkInitSuccess());
        } else {
            TLog.e(TAG, "IoT SDK 初始化失败: " + result);
            updateNotification("SDK 初始化失败: " + result);
            eventBus.post(IoTEvent.sdkInitFailed(result));
        }
    }

    private IoTCallback mIotCallback = new IoTCallback() {

        @Override
        public void onDpEvent(DPEvent event) {
            if (event != null) {
                TLog.w(TAG, "收到 DP 控制指令: " + event);
                TLog.w(TAG, "DP ID: " + event.dpid + ", Type: " + event.type + ", Value: " + 
                       (event.value != null ? event.value.toString() : "null"));
                
                // 发送 DP 事件（用于 UI 显示，无论能否执行都会发送）
                eventBus.post(IoTEvent.dpEvent(event));
                
                // 使用 TVControlManager 处理 DP 控制逻辑
                if (tvControlManager != null) {
                    boolean handled = tvControlManager.handleDPEvent(event);
                    if (handled) {
                        TLog.i(TAG, "DP " + event.dpid + " 已由 TVControlManager 处理");
                        // TVControlManager 会自己决定上报什么数据
                        return;
                    }
                }
                
                // 未处理的 DP，显示 Toast 并原样回传确认
                showToast("收到 DP " + event.dpid + ": " + event.value);
                TLog.w(TAG, "DP " + event.dpid + " 未被处理，原样回传");
//                ioTSDKManager.sendDP(event);
            }
        }

        @Override
        public void onReset(ResetType resetType) {
            TLog.e(TAG, "设备重置: resetType=" + resetType);
            updateNotification("设备已重置");
            
            // 清除本地 DP 状态记录
            if (tvControlManager != null) {
                tvControlManager.clearLocalState();
            }
            
            // 发送重置事件
            eventBus.post(IoTEvent.deviceReset(resetType.toString()));

            if (resetType == ResetType.GW_LOCAL_RESET_FACTORY) {
                // 恢复出厂设置
                TLog.e(TAG, "设备恢复出厂设置，重启服务");
            }
        }

        @Override
        public void onShorturl(String url) {
            TLog.w(TAG, "收到配网二维码 URL: " + url);
            updateNotification("等待配网激活");
            
            // 发送二维码生成事件
            eventBus.post(IoTEvent.qrCodeGenerated(url));
        }

        @Override
        public void onActive() {
            TLog.i(TAG, "设备已激活（重新上线）");
            updateNotification("设备已激活，运行中");
            
            // 初始化 TVControlManager
            initTVControlManager();
            
            // 发送设备激活事件
            eventBus.post(IoTEvent.deviceActive());
        }

        @Override
        public void onFirstActive() {
            TLog.i(TAG, "设备首次激活（首次配网成功）");
            updateNotification("设备首次激活成功");
            
            // 初始化 TVControlManager
            initTVControlManager();
            
            // 发送设备首次激活事件
            eventBus.post(IoTEvent.deviceFirstActive());
        }

        @Override
        public void onMQTTStatusChanged(int status) {
            // 保存当前 MQTT 状态
            currentMqttStatus = status;
            
            String statusStr = getMqttStatusStr(status);
            TLog.i(TAG, "MQTT 状态变化: " + statusStr);
            
            if (status == IoTSDKManager.STATUS_MQTT_ONLINE) {
                updateNotification("设备在线");
                // MQTT 上线后，同步初始状态给 App
                if (tvControlManager != null) {
                    tvControlManager.onMqttOnline();
                }
            } else if (status == IoTSDKManager.STATUS_MQTT_OFFLINE) {
                updateNotification("设备离线");
            }
            
            // 发送 MQTT 状态变化事件
            eventBus.post(IoTEvent.mqttStatusChanged(status, statusStr));
        }

        @Override
        public void onMqttMsg(int protocol, String msg) {
            TLog.d(TAG, "收到 MQTT 消息: protocol=" + protocol + ", msg=" + msg);
        }
    };

    /**
     * 初始化 TVControlManager
     */
    private void initTVControlManager() {
        if (tvControlManager == null && ioTSDKManager != null) {
            tvControlManager = new TVControlManager(this, ioTSDKManager);
            TLog.i(TAG, "TVControlManager 初始化完成");
            
            // 启动音量监听
            tvControlManager.startVolumeListener();
        }
    }

    private String getMqttStatusStr(int status) {
        switch (status) {
            case IoTSDKManager.STATUS_OFFLINE:
                return "OFFLINE";
            case IoTSDKManager.STATUS_MQTT_OFFLINE:
                return "MQTT OFFLINE";
            case IoTSDKManager.STATUS_MQTT_ONLINE:
                return "MQTT ONLINE";
            default:
                return "UNKNOWN: " + status;
        }
    }

    /**
     * 创建通知渠道（Android 8.0+）
     * 兼容 Android 4.3：使用数字常量
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) { // Build.VERSION_CODES.O = 26
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "IoT 服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("IoT 后台服务运行通知");
            channel.setShowBadge(false);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification(String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("IoT 设备服务")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    /**
     * 更新通知内容
     */
    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }
    
    /**
     * 显示 Toast 提示（单例模式，快速更新）
     */
    private void showToast(final String message) {
        if (mainHandler == null) {
            mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        mainHandler.post(() -> {
            if (singletonToast != null) {
                singletonToast.cancel();  // 取消之前的 Toast
            }
            singletonToast = android.widget.Toast.makeText(IoTService.this, message, android.widget.Toast.LENGTH_SHORT);
            singletonToast.show();
        });
    }
    
}
