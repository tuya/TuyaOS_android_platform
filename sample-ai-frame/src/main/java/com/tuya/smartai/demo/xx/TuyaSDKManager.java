//package com.tuya.smartai.demo.xx;
//
//import android.content.Context;
//import android.content.Intent;
//import android.graphics.Bitmap;
//import android.media.MediaMetadataRetriever;
//import android.net.Uri;
//import android.os.Environment;
//import android.os.SystemProperties;
//import android.text.TextUtils;
//import android.util.Log;
//
//import com.thingclips.smart.ai.bs.ThingFrameOS;
//import com.thingclips.smart.ai.bs.api.IPhotoFrameMediaUpdateListener;
//import com.thingclips.smart.ai.bs.bean.DeleteEventData;
//import com.thingclips.smart.ai.bs.bean.FrameZoneConfig;
//import com.thingclips.smart.ai.bs.bean.PhotoInfo;
//import com.thingclips.smart.ai.bs.bean.StartEventData;
//import com.thingclips.smart.ai.bs.bean.UploadFinishedEventData;
//import com.thingclips.smart.ai.bs.bean.UploadedEventData;
//import com.thingclips.smart.os.license.LicenseProvisionManager;
//import com.thingclips.smart.os.license.model.LicenseInfo;
//import com.tuya.smartai.iot_sdk.DPEvent;
//import com.tuya.smartai.iot_sdk.IIoTManager;
//import com.tuya.smartai.iot_sdk.IoTSDKManager;
//import com.tuya.smartai.iot_sdk.ThingOS;
//import com.tuya.smartai.iot_sdk.core.IoTCallback;
//import com.tuya.smartai.iot_sdk.core.IoTParams;
//import com.tuya.smartai.iot_sdk.http.ApiCallback;
//import com.tuya.smartai.iot_sdk.model.ResetType;
//import com.tuya.smartai.iot_sdk.utils.TLog;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.concurrent.CopyOnWriteArrayList;
//
//import cn.com.framex.host.R;
//import cn.com.framex.host.constant.Constant;
//import cn.com.framex.host.db.DBManager;
//import cn.com.framex.host.ui.LicenseActivity;
//import cn.com.framex.host.ui.splash.SplashActivity;
//import cn.com.framex.host.util.ActivityStackManager;
//import cn.com.framex.host.util.FileUtils;
//import cn.com.framex.host.util.PhotoUtils;
//import cn.com.framex.host.util.PrefUtils;
//import cn.com.framex.host.util.QrCodeUtil;
//import cn.com.framex.host.util.TimeUtils;
//import cn.com.framex.host.util.ToastUtils;
//
//public class TuyaSDKManager implements IPhotoFrameMediaUpdateListener, ApiCallback<Void>, LicenseProvisionManager.OnLicenseChangeListener {
//    private static final String TAG = "33";
//    private static volatile TuyaSDKManager instance;
//    private Context appContext;
//    private IIoTManager ioTSDKManager;
//    private boolean isOnline = false;
//    private String qrCodeImage = "";
//    private String deviceId = "";
//    //    private static String uuid;
////    private static String authKey;
//    private FrameZoneConfig frameZoneConfig;
//    private final List<TuyaListener> listeners = new CopyOnWriteArrayList<>();
//    private volatile boolean frameZoneInited = false;//防重复初始化
//    private final Object sdkInitLock = new Object();//防重复调用initSDK
//
//    private TuyaSDKManager(Context context) {
//        this.appContext = context;
////        uuid = SystemProperties.get("persist.chiptrip.tuya.uuid", "");
////        authKey = SystemProperties.get("persist.chiptrip.tuya.authkey", "");
//    }
//
//    public static TuyaSDKManager getInstance(Context context) {
//        if (instance == null) {
//            synchronized (TuyaSDKManager.class) {
//                if (instance == null) {
//                    instance = new TuyaSDKManager(context);
//                }
//            }
//        }
//        return instance;
//    }
//
//
//    public void initSDK() {
//        synchronized (sdkInitLock) {
//            if (ioTSDKManager != null && ioTSDKManager.isInitialized()) {
//                TLog.w(TAG, "SDK already initialized, skip");
//                return;
//            }
//
//            ioTSDKManager = ThingOS.getInstance().getIoTSDKManager();
//
//            LicenseInfo info = LicenseProvisionManager.getInstance().getLicense();
//            if (info == null) {
//                TLog.w(TAG, "LicenseInfo is null, skip initSDK");
//                return;
//            }
//
//            IoTParams params = new IoTParams.Builder()
//                    .addMode(IoTParams.Mode.MODE_QR)
//                    .productId("rqhj4jlgxpleba7i")
//                    .uuid(info.uuid)
//                    .authKey(info.key)
//                    .version("1.0.0")
//                    .initTimeout(10_000)
//                    .ioTCallback(ioTCallback)
//                    .build();
//
//            ioTSDKManager.initSDK(params);
//        }
//    }
//
//    public void initLicense() {
//        TLog.i(TAG, "初始化授权模块...");
//        LicenseProvisionManager licenseProvisionManager = LicenseProvisionManager.getInstance()
//                .init(appContext)
//                .enableAutoLaunch(true)
//                .setOnLicenseChangeListener(this);
//        licenseProvisionManager.start();
//    }
//
//    @Override
//    public void onLicenseGranted(LicenseInfo license) {
//        TLog.i(TAG, "授权码已授予: uuid=" + license.uuid);
//        ActivityStackManager.finishAll();
//        Intent intent = new Intent(appContext, SplashActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        appContext.startActivity(intent);
//    }
//
//    @Override
//    public void onLicenseRevoked() {
//        TLog.i(TAG, "授权码已回收");
//        ActivityStackManager.finishAll();
//        Intent intent = new Intent(appContext, LicenseActivity.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        appContext.startActivity(intent);
//    }
//
//    private IoTCallback ioTCallback = new IoTCallback() {
//        @Override
//        public void onDpEvent(DPEvent dpEvent) {
//
//        }
//
//        @Override
//        public void onReset(ResetType resetType) {
//            TLog.w(TAG, "remove device: ");
//            qrCodeImage = "";
//            frameZoneInited = false;
//            ThingFrameOS.getInstance().getPhotoFrame().deleteAllPhotos();
//            PrefUtils.putString("ty_device_id", "", appContext);
//            QrCodeUtil.deleteAppPrivateFile(appContext, Constant.QR_FILE_NAME);
//            onDeviceReset();
//        }
//
//        @Override
//        public void onShorturl(String s) {
//            TLog.w(TAG, "onShorturl: " + s);
//            qrCodeImage = QrCodeUtil.getShortUrl(s);
//            QrCodeUtil.generateQrCodeBitmap(appContext, qrCodeImage);//文件缓存到本地
//            notifyQrCodeUpdated(qrCodeImage);
//        }
//
//        @Override
//        public void onActive() {
//            deviceId = ioTSDKManager.getDeviceId();
//
//            TLog.w(TAG, "onActive deviceId: " + deviceId);
//            if (deviceId != null && !deviceId.isEmpty()) {
//                PrefUtils.putString("ty_device_id", deviceId, appContext);
//            }
//            onDeviceActivatedListener(deviceId);
//        }
//
//        @Override
//        public void onFirstActive() {
//            deviceId = ioTSDKManager.getDeviceId();
//
//            TLog.w(TAG, "onFirstActive deviceId: " + deviceId);
//            if (deviceId != null && !deviceId.isEmpty()) {
//                PrefUtils.putString("ty_device_id", deviceId, appContext);
//                ToastUtils.showCustomToast(appContext, appContext.getString(R.string.activation_successful));
//            }
//            onFirstActiveListener(deviceId);
//        }
//
//        @Override
//        public void onMQTTStatusChanged(int status) {
//            TLog.w(TAG, "onMQTTStatusChanged: " + status);
//            if (status == IoTSDKManager.STATUS_MQTT_ONLINE) {
//                isOnline = true;
//                if (!frameZoneInited) {
//                    frameZoneInited = true;
//                    frameZoneConfig = FrameZoneConfig.builder()
//                            .devId(deviceId)
//                            .resolution("1024*800")
//                            .allowFormats(new String[]{"png", "jpg", "mp4"})
//                            .maxFc(3)
//                            .maxFs(10485760);
//
//                    ThingFrameOS.getInstance().getPhotoFrame()
//                            .initFrameZone(frameZoneConfig, TuyaSDKManager.this);
//                }
//            } else {
//                if (!TextUtils.isEmpty(ioTSDKManager.getDeviceId())) {
//                    isOnline = false;
//                }
//            }
//            notifyDeviceOnlineChanged(isOnline);
//        }
//
//        @Override
//        public void onMqttMsg(int protocol, String msg) {
//            TLog.d(TAG, "onMqttMsg() called with: protocol = [" + protocol + "], msg = [" + msg + "]");
////            ThingOS.getInstance().getPhotoFrame().updateMqttMsg(protocol,msg);
//        }
//    };
//
//
//
//    public void resetFrame() {
//        if (ioTSDKManager == null) return;
//        ioTSDKManager.reset();
//    }
//
//    @Override
//    public void onMediaAppStartUpload(StartEventData event) {//开始传图
//        TLog.d(TAG, "手机app开始传图: sid = [" + event.getSid() + "]");
//    }
//
//
//    @Override
//    public void onMediaAppUploaded(UploadedEventData event) {//传图完成
//        TLog.d(TAG, "传图完成: sid = [" + event.getSid() + "], fileName = [" + event.getRealFn() + "]");
//    }
//
//    @Override
//    public void onMediaDeviceDownloaded(UploadedEventData event, String filePath) {//设备下载图片
////        initMedia();
//        TLog.d(TAG, "下载文件: sid = [" + event.getSid() + "], fileName = [" + event.getRealFn() + "], filePath = [" + filePath + "], title = [" + event.getTitle() + "]");
//
//        TLog.d(TAG, "displayImage: filePath = [" + filePath + "]");
//        if (filePath == null || filePath.isEmpty()) {
//            return;
//        }
//        String realFn = event.getRealFn();
//        if (filePath.endsWith(".mp4") || filePath.endsWith(".avi") || filePath.endsWith(".mov")) {
//            TLog.d(TAG, "displayImage: not image file");
//
//            File videoDir = new File(appContext.getFilesDir(), "videos");
//            if (!videoDir.exists()) {
//                videoDir.mkdirs();
//            }
//            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
//            mediaMetadataRetriever.setDataSource(appContext, Uri.parse(filePath));
//            Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(0);
//
//            String fileName = System.currentTimeMillis() + "";
//
//            String saveFileName = "DCIM/" + fileName + ".mp4";
//            String saveJpgFileName = fileName + ".jpg";
//
//            File fileThumb = new File(Environment.getExternalStorageDirectory() + "/DCIM/thumb/", saveJpgFileName);
//
//            PhotoUtils.saveBitmap(fileThumb, bitmap);
//
//            PhotoUtils.copyFile(saveFileName, filePath);
//
//            DBManager.getInstance().insertFileData("", "", fileThumb.getAbsolutePath(), TimeUtils.getCutTime(), event.getTitle(), ".mp4", realFn);
//            notifyImageDeviceDownloaded(filePath);
//            ToastUtils.showCustomToast(appContext, appContext.getString(R.string.received_video_from_mobile_phone));
//        } else {
//            String insertTimeString = (new Date()).getTime() + ".jpg";
//            File f = new File(Environment.getExternalStorageDirectory(), "DCIM/" + insertTimeString);
//            FileUtils.copySdcardFile(new File(filePath), f.getAbsolutePath());
//            DBManager.getInstance().insertFileData("", "", f.getAbsolutePath(), TimeUtils.getCutTime(), event.getTitle(), "", realFn);
//            notifyImageDeviceDownloaded(f.getAbsolutePath());
////            initMediaComplete();
//            ToastUtils.showCustomToast(appContext, appContext.getString(R.string.received_pictures_from_mobile_phone));
//        }
//    }
//
//    @Override
//    public void onMediaUploadFailed(String sid, String code, String errorMessage) {
//        TLog.d(TAG, "onImageUploadFailed() called with: sid = [" + sid + "], code = [" + code + "], errorMessage = [" + errorMessage + "]");
//        printLn();
//    }
//
//    @Override
//    public void onMediaAllUploadFinished(UploadFinishedEventData event) {
//        TLog.d(TAG, "onImageAllUploadFinished() called with: sid = [" + event.getSid() + "]");
//        printLn();
//    }
//
//    @Override
//    public void onMediaAppDelete(DeleteEventData event) {//app撤回传图
//        boolean isNull = false;
//        for (String s : event.getRealFn()) {
//            isNull = true;
//            TLog.d(TAG, "onImageDelete() called with: sid = [" + event.getSid() + "], realFn = [" + s + "]");
//            String photoUrl = DBManager.getInstance().queryPhotoUrl(s);
//            DBManager.getInstance().deletePhotoByRealFn(s);
//            FileUtils.updateDeleteInfotoSDcard(appContext, photoUrl);
//        }
//        if (isNull) {
//            Intent intent = new Intent(Constant.ACTION_DATA);
//            intent.putExtra("TYPE", Constant.DEL_PHOTO);
//            appContext.sendBroadcast(intent);
//        }
//        ToastUtils.showCustomToast(appContext, appContext.getString(R.string.recall_a_file_on_mobile));
//        printLn();
//    }
//
//    private void printLn() {
//        List<PhotoInfo> photoInfos = ThingFrameOS.getInstance().getPhotoFrame().getAllFileInfos();
//        TLog.d(TAG, "已有文件数量: " + photoInfos.size());
//        for (PhotoInfo info : photoInfos) {
//            TLog.d(TAG, "realFn: " + info.getRealFn() + ", title: " + info.getTitle() + ", type: " + info.getFileType() + ",prefix:  " + info.getPrefix() + ", centerX: " + info.getCenterX() + ", localPath: " + info.getLocalPath());
//        }
//    }
//
//    @Override
//    public void onSuccess(Void tcKeyBean) {
//        Log.i(TAG, "initFrameZone success");
//        ThingFrameOS.getInstance().getPhotoFrame().registerMediaUploadListener(TuyaSDKManager.this);
//    }
//
//    @Override
//    public void onFailure(String s, String s1) {
//        Log.i(TAG, "initFrameZone failure");
//        frameZoneInited =false;
//    }
//
//
//
//
//
//    public interface TuyaListener {
//        void onQrCodeUpdated(String qrCode);
//
//        void onDeviceOnlineChanged(boolean isOnline);
//
//        void onDeviceActivated(String deviceId);
//
//        void onImageDeviceDownloaded(String path);
//
//        void onFirstActive(String deviceId);
//
//        void onDeviceReset();
//
//        void initMedia();
//
//        void initMediaComplete();
//    }
//
//    public interface TuyaLicenseListener {
//        void onLicenseGranted();
//
//        void onLicenseRevoked();
//    }
//
//    public void initMedia() {
//        for (TuyaListener l : listeners) {
//            l.initMedia();
//        }
//    }
//
//    public void initMediaComplete() {
//        for (TuyaListener l : listeners) {
//            l.initMediaComplete();
//        }
//    }
//
//    // 注册监听
//    public void addListener(TuyaListener listener) {
//        if (listener != null && !listeners.contains(listener)) {
//            listeners.add(listener);
//        }
//    }
//
//    // 取消监听
//    public void removeListener(TuyaListener listener) {
//        listeners.remove(listener);
//    }
//
//    // 通知所有监听器
//    private void notifyQrCodeUpdated(String qrCode) {
//        for (TuyaListener l : listeners) {
//            l.onQrCodeUpdated(qrCode);
//        }
//    }
//
//    private void notifyImageDeviceDownloaded(String path) {
//        for (TuyaListener l : listeners) {
//            l.onImageDeviceDownloaded(path);
//        }
//    }
//
//    private void notifyDeviceOnlineChanged(boolean online) {
//        for (TuyaListener l : listeners) {
//            l.onDeviceOnlineChanged(online);
//        }
//    }
//
//    private void onDeviceActivatedListener(String deviceId) {
//        for (TuyaListener l : listeners) {
//            l.onDeviceActivated(deviceId);
//        }
//    }
//
//    private void onFirstActiveListener(String deviceId) {
//        for (TuyaListener l : listeners) {
//            l.onFirstActive(deviceId);
//        }
//    }
//
//    private void onDeviceReset() {
//        for (TuyaListener l : listeners) {
//            l.onDeviceReset();
//        }
//    }
//}
