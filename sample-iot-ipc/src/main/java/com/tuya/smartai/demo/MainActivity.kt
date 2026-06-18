package com.tuya.smartai.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tuya.smartai.demo.utils.QrCodeUtil
import com.tuya.smartai.iot_sdk.DPEvent
import com.tuya.smartai.iot_sdk.IoTSDKManager
import com.tuya.smartai.iot_sdk.OpenCode
import com.tuya.smartai.iot_sdk.ThingOS
import com.tuya.smartai.iot_sdk.core.IoTCallback
import com.tuya.smartai.iot_sdk.core.IoTParams
import com.tuya.smartai.iot_sdk.ipc.IPCConfig
import com.tuya.smartai.iot_sdk.ipc.IPCEventListener
import com.tuya.smartai.iot_sdk.ipc.ThingIPC
import com.tuya.smartai.iot_sdk.model.ResetType
import java.io.File

/**
 * 最小 IPC 推流 sample。流程：
 *   SDK 初始化 -> 显示二维码 -> 等待 App 扫码绑定 -> MQTT 上线后初始化 P2P/IPC
 *   -> 等待 App 拉流（onLiveVideoStart）-> 起摄像头推 H.264/G711U 实时流。
 *
 * 不含相框/相册/AI 等业务，仅保留作为 IPC 设备对外推流所需的最小骨架。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var ioTSDKManager: com.tuya.smartai.iot_sdk.IIoTManager
    private lateinit var ivQrCode: ImageView
    private lateinit var tvBindStatus: TextView
    private lateinit var tvMqttStatus: TextView

    private var hasInitIpc = false
    private val cameraStreamer by lazy { CameraStreamer(applicationContext) }

    // ---- SDK 生命周期回调 ----
    private val mIotCallback = object : IoTCallback {
        override fun onDpEvent(event: DPEvent?) {
            event ?: return
            // 简单回显：收到的 DP 原样上报
            ioTSDKManager.sendDP(event)
        }

        override fun onReset(resetType: ResetType) {
            Log.e(TAG, "onReset: $resetType")
        }

        override fun onShorturl(url: String) {
            Log.i(TAG, "onShorturl: $url")
            runOnUiThread {
                updateQrCode(QrCodeUtil.getShortUrl(url))
                tvBindStatus.text = "请使用涂鸦 App 扫码绑定设备"
            }
        }

        override fun onActive() {
            Log.i(TAG, "onActive: 设备已绑定")
            runOnUiThread { tvBindStatus.text = "设备已绑定" }
        }

        override fun onFirstActive() {
            Log.i(TAG, "onFirstActive")
            runOnUiThread { tvBindStatus.text = "绑定成功，正在连接..." }
        }

        override fun onMQTTStatusChanged(status: Int) {
            Log.i(TAG, "MQTT status: ${mqttStatusStr(status)}")
            runOnUiThread { tvMqttStatus.text = mqttStatusStr(status) }
            if (status == IoTSDKManager.STATUS_MQTT_ONLINE) {
                // MQTT 上线即可初始化 P2P/IPC，等待 App 拉流。
                initIpcChannel()
            }
        }

        override fun onMqttMsg(protocol: Int, msg: String) {
            Log.d(TAG, "onMqttMsg: protocol=$protocol")
        }
    }

    // ---- IPC/P2P 事件回调 ----
    private val mIpcListener = object : IPCEventListener {
        override fun onLiveVideoStart() {
            Log.i(TAG, "onLiveVideoStart: App 拉流 -> 启动摄像头推流")
            cameraStreamer.start()
        }

        override fun onLiveVideoStop() {
            Log.i(TAG, "onLiveVideoStop: 停止摄像头推流")
            cameraStreamer.stop()
        }

        override fun onRequestVideoKeyFrame() {
            cameraStreamer.requestKeyFrame()
        }

        override fun onVideoLoadAdjust(level: Int) {
            Log.i(TAG, "onVideoLoadAdjust: level=$level")
            cameraStreamer.onLoadAdjust(level)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ivQrCode = findViewById(R.id.ivQrCode)
        tvBindStatus = findViewById(R.id.tvBindStatus)
        tvMqttStatus = findViewById(R.id.tvMqttStatus)

        ioTSDKManager = ThingOS.getInstance().ioTSDKManager
        requestAvPermissionsIfNeeded()
        initSDK()
    }

    private fun requestAvPermissionsIfNeeded() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 0x5A)
        }
    }

    private fun initSDK() {
        val params = IoTParams.Builder()
            .addMode(IoTParams.Mode.MODE_QR)
            .productId("8nk3rz4qpzoat9un")
            .uuid("uuid0d21d10db6604fc7")
            .authKey("Lwppi8LuHc2q1wcLIOPTg8VxJV6JqUC9")
            .version("1.0.1")
            .ioTCallback(mIotCallback)
            .build()

        if (ioTSDKManager.isInitialized) {
            Log.i(TAG, "SDK already initialised, deviceId=${ioTSDKManager.deviceId}")
            return
        }
        val rt = ioTSDKManager.initSDK(params)
        if (rt != OpenCode.CODE_OK) {
            Log.e(TAG, "initSDK failed: $rt")
            runOnUiThread { tvBindStatus.text = "SDK 初始化失败: $rt" }
        } else {
            Log.i(TAG, "initSDK ok")
        }
    }

    /** MQTT 上线后初始化 P2P/IPC 通道：开启实时视频(App 拉流时按需起摄像头)。 */
    private fun initIpcChannel() {
        if (hasInitIpc) return
        val recvDir = File(filesDir, "ipc_recv").apply { mkdirs() }
        val ret = ThingIPC.getInstance().init(
            IPCConfig(
                recvDir = recvDir.absolutePath,
                enableVideo = true,
                videoWidth = 1280,
                videoHeight = 720,
                videoFps = 15,
                videoBitrateKbps = 4000,
            ),
            mIpcListener,
        )
        if (ret == ThingIPC.RESULT_OK) {
            hasInitIpc = true
            Log.i(TAG, "IPC init ok, recvDir=${recvDir.absolutePath}")
        } else {
            Log.e(TAG, "IPC init failed: $ret")
        }
    }

    private fun updateQrCode(content: String?) {
        val bmp = QrCodeUtil.generateQrCodeBitmap(content, 512, 512)
        if (bmp != null) ivQrCode.setImageBitmap(bmp)
    }

    private fun mqttStatusStr(status: Int): String = when (status) {
        IoTSDKManager.STATUS_OFFLINE -> "离线"
        IoTSDKManager.STATUS_MQTT_OFFLINE -> "MQTT 离线"
        IoTSDKManager.STATUS_MQTT_ONLINE -> "已上线"
        else -> "状态: $status"
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { cameraStreamer.stop() }
    }

    companion object {
        private const val TAG = "IPC-Sample"
    }
}
