package com.tuya.smartai.demo

import android.app.Application
import com.thingclips.smart.ai.bs.ThingFrameOS
import com.tuya.smartai.iot_sdk.ThingOS

const val TAG_PREFIX = "Frame_"

class InitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThingOS.getInstance().init(this)
        ThingFrameOS.getInstance().photoFrame
    }
}
