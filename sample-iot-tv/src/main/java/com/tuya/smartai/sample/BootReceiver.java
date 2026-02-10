package com.tuya.smartai.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import com.tuya.smartai.iot_sdk.IIoTManager;
import com.tuya.smartai.iot_sdk.ThingOS;
import com.tuya.smartai.iot_sdk.utils.TLog;

/**
 * 开机自启动广播接收器
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TLog.i(TAG, "系统启动完成，检查设备配网状态...");
            
            // 检查设备是否已配网
            IIoTManager ioTSDKManager = ThingOS.getInstance().getIoTSDKManager();
            if (ioTSDKManager.isInitialized() && !TextUtils.isEmpty(ioTSDKManager.getDeviceId())) {
                // 已配网，启动后台服务
                TLog.i(TAG, "设备已配网，启动 IoT 服务");
                Intent serviceIntent = new Intent(context, IoTService.class);
                // 兼容 Android 4.3：使用数字常量
                if (Build.VERSION.SDK_INT >= 26) { // Build.VERSION_CODES.O = 26
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                // 未配网，启动权限申请页面（进入配网流程）
                TLog.w(TAG, "设备未配网，启动配网流程");
                Intent activityIntent = new Intent(context, PermissionActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(activityIntent);
            }
        }
    }
}
