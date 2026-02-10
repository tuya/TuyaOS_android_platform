package com.tuya.smartai.sample.event;

import com.tuya.smartai.iot_sdk.DPEvent;

/**
 * IoT 事件基类
 */
public class IoTEvent {
    
    /**
     * 事件类型
     */
    public enum Type {
        SERVICE_STARTED,        // 服务已启动
        SERVICE_READY,          // 服务就绪（SDK 初始化完成）
        SDK_INIT_SUCCESS,       // SDK 初始化成功
        SDK_INIT_FAILED,        // SDK 初始化失败
        QR_CODE_GENERATED,      // 二维码生成
        DEVICE_ACTIVE,          // 设备激活（重新上线）
        DEVICE_FIRST_ACTIVE,    // 设备首次激活（首次配网成功）
        DEVICE_RESET,           // 设备重置
        DP_EVENT,               // DP 数据事件
        MQTT_STATUS_CHANGED,    // MQTT 状态变化
        REQUEST_STATUS          // 请求当前状态（Activity -> Service）
    }
    
    private final Type type;
    private Object data;
    private String message;
    
    public IoTEvent(Type type) {
        this.type = type;
    }
    
    public IoTEvent(Type type, String message) {
        this.type = type;
        this.message = message;
    }
    
    public IoTEvent(Type type, Object data) {
        this.type = type;
        this.data = data;
    }
    
    public IoTEvent(Type type, Object data, String message) {
        this.type = type;
        this.data = data;
        this.message = message;
    }
    
    public Type getType() {
        return type;
    }
    
    public Object getData() {
        return data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public <T> T getData(Class<T> clazz) {
        if (data != null && clazz.isInstance(data)) {
            return clazz.cast(data);
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "IoTEvent{type=" + type + ", message='" + message + "', data=" + data + "}";
    }
    
    // 便捷的静态方法创建常用事件
    
    public static IoTEvent serviceStarted() {
        return new IoTEvent(Type.SERVICE_STARTED, "服务已启动");
    }
    
    public static IoTEvent serviceReady(boolean isConfigured) {
        return new IoTEvent(Type.SERVICE_READY, isConfigured, 
            isConfigured ? "设备已配网" : "设备未配网");
    }
    
    public static IoTEvent sdkInitSuccess() {
        return new IoTEvent(Type.SDK_INIT_SUCCESS, "SDK 初始化成功");
    }
    
    public static IoTEvent sdkInitFailed(int errorCode) {
        return new IoTEvent(Type.SDK_INIT_FAILED, errorCode, "SDK 初始化失败: " + errorCode);
    }
    
    public static IoTEvent qrCodeGenerated(String url) {
        return new IoTEvent(Type.QR_CODE_GENERATED, url, "二维码已生成");
    }
    
    public static IoTEvent deviceActive() {
        return new IoTEvent(Type.DEVICE_ACTIVE, "设备已激活");
    }
    
    public static IoTEvent deviceFirstActive() {
        return new IoTEvent(Type.DEVICE_FIRST_ACTIVE, "设备首次激活");
    }
    
    public static IoTEvent deviceReset(String resetType) {
        return new IoTEvent(Type.DEVICE_RESET, resetType, "设备已重置");
    }
    
    public static IoTEvent dpEvent(DPEvent dpEvent) {
        return new IoTEvent(Type.DP_EVENT, dpEvent, "收到 DP 数据");
    }
    
    public static IoTEvent mqttStatusChanged(int status, String statusStr) {
        return new IoTEvent(Type.MQTT_STATUS_CHANGED, status, "MQTT: " + statusStr);
    }
}
