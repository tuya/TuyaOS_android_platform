package com.tuya.smartai.demo

import android.app.Application
import com.tuya.smartai.iot_sdk.ThingOS

/**
 * 最小 IPC 推流 sample 的 Application：只做 TuyaOS SDK 初始化。
 * 不含相框（ThingFrameOS）等业务模块。
 */
class InitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThingOS.getInstance().init(this)
    }
}
