package com.tuya.smartai.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

import com.tuya.smartai.iot_sdk.DPEvent;
import com.tuya.smartai.iot_sdk.IIoTManager;
import com.tuya.smartai.iot_sdk.utils.TLog;

/**
 * 电视控制管理器
 * 处理来自 APP 的遥控指令，并监听本地状态变化上报给 APP
 * 
 * 新版 DP 功能点定义（标准品类）：
 * - DP 1: switch (Bool) - 开关
 * - DP 2: volume_set (Value) - 音量设置 (0-100)
 * - DP 3: mute (Bool) - 静音
 * - DP 4: channel (Value) - 频道（不处理）
 * - DP 5: channel_change (Enum) - 切台（不处理）
 * - DP 6: direction_control (Enum) - 方向控制 (up, down, left, right)
 * - DP 7: tv_menu (Bool) - 菜单
 * - DP 8: tv_home (Bool) - 主页（无状态，执行后上报 false）
 * - DP 9: enter (Bool) - 确认
 * - DP 10: back (Bool) - 返回（无状态，执行后上报 false）
 * - DP 11: exit (Bool) - 退出（不处理）
 */
public class TVControlManager {
    private static final String TAG = "TVControlManager";
    private static final String PREF_NAME = "tv_control_dp_state";
    
    // DP ID 定义（新版标准品类）
    public static final int DP_SWITCH = 1;          // 开关
    public static final int DP_VOLUME_SET = 2;      // 音量设置 (0-100)
    public static final int DP_MUTE = 3;            // 静音
    public static final int DP_CHANNEL = 4;         // 频道（不处理）
    public static final int DP_CHANNEL_CHANGE = 5;  // 切台（不处理）
    public static final int DP_DIRECTION = 6;       // 方向控制
    public static final int DP_MENU = 7;            // 菜单
    public static final int DP_HOME = 8;            // 主页（无状态）
    public static final int DP_ENTER = 9;           // 确认
    public static final int DP_BACK = 10;           // 返回（无状态）
    public static final int DP_EXIT = 11;           // 退出（不处理）
    
    // 方向键枚举值 (up=0, down=1, left=2, right=3)
    public static final String DIRECTION_UP = "up";
    public static final String DIRECTION_DOWN = "down";
    public static final String DIRECTION_LEFT = "left";
    public static final String DIRECTION_RIGHT = "right";
    
    // SharedPreferences key
    private static final String KEY_VOLUME = "dp_volume";
    private static final String KEY_MUTE = "dp_mute";
    private static final String KEY_SWITCH = "dp_switch";
    private static final String KEY_FIRST_SYNC = "first_sync_done";
    
    private Context context;
    private AudioManager audioManager;
    private IIoTManager ioTSDKManager;
    private Handler mainHandler;
    private Toast singletonToast;
    private SharedPreferences prefs;
    
    // 音量变化监听
    private ContentObserver volumeObserver;
    private int lastReportedVolume = -1;
    private boolean lastReportedMute = false;
    
    // 是否正在通过 App 设置音量（避免重复上报）
    private volatile boolean isSettingVolumeFromApp = false;
    
    public TVControlManager(Context context, IIoTManager ioTSDKManager) {
        this.context = context;
        this.ioTSDKManager = ioTSDKManager;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // 初始化上次上报的值
        lastReportedVolume = prefs.getInt(KEY_VOLUME, -1);
        lastReportedMute = prefs.getBoolean(KEY_MUTE, false);
        
        TLog.i(TAG, "TVControlManager 初始化完成");
    }
    
    /**
     * 开始监听音量变化
     */
    public void startVolumeListener() {
        if (volumeObserver != null) {
            return; // 已经在监听
        }
        
        volumeObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // 如果是 App 设置的，不重复上报
                if (isSettingVolumeFromApp) {
                    return;
                }
                onLocalVolumeChanged();
            }
        };
        
        // 监听系统设置变化（包括音量）
        context.getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver
        );
        
        TLog.i(TAG, "开始监听音量变化");
    }
    
    /**
     * 停止监听音量变化
     */
    public void stopVolumeListener() {
        if (volumeObserver != null) {
            context.getContentResolver().unregisterContentObserver(volumeObserver);
            volumeObserver = null;
            TLog.i(TAG, "停止监听音量变化");
        }
    }
    
    /**
     * 本地音量变化时的处理
     */
    private void onLocalVolumeChanged() {
        if (audioManager == null) return;
        
        int currentLevel = getCurrentVolumeLevel();
        boolean currentMute = audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        
        // 检查音量是否变化
        if (currentLevel != lastReportedVolume) {
            TLog.i(TAG, "本地音量变化: " + lastReportedVolume + " -> " + currentLevel);
            reportVolumeIfChanged(currentLevel);
        }
        
        // 检查静音状态是否变化
        if (currentMute != lastReportedMute) {
            TLog.i(TAG, "本地静音变化: " + lastReportedMute + " -> " + currentMute);
            reportMuteIfChanged(currentMute);
        }
    }
    
    /**
     * 上报音量（仅当变化时）
     */
    private void reportVolumeIfChanged(int level) {
        if (level != lastReportedVolume) {
            lastReportedVolume = level;
            prefs.edit().putInt(KEY_VOLUME, level).apply();
            reportDPValue(DP_VOLUME_SET, level);
            reportDPBool(DP_MUTE, false);
            TLog.i(TAG, "上报音量变化: " + level);
        }
    }
    
    /**
     * 上报静音状态（仅当变化时）
     */
    private void reportMuteIfChanged(boolean mute) {
        if (mute != lastReportedMute) {
            lastReportedMute = mute;
            prefs.edit().putBoolean(KEY_MUTE, mute).apply();
            reportDPBool(DP_MUTE, mute);
            TLog.i(TAG, "上报静音变化: " + mute);
        }
    }
    
    /**
     * MQTT 上线后同步初始状态
     * 调用时机：onMQTTStatusChanged 收到上线状态时
     */
    public void onMqttOnline() {
        TLog.i(TAG, "MQTT 上线，同步初始状态");
        
        // 获取当前实际状态（档位值）
        int currentLevel = getCurrentVolumeLevel();
        boolean currentMute = audioManager != null && audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        
        // 检查是否需要首次同步
        boolean firstSyncDone = prefs.getBoolean(KEY_FIRST_SYNC, false);
        
        if (!firstSyncDone) {
            // 首次同步，强制上报所有状态
            TLog.i(TAG, "首次同步，上报所有初始状态");
            
            // 音量 - 根据实际档位值
            reportDPValue(DP_VOLUME_SET, currentLevel);
            lastReportedVolume = currentLevel;
            prefs.edit().putInt(KEY_VOLUME, currentLevel).apply();
            
            // 静音 - 根据实际值
            reportDPBool(DP_MUTE, false);
            lastReportedMute = currentMute;
            prefs.edit().putBoolean(KEY_MUTE, currentMute).apply();
            
            reportDPBool(DP_SWITCH, true);
            prefs.edit().putBoolean(KEY_SWITCH, false).apply();
            
            // 主页、返回 - 上报 false（无状态操作）
            reportDPBool(DP_HOME, false);
            reportDPBool(DP_BACK, false);
            
            // 标记首次同步完成
            prefs.edit().putBoolean(KEY_FIRST_SYNC, true).apply();
            
        } else {
            // 非首次同步，只上报有变化的状态
            TLog.i(TAG, "非首次同步，检查状态变化");
            
            // 检查音量是否变化
            int savedLevel = prefs.getInt(KEY_VOLUME, -1);
            if (savedLevel != currentLevel) {
                TLog.i(TAG, "音量有变化: " + savedLevel + " -> " + currentLevel);
                reportDPValue(DP_VOLUME_SET, currentLevel);
                lastReportedVolume = currentLevel;
                prefs.edit().putInt(KEY_VOLUME, currentLevel).apply();
            }
            
            // 检查静音是否变化
            boolean savedMute = prefs.getBoolean(KEY_MUTE, false);
            if (savedMute != currentMute) {
                TLog.i(TAG, "静音有变化: " + savedMute + " -> " + currentMute);
                reportDPBool(DP_MUTE, currentMute);
                lastReportedMute = currentMute;
                prefs.edit().putBoolean(KEY_MUTE, currentMute).apply();
            }
            
            // 开关、主页、返回 - 每次上线都上报 false
            reportDPBool(DP_HOME, false);
            reportDPBool(DP_BACK, false);
        }
    }
    
    /**
     * 显示 Toast 提示（单例模式，快速更新）
     */
    private void showToast(final String message) {
        mainHandler.post(() -> {
            if (singletonToast != null) {
                singletonToast.cancel();
            }
            singletonToast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            singletonToast.show();
        });
    }
    
    /**
     * 处理收到的 DP 事件
     * @param event DP 事件
     * @return true 表示该事件已被处理，false 表示未处理（会显示在 UI 上）
     */
    public boolean handleDPEvent(DPEvent event) {
        if (event == null) {
            return false;
        }
        
        int dpId = event.dpid;
        Object value = event.value;
        
        TLog.i(TAG, "处理 DP 事件: dpId=" + dpId + ", value=" + value);
        
        switch (dpId) {
            case DP_SWITCH:
                if (value instanceof Boolean) {
                    handleSwitch((Boolean) value);
                }
                return true;
                
            case DP_VOLUME_SET:
                if (value instanceof Integer) {
                    handleVolumeSet((Integer) value);
                }
                return true;
                
            case DP_MUTE:
                if (value instanceof Boolean) {
                    handleMute((Boolean) value);
                }
                return true;
                
            case DP_DIRECTION:
                handleDirection(value);
                return true;
                
            case DP_MENU:
                if (value instanceof Boolean && (Boolean) value) {
                    handleMenu();
                }
                return true;
                
            case DP_HOME:
                // 主页：无论下发什么，执行后都上报 false
                if (value instanceof Boolean && (Boolean) value) {
                    handleHome();
                }
                // 无论收到什么值，都上报 false（无状态操作）
                reportDPBool(DP_HOME, false);
                return true;
                
            case DP_ENTER:
                if (value instanceof Boolean && (Boolean) value) {
                    handleEnter();
                }
                return true;
                
            case DP_BACK:
                // 返回：无论下发什么，执行后都上报 false
                if (value instanceof Boolean && (Boolean) value) {
                    handleBack();
                }
                // 无论收到什么值，都上报 false（无状态操作）
                reportDPBool(DP_BACK, false);
                return true;
                
            case DP_CHANNEL:
            case DP_CHANNEL_CHANGE:
            case DP_EXIT:
            default:
                TLog.w(TAG, "未处理的 DP ID: " + dpId + ", value: " + value);
                return false;
        }
    }
    
    /**
     * 开关控制（预留）
     */
    private void handleSwitch(boolean on) {
        TLog.i(TAG, "执行: 开关=" + (on ? "开" : "关") + " (预留功能)");
        showToast("⚡ 开关控制需要系统权限 (预留)");
        // 上报 false（让 App 下次可以发 true）
        reportDPBool(DP_SWITCH, on);
        prefs.edit().putBoolean(KEY_SWITCH, false).apply();
    }
    
    /**
     * 设置音量值
     * App 下发的是档位值（0-100），不是百分比
     * 如果超过 maxVolume，则设置为 maxVolume，并上报实际档位值
     */
    private void handleVolumeSet(int targetLevel) {
        if (audioManager == null) return;
        
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        
        TLog.i(TAG, "执行: App下发档位=" + targetLevel + ", 设备最大档位=" + maxVolume);
        
        // 边界检查：如果超过最大档位，就设置为最大档位
        if (targetLevel > maxVolume) {
            targetLevel = maxVolume;
            TLog.i(TAG, "下发档位超过最大，调整为: " + targetLevel);
        }
        if (targetLevel < 0) {
            targetLevel = 0;
        }
        
        // 标记正在通过 App 设置，避免 Observer 重复上报
        isSettingVolumeFromApp = true;
        
        // 设置音量档位
        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetLevel,
                AudioManager.FLAG_SHOW_UI
        );
        
        // 读取实际设置后的档位
        int actualLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        TLog.i(TAG, "音量设置完成: 目标档位=" + targetLevel + " -> 实际档位=" + actualLevel);
        
        // 上报实际档位值（不是百分比）
        reportDPValue(DP_VOLUME_SET, actualLevel);
        reportDPBool(DP_MUTE, false);
        lastReportedVolume = actualLevel;
        prefs.edit().putInt(KEY_VOLUME, actualLevel).apply();
        
        // 延迟重置标记
        mainHandler.postDelayed(() -> isSettingVolumeFromApp = false, 500);
    }
    
    /**
     * 静音控制
     * 兼容 Android 4.3：使用 setStreamMute() 而不是 adjustStreamVolume()
     */
    private void handleMute(boolean mute) {
        TLog.i(TAG, "执行: 静音=" + mute);
        if (audioManager != null) {
            // 标记正在通过 App 设置
            isSettingVolumeFromApp = true;
            
            // 兼容 Android 4.3：使用 setStreamMute() 方法（API 1+）
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
            
            reportDPBool(DP_MUTE, mute);
            lastReportedMute = mute;
            prefs.edit().putBoolean(KEY_MUTE, mute).apply();
            
            mainHandler.postDelayed(() -> isSettingVolumeFromApp = false, 500);
        }
    }
    
    /**
     * 方向键控制
     */
    private void handleDirection(Object value) {
        String direction = null;
        int enumIndex = -1;
        
        if (value instanceof Integer) {
            enumIndex = (Integer) value;
            switch (enumIndex) {
                case 0: direction = DIRECTION_UP; break;
                case 1: direction = DIRECTION_DOWN; break;
                case 2: direction = DIRECTION_LEFT; break;
                case 3: direction = DIRECTION_RIGHT; break;
            }
        } else if (value instanceof String) {
            direction = (String) value;
        }
        
        if (direction == null) {
            TLog.w(TAG, "无效的方向值: " + value);
            return;
        }
        
        TLog.i(TAG, "执行: 方向键=" + direction);
        
        String directionEmoji;
        int keyCode;
        switch (direction) {
            case DIRECTION_UP:
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                directionEmoji = "⬆️ 上";
                enumIndex = 0;
                break;
            case DIRECTION_DOWN:
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                directionEmoji = "⬇️ 下";
                enumIndex = 1;
                break;
            case DIRECTION_LEFT:
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                directionEmoji = "⬅️ 左";
                enumIndex = 2;
                break;
            case DIRECTION_RIGHT:
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                directionEmoji = "➡️ 右";
                enumIndex = 3;
                break;
            default:
                TLog.w(TAG, "未知方向: " + direction);
                return;
        }
        
        simulateKeyEvent(keyCode, "方向键 " + directionEmoji);
        reportDPEnum(DP_DIRECTION, enumIndex);
    }
    
    /**
     * 菜单键
     */
    private void handleMenu() {
        TLog.i(TAG, "执行: 菜单键");
        simulateKeyEvent(KeyEvent.KEYCODE_MENU, "📋 菜单");
//        reportDPBool(DP_MENU, false);
    }
    
    /**
     * 回到首页（无状态操作）
     */
    private void handleHome() {
        TLog.i(TAG, "执行: Home (回到首页)");
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(homeIntent);
            TLog.i(TAG, "已发送 Home Intent");
        } catch (Exception e) {
            TLog.e(TAG, "回到首页失败: " + e.getMessage());
            showToast("🏠 回到首页失败");
        }
        // 注意：上报 false 在 handleDPEvent 中统一处理
    }
    
    /**
     * 确认键
     */
    private void handleEnter() {
        TLog.i(TAG, "执行: 确认键");
        simulateKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, "✓ 确认");
        reportDPBool(DP_ENTER, true);
    }
    
    /**
     * 返回键（无状态操作）
     */
    private void handleBack() {
        TLog.i(TAG, "执行: 返回键");
        simulateKeyEvent(KeyEvent.KEYCODE_BACK, "⬅ 返回");
        // 注意：上报 false 在 handleDPEvent 中统一处理
    }
    
    /**
     * 模拟按键事件（异步执行，避免 ANR）
     * 兼容 Android 4.3：使用传统方式实现超时
     */
    private void simulateKeyEvent(int keyCode, String keyName) {
        TLog.i(TAG, "模拟按键: " + keyCode + " (" + keyName + ")");
        
        new Thread() {
            @Override
            public void run() {
                Process process = null;
                try {
                    String command = "input keyevent " + keyCode;
                    process = Runtime.getRuntime().exec(command);
                    
                    // 兼容 Android 4.3：使用传统方式实现超时
                    final Process finalProcess = process;
                    final long timeout = 500; // 500ms 超时
                    
                    // 启动超时检查线程
                    Thread timeoutThread = new Thread() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(timeout);
                                // 超时后检查进程是否还在运行
                                if (finalProcess != null) {
                                    try {
                                        finalProcess.exitValue(); // 如果已结束，会抛出异常
                                    } catch (IllegalThreadStateException e) {
                                        // 进程还在运行，销毁它
                                        finalProcess.destroy();
                                        TLog.w(TAG, "按键命令执行超时: " + command);
                                        showToast(keyName + " 执行超时（需要系统权限）");
                                    }
                                }
                            } catch (InterruptedException e) {
                                // 被中断，说明进程已正常结束
                            }
                        }
                    };
                    timeoutThread.setDaemon(true);
                    timeoutThread.start();
                    
                    // 等待进程结束（可能被超时线程中断）
                    int exitCode = process.waitFor();
                    
                    // 取消超时检查
                    timeoutThread.interrupt();
                    
                    if (exitCode == 0) {
                        TLog.i(TAG, "按键命令执行成功: " + command);
                    } else {
                        TLog.w(TAG, "按键命令执行失败，exitCode: " + exitCode);
                        showToast(keyName + " 执行失败（需要系统权限）");
                    }
                } catch (Exception e) {
                    TLog.e(TAG, "模拟按键失败: " + e.getMessage());
                    showToast(keyName + " 执行失败");
                } finally {
                    if (process != null) {
                        try {
                            process.exitValue(); // 检查是否已结束
                        } catch (IllegalThreadStateException e) {
                            // 进程还在运行，销毁它
                            process.destroy();
                        }
                    }
                }
            }
        }.start();
    }
    
    /**
     * 获取当前音量档位值
     */
    public int getCurrentVolumeLevel() {
        if (audioManager != null) {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        return 0;
    }
    
    /**
     * 上报 Boolean 类型 DP
     */
    private void reportDPBool(int dpId, boolean value) {
        if (ioTSDKManager == null) {
            TLog.w(TAG, "ioTSDKManager 为空，无法上报 DP");
            return;
        }
        
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        DPEvent event = new DPEvent(dpId, (byte) DPEvent.Type.PROP_BOOL, value, timestamp);
        
        TLog.i(TAG, "上报 DP: dpId=" + dpId + ", value=" + value);
        ioTSDKManager.sendDP(event);
    }
    
    /**
     * 上报 Value 类型 DP
     */
    private void reportDPValue(int dpId, int value) {
        if (ioTSDKManager == null) {
            TLog.w(TAG, "ioTSDKManager 为空，无法上报 DP");
            return;
        }
        
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        DPEvent event = new DPEvent(dpId, (byte) DPEvent.Type.PROP_VALUE, value, timestamp);
        
        TLog.i(TAG, "上报 DP: dpId=" + dpId + ", value=" + value);
        ioTSDKManager.sendDP(event);
    }
    
    /**
     * 上报 Enum 类型 DP
     */
    private void reportDPEnum(int dpId, int enumIndex) {
        if (ioTSDKManager == null) {
            TLog.w(TAG, "ioTSDKManager 为空，无法上报 DP");
            return;
        }
        
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        DPEvent event = new DPEvent(dpId, (byte) DPEvent.Type.PROP_ENUM, enumIndex, timestamp);
        
        TLog.i(TAG, "上报 DP: dpId=" + dpId + ", value=" + enumIndex);
        ioTSDKManager.sendDP(event);
    }
    
    /**
     * 清除本地状态记录（用于恢复出厂设置）
     */
    public void clearLocalState() {
        prefs.edit().clear().apply();
        lastReportedVolume = -1;
        lastReportedMute = false;
        TLog.i(TAG, "已清除本地 DP 状态记录");
    }
    
    /**
     * 销毁时调用
     */
    public void destroy() {
        stopVolumeListener();
        TLog.i(TAG, "TVControlManager 已销毁");
    }
}
