package com.tuya.smartai.demo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;
import com.thingclips.sdk.aistream.ConnectCallback;
import com.thingclips.sdk.aistream.EventStartCallback;
import com.thingclips.sdk.aistream.SessionCallback;
import com.thingclips.sdk.aistream.StreamResultCallback;
import com.thingclips.sdk.aistream.ThingAiStreamConstant;
import com.thingclips.sdk.aistream.ThingAiStreamListener;
import com.thingclips.sdk.aistream.audio.AudioPlayCallback;
import com.thingclips.sdk.aistream.helper.EventStartOptions;
import com.thingclips.smart.ai.stream.IThingAiStream;
import com.thingclips.smart.ai.stream.ThingAIOS;
import com.thingclips.smart.android.aistream.Constants;
import com.thingclips.smart.android.aistream.ThingStreamManager;
import com.thingclips.smart.android.aistream.data.StreamAudio;
import com.thingclips.smart.android.aistream.data.StreamEvent;
import com.thingclips.smart.android.aistream.data.StreamFile;
import com.thingclips.smart.android.aistream.data.StreamImage;
import com.thingclips.smart.android.aistream.data.StreamText;
import com.thingclips.smart.android.aistream.data.StreamVideo;
import com.tuya.smartai.iot_sdk.utils.TLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


public class AIStreamActivity extends AppCompatActivity {
    public static final String TAG = "AiActivity";
    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    
    /**
     * 启动AIStreamActivity的便捷方法
     * @param context 上下文
     * @param deviceId 设备ID
     */
    public static void start(Context context, String deviceId) {
        Intent intent = new Intent(context, AIStreamActivity.class);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        context.startActivity(intent);
    }
    
    private IThingAiStream aiStream;
    private TextView statusTextView;
    private Button connectButton;
    private Button disconnectButton;
    private Button createSessionButton;
    private Button closeSessionButton;
    private Button sendAudioButton;
    private Button stopAudioButton;
    private Button stopTtsButton;
    private Button getAgentTokenButton;
    private TextView logTextView;  // 日志显示TextView
    private ScrollView logScrollView;  // 日志滚动视图
    private ImageView imageView;  // 图片显示ImageView
    private String currentSessionId;  // 存储会话ID
    private String deviceId;  // 存储设备ID

    private boolean isRecording = false;
    private String mEventId;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    // 录音相关变量
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private AtomicBoolean isRecordingAudio = new AtomicBoolean(false);
    private ByteArrayOutputStream audioDataStream;
    private String audioEventId; // 音频事件ID
    private boolean isEventStarted = false; // 事件是否已开始
    private boolean isFirstFrameSent = false; // 首帧数据是否已发送
    private ByteArrayOutputStream pendingAudioData; // 待发送的音频数据缓存
    
    // 录音参数
    private static final int SAMPLE_RATE = 16000; // 采样率
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // 单声道
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 16位编码
    private int bufferSize;

    private ThingAiStreamListener listener = new ThingAiStreamListener() {
        @Override
        public void onConnectStateChanged(String connectionId, int state, int errorCode) {
            TLog.i(TAG, "连接状态变更: connectionId=" + connectionId + ", state=" + state + ", errorCode=" + errorCode);
            addLog("连接状态变更: connectionId=" + connectionId + ", state=" + state + ", errorCode=" + errorCode);
            runOnUiThread(() -> {
                if (state == Constants.ConnectState.CONNECTED) { // 已连接
                    statusTextView.setText("已连接: " + connectionId);
                } else {
                    statusTextView.setText("已断开连接: " + errorCode);
                }
            });
        }

        @Override
        public void onSessionStateChanged(String sessionId, int state, int errorCode) {
            TLog.i(TAG, "会话状态变更: sessionId=" + sessionId + ", state=" + state + ", errorCode=" + errorCode);
            addLog("会话状态变更: sessionId=" + sessionId + ", state=" + state + ", errorCode=" + errorCode);
            currentSessionId = sessionId;
            runOnUiThread(() -> {
                if (state == Constants.SessionState.CREATE_SUCCESS) {
                    statusTextView.setText("会话创建成功: " + sessionId);
                } else if (state == Constants.SessionState.CREATE_FAILED) {
                    statusTextView.setText("会话创建失败: " + errorCode);
                } else if (state == Constants.SessionState.CLOSED_BY_SERVER) {
                    statusTextView.setText("会话被服务器关闭: " + sessionId);
                    currentSessionId = null;
                }
            });
        }

        public void onAudioReceived(StreamAudio audioData) {
            if (audioData.streamFlag == Constants.StreamFlag.END || audioData.payload == null) return;
            TLog.i(TAG, "收到音频数据: " + audioData.payload.length + " 字节, streamFlag=" + audioData.streamFlag);
            if (audioData.streamFlag == Constants.StreamFlag.START) {
                aiStream.startPlayAudio(audioData, new AudioPlayCallback() {
                    @Override
                    public void onPlayStart() {
                        TLog.i(TAG, "onPlayStart: ");
                        addLog("=====onPlayStart: ");
                    }

                    @Override
                    public void onPlayFinish() {
                        TLog.i(TAG, "onPlayFinish: ");
                        addLog("=====onPlayFinish: ");
                    }

                    @Override
                    public void onPlayError(int errorCode, @NonNull String errorMessage) {
                        TLog.e(TAG, "onPlayError() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");
                        addLog("=====onPlayError: " + errorCode + ",msg: " + errorMessage);
                    }
                });
            }
        }
        @Override
        public void onVideoReceived(StreamVideo videoData) {
            TLog.i(TAG, "收到视频数据: " + videoData.payload.length + " 字节");
            addLog("收到视频数据: " + videoData.payload.length + " 字节");
        }

        @Override
        public void onImageReceived(StreamImage imageData) {

            if (imageData.streamFlag == Constants.StreamFlag.END) {
                TLog.e(TAG, "=======收到结束包 filePath ： " + imageData.filePath + ", " + imageData.format  + ",streamFlag:" + imageData.streamFlag);
                return;
            }
            TLog.i(TAG, "收到图像数据: " + imageData.payload.length + " 字节 filePath ： " + imageData.filePath + ", " + imageData.format);
            addLog("收到图像数据: " + imageData.payload.length+ " 字节,filePath: " + imageData.filePath + ",imageUrl " + imageData.imageUrl + ",format: " + imageData.format + ",streamFlag:" + imageData.streamFlag);

            displayImage(imageData.filePath);
        }

        @Override
        public void onFileReceived(StreamFile fileData) {
            TLog.i(TAG, "收到文件数据: " + fileData.fileName);
            addLog("收到文件数据: " + fileData.fileName);
        }

        @Override
        public void onTextReceived(StreamText textData) {
            TLog.i(TAG, "收到文本数据: " + textData.text );

            TextData parsedData = parseTextFromJson(textData.text);
            if (parsedData != null) {
                addLog(">>>>" + parsedData.getBizType() +": " + parsedData.getEof() + "->" + parsedData.getText());
            }
        }

        @Override
        public void onEventReceived(StreamEvent event) {
            TLog.i(TAG, "收到事件: " + event.toString());
            addLog("收到事件: " + event.toString());
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionToRecordAccepted) {
                startEventAndRecord();
            } else {
                Toast.makeText(this, "录音权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai);
        
        // 获取Intent中的设备ID
        deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);

        ThingStreamManager.getInstance().enableDebugLog(true);
        aiStream = ThingAIOS.getInstance().getAiStream();
        statusTextView = findViewById(R.id.statusTextView);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        createSessionButton = findViewById(R.id.createSessionButton);
        closeSessionButton = findViewById(R.id.closeSessionButton);
        sendAudioButton = findViewById(R.id.sendAudio);
        stopAudioButton = findViewById(R.id.stopAudio);
        stopTtsButton = findViewById(R.id.stopTtsButton);
        logTextView = findViewById(R.id.logTextView);
        logScrollView = findViewById(R.id.logScrollView);
        imageView = findViewById(R.id.imageView);


        // 设置流监听器
        setStreamListener();
        // 添加初始日志
        addLog("AIStreamActivity 初始化完成，设备ID: " + deviceId);

        findViewById(R.id.createSessionButton2).setOnClickListener(v -> {
            createSession2();
        });



        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> disconnect());
        createSessionButton.setOnClickListener(v -> createSession());
        closeSessionButton.setOnClickListener(v -> closeSession());
        sendAudioButton.setOnClickListener(v -> {
            if (!isRecordingAudio.get()) {
                startEventAndRecord();
            } else {
                addLog("正在录音中，请先停止录音");
                Toast.makeText(this, "正在录音中，请先停止录音", Toast.LENGTH_SHORT).show();
            }
        });
        stopAudioButton.setOnClickListener(v -> stopEventAndRecord());

        findViewById(R.id.sendText).setOnClickListener(v -> {
            startTextEventSample();
        });

        findViewById(R.id.sendImage).setOnClickListener(v -> {
            startImageEventSample();
        });

        stopTtsButton.setOnClickListener(v -> {
            aiStream.stopPlayAudio();


        });


    }

    private void setStreamListener() {
        aiStream.setStreamListener(listener);
    }
    


    private void connect() {
        addLog("开始连接设备: " + deviceId);

        aiStream.connectWithDevice(deviceId, new ConnectCallback() {
            @Override
            public void onSuccess(String connectionId) {
                addLog("连接成功: connectionId=" + connectionId);
                runOnUiThread(() -> {
                    statusTextView.setText("连接成功：connectionId： " + connectionId);
                });
            }

            @Override
            public void onError(int code, String error) {
                addLog("连接失败: code=" + code + ", error=" + error);
                runOnUiThread(() -> {
                    statusTextView.setText("连接失败: " + error);
                    Toast.makeText(AIStreamActivity.this, "连接失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void disconnect() {
        addLog("断开连接");
        isRecording = false;
        aiStream.disconnect();
    }

    boolean isGenerateImageSession = false;


    private void createSession2() {

        if (currentSessionId != null && !currentSessionId.isEmpty()) {
            Toast.makeText(AIStreamActivity.this, "Demo只维护一个 session，请先关闭其他的", Toast.LENGTH_SHORT).show();
            return;
        }

        addLog("创建会话2: " + deviceId);
        aiStream.createSession(deviceId, "generate_image", null, new SessionCallback() {
            @Override
            public void onSuccess(@NonNull String sessionId, @NonNull Map<String, Integer> sendDataChannels, @NonNull Map<String, Integer> revDataChannels) {
                TLog.i(TAG, "会话创建成功: " + sessionId +
                        ", sendDataCodes: " + sendDataChannels +
                        ", revDataCodes: " + revDataChannels);
                isGenerateImageSession = true;
                runOnUiThread(() -> {
                    statusTextView.setText("会话创建成功: " + sessionId);
                    addLog("会话创建成功: 【generate_image】 " + sessionId);
                    Toast.makeText(AIStreamActivity.this, "会话创建成功", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(int errorCode, @NonNull String message) {
                runOnUiThread(() -> {
                    statusTextView.setText("会话创建失败: " + message);
                    addLog("会话创建失败: " + message);
                    Toast.makeText(AIStreamActivity.this, "会话创建失败", Toast.LENGTH_SHORT).show();
                });
            }
        });

    }





    private void createSession() {


        if (currentSessionId != null && !currentSessionId.isEmpty()) {
            Toast.makeText(AIStreamActivity.this, "Demo只维护一个 session，请先关闭其他的", Toast.LENGTH_SHORT).show();
            return;
        }

        addLog("创建会话1: " + deviceId);

        // 添加自定义参数 custom.solution:generate_image
        // 根据 iOS 示例，userData 应该是 JSON 格式，包含 sessionAttributes
        // 格式: {"sessionAttributes": {"custom.param": {"solution": {"value": "generate_image"}}}}
        try {
            JSONObject sessionAttributes = new JSONObject();
            
            // solution 应该存放在 custom.param 中
            JSONObject customParam = new JSONObject();
            JSONObject solution = new JSONObject();
            solution.put("value", "generate_image");
            customParam.put("custom.solution", solution);
            
            sessionAttributes.put("custom.param", customParam);
            
            // 添加 tts.order.supports 默认参数
            JSONArray ttsSupports = new JSONArray();
            JSONObject ttsConfig = new JSONObject();
            ttsConfig.put("container", "");
            ttsConfig.put("channels", 1);
            ttsConfig.put("bitDepth", "16");
            ttsConfig.put("bitRate", "32000");
            ttsConfig.put("format", "mp3");
            ttsConfig.put("sampleRate", 16000);
            ttsSupports.put(ttsConfig);
            sessionAttributes.put("tts.order.supports", ttsSupports);
            
            JSONObject userData = new JSONObject();
            userData.put("sessionAttributes", sessionAttributes);
            
            String customAttribute = userData.toString();
            addLog("使用自定义参数: " + customAttribute);
            
            aiStream.createSession(deviceId, "", customAttribute, new SessionCallback() {
                @Override
                public void onSuccess(@NonNull String sessionId, @NonNull Map<String, Integer> sendDataChannels, @NonNull Map<String, Integer> revDataChannels) {
                    TLog.i(TAG, "会话创建成功: " + sessionId +
                            ", sendDataCodes: " + sendDataChannels +
                            ", revDataCodes: " + revDataChannels);
                    isGenerateImageSession = false;
                    runOnUiThread(() -> {
                        statusTextView.setText("会话创建成功: " + sessionId);
                        addLog("会话创建成功: 【】 " + sessionId);
                        Toast.makeText(AIStreamActivity.this, "会话创建成功", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(int errorCode, @NonNull String message) {
                    runOnUiThread(() -> {
                        statusTextView.setText("会话创建失败: " + message);
                        addLog("会话创建失败: " + message);
                        Toast.makeText(AIStreamActivity.this, "会话创建失败", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "构建 userData JSON 失败", e);
            addLog("构建 userData JSON 失败: " + e.getMessage());
            runOnUiThread(() -> {
                statusTextView.setText("构建 userData 失败");
                Toast.makeText(AIStreamActivity.this, "构建 userData 失败", Toast.LENGTH_SHORT).show();
            });
        }

    }


    private void closeSession() {
        if (currentSessionId != null && !currentSessionId.isEmpty()) {
            aiStream.closeSession(currentSessionId, new StreamResultCallback() {
                @Override
                public void onSuccess() {
                    currentSessionId = null;
                    runOnUiThread(() -> {
                        addLog("session会话 已关闭");
                        statusTextView.setText("session会话 已关闭");
                    });
                }

                @Override
                public void onError(int i, @NonNull String s) {
                    currentSessionId = null;
                    addLog("session会话关闭失败");
                    runOnUiThread(() -> {
                        statusTextView.setText("session 关闭失败 " + s);
                    });
                }
            });

        } else {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
        }
    }



    private void startTextEventSample() {

        if (TextUtils.isEmpty(currentSessionId)) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!aiStream.isConnected(Constants.ClientType.DEVICE, deviceId)) {
            Toast.makeText(this, "没有活动的会话q", Toast.LENGTH_SHORT).show();
            return;
        }

        StreamText textData = new StreamText();
        textData.text = "给我生成两张照片，一张是大老虎的照片，一张是海边看日出的照片";
        if(isGenerateImageSession){
            textData.text ="Please generate an image of two children chasing each other on the beach.";
        }

        EventStartOptions options = new EventStartOptions.Builder(currentSessionId)
                .build();
        //step1: event start
        //step2: 发送数据
        //step3: 发送数据结束
        //step4: event end
        aiStream.sendEventStart(options, new EventStartCallback() {
            @Override
            public void onSuccess(@NonNull String eventId) {
                addLog("<---text:" + textData.text);
                aiStream.sendTextData(currentSessionId, textData, new StreamResultCallback() {
                    @Override
                    public void onSuccess() {
                        finalAudioEvent(eventId);
                    }

                    @Override
                    public void onError(int errorCode, @NonNull String errorMessage) {
                        finalAudioEvent(eventId);
                    }
                });
            }

            @Override
            public void onError(int errorCode, @NonNull String errorMessage) {
                Log.e(TAG, "onError() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");

                addLog("onError() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");
            }
        });
    }


    private int packageCount = 0;
    /**
     * 开始录音事件
     */
    private void startEventAndRecord() {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        if (TextUtils.isEmpty(currentSessionId)) {
            addLog("请先创建会话");
            Toast.makeText(this, "请先创建会话", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRecordingAudio.get()) {
            addLog("已经在录音中");
            return;
        }

        try {
            // 计算缓冲区大小
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            
            // 创建AudioRecord
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                addLog("AudioRecord初始化失败");
                return;
            }

            // 初始化音频数据流和缓存
            audioDataStream = new ByteArrayOutputStream();
            pendingAudioData = new ByteArrayOutputStream();
            isEventStarted = false;
            isFirstFrameSent = false;
            packageCount = 0;
            addLog("录音初始化完成: pendingAudioData=" + pendingAudioData);
            Log.d(TAG, "录音初始化完成: pendingAudioData=" + pendingAudioData);
            
            // 开始录音
            audioRecord.startRecording();
            isRecordingAudio.set(true);
            
            addLog("开始录音...");
            Toast.makeText(this, "开始录音", Toast.LENGTH_SHORT).show();
            
            // 启动录音线程
            recordingThread = new Thread(this::recordingLoop, "RecordingThread");
            recordingThread.start();
            
            // 发送事件开始
            sendAudioEventStart();
            
        } catch (Exception e) {
            addLog("启动录音失败: " + e.getMessage());
            Log.e(TAG, "启动录音失败", e);
        }
    }

    /**
     * 发送音频事件开始
     */
    private void sendAudioEventStart() {
        EventStartOptions options = new EventStartOptions.Builder(currentSessionId)
                .build();
        
        aiStream.sendEventStart(options, new EventStartCallback() {
            @Override
            public void onSuccess(@NonNull String eventId) {
                audioEventId = eventId;
                isEventStarted = true;
                addLog("-------> 音频事件开始，eventId: " + eventId);
                
                // 尝试发送缓存的音频数据
                sendPendingAudioData();
            }

            @Override
            public void onError(int errorCode, @NonNull String errorMessage) {
                addLog("音频事件开始失败: " + errorCode + ", " + errorMessage);
                Log.e(TAG, "音频事件开始失败: " + errorCode + ", " + errorMessage);
            }
        });
    }

    /**
     * 发送缓存的音频数据
     */
    private void sendPendingAudioData() {
        if (pendingAudioData != null && pendingAudioData.size() > 0) {
            byte[] cachedData = pendingAudioData.toByteArray();
            // 发送首帧数据
            sendAudioStartDataToStream(cachedData, cachedData.length);
            
            // 清空缓存
            try {
                pendingAudioData.close();
                pendingAudioData = new ByteArrayOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "清空音频缓存失败", e);
            }
        } else {
            Log.i(TAG, "sendPendingAudioData 缓存为空，不需要使用缓存 ");
        }
    }

    /**
     * 录音循环
     */
    private void recordingLoop() {
        byte[] buffer = new byte[bufferSize];
        
        while (isRecordingAudio.get()) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    // 将录音数据写入流
                    audioDataStream.write(buffer, 0, bytesRead);


                    if (isEventStarted && isFirstFrameSent) {
                        //开始事件完成，首帧发送完成，直接发送持中间数据包
                        sendAudioDataToStream(buffer, bytesRead);
                    } else if (isEventStarted) {
                        //开始事件完成，但是首帧还未发送
                        if (pendingAudioData.size() == 0) {
                            // 若无缓存，则该数据直接作为首帧发送
                            sendAudioStartDataToStream(buffer, bytesRead);
                        } else {
                            //有缓存
                            Log.e(TAG, "ignore buffer length : " + buffer.length);
                        }
                    } else {
                        // 缓存音频数据，等待事件开始和首帧发送完成
                        if (pendingAudioData != null) {
                            pendingAudioData.write(buffer, 0, bytesRead);
                            Log.d(TAG, "音频数据已缓存，当前缓存大小: " + pendingAudioData.size() + " 字节");
                        }
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "录音循环出错", e);
                break;
            }
        }
    }

    private void sendAudioStartDataToStream(byte[] audioData, int length) {
        try {
            StreamAudio audio = new StreamAudio();

            // 复制有效的音频数据
            byte[] validData = new byte[length];
            System.arraycopy(audioData, 0, validData, 0, length);

            audio.payload = validData;
            audio.timestamp = System.currentTimeMillis();
//            audio.pts = System.currentTimeMillis() * 1000; // 转换为微秒
            audio.codecType = Constants.AudioCodec.PCM; // PCM格式
            audio.sampleRate = SAMPLE_RATE;
            audio.channels = Constants.AudioChannel.CHANNEL_MONO;
            audio.bitDepth = 16;
            audio.streamFlag = Constants.StreamFlag.START; // 首帧数据
            addLog("发送首帧数据: data:  " + length + " 字节");
            aiStream.sendAudioData(currentSessionId, audio, new StreamResultCallback() {
                @Override
                public void onSuccess() {
                    // 首帧数据发送成功，设置标志
                    isFirstFrameSent = true;
                    addLog("-----> Start -----首帧音频数据发送成功，开始发送后续数据");
                }

                @Override
                public void onError(int errorCode, @NonNull String errorMessage) {
                    addLog("首帧音频数据发送失败: " + errorCode + ", " + errorMessage);
                    Log.e(TAG, "首帧音频数据发送失败: " + errorCode + ", " + errorMessage);
                }
            });

        } catch (Exception e) {
            addLog("发送首帧音频数据出错: " + e.getMessage());
            Log.e(TAG, "发送首帧音频数据出错", e);
        }
    }

    /**
     * 发送音频数据到流
     */
    private void sendAudioDataToStream(byte[] audioData, int length) {
        try {
            StreamAudio audio = new StreamAudio();
            
            // 复制有效的音频数据
            byte[] validData = new byte[length];
            System.arraycopy(audioData, 0, validData, 0, length);
            
            audio.payload = validData;
            audio.timestamp = System.currentTimeMillis();
//            audio.pts = System.currentTimeMillis() * 1000; // 转换为微秒
            audio.codecType = Constants.AudioCodec.PCM; // PCM格式
            audio.sampleRate = SAMPLE_RATE;
            audio.channels = Constants.AudioChannel.CHANNEL_MONO;
            audio.bitDepth = 16;
            audio.streamFlag = Constants.StreamFlag.IN_PROGRESS ; // 持续数据
            packageCount++;
            aiStream.sendAudioData(currentSessionId, audio, new StreamResultCallback() {
                @Override
                public void onSuccess() {
                    // 成功发送音频数据
                }

                @Override
                public void onError(int errorCode, @NonNull String errorMessage) {
                    Log.e(TAG, "发送音频数据失败: " + errorCode + ", " + errorMessage);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "发送音频数据出错", e);
        }
    }

    private void stopEventAndRecord() {
        if (!isRecordingAudio.get()) {
            addLog("当前没有在录音");
            return;
        }

        try {
            // 停止录音
            isRecordingAudio.set(false);
            
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            
            // 等待录音线程结束
            if (recordingThread != null && recordingThread.isAlive()) {
                recordingThread.interrupt();
                recordingThread = null;
            }

            // 发送最终的音频数据
            if (audioDataStream != null) {
                audioDataStream.close();
                audioDataStream = null;
            }
            
            // 清空缓存数据
            if (pendingAudioData != null) {
                pendingAudioData.close();
                pendingAudioData = null;
            }
            
            addLog("录音结束");
            Toast.makeText(this, "录音结束", Toast.LENGTH_SHORT).show();
            
            // 发送事件结束流程
            sendAudioEventEnd();
            
        } catch (Exception e) {
            addLog("停止录音失败: " + e.getMessage());
            Log.e(TAG, "停止录音失败", e);
        }
    }

    /**
     * 发送音频事件结束流程
     */
    private void sendAudioEventEnd() {
        if (TextUtils.isEmpty(audioEventId)) {
            addLog("音频事件ID为空，无法发送结束事件");
            return;
        }

        // 1. 发送最终音频数据
        sendFinalAudioData();
    }

    /**
     * 发送最终音频事件结束
     */
    private void finalAudioEvent(String eventId) {
        aiStream.sendEventEnd(eventId, currentSessionId, null, new StreamResultCallback() {
            @Override
            public void onSuccess() {
                addLog("事件发送完成");
                // 重置状态
                audioEventId = null;
                isEventStarted = false;
                isFirstFrameSent = false;
            }

            @Override
            public void onError(int errorCode, @NonNull String errorMessage) {
                addLog("事件发送完成: " + errorCode + ", " + errorMessage);
                // 重置状态
                audioEventId = null;
                isEventStarted = false;
                isFirstFrameSent = false;
            }
        });
    }

    /**
     * 发送最终音频数据
     */
    private void sendFinalAudioData() {
        try {
            StreamAudio audio = new StreamAudio();
            audio.payload = new byte[]{0x00};
            audio.timestamp = System.currentTimeMillis();
//            audio.pts = System.currentTimeMillis() * 1000;
            audio.codecType = Constants.AudioCodec.PCM;
            audio.sampleRate = SAMPLE_RATE;
            audio.channels = Constants.AudioChannel.CHANNEL_MONO;
            audio.bitDepth = 16;
            audio.streamFlag = Constants.StreamFlag.END; // 结束标志

            aiStream.sendAudioData(currentSessionId, audio, new StreamResultCallback() {
                @Override
                public void onSuccess() {
                    addLog("----> END ----- 事件发送完成,package Count: " + packageCount);
                    // 2. 发送事件载荷结束
                    finalAudioEvent(audioEventId);
                }

                @Override
                public void onError(int errorCode, @NonNull String errorMessage) {
                    addLog("发送音频数据失败: " + errorCode + ", " + errorMessage);
                    Log.e(TAG, "onError() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");
                    finalAudioEvent(audioEventId);
                }
            });
            
        } catch (Exception e) {
            addLog("发送最终音频数据出错: " + e.getMessage());
        }
    }

    /**
     * 判断字符串是否为图片 URL
     * @param url 待检查的字符串
     * @return 是否为图片 URL
     */
    private boolean isImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        // 检查是否以 http:// 或 https:// 开头
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        // 检查常见图片扩展名或图片服务 URL 特征
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".jpg") 
            || lowerUrl.contains(".jpeg") 
            || lowerUrl.contains(".png") 
            || lowerUrl.contains(".gif") 
            || lowerUrl.contains(".webp")
            || lowerUrl.contains(".bmp")
            || lowerUrl.contains("image")
            || lowerUrl.contains("/img")
            || lowerUrl.contains("tos-cn")  // 火山引擎 TOS 图片服务
            || lowerUrl.contains("volces.com");  // 火山引擎域名
    }

    /**
     * 解析JSON格式的文本数据
     * @param jsonText JSON格式的文本数据
     * @return 解析出的TextData对象
     */
    private TextData parseTextFromJson(String jsonText) {
        try {
            JSONObject jsonObject = new JSONObject(jsonText);
            
            // 创建TextData对象
            TextData textData = new TextData();
            
            // 解析基本字段
            if (jsonObject.has("bizId")) {
                textData.setBizId(jsonObject.getString("bizId"));
            }
            
            if (jsonObject.has("bizType")) {
                textData.setBizType(jsonObject.getString("bizType"));
            }
            
            if (jsonObject.has("eof")) {
                textData.setEof(jsonObject.getInt("eof"));
            }
            
            // 解析 data 字段
            if (jsonObject.has("data")) {
                // 尝试获取 data 作为字符串（可能是图片 URL）
                Object dataValue = jsonObject.get("data");
                
                if (dataValue instanceof String) {
                    // data 是字符串，检查是否是图片 URL
                    String dataString = (String) dataValue;
                    if (isImageUrl(dataString)) {
                        textData.setText("[图片URL]");
                        textData.setImageUrl(dataString);
                        runOnUiThread(() -> {
                            displayImage(dataString);
                            addLog("收到图片: " + dataString.substring(0, Math.min(80, dataString.length())) + "...");
                        });
                    } else {
                        textData.setText(dataString);
                    }
                } else if (dataValue instanceof JSONObject) {
                    // data 是 JSON 对象
                    JSONObject dataObject = (JSONObject) dataValue;
                    
                    if (dataObject.has("text")) {
                        textData.setText(dataObject.getString("text"));
                    }

                    if (dataObject.has("content")) {
                        String content = dataObject.getString("content");
                        if (!content.isEmpty()) {
                            textData.setText(content);
                        }
                    }

                    if (dataObject.has("images")) {
                        try {
                            JSONArray imagesArray = dataObject.getJSONArray("images");
                            for (int i = 0; i < imagesArray.length(); i++) {
                                JSONObject imageObj = imagesArray.getJSONObject(i);
                                if (imageObj.has("url")) {
                                    String imageUrl = imageObj.getString("url");
                                    textData.setText("[图片URL: " + imageUrl + "]");
                                    textData.setImageUrl(imageUrl);
                                    runOnUiThread(() -> {
                                        displayImage(imageUrl);
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            // images 可能不是数组格式，尝试作为对象处理
                            JSONObject imagesObject = dataObject.getJSONObject("images");
                            if (imagesObject.has("url")) {
                                JSONArray urlArray = imagesObject.getJSONArray("url");
                                if (urlArray.length() > 0) {
                                    String imageUrl = urlArray.getString(0);
                                    textData.setText("[图片URL: " + imageUrl + "]");
                                    textData.setImageUrl(imageUrl);
                                    runOnUiThread(() -> {
                                        displayImage(imageUrl);
                                    });
                                }
                            }
                        }
                    }
                }
            }
            
            return textData;
            
        } catch (JSONException e) {
            // 如果JSON解析失败，返回null
            return null;
        }
    }

    /**
     * 添加日志到日志显示区域
     * @param message 日志消息
     */
    private void addLog(String message) {
        runOnUiThread(() -> {
            if (logTextView != null) {
                String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                String logEntry = "[" + timestamp + "] " + message + "\n";
                
                // 获取当前文本并添加新日志
                String currentText = logTextView.getText().toString();
                String newText = currentText + logEntry;
                
                // 限制日志行数，避免内存过多占用
                String[] lines = newText.split("\n");
                if (lines.length > 1000) {
                    // 保留最新的800行
                    StringBuilder sb = new StringBuilder();
                    for (int i = lines.length - 800; i < lines.length; i++) {
                        sb.append(lines[i]).append("\n");
                    }
                    newText = sb.toString();
                }
                
                logTextView.setText(newText);
                
                // 自动滚动到底部
                if (logScrollView != null) {
                    logScrollView.post(() -> {
                        try {
                            logScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        } catch (Exception e) {
                            // 忽略滚动错误
                        }
                    });
                }
            }
        });
    }



    //**********************发送图片******************
    private void startImageEventSample() {

        if (TextUtils.isEmpty(currentSessionId)) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!aiStream.isConnected(Constants.ClientType.DEVICE, deviceId)) {
            Toast.makeText(this, "没有活动的会话", Toast.LENGTH_SHORT).show();
            return;
        }


        EventStartOptions options = new EventStartOptions.Builder(currentSessionId)
                .build();
        //step1: event start
        //step2: 发送数据
        //step3: 发送数据结束
        //step4: event end
        aiStream.sendEventStart(options, new EventStartCallback() {
            @Override
            public void onSuccess(@NonNull String eventId) {

                if(TextUtils.isEmpty(lastFilePath)) return;
                addLog("<---current: image: " + lastFilePath);
                displayImage(lastFilePath);
                aiStream.sendImageData(currentSessionId, lastFilePath, null, new StreamResultCallback() {
                    @Override
                    public void onSuccess() {


                        StreamText textData = new StreamText();
                        textData.text = "描述一下这个照片";
                        if (isGenerateImageSession) {
                            textData.text = "Transform this photo into a sketch style";
                        }
                        addLog("<---text: " + textData.text);
                        aiStream.sendTextData(currentSessionId, textData, new StreamResultCallback() {
                            @Override
                            public void onSuccess() {
                                finalAudioEvent(eventId);
                            }

                            @Override
                            public void onError(int errorCode, @NonNull String errorMessage) {
                                finalAudioEvent(eventId);
                            }
                        });


                    }

                    @Override
                    public void onError(int errorCode, @NonNull String errorMessage) {

                    }
                });
            }

            @Override
            public void onError(int errorCode, @NonNull String errorMessage) {
                Log.e(TAG, "onError() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");

                addLog("onError() called with: errorCode = [" + errorCode + "], errorMessage = [" + errorMessage + "]");
            }
        });
    }


    String lastFilePath = null;
    /**
     * 显示图片（参考 MainActivity 的实现）
     * @param filePath 图片文件路径
     */
    private void displayImage(String filePath) {
        TLog.d(TAG, "displayImage: filePath = [" + filePath + "]");
        if (filePath == null || filePath.isEmpty()) {
            if (imageView != null) {
                imageView.setVisibility(android.view.View.GONE);
            }
            return;
        }
        lastFilePath = filePath;
        imageView.setVisibility(View.VISIBLE);
        // 标准图片格式，使用 Glide 正常加载
        Glide.with(this)
                .load(filePath)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imageView);

    }

    /**
     * 使用 BitmapFactory 直接加载图片，适用于非标准格式（如 .image）
     * 这样可以完全绕过 Glide 的解码器选择机制
     */
    private void loadImageWithBitmapFactory(String filePath, int maxWidth, int maxHeight) {
        try {
            File imageFile = new File(filePath);
            if (!imageFile.exists()) {
                TLog.e(TAG, "Image file does not exist: " + filePath);
                if (imageView != null) {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                }
                return;
            }

            // 首先获取图片的原始尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            
            int imageWidth = options.outWidth;
            int imageHeight = options.outHeight;
            
            if (imageWidth <= 0 || imageHeight <= 0) {
                TLog.e(TAG, "Invalid image dimensions: " + imageWidth + "x" + imageHeight);
                if (imageView != null) {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                }
                return;
            }
            
            TLog.d(TAG, "Image original size: " + imageWidth + "x" + imageHeight);
            
            // 计算缩放比例
            int sampleSize = calculateInSampleSize(imageWidth, imageHeight, maxWidth, maxHeight);
            TLog.d(TAG, "Calculated sample size: " + sampleSize);
            
            // 使用计算出的 sampleSize 加载图片
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // 使用更节省内存的配置
            
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
            
            if (bitmap != null) {
                TLog.d(TAG, "Bitmap loaded successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            } else {
                TLog.e(TAG, "Failed to decode bitmap from file: " + filePath);
                if (imageView != null) {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            }
        } catch (Exception e) {
            TLog.e(TAG, "Error loading image with BitmapFactory", e);
            if (imageView != null) {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        }
    }

    /**
     * 计算合适的 inSampleSize 值，用于缩放图片
     */
    private int calculateInSampleSize(int imageWidth, int imageHeight, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        
        if (imageHeight > reqHeight || imageWidth > reqWidth) {
            final int halfHeight = imageHeight / 2;
            final int halfWidth = imageWidth / 2;
            
            // 计算最大的 inSampleSize 值，保证缩放后的图片尺寸仍然大于等于请求的尺寸
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();


        if (aiStream != null) {
            aiStream.releaseAudioPlayer();
            aiStream.destroy();
            aiStream = null;
        }
    }
}