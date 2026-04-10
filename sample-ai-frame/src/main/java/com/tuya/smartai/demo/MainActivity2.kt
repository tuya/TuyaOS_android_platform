package com.tuya.smartai.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.alibaba.fastjson.JSONObject
import com.bumptech.glide.Glide
import com.thingclips.smart.ai.bs.ThingFrameOS
import com.thingclips.smart.ai.bs.api.IPhotoFrameMediaUpdateListener
import com.thingclips.smart.ai.bs.bean.DeleteEventData
import com.thingclips.smart.ai.bs.bean.FrameZoneConfig
import com.thingclips.smart.ai.bs.bean.PhotoInfo
import com.thingclips.smart.ai.bs.bean.StartEventData
import com.thingclips.smart.ai.bs.bean.UploadFinishedEventData
import com.thingclips.smart.ai.bs.bean.UploadedEventData
import com.thingclips.smart.os.license.LicenseProvisionManager
import com.thingclips.smart.os.license.model.LicenseInfo
import com.tuya.smartai.demo.ai.AiChatActivity
import com.tuya.smartai.demo.talk.AiLongEventActivity
import com.tuya.smartai.demo.utils.LoadingDialog
import com.tuya.smartai.demo.utils.QrCodeUtil
import com.tuya.smartai.iot_sdk.DPEvent
import com.tuya.smartai.iot_sdk.IIoTManager
import com.tuya.smartai.iot_sdk.IoTSDKManager
import com.tuya.smartai.iot_sdk.OpenCode
import com.tuya.smartai.iot_sdk.ThingOS
import com.tuya.smartai.iot_sdk.UpgradeEventCallback
import com.tuya.smartai.iot_sdk.core.IoTCallback
import com.tuya.smartai.iot_sdk.core.IoTParams
import com.tuya.smartai.iot_sdk.http.ApiCallback
import com.tuya.smartai.iot_sdk.http.ApiParams
import com.tuya.smartai.iot_sdk.http.ThingApiHelper
import com.tuya.smartai.iot_sdk.model.FirmwareUpgradeInfo
import com.tuya.smartai.iot_sdk.model.ResetType
import com.tuya.smartai.iot_sdk.model.UpgradeStatus
import com.tuya.smartai.iot_sdk.model.WeatherInfo
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class MainActivity2 : AppCompatActivity() {

    private lateinit var ivQrCode: ImageView
    private lateinit var ioTSDKManager: IIoTManager
    private lateinit var loadingDialog: LoadingDialog
    private lateinit var tvBindStatus: TextView
    private lateinit var tvDataConsole: TextView
    private val dataConsole = StringBuilder()
    private lateinit var tvMqttStatus: TextView
    private lateinit var downloadImageView: ImageView
    private lateinit var tvPhotoCount: TextView
    private lateinit var tvPhotoInfo: TextView
    private lateinit var videoView: VideoView
    private var mediaController: MediaController? = null
    private var currentPosition = 0
    private var hasInitFrame = false
    private var currentRealFn = ""
    private var currentFilePath = ""

    private val mIotCallback = object : IoTCallback {
        override fun onDpEvent(event: DPEvent?) {
            event ?: return
            Log.w(TAG, "rev dp: $event")
            dataConsole.append("rev dp: ").append(event).append("\n")
            tvDataConsole.text = dataConsole.toString()
            if (event.type.toInt() == DPEvent.Type.PROP_RAW) {
                Log.w(TAG, Base64.encodeToString(event.value as ByteArray, Base64.DEFAULT))
            }
            dataConsole.append("publishDps: ").append(event).append("\n")
            tvDataConsole.text = dataConsole.toString()
            ioTSDKManager.sendDP(event)
        }

        override fun onReset(resetType: ResetType) {
            Log.e(TAG, "onReset:======================================= resetType=$resetType")
            ThingFrameOS.getInstance().photoFrame.deleteAllPhotos()
            if (resetType == ResetType.GW_LOCAL_RESET_FACTORY) {
                exitApp()
            }
        }

        override fun onShorturl(url: String) {
            Log.w(TAG, "shorturl: $url")
            updateQrCode(QrCodeUtil.getShortUrl(url))
            tvBindStatus.text = "请使用APP扫码激活设备"
            ThingFrameOS.getInstance().photoFrame.deleteAllPhotos()
        }

        override fun onActive() {
            Log.w(TAG, "onActive---------------")
            val deviceId = ioTSDKManager.deviceId
            Log.w(TAG, "deviceId: $deviceId")
            if (!deviceId.isNullOrEmpty()) {
                tvBindStatus.text = "设备已激活，ID: $deviceId"
                removeQrCode()
                hasInitFrame = false
            } else {
                tvBindStatus.text = "出错了"
            }
        }

        override fun onFirstActive() {
            Log.w(TAG, "onFirstActive -----------------")
            val deviceId = ioTSDKManager.deviceId
            Log.w(TAG, "deviceId: $deviceId")
            if (!deviceId.isNullOrEmpty()) {
                tvBindStatus.text = "激活成功，ID: $deviceId"
                removeQrCode()
                initKey()
            } else {
                tvBindStatus.text = "出错了"
            }
        }

        override fun onMQTTStatusChanged(status: Int) {
            Log.e(TAG, "Status: ${getMqttStatusStr(status)}, hasInitFrame :$hasInitFrame")

            if (status == IoTSDKManager.STATUS_MQTT_ONLINE) {
                tvMqttStatus.text = "设备上线"
                tvMqttStatus.setTextColor(ContextCompat.getColor(this@MainActivity2, R.color.colorPrimary))
                val deviceId = ioTSDKManager.deviceId
                if (!hasInitFrame) {
                    val config = FrameZoneConfig.builder()
                        .devId(deviceId)
                        .resolution("1024*800")
                        .allowFormats(arrayOf("png", "jpg", "mp4"))
                        .maxFc(3)
                        .maxFs(10485760)

                    ThingFrameOS.getInstance().photoFrame.initFrameZone(config, object : ApiCallback<Void> {
                        override fun onSuccess(result: Void?) {
                            hasInitFrame = true
                            initFrameImageUpdateListener()
                        }

                        override fun onFailure(errorCode: String, errorMessage: String) {
                            Log.e(TAG, "onFailure() called with: errorCode = [$errorCode], errorMessage = [$errorMessage]")
                            hasInitFrame = false
                        }
                    })
                }
            } else {
                if (!ioTSDKManager.deviceId.isNullOrEmpty()) {
                    tvMqttStatus.text = "设备离线"
                    tvMqttStatus.setTextColor(ContextCompat.getColor(this@MainActivity2, R.color.colorAccent))
                }
            }
        }

        override fun onMqttMsg(protocol: Int, msg: String) {
            Log.d(TAG, "onMqttMsg() called with: protocol = [$protocol], msg = [$msg]")
        }
    }

    private val mUpgradeCallback = object : UpgradeEventCallback {
        override fun onUpgradeInfo(info: FirmwareUpgradeInfo) {
            Log.w(TAG, "onUpgradeInfo: $info")
            dataConsole.append("===== 收到固件升级推送 =====\n")
                .append("版本: ").append(info.version).append("\n")
                .append("通道: ").append(info.deviceTypeDesc).append("\n")
                .append("升级类型: ").append(info.upgradeTypeDesc).append("\n")
                .append("文件大小: ").append(info.fileSize).append(" 字节\n")
                .append("MD5: ").append(info.fwMd5).append("\n")
                .append("差分升级: ").append(if (info.diffOta) "是" else "否").append("\n")
                .append("下载URL: ").append(info.fwUrl).append("\n")
            tvDataConsole.text = dataConsole.toString()

            val version = ioTSDKManager.getCurrentFwVersion(0)
            Log.i(TAG, "当前固件版本1: $version")
            Log.i(TAG, "收到升级推送，直接开始下载")
            Toast.makeText(this@MainActivity2, "收到固件升级通知 v${info.version}，开始自动升级", Toast.LENGTH_SHORT).show()
            ioTSDKManager.startUpgradeDownload()
        }

        override fun onUpgradeDownloadStart() {
            Log.i(TAG, "onUpgradeDownloadStart")
            dataConsole.append("升级文件开始下载...\n")
            tvDataConsole.text = dataConsole.toString()
        }

        override fun onUpgradeDownloadUpdate(progress: Int) {
            Log.i(TAG, "onUpgradeDownloadUpdate: $progress%")
            dataConsole.append("升级下载进度: ").append(progress).append("%\n")
            tvDataConsole.text = dataConsole.toString()
        }

        override fun upgradeFileDownloadFinished(resultCode: Int, file: String) {
            Log.i(TAG, "upgradeFileDownloadFinished: resultCode=$resultCode, file=$file")
            dataConsole.append("升级文件下载完成: resultCode=").append(resultCode)
                .append(", file=").append(file).append("\n")
            tvDataConsole.text = dataConsole.toString()
            if (resultCode == 0) {
                Toast.makeText(this@MainActivity2, "升级文件下载成功，准备安装...", Toast.LENGTH_LONG).show()
                Thread { installApk(file) }.start()
            } else {
                Toast.makeText(this@MainActivity2, "升级文件下载失败: $resultCode", Toast.LENGTH_SHORT).show()
                Thread { ioTSDKManager.reportUpgradeStatus(0, UpgradeStatus.TUS_UPGRADE_ERROR_LOW_BATTERY) }.start()
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ioTSDKManager = ThingOS.getInstance().ioTSDKManager
        loadingDialog = LoadingDialog(this)

        findViewById<View>(R.id.reset).setOnClickListener(::onClick)
        tvBindStatus = findViewById(R.id.tv_bind_status)
        findViewById<View>(R.id.dp_send).setOnClickListener(::onClick)
        findViewById<View>(R.id.http).setOnClickListener(::onClick)
        findViewById<View>(R.id.get_events).setOnClickListener(::onClick)
        ivQrCode = findViewById(R.id.ivQrCode)
        tvDataConsole = findViewById(R.id.tv_data_console)
        tvMqttStatus = findViewById(R.id.tv_mqtt_status)
        downloadImageView = findViewById(R.id.downloaded_image_view)
        videoView = findViewById(R.id.downloaded_video_view)
        tvPhotoCount = findViewById(R.id.tv_photo_count)
        tvPhotoInfo = findViewById(R.id.tv_photo_info)
        findViewById<View>(R.id.next_photo).setOnClickListener(::onClick)
        findViewById<View>(R.id.ai).setOnClickListener(::onClick)
        findViewById<View>(R.id.ai_chat).setOnClickListener(::onClick)
        findViewById<View>(R.id.cloud_vad).setOnClickListener(::onClick)
        findViewById<View>(R.id.reset).setOnClickListener(::onClick)
        findViewById<View>(R.id.fetch_device_info_partial).setOnClickListener(::onClick)
        findViewById<View>(R.id.get_weather).setOnClickListener(::onClick)
        findViewById<View>(R.id.get_weather_default).setOnClickListener(::onClick)
        findViewById<View>(R.id.check_firmware_upgrade).setOnClickListener(::onClick)

        LicenseProvisionManager.getInstance()
            .init(this).grantLicense("uuidf0e66ee59b1d5fc7", "bQJ5BeCKLEwkpXeg1R3wxkUt7B3aIMFW")

        initLicenseModule()
    }

    private fun printLn() {
        val photoInfos = ThingFrameOS.getInstance().photoFrame.allFileInfos
        Log.d(TAG, "已有文件数量: ${photoInfos.size}")
        for (info in photoInfos) {
            Log.d(TAG, "realFn: ${info.realFn}, title: ${info.title}, type: ${info.fileType},prefix: ${info.prefix}, centerX: ${info.centerX}, localPath: ${info.localPath}")
        }
    }

    private fun updateQrCode(content: String?) {
        val bitmap = QrCodeUtil.generateQrCodeBitmap(content, 512, 512)
        if (bitmap != null) {
            ivQrCode.setImageBitmap(bitmap)
            ivQrCode.visibility = View.VISIBLE
        } else {
            Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeQrCode() {
        ivQrCode.setImageBitmap(null)
        ivQrCode.visibility = View.GONE
    }

    private fun installApk(apkPath: String) {
        Log.i(TAG, "installApk: 开始静默安装 APK: $apkPath")

        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Log.e(TAG, "installApk: APK 文件不存在: $apkPath")
            runOnUiThread { Toast.makeText(this, "安装失败：APK 文件不存在", Toast.LENGTH_SHORT).show() }
            return
        }

        val apkLength = apkFile.length()
        Log.i(TAG, "installApk: APK 文件大小: $apkLength 字节")

        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setSize(apkLength) }

        var session: PackageInstaller.Session? = null
        var fis: FileInputStream? = null
        var os: OutputStream? = null

        try {
            val sessionId = packageInstaller.createSession(params)
            Log.i(TAG, "installApk: 创建安装会话, sessionId=$sessionId")

            session = packageInstaller.openSession(sessionId)
            os = session.openWrite("ota_upgrade", 0, apkLength)
            fis = FileInputStream(apkFile)
            val buffer = ByteArray(65536)
            var totalWritten = 0L
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                os.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
            }
            session.fsync(os)
            Log.i(TAG, "installApk: APK 写入完成, totalWritten=$totalWritten")

            os.close(); os = null
            fis.close(); fis = null

            val intent = Intent("com.tuya.smartai.demo.INSTALL_COMPLETE").apply { setPackage(packageName) }
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT
            )

            Log.i(TAG, "installApk: 提交安装会话...")
            session.commit(pendingIntent.intentSender)
            Log.i(TAG, "installApk: 安装会话已提交，等待系统处理")

            runOnUiThread {
                dataConsole.append("APK 安装请求已提交，等待系统安装...\n")
                tvDataConsole.text = dataConsole.toString()
                Toast.makeText(this, "安装中，应用即将重启...", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "installApk: 安装异常", e)
            session?.abandon()
            runOnUiThread { Toast.makeText(this, "安装异常: ${e.message}", Toast.LENGTH_LONG).show() }
        } finally {
            try { os?.close() } catch (_: Exception) {}
            try { fis?.close() } catch (_: Exception) {}
        }
    }

    private fun getMqttStatusStr(status: Int): String = when (status) {
        IoTSDKManager.STATUS_OFFLINE -> "OFFLINE"
        IoTSDKManager.STATUS_MQTT_OFFLINE -> "MQTT OFFLINE"
        IoTSDKManager.STATUS_MQTT_ONLINE -> "MQTT ONLINE"
        else -> "Status: $status"
    }

    private fun initSDK() {
        val info = LicenseProvisionManager.getInstance().license
        Log.e(TAG, "initSDK license : uuid=${info.uuid}, key=${info.key}")

        val params = IoTParams.Builder()
            .addMode(IoTParams.Mode.MODE_QR)
            .productId("aqcxhpklvzlblyl3")
            .uuid(info.uuid)
            .authKey(info.key)
            .version("1.0.1")
            .ioTCallback(mIotCallback)
            .build()

        val rt = ioTSDKManager.initSDK(params)
        if (rt != OpenCode.CODE_OK) {
            showDialog("初始化失败了,请检查")
        } else {
            Log.e(TAG, "---------初始化成功---------")
            ioTSDKManager.setUpgradeCallback(mUpgradeCallback)
        }
    }

    private fun initLicenseModule() {
        Log.i(TAG, "初始化授权模块...")
        LicenseProvisionManager.getInstance()
            .init(this)
            .setPersistentStoragePath("/vendor")
            .enableAutoLaunch(true)
            .setUseSystemProperty(true)
            .setOnLicenseChangeListener(object : LicenseProvisionManager.OnLicenseChangeListener {
                override fun onLicenseGranted(license: LicenseInfo) {
                    Log.i(TAG, "授权码已授予: uuid=${license.uuid},ioTSDKManager.isInitialized() ${ioTSDKManager.isInitialized}")
                    if (!ioTSDKManager.isInitialized) {
                        Toast.makeText(this@MainActivity2, "授权成功，正在初始化SDK...", Toast.LENGTH_SHORT).show()
                        initSDK()
                    }
                }

                override fun onLicenseRevoked() {
                    Log.i(TAG, "授权码已回收")
                    showDialog("授权已回收，设备将无法使用")
                }
            })
            .start()

        val info = LicenseProvisionManager.getInstance().license
        if (info != null) {
            Log.i(TAG, "检测到已有授权: uuid=${info.uuid}")
            initSDK()
            if (LicenseProvisionManager.getInstance().hasExternalSDCardWithLicenseFile()) {
                showLicenseGuideDialog()
            }
        } else {
            Log.w(TAG, "未检测到授权码，等待授权...")
            tvBindStatus.text = "请插入授权SD卡进行授权"
            showLicenseGuideDialog()
        }
    }

    private fun showLicenseGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("授权相关")
            .setMessage("当前设备未授权或者具有授权 SD卡\n\n需要操作授权码吗\n")
            .setPositiveButton("进入授权页面") { _, _ ->
                LicenseProvisionManager.getInstance().showProvisionPage()
            }
            .setNegativeButton("稍后", null)
            .setCancelable(false)
            .show()
    }

    private fun showDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("警告⚠️")
            .setMessage(msg)
            .setPositiveButton("确认") { _, _ -> exitApp() }
            .setCancelable(false)
            .show()
    }

    private fun exitApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        finish()
        Runtime.getRuntime().exit(0)
    }

    private fun showEmotionSelectDialog() {
        val emotions = arrayOf("0 -  不显示", "1 - 😍 惊喜", "2 - 😲 开心", "3 - 😢 难过")
        AlertDialog.Builder(this)
            .setTitle("选择表情")
            .setSingleChoiceItems(emotions, -1) { dialog, which ->
                sendEmotionDP(which.toString())
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendEmotionDP(mode: String) {
        val timestamp = (System.currentTimeMillis() / 1000L).toInt()
        val valueMap = hashMapOf(
            "real-fn" to "2b0f3be64f48ad87d45c42869fabadef_1768880668289.jpg",
            "meta" to "ay15269934114205cqNm",
            "mode" to mode
        )
        Log.e(TAG, "sendEmotionDP value: ${JSONObject.toJSONString(valueMap)}")
        val event1 = DPEvent(2, DPEvent.Type.PROP_RAW.toByte(), JSONObject.toJSONString(valueMap).toByteArray(), timestamp)
        dataConsole.append("发送表情(mode=$mode): ").append(event1).append("\n")
        tvDataConsole.text = dataConsole.toString()
        ioTSDKManager.sendDPWithTimeStamp(event1)

        val emotionNames = arrayOf("-", "开心", "惊喜", "难过")
        val modeIndex = mode.toIntOrNull() ?: return
        if (modeIndex in emotionNames.indices) {
            Toast.makeText(this, "已发送表情: ${emotionNames[modeIndex]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("重置设备")
            .setMessage("请选择重置方式：")
            .setPositiveButton("设备重置") { _, _ ->
                val success = ioTSDKManager.reset()
                Log.d(TAG, if (success) "设备重置成功" else "设备重置失败")
            }
            .setNeutralButton("恢复出厂设置") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("警告⚠️")
                    .setMessage("恢复出厂设置将清除所有数据，设备无法热重置,需要重启。确定要继续吗？")
                    .setPositiveButton("确定") { _, _ ->
                        val success = ioTSDKManager.resetFactory()
                        Log.d(TAG, if (success) "恢复出厂设置成功" else "恢复出厂设置失败")
                    }
                    .setNegativeButton("取消", null)
                    .setCancelable(false)
                    .show()
            }
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show()
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.next_photo -> initLicenseModule()
            R.id.ai -> {
                val deviceId = ioTSDKManager.deviceId
                AIStreamActivity.start(this, deviceId)
            }
            R.id.ai_chat -> {
                val devId = ioTSDKManager.deviceId
                if (devId.isNullOrEmpty()) {
                    Toast.makeText(this, "设备未激活", Toast.LENGTH_SHORT).show()
                    return
                }
                AiChatActivity.start(this, devId)
            }
            R.id.cloud_vad -> {
                val vadDevId = ioTSDKManager.deviceId
                if (vadDevId.isNullOrEmpty()) {
                    Toast.makeText(this, "设备未激活", Toast.LENGTH_SHORT).show()
                    return
                }
                AiLongEventActivity.start(this, vadDevId)
            }
            R.id.reset -> showResetOptionsDialog()
            R.id.http -> {
                val apiParams = ApiParams("thing.weather.get", "1.0")
                val codes = arrayListOf("w.temp", "w.humidity", "w.windLevel", "w.pm25", "w.conditionNum", "w.date", "w.currdate")
                apiParams.put("codes", JSONObject.toJSONString(codes))
                ThingApiHelper.request(apiParams, String::class.java, object : ApiCallback<String> {
                    override fun onSuccess(result: String) {
                        Log.w(TAG, "http1 request success: $result")
                    }

                    override fun onFailure(errorCode: String, errorMessage: String) {
                        Log.e(TAG, "http1 request failed: $errorCode, $errorMessage")
                    }
                })
            }
            R.id.dp_send -> showEmotionSelectDialog()
            R.id.get_events -> {
                ioTSDKManager.events?.forEach { event ->
                    if (event != null) {
                        Log.w(TAG, event.toString())
                        dataConsole.append("getEvents: ").append(event).append("\n")
                        tvDataConsole.text = dataConsole.toString()
                    }
                }
            }
            R.id.fetch_device_info_partial -> {
                val fields = arrayListOf("productId", "name")
                ioTSDKManager.deviceApi.fetchDeviceInfo(fields, object : ApiCallback<String> {
                    override fun onSuccess(result: String) {
                        Log.w(TAG, "fetchDeviceInfo partial success: $result")
                        runOnUiThread {
                            dataConsole.append("设备信息(部分): ").append(result).append("\n")
                            tvDataConsole.text = dataConsole.toString()
                            Toast.makeText(this@MainActivity2, "获取成功", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(errorMessage: String, msg: String) {
                        Log.e(TAG, "fetchDeviceInfo partial failed: $errorMessage")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity2, "获取失败: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            R.id.get_weather -> {
                val codes2 = arrayListOf("c.area", "c.province", "c.city", "w.temp", "w.conditionNum", "w.date", "w.currdate")
                ioTSDKManager.deviceApi.getWeather(codes2, object : ApiCallback<String> {
                    override fun onSuccess(result: String) {
                        Log.w(TAG, "getWeather success: $result")
                        runOnUiThread {
                            dataConsole.append("天气信息: ").append(result).append("\n")
                            tvDataConsole.text = dataConsole.toString()
                            Toast.makeText(this@MainActivity2, "天气获取成功", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(errorCode: String, errorMessage: String) {
                        Log.e(TAG, "getWeather failed: $errorCode, $errorMessage")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity2, "天气获取失败: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            R.id.get_weather_default -> {
                ioTSDKManager.deviceApi.getWeatherDefault(object : ApiCallback<WeatherInfo> {
                    override fun onSuccess(weatherInfo: WeatherInfo) {
                        Log.w(TAG, "getWeatherDefault success: $weatherInfo")
                        runOnUiThread {
                            val weatherText = StringBuilder("默认天气信息:\n")
                            weatherText.append("原始数据: ").append(weatherInfo).append("\n")
                            weatherInfo.getTemp(0)?.let { weatherText.append("今天温度: ${it}°C\n") }
                            weatherInfo.getHumidity(0)?.let { weatherText.append("今天湿度: ${it}%\n") }
                            weatherInfo.getConditionNum(0)?.let { weatherText.append("今天天气状况: $it\n") }
                            dataConsole.append(weatherText).append("\n")
                            tvDataConsole.text = dataConsole.toString()
                            Toast.makeText(this@MainActivity2, "默认天气获取成功", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(errorCode: String, errorMessage: String) {
                        Log.e(TAG, "getWeatherDefault failed: $errorCode, $errorMessage")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity2, "默认天气获取失败: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            R.id.check_firmware_upgrade -> checkFirmwareUpgrade()
        }
    }

    private fun checkFirmwareUpgrade() {
        val devId = ioTSDKManager.deviceId
        if (devId.isNullOrEmpty()) {
            Toast.makeText(this, "设备未激活，无法检查升级", Toast.LENGTH_SHORT).show()
            return
        }

        Log.i(TAG, "开始检查固件升级信息...")
        Toast.makeText(this, "正在检查固件升级...", Toast.LENGTH_SHORT).show()

        val apiParams = ApiParams("tuya.device.upgrade.get", "4.4")
        apiParams.put("type", 0)

        ThingApiHelper.request(apiParams, String::class.java, object : ApiCallback<String> {
            override fun onSuccess(result: String) {
                Log.w(TAG, "checkFirmwareUpgrade success: $result")
                runOnUiThread {
                    val upgradeInfo = StringBuilder("固件升级信息:\n")
                    try {
                        val json = JSONObject.parseObject(result)
                        if (json != null) {
                            json.getString("version")?.let { upgradeInfo.append("版本: $it\n") }
                            json.getInteger("type")?.let { upgradeInfo.append("模块类型: $it\n") }
                            json.getInteger("upgradeType")?.let { upgradeInfo.append("升级类型: $it\n") }
                            json.getString("size")?.let { upgradeInfo.append("大小: $it bytes\n") }
                            json.getString("md5")?.let { upgradeInfo.append("MD5: $it\n") }
                            json.getString("url")?.let { upgradeInfo.append("下载地址: $it\n") }
                            json.getString("httpsUrl")?.let { upgradeInfo.append("HTTPS下载地址: $it\n") }
                            json.getLong("execTime")?.let { upgradeInfo.append("执行时间: $it\n") }

                            json.getString("diffUrl")?.let { diffUrl ->
                                upgradeInfo.append("\n--- 差分包信息 ---\n")
                                upgradeInfo.append("差分地址: $diffUrl\n")
                                json.getString("diffSize")?.let { upgradeInfo.append("差分包大小: $it\n") }
                                json.getString("diffMd5")?.let { upgradeInfo.append("差分包MD5: $it\n") }
                            }
                        } else {
                            upgradeInfo.append("暂无升级信息 (返回为空)\n")
                        }
                    } catch (e: Exception) {
                        upgradeInfo.append("原始数据: ").append(result).append("\n")
                    }
                    dataConsole.append(upgradeInfo).append("\n")
                    tvDataConsole.text = dataConsole.toString()
                    Toast.makeText(this@MainActivity2, "固件升级信息获取成功", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(errorCode: String, errorMessage: String) {
                Log.e(TAG, "checkFirmwareUpgrade failed: $errorCode, $errorMessage")
                runOnUiThread {
                    dataConsole.append("固件升级检查失败: $errorCode - $errorMessage\n")
                    tvDataConsole.text = dataConsole.toString()
                    Toast.makeText(this@MainActivity2, "固件升级检查失败: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun chooseNewImage(realFn: String, filePath: String, next: Boolean) {
        var fn = realFn
        var path = filePath
        val photoInfos = ThingFrameOS.getInstance().photoFrame.allFileInfos
        Log.d(TAG, "已有文件数量: ${photoInfos.size}")
        for (info in photoInfos) {
            Log.d(TAG, "realFn: ${info.realFn}, title: ${info.title}, type: ${info.fileType},prefix: ${info.prefix}, centerX: ${info.centerX}, localPath: ${info.localPath}")
        }

        var index = 0
        if (fn.isEmpty() && photoInfos.isNotEmpty()) {
            fn = photoInfos[0].realFn
            path = photoInfos[0].localPath
        }

        if (next) {
            for (i in photoInfos.indices) {
                if (fn == photoInfos[i].realFn) {
                    if (i == photoInfos.size - 1) {
                        fn = photoInfos[0].realFn
                        path = photoInfos[0].localPath
                    } else {
                        index = i + 1
                        fn = photoInfos[i + 1].realFn
                        path = photoInfos[i + 1].localPath
                    }
                    break
                }
            }
        }

        tvPhotoCount.text = if (photoInfos.isNotEmpty()) "展示图片: ${index + 1}/${photoInfos.size}" else "展示图片: 0/0"
        currentRealFn = fn
        currentFilePath = path
        displayImage(path)
        getImageSize(path)
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog.dismiss()
        videoView.stopPlayback()
    }

    private fun initKey() {}

    private fun initFrameImageUpdateListener() {
        ThingFrameOS.getInstance().photoFrame.registerMediaUploadListener(object : IPhotoFrameMediaUpdateListener {
            override fun onMediaAppStartUpload(event: StartEventData) {
                Log.d(TAG, "onImageAppStartUpload() called with: sid = [${event.sid}]")
            }

            override fun onMediaAppUploaded(event: UploadedEventData) {
                Log.e(TAG, "onImageAppUploaded() called with: sid = [${event.sid}], fileName = [${event.realFn}] ${event.userInfo}")
            }

            override fun onMediaDeviceDownloaded(event: UploadedEventData, filePath: String) {
                Log.d(TAG, "onImageDeviceDownloaded() called with: sid = [${event.sid}], fileName = [${event.realFn}], filePath = [$filePath]")
                chooseNewImage(event.realFn, filePath, false)
            }

            override fun onMediaAllUploadFinished(event: UploadFinishedEventData) {
                Log.d(TAG, "onImageAllUploadFinished() called with: sid = [${event.sid}]")
                printLn()
            }

            override fun onMediaUploadFailed(sid: String, code: String, errorMessage: String) {
                Log.d(TAG, "onImageUploadFailed() called with: sid = [$sid], code = [$code], errorMessage = [$errorMessage]")
                printLn()
            }

            override fun onMediaAppDelete(event: DeleteEventData) {
                Log.d(TAG, "onImageDelete() called with: sid = [${event.sid}], realFn = [${event.realFn}]")
                chooseNewImage("", "", false)
            }
        })
    }

    private fun displayImage(filePath: String?) {
        Log.d(TAG, "displayImage: filePath = [$filePath]")
        if (filePath.isNullOrEmpty()) {
            videoView.visibility = View.GONE
            downloadImageView.visibility = View.GONE
            tvPhotoInfo.text = "file: N/A"
            return
        }

        tvPhotoInfo.text = "file: $currentRealFn"

        if (filePath.endsWith(".mp4") || filePath.endsWith(".avi") || filePath.endsWith(".mov")) {
            Log.d(TAG, "displayImage: not image file")
            videoView.visibility = View.VISIBLE
            downloadImageView.visibility = View.GONE
            playVideo(filePath)
            return
        }

        videoView.visibility = View.GONE
        downloadImageView.visibility = View.VISIBLE

        val dm = resources.displayMetrics
        val maxWidth = minOf(dm.widthPixels * 2, 2048)
        val maxHeight = minOf(dm.heightPixels * 2, 2048)
        Log.d(TAG, "displayImage: maxSize = ${maxWidth}x$maxHeight")

        val imageFile = File(filePath)
        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist: $filePath")
            return
        }
        if (!imageFile.canRead()) {
            Log.e(TAG, "Image file cannot be read: $filePath")
            return
        }

        Glide.with(this)
            .load(filePath)
            .override(maxWidth, maxHeight)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(downloadImageView)
    }

    private fun playVideo(filePath: String?) {
        if (filePath.isNullOrEmpty()) return

        val videoFile = File(filePath)
        if (!videoFile.exists()) {
            Log.e("MediaPlayer", "Video file does not exist at path: $filePath")
            return
        }

        val videoUri = Uri.fromFile(videoFile)

        if (mediaController == null) {
            mediaController = MediaController(this).also {
                it.setAnchorView(videoView)
                videoView.setMediaController(it)
            }
        }

        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener {
            videoView.seekTo(if (currentPosition > 0) currentPosition else 1)
            videoView.start()
        }
        videoView.setOnCompletionListener { videoView.start() }
    }

    override fun onPause() {
        super.onPause()
        if (videoView.isPlaying) {
            currentPosition = videoView.currentPosition
            videoView.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentPosition > 0) {
            videoView.seekTo(currentPosition)
            videoView.start()
        }
    }

    companion object {
        private const val TAG = TAG_PREFIX + "MainActivity2"

        @JvmStatic
        fun getImageSize(imagePath: String) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val fileSizeInKB = imageFile.length() / 1024
                Log.d(TAG, "图片文件大小: $fileSizeInKB KB")
            } else {
                Log.d(TAG, "图片文件不存在！")
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, options)
            Log.d(TAG, "--- 图片分辨率 --- ( ${options.outWidth} x ${options.outHeight} )")
        }
    }
}
