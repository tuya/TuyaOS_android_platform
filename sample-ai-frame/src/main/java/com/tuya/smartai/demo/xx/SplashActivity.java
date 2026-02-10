//package com.tuya.smartai.demo.xx;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.text.TextUtils;
//
//import androidx.annotation.Nullable;
//
//import com.thingclips.smart.os.license.LicenseProvisionManager;
//import com.thingclips.smart.os.license.model.LicenseInfo;
//import com.tuya.smartai.iot_sdk.utils.TLog;
//
//import cn.com.framex.host.R;
//import cn.com.framex.host.base.BaseActivity;
//import cn.com.framex.host.ui.LicenseActivity;
//import cn.com.framex.host.ui.home.MainActivity;
//import cn.com.framex.host.ui.home.data.TuyaSDKManager;
//import cn.com.framex.host.ui.presets.PresetsActivity;
//
//public class SplashActivity extends BaseActivity {
//
//    private SplashFlowController controller;
//
//    @Override
//    protected void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_splash);
//
//        TuyaSDKManager.getInstance(getApplicationContext()).initLicense();
//
//        if (LicenseProvisionManager.getInstance().hasExternalSDCardWithLicenseFile()) {
//            LicenseProvisionManager.getInstance().showProvisionPage();
//        }
//
//        controller = new SplashFlowController(this);
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        controller.startOnce();
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        controller.release();
//    }
//}
