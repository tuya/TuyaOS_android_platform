package com.tuya.smartai.sample.event;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简单的本地事件总线，用于 Service 和 Activity 之间的事件通信
 * 线程安全，支持主线程回调
 */
public class IoTEventBus {
    private static final String TAG = "IoTEventBus";
    private static volatile IoTEventBus instance;
    
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private IoTEventBus() {}
    
    public static IoTEventBus getInstance() {
        if (instance == null) {
            synchronized (IoTEventBus.class) {
                if (instance == null) {
                    instance = new IoTEventBus();
                }
            }
        }
        return instance;
    }
    
    /**
     * 注册事件监听器
     */
    public void register(EventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 注销事件监听器
     */
    public void unregister(EventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
    
    /**
     * 发送事件（在主线程回调）
     */
    public void post(final IoTEvent event) {
        if (event == null) {
            return;
        }
        
        // 在主线程分发事件
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchEvent(event);
        } else {
            mainHandler.post(() -> dispatchEvent(event));
        }
    }
    
    /**
     * 分发事件给所有监听器
     */
    private void dispatchEvent(IoTEvent event) {
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 事件监听器接口
     */
    public interface EventListener {
        void onEvent(IoTEvent event);
    }
}
