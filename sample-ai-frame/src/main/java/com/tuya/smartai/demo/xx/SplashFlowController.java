//package com.tuya.smartai.demo.xx;
//
//
//import android.app.Activity;
//import android.content.Intent;
//import android.os.Handler;
//import android.os.Looper;
//import android.text.TextUtils;
//
//import com.thingclips.smart.os.license.LicenseProvisionManager;
//import com.thingclips.smart.os.license.model.LicenseInfo;
//import com.tuya.smartai.iot_sdk.utils.TLog;
//
//import cn.com.framex.host.ui.LicenseActivity;
//import cn.com.framex.host.ui.home.MainActivity;
//import cn.com.framex.host.ui.home.data.TuyaSDKManager;
//import cn.com.framex.host.ui.presets.PresetsActivity;
//
//public class SplashFlowController implements TuyaSDKManager.TuyaListener {
//
//    private static final String TAG = "SplashFlowController";
//    private static final int SPLASH_DELAY = 5000;
//
//    private final Activity activity;
//    private final Handler handler = new Handler(Looper.getMainLooper());
//
//    private boolean started = false;
//    private boolean finished = false;
//    private String deviceId;
//
//    public SplashFlowController(Activity activity) {
//        this.activity = activity;
//    }
//
//    /** 防止重复执行，只执行一次 */
//    public void startOnce() {
//        if (started) return;
//        started = true;
//
//        checkLicense();
//    }
//
//    private void checkLicense() {
//        LicenseInfo info = LicenseProvisionManager.getInstance().getLicense();
//        if (info != null) {
//            TLog.i(TAG, "已有授权: " + info.uuid);
//            initTuya();
//        } else {
//            TLog.w(TAG, "无授权，跳授权页");
//            activity.startActivity(new Intent(activity, LicenseActivity.class));
//            activity.finish();
//        }
//    }
//
//    private void initTuya() {
//        TuyaSDKManager sdk = TuyaSDKManager.getInstance(activity.getApplicationContext());
//        sdk.addListener(this);
//        sdk.initSDK();
//
//        handler.postDelayed(this::jumpNext, SPLASH_DELAY);
//    }
//
//    private void jumpNext() {
//        if (finished || activity.isFinishing()) return;
//        finished = true;
//
//        Intent intent;
//        if (TextUtils.isEmpty(deviceId)) {
//            intent = new Intent(activity, PresetsActivity.class);
//        } else {
//            intent = new Intent(activity, MainActivity.class);
//        }
//        activity.startActivity(intent);
//        activity.finish();
//    }
//
//    @Override
//    public void onDeviceActivated(String deviceId) {
//        this.deviceId = deviceId;
//    }
//
//    @Override
//    public void onFirstActive(String deviceId) {
//        this.deviceId = deviceId;
//    }
//
//    public void release() {
//        handler.removeCallbacksAndMessages(null);
//        TuyaSDKManager.getInstance(activity.getApplicationContext()).removeListener(this);
//    }
//
//    @Override public void onQrCodeUpdated(String qrCode) {}
//    @Override public void onDeviceOnlineChanged(boolean isOnline) {}
//    @Override public void onImageDeviceDownloaded(String path) {}
//    @Override public void onDeviceReset() {}
//    @Override public void initMedia() {}
//    @Override public void initMediaComplete() {}
//}
