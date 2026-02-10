package com.tuya.smartai.sample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class PermissionActivity extends AppCompatActivity {
    private static final String TAG = "PermissionActivity";

    private TextView tvNetworkStatus;
    private Button btnRequestNetwork;
    private Button btnEnterIoTActivity;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        // 初始化视图
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        btnRequestNetwork = findViewById(R.id.btnRequestNetwork);

        btnEnterIoTActivity = findViewById(R.id.btnEnterIoTActivity);
        btnEnterIoTActivity.setOnClickListener(v -> {
            // 进入配网界面
            Intent intent = new Intent(PermissionActivity.this, IoTActivity.class);
            startActivity(intent);
            PermissionActivity.this.finish();
        });

        // 初始时更新所有权限状态
        updateAllPermissionStatus();

        // 如果所有权限都已授予，检查配网状态
        if (isAllPermissionsGranted()) {
            checkNetworkStatusAndStart();
        }
    }

    private boolean isAllPermissionsGranted() {
        // 网络权限是自动授予的，直接返回 true
        return true;
    }

    private void updateEnterButtonState() {
        boolean allPermissionsGranted = isAllPermissionsGranted();
        btnEnterIoTActivity.setEnabled(allPermissionsGranted);
        if (allPermissionsGranted) {
            btnEnterIoTActivity.setText("进入物联网应用");
        } else {
            btnEnterIoTActivity.setText("请先授予权限");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAllPermissionStatus();
    }

    private void updateAllPermissionStatus() {
        // 网络权限特殊处理（网络权限是自动授予的）
        boolean internetGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        updateStatusTextView(tvNetworkStatus, internetGranted);
        if (btnRequestNetwork != null) {
            btnRequestNetwork.setVisibility(View.INVISIBLE);
        }
        updateEnterButtonState();
    }



    private void updateStatusTextView(TextView textView, boolean granted) {
        if (granted) {
            textView.setText("已授予");
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            textView.setText("未授予");
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }

    /**
     * 检查配网状态并启动服务和界面
     */
    private void checkNetworkStatusAndStart() {
        // 先启动 IoT 服务（无论是否已配网）
        startIoTService();
        
        // 无论是否已配网，都打开 IoTActivity 来查看状态
        // 延迟一点再打开界面，确保服务先启动
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(PermissionActivity.this, IoTActivity.class);
                startActivity(intent);
                finish();
            }
        }, 500);
    }

    /**
     * 启动 IoT 后台服务
     */
    private void startIoTService() {
        Intent serviceIntent = new Intent(this, IoTService.class);
        // 兼容 Android 4.3：使用数字常量
        if (Build.VERSION.SDK_INT >= 26) { // Build.VERSION_CODES.O = 26
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.i(TAG, "IoT 服务已启动");
    }
}
