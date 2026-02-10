package com.tuya.smartai.sample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tuya.smartai.iot_sdk.DPEvent;
import com.tuya.smartai.iot_sdk.IIoTManager;
import com.tuya.smartai.iot_sdk.ThingOS;
import com.tuya.smartai.iot_sdk.utils.TLog;
import com.tuya.smartai.sample.event.IoTEvent;
import com.tuya.smartai.sample.event.IoTEventBus;
import com.tuya.smartai.sample.utils.QrCodeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class IoTActivity extends AppCompatActivity implements IoTEventBus.EventListener {
    private static final String TAG = "IoTActivity";

    private IIoTManager ioTSDKManager;
    private ImageView ivQrCode;
    private TextView tvBindStatus;
    private TextView tvServiceStatus;
    private TextView tvDeviceId;
    private TextView tvDpData;
    private Button btnResetFactory;
    private Button btnMoveToBackground;
    private StringBuilder dpDataLog = new StringBuilder();
    
    private IoTEventBus eventBus;
    private boolean isDeviceConfigured = false;
    private String currentDeviceId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot);

        initViews();
        registerEventBus();
    }

    private void initViews() {
        ivQrCode = findViewById(R.id.ivQrCode);
        tvBindStatus = findViewById(R.id.tvBindStatus);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        tvDeviceId = findViewById(R.id.tvDeviceId);
        tvDpData = findViewById(R.id.tvDpData);
        btnResetFactory = findViewById(R.id.btnResetFactory);
        btnMoveToBackground = findViewById(R.id.btnMoveToBackground);

        tvDpData.setMovementMethod(new ScrollingMovementMethod());

        btnResetFactory.setOnClickListener(v -> showResetConfirmDialog());
        btnMoveToBackground.setOnClickListener(v -> {
            Toast.makeText(this, "界面已隐藏，服务继续在后台运行", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true); // 移到后台，不是 finish
        });

        // 获取 IoT SDK Manager
        ioTSDKManager = ThingOS.getInstance().getIoTSDKManager();
        
        // 初始显示状态
        tvBindStatus.setText("等待服务初始化...");
        tvServiceStatus.setText("服务状态: 等待连接");
        
        // 初始禁用所有按钮（未配网状态）
        updateButtonsState(false);
    }
    
    /**
     * 注册事件总线监听器
     */
    private void registerEventBus() {
        eventBus = IoTEventBus.getInstance();
        eventBus.register(this);
        TLog.i(TAG, "EventBus 监听器已注册");
        
        // 注册后立即检查当前设备状态
        checkCurrentDeviceStatus();
    }
    
    /**
     * 检查当前设备状态（防止错过事件）
     */
    private void checkCurrentDeviceStatus() {
        if (ioTSDKManager == null) {
            return;
        }
        
        // 检查是否已初始化（已配网）
        if (ioTSDKManager.isInitialized()) {
            String deviceId = ioTSDKManager.getDeviceId();
            TLog.i(TAG, "检测到设备已配网，DeviceId: " + deviceId);
            
            // 更新UI状态
            isDeviceConfigured = true;
            tvBindStatus.setText("✓ 设备已配网");
            
            // 显示设备ID
            if (deviceId != null && !deviceId.isEmpty()) {
                currentDeviceId = deviceId;
                updateDeviceIdDisplay(deviceId);
            }
            
            // 启用按钮
            updateButtonsState(true);
            
            // 隐藏二维码
            removeQrCode();
            
            TLog.i(TAG, "已配网状态UI更新完成");
        } else {
            TLog.i(TAG, "设备未配网，等待配网流程");
        }
        
        // 请求当前 MQTT 状态（让 Service 发送最新状态）
        eventBus.post(new IoTEvent(IoTEvent.Type.REQUEST_STATUS));
        TLog.i(TAG, "已发送状态请求");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventBus != null) {
            eventBus.unregister(this);
            TLog.i(TAG, "EventBus 监听器已注销");
        }
    }
    
    /**
     * 事件总线回调 - 处理所有 IoT 事件
     */
    @Override
    public void onEvent(IoTEvent event) {
        TLog.i(TAG, "收到事件: " + event);
        
        switch (event.getType()) {
            case SERVICE_STARTED:
                tvServiceStatus.setText("服务状态: 已启动");
                break;
                
            case SERVICE_READY:
                Boolean isConfiguredObj = event.getData(Boolean.class);
                isDeviceConfigured = isConfiguredObj != null && isConfiguredObj;
                
                // 如果事件携带设备ID（已配网情况）
                String deviceId = event.getData(String.class);
                if (deviceId != null && !deviceId.isEmpty()) {
                    currentDeviceId = deviceId;
                    updateDeviceIdDisplay(deviceId);
                }
                
                if (isDeviceConfigured) {
                    tvBindStatus.setText("✓ 设备已配网");
                    tvServiceStatus.setText("服务状态: 设备已就绪");
                    removeQrCode();
                    updateButtonsState(true);
                } else {
                    tvBindStatus.setText("等待生成配网二维码...");
                    tvServiceStatus.setText("服务状态: 初始化中");
                    updateButtonsState(false);
                }
                break;
                
            case SDK_INIT_SUCCESS:
                tvServiceStatus.setText("服务状态: SDK 初始化成功");
                break;
                
            case SDK_INIT_FAILED:
                Integer errorCode = event.getData(Integer.class);
                tvBindStatus.setText("✗ SDK 初始化失败: " + errorCode);
                tvServiceStatus.setText("服务状态: 初始化失败");
                Toast.makeText(this, "SDK 初始化失败: " + errorCode, Toast.LENGTH_LONG).show();
                break;
                
            case QR_CODE_GENERATED:
                String url = event.getData(String.class);
                if (url != null) {
                    String shortUrl = QrCodeUtil.getShortUrl(url);
                    if (shortUrl != null && !shortUrl.isEmpty()) {
                        updateQrCode(shortUrl);
                        tvBindStatus.setText("请使用 APP 扫码激活设备");
                        tvServiceStatus.setText("服务状态: 等待配网");
                        // 未配网时禁用按钮
                        updateButtonsState(false);
                    }
                }
                break;
                
            case DEVICE_ACTIVE:
            case DEVICE_FIRST_ACTIVE:
                removeQrCode();
                isDeviceConfigured = true;
                tvBindStatus.setText("✓ 配网成功！服务已在后台运行");
                
                // 获取并显示设备ID
                if (ioTSDKManager != null) {
                    String devId = ioTSDKManager.getDeviceId();
                    if (devId != null && !devId.isEmpty()) {
                        currentDeviceId = devId;
                        updateDeviceIdDisplay(devId);
                    }
                }
                
                updateButtonsState(true);
                
                Toast.makeText(this, "配网成功！可以点击下方按钮隐藏界面", Toast.LENGTH_LONG).show();
                break;
                
            case DEVICE_RESET:
                tvBindStatus.setText("设备已重置: " + event.getMessage());
                removeQrCode();
                dpDataLog.setLength(0);
                tvDpData.setText("");
                isDeviceConfigured = false;
                currentDeviceId = null;
                updateDeviceIdDisplay(null);
                updateButtonsState(false);
                Toast.makeText(this, "设备已重置，应用即将重启", Toast.LENGTH_SHORT).show();
                
                // 延迟后重启应用
                new Handler().postDelayed(() -> exitApp(), 2000);
                break;
                
            case DP_EVENT:
                DPEvent dpEvent = event.getData(DPEvent.class);
                if (dpEvent != null) {
                    String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    String dpInfo = String.format("[%s] DP ID: %d, Type: %d, Value: %s\n",
                            time, dpEvent.dpid, dpEvent.type, 
                            dpEvent.value != null ? dpEvent.value.toString() : "null");
                    
                    dpDataLog.append(dpInfo);
                    tvDpData.setText(dpDataLog.toString());
                    
                    // 自动滚动到底部
                    if (tvDpData.getLayout() != null) {
                        int scrollAmount = tvDpData.getLayout().getLineTop(tvDpData.getLineCount()) 
                                - tvDpData.getHeight();
                        if (scrollAmount > 0) {
                            tvDpData.scrollTo(0, scrollAmount);
                        }
                    }
                }
                break;
                
            case MQTT_STATUS_CHANGED:
                Integer status = event.getData(Integer.class);
                String statusMsg = event.getMessage();
                
                // 根据状态设置颜色
                if (status != null && status == 2) { // STATUS_MQTT_ONLINE
                    tvServiceStatus.setText("✓ 服务状态: 在线");
                    tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else if (status != null && status == 1) { // STATUS_MQTT_OFFLINE
                    tvServiceStatus.setText("✗ 服务状态: 离线");
                    tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                } else {
                    tvServiceStatus.setText("服务状态: " + statusMsg);
                    tvServiceStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                }
                break;
        }
    }
    
    /**
     * 更新设备 ID 显示
     */
    private void updateDeviceIdDisplay(String deviceId) {
        if (deviceId != null && !deviceId.isEmpty()) {
            tvDeviceId.setText("设备 ID: " + deviceId);
            tvDeviceId.setVisibility(View.VISIBLE);
        } else {
            tvDeviceId.setText("设备 ID: 未获取");
            tvDeviceId.setVisibility(View.GONE);
        }
    }
    
    /**
     * 重启应用
     */
    private void exitApp() {
        Context context = IoTActivity.this;
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
        Runtime.getRuntime().exit(0);
    }

    private void updateQrCode(String content) {
        Bitmap bitmap = QrCodeUtil.generateQrCodeBitmap(content, 512, 512);
        if (bitmap != null) {
            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, "生成二维码失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeQrCode() {
        ivQrCode.setImageBitmap(null);
        ivQrCode.setVisibility(View.GONE);
    }

    /**
     * 更新所有按钮状态（恢复出厂设置 + 隐藏界面）
     */
    private void updateButtonsState(boolean enabled) {
        btnResetFactory.setEnabled(enabled);
        btnResetFactory.setAlpha(enabled ? 1.0f : 0.5f);
        
        btnMoveToBackground.setEnabled(enabled);
        btnMoveToBackground.setAlpha(enabled ? 1.0f : 0.5f);
        btnMoveToBackground.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }
    
    private void showResetConfirmDialog() {
        if (!isDeviceConfigured || ioTSDKManager == null) {
            Toast.makeText(this, "设备未配网或服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("恢复出厂设置")
                .setMessage("恢复出厂设置将清除所有数据，设备需要重新配网。应用将自动重启。确定要继续吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    boolean success = ioTSDKManager.resetFactory();
                    if (success) {
                        Toast.makeText(this, "正在恢复出厂设置...", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "恢复出厂设置失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
