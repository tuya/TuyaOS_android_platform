package com.tuya.smartai.demo;

import android.app.Application;

import com.thingclips.smart.ai.bs.ThingFrameOS;
import com.tuya.smartai.iot_sdk.ThingOS;

public class InitApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThingOS.getInstance().init(this);
        ThingFrameOS.getInstance().getPhotoFrame();
    }
}
