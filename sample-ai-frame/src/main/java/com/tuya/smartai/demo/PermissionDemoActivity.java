package com.tuya.smartai.demo;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionDemoActivity extends AppCompatActivity {
    private static final String TAG = "PermissionDemoActivity";

    private static final int REQUEST_CODE_BLUETOOTH = 101;
    private static final int REQUEST_CODE_STORAGE = 102;
    private static final int REQUEST_CODE_AUDIO = 103;
    private static final int REQUEST_CODE_CAMERA = 104;
    /** Android 11+：所有文件访问权限 */
    private static final int REQUEST_CODE_MANAGE_ALL_FILES = 105;

    private TextView tvBluetoothStatus, tvNetworkStatus, tvStorageStatus, tvAudioStatus, tvCameraStatus;
    private Button btnRequestBluetooth, btnRequestStorage, btnRequestAudio, btnRequestCamera;
    // 网络按钮通常不需要，但我们保留以更新其状态
    private Button btnRequestNetwork;
    private Button btnEnterIoTActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_demo);

        // 初始化视图
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus);
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        tvStorageStatus = findViewById(R.id.tvStorageStatus);
        tvAudioStatus = findViewById(R.id.tvAudioStatus);
        tvCameraStatus = findViewById(R.id.tvCameraStatus);

        btnRequestBluetooth = findViewById(R.id.btnRequestBluetooth);
        btnRequestNetwork = findViewById(R.id.btnRequestNetwork); // 虽然通常隐藏
        btnRequestStorage = findViewById(R.id.btnRequestStorage);
        btnRequestAudio = findViewById(R.id.btnRequestAudio);
        btnRequestCamera = findViewById(R.id.btnRequestCamera);

        // 设置按钮点击事件
        btnRequestBluetooth.setOnClickListener(v -> requestBluetoothPermissions());
        btnRequestStorage.setOnClickListener(v -> requestStoragePermissions());
        btnRequestAudio.setOnClickListener(v -> requestAudioPermission());
        btnRequestCamera.setOnClickListener(v -> requestCameraPermission());

        btnEnterIoTActivity = findViewById(R.id.btnEnterIoTActivity);
        btnEnterIoTActivity.setOnClickListener(v -> {
            Intent intent = new Intent(PermissionDemoActivity.this, MainActivity.class);
            startActivity(intent);
            PermissionDemoActivity.this.finish();
        });

        // 初始时更新所有权限状态
        updateAllPermissionStatus();

        if(isAllPermissionsGranted()){
            //又权限自动进入主逻辑
            Intent intent = new Intent(PermissionDemoActivity.this, MainActivity.class);
            startActivity(intent);
            PermissionDemoActivity.this.finish();
        }
    }


    private boolean isAllPermissionsGranted() {
        // 检查蓝牙权限
        for (String permission : getBluetoothPermissionsToRequest()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        
        // 检查存储权限（产测功能需要）
        if (!hasStoragePermission()) {
            return false;
        }
        
        return true;
    }

    private void updateEnterButtonState() {


//        // 检查音频权限
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            allPermissionsGranted = false;
//        }
//
//        // 检查相机权限
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            allPermissionsGranted = false;
//        }

        // 网络权限通常已授予
        boolean allPermissionsGranted = isAllPermissionsGranted();
        // 更新按钮状态
        btnEnterIoTActivity.setEnabled(allPermissionsGranted);
        if (allPermissionsGranted) {
            btnEnterIoTActivity.setText("进入主逻辑");
        } else {
            btnEnterIoTActivity.setText("请先授予权限");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 当用户从设置页面返回时，也更新权限状态
        updateAllPermissionStatus();
    }

    private void updateAllPermissionStatus() {
        updatePermissionStatusUI(getBluetoothPermissionsToRequest(), tvBluetoothStatus, btnRequestBluetooth);
        updateStoragePermissionStatusUI();
        updatePermissionStatusUI(new String[]{Manifest.permission.RECORD_AUDIO}, tvAudioStatus, btnRequestAudio);
        updatePermissionStatusUI(new String[]{Manifest.permission.CAMERA}, tvCameraStatus, btnRequestCamera);

        // 网络权限特殊处理
        boolean internetGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        updateStatusTextView(tvNetworkStatus, internetGranted);
        if (btnRequestNetwork != null) { // btnRequestNetwork 可能在布局中被设置为 gone
            btnRequestNetwork.setVisibility(View.INVISIBLE); // 通常不需要按钮
        }
        updateEnterButtonState();
    }
    
    /**
     * 更新存储权限状态UI
     * Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
     * Android 10- 使用 READ/WRITE_EXTERNAL_STORAGE
     */
    private void updateStoragePermissionStatusUI() {
        boolean hasPermission = hasStoragePermission();
        updateStatusTextView(tvStorageStatus, hasPermission);
        if (hasPermission) {
            btnRequestStorage.setText("已授予");
            btnRequestStorage.setEnabled(false);
        } else {
            btnRequestStorage.setText("授予");
            btnRequestStorage.setEnabled(true);
        }
    }
    
    /**
     * 检查是否有存储权限
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：检查 MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 及以下：检查 READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private String[] getBluetoothPermissionsToRequest() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        // 蓝牙扫描通常需要位置权限
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        // permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION); // 如果只需要粗略位置
        return permissions.toArray(new String[0]);
    }

//    private String[] getStoragePermissionsToRequest() {
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
////            return new String[]{
////                    Manifest.permission.READ_MEDIA_IMAGES,
////                    Manifest.permission.READ_MEDIA_VIDEO,
////                    // Manifest.permission.READ_MEDIA_AUDIO // 如果也需要音频
////            };
////        } else
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 & 12
//            return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
//            // WRITE_EXTERNAL_STORAGE 在API 30+作用很小，除非有特殊需求（如管理非媒体文件）
//            // 或者通过 requestLegacyExternalStorage=true (不推荐)
//        } else { // Android 10 及以下
//            return new String[]{
//                    Manifest.permission.READ_EXTERNAL_STORAGE,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE
//            };
//        }
//    }


    private void requestBluetoothPermissions() {
        requestPermissionsGroup(getBluetoothPermissionsToRequest(), REQUEST_CODE_BLUETOOTH, tvBluetoothStatus, btnRequestBluetooth);
    }

    /**
     * 申请存储权限
     * Android 11+ 需要跳转到设置页面授权 MANAGE_EXTERNAL_STORAGE
     * Android 10- 使用运行时权限
     */
    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：需要跳转设置页面授权
            if (!Environment.isExternalStorageManager()) {
                showManageAllFilesDialog();
            } else {
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
                updateStoragePermissionStatusUI();
            }
        } else {
            // Android 10 及以下：运行时权限
            String[] permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            requestPermissionsGroup(permissions, REQUEST_CODE_STORAGE, tvStorageStatus, btnRequestStorage);
        }
    }
    
    /**
     * 显示"所有文件访问权限"申请对话框（Android 11+）
     */
    private void showManageAllFilesDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要存储访问权限")
            .setMessage("为了读取SD卡中的授权文件，需要授予\"所有文件访问权限\"。\n\n" +
                       "请在下一个页面选择\"允许管理所有文件\"。")
            .setPositiveButton("去设置", (dialog, which) -> {
                Log.i(TAG, "showManageAllFilesDialog - 用户点击去设置");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_ALL_FILES);
                } catch (Exception e) {
                    Log.w(TAG, "showManageAllFilesDialog - 无法直接跳转应用设置，使用通用设置: " + e.getMessage());
                    // 部分设备不支持直接跳转到应用设置
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_ALL_FILES);
                }
            })
            .setNegativeButton("取消", (dialog, which) -> {
                Log.w(TAG, "showManageAllFilesDialog - 用户取消权限申请");
                Toast.makeText(this, "未授权存储权限，产测功能将无法使用", Toast.LENGTH_LONG).show();
            })
            .setCancelable(false)
            .show();
    }

    private void requestAudioPermission() {
        requestPermissionsGroup(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_AUDIO, tvAudioStatus, btnRequestAudio);
    }

    private void requestCameraPermission() {
        requestPermissionsGroup(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA, tvCameraStatus, btnRequestCamera);
    }

    private void requestPermissionsGroup(String[] permissions, int requestCode, TextView statusView, Button buttonView) {
        List<String> permissionsToRequest = new ArrayList<>();
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
                allGranted = false;
            }
        }

        if (allGranted) {
            updateStatusTextView(statusView, true);
            buttonView.setText("已授予");
            buttonView.setEnabled(false);
            Toast.makeText(this, "权限 '" + getFriendlyPermissionName(requestCode) + "' 已授予", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), requestCode);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allPermissionsInGroupGranted = true;
        if (grantResults.length > 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsInGroupGranted = false;
                    break;
                }
            }
        } else { // 如果grantResults为空，意味着请求被取消或出现问题
            allPermissionsInGroupGranted = false;
        }


        // 检查所有最初请求的权限是否都已授予（因为系统可能只返回它实际提示用户的那些）
        String[] originalPermissionsToCheck;
        TextView targetStatusView;
        Button targetButtonView;

        switch (requestCode) {
            case REQUEST_CODE_BLUETOOTH:
                originalPermissionsToCheck = getBluetoothPermissionsToRequest();
                targetStatusView = tvBluetoothStatus;
                targetButtonView = btnRequestBluetooth;
                break;
            case REQUEST_CODE_STORAGE:
                // Android 10 及以下的存储权限回调
                originalPermissionsToCheck = new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
                targetStatusView = tvStorageStatus;
                targetButtonView = btnRequestStorage;
                break;
            case REQUEST_CODE_AUDIO:
                originalPermissionsToCheck = new String[]{Manifest.permission.RECORD_AUDIO};
                targetStatusView = tvAudioStatus;
                targetButtonView = btnRequestAudio;
                break;
            case REQUEST_CODE_CAMERA:
                originalPermissionsToCheck = new String[]{Manifest.permission.CAMERA};
                targetStatusView = tvCameraStatus;
                targetButtonView = btnRequestCamera;
                break;
            default:
                return; // 未知请求码
        }

        // 再次检查原始请求的所有权限
        boolean allOriginalPermissionsNowGranted = true;
        for (String perm : originalPermissionsToCheck) {
            Log.i(TAG, "onRequestPermissionsResult:  perm " + perm + ",isGrant:  " + ContextCompat.checkSelfPermission(this, perm));
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allOriginalPermissionsNowGranted = false;
                break;
            }
        }

        updateStatusTextView(targetStatusView, allOriginalPermissionsNowGranted);
        if (allOriginalPermissionsNowGranted) {
            targetButtonView.setText("已授予");
            targetButtonView.setEnabled(false);
            Toast.makeText(this, "权限 '" + getFriendlyPermissionName(requestCode) + "' 已成功授予", Toast.LENGTH_SHORT).show();
        } else {
            targetButtonView.setText("授予");
            targetButtonView.setEnabled(true);
            Toast.makeText(this, "权限 '" + getFriendlyPermissionName(requestCode) + "' 未完全授予", Toast.LENGTH_SHORT).show();
            // 你可以在这里添加逻辑，解释为什么需要这些权限，或者如果用户永久拒绝了某些权限，引导他们到设置
            // e.g. if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
            //      // 用户选择了 "不再询问"
            // }
        }

        updateEnterButtonState();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_MANAGE_ALL_FILES) {
            // Android 11+ 从设置页面返回
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                boolean isManager = Environment.isExternalStorageManager();
                Log.i(TAG, "onActivityResult - MANAGE_ALL_FILES 结果: " + (isManager ? "已授予" : "未授予"));
                if (isManager) {
                    Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "存储权限未授予，产测功能将无法使用", Toast.LENGTH_LONG).show();
                }
                updateStoragePermissionStatusUI();
                updateEnterButtonState();
            }
        }
    }

    private boolean areAllCriticalPermissionsGranted() {
        // 只要检查关键权限（蓝牙、相机、录音、存储等）
        String[][] permissionGroups = {
                getBluetoothPermissionsToRequest(),
                new String[]{Manifest.permission.RECORD_AUDIO},
                new String[]{Manifest.permission.CAMERA}
                // 如果要加存储就再写一组
        };
        for (String[] group : permissionGroups) {
            for (String perm : group) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
    private void updatePermissionStatusUI(String[] permissionsToCheck, TextView statusView, Button buttonView) {
        boolean allGranted = true;
        if (permissionsToCheck == null || permissionsToCheck.length == 0) {
            allGranted = false; // 如果没有权限可查（不应发生），则视为未授予
        } else {
            for (String permission : permissionsToCheck) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
        }

        updateStatusTextView(statusView, allGranted);
        if (allGranted) {
            buttonView.setText("已授予");
            buttonView.setEnabled(false);
        } else {
            buttonView.setText("授予");
            buttonView.setEnabled(true);
        }
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

    private String getFriendlyPermissionName(int requestCode) {
        switch (requestCode) {
            case REQUEST_CODE_BLUETOOTH:
                return "蓝牙";
            case REQUEST_CODE_STORAGE:
                return "存储";
            case REQUEST_CODE_AUDIO:
                return "录音";
            case REQUEST_CODE_CAMERA:
                return "相机";
            default:
                return "未知";
        }
    }
}