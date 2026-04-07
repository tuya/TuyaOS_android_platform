package com.tuya.smartai.demo;

import static android.util.Base64.DEFAULT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.thingclips.smart.ai.bs.ThingFrameOS;
import com.thingclips.smart.ai.bs.api.IPhotoFrameMediaUpdateListener;
import com.thingclips.smart.ai.bs.bean.DeleteEventData;
import com.thingclips.smart.ai.bs.bean.FrameZoneConfig;
import com.thingclips.smart.ai.bs.bean.PhotoInfo;
import com.thingclips.smart.ai.bs.bean.StartEventData;
import com.thingclips.smart.ai.bs.bean.UploadFinishedEventData;
import com.thingclips.smart.ai.bs.bean.UploadedEventData;
import com.thingclips.smart.os.license.LicenseProvisionManager;
import com.thingclips.smart.os.license.model.LicenseInfo;
import com.tuya.smartai.demo.ai.AiChatActivity;
import com.tuya.smartai.demo.utils.LoadingDialog;
import com.tuya.smartai.demo.utils.QrCodeUtil;
import com.tuya.smartai.iot_sdk.DPEvent;
import com.tuya.smartai.iot_sdk.IIoTManager;
import com.tuya.smartai.iot_sdk.IoTSDKManager;
import com.tuya.smartai.iot_sdk.OpenCode;
import com.tuya.smartai.iot_sdk.ThingOS;
import com.tuya.smartai.iot_sdk.core.IoTCallback;
import com.tuya.smartai.iot_sdk.core.IoTParams;
import com.tuya.smartai.iot_sdk.http.ApiCallback;
import com.tuya.smartai.iot_sdk.http.ApiParams;
import com.tuya.smartai.iot_sdk.http.ThingApiHelper;
import com.tuya.smartai.iot_sdk.model.ResetType;
import com.tuya.smartai.iot_sdk.model.WeatherInfo;
import com.tuya.smartai.iot_sdk.utils.TLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * 包含授权烧录系统 对接的方式demo
 */

public class MainActivity2 extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView ivQrCode;
    private IIoTManager ioTSDKManager;
    private LoadingDialog loadingDialog;
    private TextView tvBindStatus;
    private TextView tvDataConsole;
    private StringBuffer dataConsole = new StringBuffer();
    private TextView tvMqttStatus;
    private ImageView downloadImageView;
    private TextView tvPhotoCount;
    private TextView tvPhotoInfo;
    private VideoView videoView;
    private MediaController mediaController;
    private int currentPosition = 0; // 用于保存暂停时的播放位置
    private boolean hasInitFrame = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //再application中创建过了
        ioTSDKManager = ThingOS.getInstance().getIoTSDKManager();
        loadingDialog = new LoadingDialog(this);

        findViewById(R.id.reset).setOnClickListener(this::onClick);
        tvBindStatus = findViewById(R.id.tv_bind_status);
        findViewById(R.id.dp_send).setOnClickListener(this::onClick);
        findViewById(R.id.http).setOnClickListener(this::onClick);
        findViewById(R.id.get_events).setOnClickListener(this::onClick);
        ivQrCode = findViewById(R.id.ivQrCode);
        tvDataConsole = findViewById(R.id.tv_data_console);
        tvMqttStatus = findViewById(R.id.tv_mqtt_status);
        downloadImageView = findViewById(R.id.downloaded_image_view);
        videoView = findViewById(R.id.downloaded_video_view);
        tvPhotoCount = findViewById(R.id.tv_photo_count);
        tvPhotoInfo = findViewById(R.id.tv_photo_info);
        findViewById(R.id.next_photo).setOnClickListener(this::onClick);
        findViewById(R.id.ai).setOnClickListener(this::onClick);
        findViewById(R.id.ai_chat).setOnClickListener(this::onClick);
        findViewById(R.id.reset).setOnClickListener(this::onClick);
        findViewById(R.id.fetch_device_info_partial).setOnClickListener(this::onClick);
        findViewById(R.id.get_weather).setOnClickListener(this::onClick);
        findViewById(R.id.get_weather_default).setOnClickListener(this::onClick);

        //添加调试代码，添加该代码后，属于内置了授权码，可以不会走授权流程
//         LicenseProvisionManager.getInstance()
//                 .init(this).grantLicense("uuidb3d877629c43fe71", "XVCWC2tUf1Xcr21h9KpdEkQ8FERGal4G");

        // 初始化授权模块并检查授权状态
        initLicenseModule();

        initSDK();

    }


    private void printLn() {
        List<PhotoInfo> photoInfos = ThingFrameOS.getInstance().getPhotoFrame().getAllFileInfos();
        TLog.d(TAG, "已有文件数量: " + photoInfos.size());
        for (PhotoInfo info : photoInfos) {
            TLog.d(TAG, "realFn: " + info.getRealFn() + ", title: " + info.getTitle() + ", type: " + info.getFileType() + ",prefix:  " + info.getPrefix() + ", centerX: " + info.getCenterX() + ", localPath: " + info.getLocalPath());
        }
    }

    private void updateQrCode(String content) {
        Bitmap bitmap = QrCodeUtil.generateQrCodeBitmap(content, 512, 512);
        if (bitmap != null) {
            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeQrCode() {
        ivQrCode.setImageBitmap(null);
        ivQrCode.setVisibility(View.GONE);
    }

    private IoTCallback mIotCallback = new IoTCallback() {

        @Override
        public void onDpEvent(DPEvent event) {
            if (event != null) {
                TLog.w(TAG, "rev dp: " + event);
                //step1:收到dp请求
                dataConsole.append("rev dp: ").append(event).append("\n");
                tvDataConsole.setText(dataConsole.toString());
                if (event.type == DPEvent.Type.PROP_RAW) {
                    TLog.w(TAG, Base64.encodeToString((byte[]) event.value, DEFAULT));
                }
                dataConsole.append("publishDps: ").append(event).append("\n");
                tvDataConsole.setText(dataConsole.toString());

                ioTSDKManager.sendDP(event);
            }
        }

        @Override
        public void onReset(ResetType resetType) {
            TLog.e(TAG, "onReset:======================================= resetType=" + resetType);
            //删除所有的文件
            ThingFrameOS.getInstance().getPhotoFrame().deleteAllPhotos();

            if (resetType == ResetType.GW_LOCAL_RESET_FACTORY) {
                exitApp();
            }
        }

        @Override
        public void onShorturl(String url) {
            TLog.w(TAG, "shorturl: " + url);

            updateQrCode(QrCodeUtil.getShortUrl(url));

            tvBindStatus.setText("请使用APP扫码激活设备");
            //未激活 删除所有照片
            ThingFrameOS.getInstance().getPhotoFrame().deleteAllPhotos();
        }

        @Override
        public void onActive() {
            TLog.w(TAG, "onActive---------------");
            String deviceId = ioTSDKManager.getDeviceId();
            TLog.w(TAG, "deviceId: " + deviceId);
            if (deviceId != null && !deviceId.isEmpty()) {
                tvBindStatus.setText("设备已激活，ID: " + deviceId);
                removeQrCode();

                hasInitFrame = false;
            } else {
                tvBindStatus.setText("出错了");
            }

        }

        @Override
        public void onFirstActive() {
            TLog.w(TAG, "onFirstActive -----------------");


            String deviceId = ioTSDKManager.getDeviceId();
            TLog.w(TAG, "deviceId: " + deviceId);
            if (deviceId != null && !deviceId.isEmpty()) {
                tvBindStatus.setText("激活成功，ID: " + deviceId);
                removeQrCode();
                //初始化 相框key
                initKey();
            } else {
                tvBindStatus.setText("出错了");
            }

        }

        @Override
        public void onMQTTStatusChanged(int status) {
            TLog.e(TAG, "Status: " + getMqttStatusStr(status) + ", hasInitFrame :" + hasInitFrame);

            if (status == IoTSDKManager.STATUS_MQTT_ONLINE) {
                tvMqttStatus.setText("设备上线");
                tvMqttStatus.setTextColor(ContextCompat.getColor(MainActivity2.this, R.color.colorPrimary));
                String deviceId = ioTSDKManager.getDeviceId();
                if (!hasInitFrame) {
                    FrameZoneConfig config = FrameZoneConfig.builder()
                            .devId(deviceId)
                            .resolution("1024*800")
                            .allowFormats(new String[]{"png", "jpg", "mp4"})
                            .maxFc(3)
                            .maxFs(10485760); // 10MB

                    ThingFrameOS.getInstance().getPhotoFrame().initFrameZone(config, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            hasInitFrame = true;
                            initFrameImageUpdateListener();
                        }

                        @Override
                        public void onFailure(String errorCode, String errorMessage) {
                            TLog.e(TAG, "onFailure() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");
                            hasInitFrame = false;
                        }
                    });
                }
            } else {
                if (!TextUtils.isEmpty(ioTSDKManager.getDeviceId())) {
                    tvMqttStatus.setText("设备离线");
                    tvMqttStatus.setTextColor(ContextCompat.getColor(MainActivity2.this, R.color.colorAccent));
                }
            }

        }

        @Override
        public void onMqttMsg(int protocol, String msg) {
            TLog.d("TAG", "onMqttMsg() called with: protocol = [" + protocol + "], msg = [" + msg + "]");

        }
    };

    private String getMqttStatusStr(int status) {

        switch (status) {
            case IoTSDKManager.STATUS_OFFLINE:
                return "OFFLINE";
            case IoTSDKManager.STATUS_MQTT_OFFLINE:
                return "MQTT OFFLINE";
            case IoTSDKManager.STATUS_MQTT_ONLINE:
                return "MQTT ONLINE";
        }
        return "Status: " + status;
    }

    private void initSDK() {

        LicenseInfo info = LicenseProvisionManager.getInstance().getLicense();
        TLog.e(TAG, "initSDK license : uuid=" + info.uuid + ", key=" + info.key);

        IoTParams params = new IoTParams.Builder()
                .addMode(IoTParams.Mode.MODE_QR) //配网模式，QR为二维码模式
                .productId("le2fomtcgvicqawl") //  aqcxhpklvzlblyl3 产品ID，设备对应的产品ID,一类产品 一个产品id，产品id中会定义产品的各种功能和属性
                .uuid(info.uuid) //授权码中的uuid，一个设备一组授权码
                .authKey(info.key) //授权码中的authKey，一个设备一组授权码
                .version("1.0.0") //设备 software 版本号,会跟ota版本关联
                .ioTCallback(mIotCallback)
                .build();

        int rt = ioTSDKManager.initSDK(params);

        if (rt != OpenCode.CODE_OK) {
            showDialog("初始化失败了,请检查");
        } else {
            TLog.e(TAG, "---------初始化成功---------");
        }
    }

    /**
     * 初始化授权模块并检查授权状态
     */
    private void initLicenseModule() {
        TLog.i(TAG, "初始化授权模块...");
        // 初始化授权烧录模块
        LicenseProvisionManager.getInstance()
                .init(this)
                .enableAutoLaunch(true)  // 启用SD卡自动弹出授权页面
                .setOnLicenseChangeListener(new LicenseProvisionManager.OnLicenseChangeListener() {
                    @Override
                    public void onLicenseGranted(LicenseInfo license) {
                        TLog.i(TAG, "授权码已授予: uuid=" + license.uuid + ",ioTSDKManager.isInitialized() " + ioTSDKManager.isInitialized());
                        if (!ioTSDKManager.isInitialized()) {
                            Toast.makeText(MainActivity2.this, "授权成功，正在初始化SDK...", Toast.LENGTH_SHORT).show();
                            initSDK();
                        }
                    }

                    @Override
                    public void onLicenseRevoked() {
                        TLog.i(TAG, "授权码已回收");
                        showDialog("授权已回收，设备将无法使用");
                    }
                })
                .start();

        // 检查当前是否已有授权
        LicenseInfo info = LicenseProvisionManager.getInstance().getLicense();

        if (info != null) {
            // 已有授权，直接初始化SDK
            TLog.i(TAG, "检测到已有授权: uuid=" + info.uuid);
            initSDK();
            if (LicenseProvisionManager.getInstance().hasExternalSDCardWithLicenseFile()) {
                showLicenseGuideDialog();
            }
        } else {
            // 无授权，引导用户授权
            TLog.w(TAG, "未检测到授权码，等待授权...");
            tvBindStatus.setText("请插入授权SD卡进行授权");
            showLicenseGuideDialog();
        }
    }

    /**
     * 显示授权引导对话框
     */
    private void showLicenseGuideDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("授权相关")
                .setMessage("当前设备未授权或者具有授权 SD卡\n\n" +
                        "需要操作授权码吗\n")
                .setPositiveButton("进入授权页面", (dialog, which) -> {
                    LicenseProvisionManager.getInstance().showProvisionPage();
                })
                .setNegativeButton("稍后", null)
                .setCancelable(false)
                .show();
    }

    private void showDialog(String msg) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("警告⚠️")
                .setMessage(msg)
                .setPositiveButton("确认", (dialog, which) -> {
                    exitApp();
                })
                .setCancelable(false)
                .show();
    }

    private void exitApp() {
        Context context = MainActivity2.this;
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
        Runtime.getRuntime().exit(0);
    }

    /**
     * 表情选择对话框
     * mode 0-5 对应不同表情
     */
    private void showEmotionSelectDialog() {
        final String[] emotions = {
                "0 -  不显示",
                "1 - 😍 惊喜",
                "2 - 😲 开心",
                "3 - 😢 难过",
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("选择表情")
                .setSingleChoiceItems(emotions, -1, (dialog, which) -> {
                    // 发送选中的表情
                    sendEmotionDP(String.valueOf(which));
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 发送表情DP事件
     *
     * @param mode 表情模式 0-5
     */
    private void sendEmotionDP(String mode) {
        int timestamp = (int) (System.currentTimeMillis() / 1000L);

        HashMap<String, String> valueMap = new HashMap<>();
        valueMap.put("real-fn", "2b0f3be64f48ad87d45c42869fabadef_1768880668289.jpg");//填入图片的 fn 用于匹配是哪个照片被点赞
        valueMap.put("meta", "ay15269934114205cqNm"); // meta 值，存放的是 user id
        valueMap.put("mode", mode); //mode表示的是表情 1:喜爱 2：开心  3：难过

        TLog.e(TAG, "sendEmotionDP value: " + JSONObject.toJSONString(valueMap));

        DPEvent event1 = new DPEvent(2, (byte) DPEvent.Type.PROP_RAW, JSONObject.toJSONString(valueMap).getBytes(), timestamp);
        DPEvent[] events = {event1};

        for (DPEvent dpEvent : events) {
            dataConsole.append("发送表情(mode=").append(mode).append("): ").append(dpEvent).append("\n");
            tvDataConsole.setText(dataConsole.toString());
        }

        ioTSDKManager.sendDPWithTimeStamp(events);

        String[] emotionNames = {"-", "开心", "惊喜", "难过"};
        int modeIndex = Integer.parseInt(mode);
        if (modeIndex >= 0 && modeIndex < emotionNames.length) {
            Toast.makeText(this, "已发送表情: " + emotionNames[modeIndex], Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示重置选项对话框
     */
    private void showResetOptionsDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("重置设备")
                .setMessage("请选择重置方式：")
                .setPositiveButton("设备重置", (dialog, which) -> {
                    // 重置设备（解绑）
                    boolean success = ioTSDKManager.reset();
                    if (success) {
                        TLog.d(TAG, "设备重置成功");
                    } else {
                        TLog.e(TAG, "设备重置失败");
                    }
                })
                .setNeutralButton("恢复出厂设置", (dialog, which) -> {
                    // 恢复出厂设置
                    new android.app.AlertDialog.Builder(MainActivity2.this)
                            .setTitle("警告⚠️")
                            .setMessage("恢复出厂设置将清除所有数据，设备无法热重置,需要重启。确定要继续吗？")
                            .setPositiveButton("确定", (confirmDialog, confirmWhich) -> {
                                boolean success = ioTSDKManager.resetFactory();
                                if (success) {
                                    TLog.d(TAG, "恢复出厂设置成功");
                                } else {
                                    TLog.e(TAG, "恢复出厂设置失败");
                                }
                            })
                            .setNegativeButton("取消", null)
                            .setCancelable(false)
                            .show();
                })
                .setNegativeButton("取消", null)
                .setCancelable(true)
                .show();
    }

    public void onClick(View v) {
        if (ioTSDKManager == null) {
            return;
        }

        switch (v.getId()) {
            case R.id.next_photo:
                chooseNewImage(currentRealFn, currentFilePath, true);
                break;
            case R.id.ai:
                String deviceId = ioTSDKManager.getDeviceId();
                AIStreamActivity.start(this, deviceId);
                break;
            case R.id.ai_chat:
                String devId = ioTSDKManager.getDeviceId();
                if (TextUtils.isEmpty(devId)) {
                    Toast.makeText(this, "设备未激活", Toast.LENGTH_SHORT).show();
                    return;
                }
                AiChatActivity.Companion.start(this, devId);
                break;
            case R.id.reset:
                showResetOptionsDialog();
                break;
            case R.id.http:
                ApiParams apiParams = new ApiParams("thing.weather.get", "1.0");
                ArrayList<String> codes = new ArrayList<>();
                codes.add("w.temp");
                codes.add("w.humidity");
                codes.add("w.windLevel");
                codes.add("w.pm25");
                codes.add("w.conditionNum");
                codes.add("w.date");
                codes.add("w.currdate");
                apiParams.put("codes", JSONObject.toJSONString(codes));

                ThingApiHelper.request(apiParams, String.class, new ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        TLog.w(TAG, "http1 request success: " + result);
                    }

                    @Override
                    public void onFailure(String errorCode, String errorMessage) {

                        TLog.e(TAG, "http1 request failed: " + errorCode + ", " + errorMessage);
                    }
                });

                break;
            case R.id.dp_send:
                /**
                 * {"real-fn":"2b0f3be64f48ad87d45c42869fabadef_1768875270077.jpg","meta":"ay15269934114205cqNm","mode":"1"}
                 * {"real-fn":"2b0f3be64f48ad87d45c42869fabadef_1768875270077.jpg","meta":"ay15269934114205cqNm","mode":"2"}
                 * {"real-fn":"2b0f3be64f48ad87d45c42869fabadef_1768875270077.jpg","meta":"ay15269934114205cqNm","mode":"3"}
                 */
                //发送DP事件 dpID要再定义中的才可以发送 ，以及注意数据格式问题，比如raw类型的 要传byte[]类型的
                showEmotionSelectDialog();
                break;
            case R.id.get_events:
                DPEvent[] dpEvents = ioTSDKManager.getEvents();
                if (dpEvents != null) {
                    for (DPEvent event : dpEvents) {
                        if (event != null) {
                            TLog.w(TAG, event.toString());
                            dataConsole.append("getEvents: ").append(event).append("\n");
                            tvDataConsole.setText(dataConsole.toString());
                        }
                    }
                }
                break;
            case R.id.fetch_device_info_partial:
                // 获取设备信息 - 获取名称
                ArrayList<String> fields = new ArrayList<>();
                fields.add("productId");
                fields.add("name");
                ioTSDKManager.getDeviceApi().fetchDeviceInfo(fields, new ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        TLog.w(TAG, "fetchDeviceInfo partial success: " + result);
                        runOnUiThread(() -> {
                            dataConsole.append("设备信息(部分): ").append(result).append("\n");
                            tvDataConsole.setText(dataConsole.toString());
                            Toast.makeText(MainActivity2.this, "获取成功", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage, String msg) {
                        TLog.e(TAG, "fetchDeviceInfo partial failed: " + errorMessage);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity2.this, "获取失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
                break;
            case R.id.get_weather:
                // 获取天气信息
                ArrayList<String> codes2 = new ArrayList<>();
                codes2.add("c.area");
                codes2.add("c.province");
                codes2.add("c.city");
                codes2.add("w.temp");
                codes2.add("w.conditionNum");
                codes2.add("w.date");
                codes2.add("w.currdate");
                ioTSDKManager.getDeviceApi().getWeather(codes2, new ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        TLog.w(TAG, "getWeather success: " + result);
                        runOnUiThread(() -> {
                            dataConsole.append("天气信息: ").append(result).append("\n");
                            tvDataConsole.setText(dataConsole.toString());
                            Toast.makeText(MainActivity2.this, "天气获取成功", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String errorCode, String errorMessage) {
                        TLog.e(TAG, "getWeather failed: " + errorCode + ", " + errorMessage);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity2.this, "天气获取失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
                break;
            case R.id.get_weather_default:
                // 获取默认天气信息（使用内置参数）
                ioTSDKManager.getDeviceApi().getWeatherDefault(new ApiCallback<WeatherInfo>() {
                    @Override
                    public void onSuccess(WeatherInfo weatherInfo) {
                        TLog.w(TAG, "getWeatherDefault success: " + weatherInfo);
                        runOnUiThread(() -> {
                            StringBuilder weatherText = new StringBuilder("默认天气信息:\n");
                            weatherText.append("原始数据: ").append(weatherInfo.toString()).append("\n");

                            // 显示一些常用字段
                            Integer temp0 = weatherInfo.getTemp(0);
                            Integer humidity0 = weatherInfo.getHumidity(0);
                            String conditionNum0 = weatherInfo.getConditionNum(0);

                            if (temp0 != null) {
                                weatherText.append("今天温度: ").append(temp0).append("°C\n");
                            }
                            if (humidity0 != null) {
                                weatherText.append("今天湿度: ").append(humidity0).append("%\n");
                            }
                            if (conditionNum0 != null) {
                                weatherText.append("今天天气状况: ").append(conditionNum0).append("\n");
                            }

                            dataConsole.append(weatherText.toString()).append("\n");
                            tvDataConsole.setText(dataConsole.toString());
                            Toast.makeText(MainActivity2.this, "默认天气获取成功", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String errorCode, String errorMessage) {
                        TLog.e(TAG, "getWeatherDefault failed: " + errorCode + ", " + errorMessage);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity2.this, "默认天气获取失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
                break;
        }
    }

    private String currentRealFn = "";
    private String currentFilePath = "";


    private void chooseNewImage(String realFn, String filePath, boolean Next) {
        List<PhotoInfo> photoInfos = ThingFrameOS.getInstance().getPhotoFrame().getAllFileInfos();
        TLog.d(TAG, "已有文件数量: " + photoInfos.size());
        for (PhotoInfo info : photoInfos) {
            TLog.d(TAG, "realFn: " + info.getRealFn() + ", title: " + info.getTitle() + ", type: " + info.getFileType() + ",prefix:  " + info.getPrefix() + ", centerX: " + info.getCenterX() + ", localPath: " + info.getLocalPath());
        }

        int index = 0;

        if (TextUtils.isEmpty(realFn) && !photoInfos.isEmpty()) {
            realFn = photoInfos.get(0).getRealFn();
            filePath = photoInfos.get(0).getLocalPath();
        }

        if (Next) {
            for (int i = 0; i < photoInfos.size(); i++) {
                if (TextUtils.equals(realFn, photoInfos.get(i).getRealFn())) {
                    if (i == photoInfos.size() - 1) {
                        realFn = photoInfos.get(0).getRealFn();
                        filePath = photoInfos.get(0).getLocalPath();
                    } else {
                        index = i + 1;
                        realFn = photoInfos.get(i + 1).getRealFn();
                        filePath = photoInfos.get(i + 1).getLocalPath();
                    }
                    break;
                }
            }
        }
        if (!photoInfos.isEmpty()) {
            tvPhotoCount.setText("展示图片: " + (index + 1) + "/" + photoInfos.size());
        } else {
            tvPhotoCount.setText("展示图片: 0/0");
        }
        currentRealFn = realFn;
        currentFilePath = filePath;
        displayImage(filePath);
        getImageSize(filePath);
    }


    public static void getImageSize(String imagePath) {
        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            long fileSizeInBytes = imageFile.length();
            long fileSizeInKB = fileSizeInBytes / 1024;
            long fileSizeInMB = fileSizeInKB / 1024;

            TLog.d(TAG, "图片文件大小: " + fileSizeInKB + " KB");
        } else {
            TLog.d(TAG, "图片文件不存在！");
        }

        // 创建一个 BitmapFactory.Options 对象
        BitmapFactory.Options options = new BitmapFactory.Options();

        // 设置 inJustDecodeBounds 为 true。
        // 这意味着解码器只会解析图片的边界（尺寸），而不会加载实际的像素数据到内存中。
        options.inJustDecodeBounds = true;

        // 使用 decodeFile 方法来读取图片信息
        BitmapFactory.decodeFile(imagePath, options);

        // 从 options 对象中获取图片的宽度和高度
        int width = options.outWidth;
        int height = options.outHeight;

        TLog.d(TAG, "--- 图片分辨率 --- ( " + width + " x " + height + " )");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();


        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }

        // 释放资源
        videoView.stopPlayback();

    }

    private void initKey() {


    }

    private void initFrameImageUpdateListener() {
        ThingFrameOS.getInstance().getPhotoFrame().registerMediaUploadListener(new IPhotoFrameMediaUpdateListener() {

            @Override
            public void onMediaAppStartUpload(StartEventData event) {
                TLog.d(TAG, "onImageAppStartUpload() called with: sid = [" + event.getSid() + "]");
            }

            @Override
            public void onMediaAppUploaded(UploadedEventData event) {
                TLog.e(TAG, "onImageAppUploaded() called with: sid = [" + event.getSid() + "], fileName = [" + event.getRealFn() + "] " + event.getUserInfo());
            }

            @Override
            public void onMediaDeviceDownloaded(UploadedEventData event, String filePath) {
                TLog.d(TAG, "onImageDeviceDownloaded() called with: sid = [" + event.getSid() + "], fileName = [" + event.getRealFn() + "], filePath = [" + filePath + "]");
                chooseNewImage(event.getRealFn(), filePath, false);
            }

            @Override
            public void onMediaAllUploadFinished(UploadFinishedEventData event) {
                TLog.d(TAG, "onImageAllUploadFinished() called with: sid = [" + event.getSid() + "]");
                printLn();
            }

            @Override
            public void onMediaUploadFailed(String sid, String code, String errorMessage) {
                TLog.d(TAG, "onImageUploadFailed() called with: sid = [" + sid + "], code = [" + code + "], errorMessage = [" + errorMessage + "]");
                printLn();
            }

            @Override
            public void onMediaAppDelete(DeleteEventData event) {
                TLog.d(TAG, "onImageDelete() called with: sid = [" + event.getSid() + "], realFn = [" + event.getRealFn() + "]");
                chooseNewImage("", "", false);
            }
        });
    }

    private void displayImage(String filePath) {
        TLog.d(TAG, "displayImage: filePath = [" + filePath + "]");
        if (filePath == null || filePath.isEmpty()) {
            videoView.setVisibility(View.GONE);
            downloadImageView.setVisibility(View.GONE);
            tvPhotoInfo.setText("file: N/A");
            return;
        }

        tvPhotoInfo.setText("file: " + currentRealFn);

        // 检测是否为视频文件
        if (filePath.endsWith(".mp4") || filePath.endsWith(".avi") || filePath.endsWith(".mov")) {
            TLog.d(TAG, "displayImage: video file detected, opening in external player/browser");

            // 隐藏内部播放器和图片控件
            videoView.setVisibility(View.GONE);
            downloadImageView.setVisibility(View.GONE);

            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                // --- 关键点：Android 7.0+ 临时解除 file:// URI 限制 ---
                // 如果你的项目已经配置了 FileProvider，建议使用 FileProvider.getUriForFile 替代
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.os.StrictMode.VmPolicy.Builder builder = new android.os.StrictMode.VmPolicy.Builder();
                    android.os.StrictMode.setVmPolicy(builder.build());
                }
                // -----------------------------------------------------

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // 设置数据和类型为视频，系统会弹出选择框（浏览器/视频播放器等）
                Uri uri = Uri.fromFile(file);
                intent.setDataAndType(uri, "video/*");

                startActivity(intent);

            } catch (Exception e) {
                TLog.e(TAG, "跳转播放失败: " + e.getMessage());
                Toast.makeText(this, "无法调用外部播放: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 图片逻辑保持不变
        videoView.setVisibility(View.GONE);
        downloadImageView.setVisibility(View.VISIBLE);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        int maxWidth = Math.min(screenWidth * 2, 2048);
        int maxHeight = Math.min(screenHeight * 2, 2048);
        TLog.d(TAG, "displayImage: maxSize = " + maxWidth + "x" + maxHeight);

        // 检查文件是否存在
        File imageFile = new File(filePath);
        if (!imageFile.exists()) {
            TLog.e(TAG, "Image file does not exist: " + filePath);
            return;
        }

        // 检查文件是否可读
        if (!imageFile.canRead()) {
            TLog.e(TAG, "Image file cannot be read: " + filePath);
            return;
        }

        Glide.with(this)
                .load(filePath)
                .override(maxWidth, maxHeight)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(downloadImageView);
    }

    private void playVideo(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            // 处理路径无效的情况
            return;
        }

        // 1. 将文件路径字符串转换为一个 File 对象
        File videoFile = new File(filePath);

        // 2. 检查文件是否存在（一个好的健壮性检查）
        if (!videoFile.exists()) {
            Log.e("MediaPlayer", "Video file does not exist at path: " + filePath);
            return;
        }

        Uri videoUri = Uri.fromFile(videoFile);

        if (mediaController == null) {
            mediaController = new MediaController(this);
            // 将控制器与 VideoView 关联
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
        }


        videoView.setVideoURI(videoUri);

        videoView.setOnPreparedListener(mp -> {
            // 跳转到上次暂停的位置
            if (currentPosition > 0) {
                videoView.seekTo(currentPosition);
            } else {
                // 从头开始
                videoView.seekTo(1); // 跳转到第1毫秒可以避免某些设备黑屏
            }
            // 开始播放
            videoView.start();
        });

        videoView.setOnCompletionListener(mp -> {
            videoView.start();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 当 Activity 不可见时，暂停视频并记录位置
        if (videoView.isPlaying()) {
            currentPosition = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 当 Activity 重新可见时，恢复播放
        if (currentPosition > 0) {
            videoView.seekTo(currentPosition);
            videoView.start();
        }
    }
}
