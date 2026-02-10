package com.tuya.smartai.sample;

import android.app.Application;

import com.tuya.smartai.iot_sdk.ThingOS;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化 ThingOS SDK
        ThingOS.getInstance().init(this);
    }
}
