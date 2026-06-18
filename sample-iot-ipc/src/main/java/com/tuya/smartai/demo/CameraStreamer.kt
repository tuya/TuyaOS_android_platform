package com.tuya.smartai.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import com.tuya.smartai.iot_sdk.ipc.ThingIPC

/**
 * Headless camera -> H.264 -> P2P live stream feeder.
 *
 * No UI/Activity needed: Camera2 renders into the MediaCodec input Surface, the
 * encoder output is fed straight into [ThingIPC.appendVideoFrame]. Driven by the
 * IPC live-video events:
 *   - onLiveVideoStart -> [start]
 *   - onLiveVideoStop  -> [stop]
 *   - onRequestVideoKeyFrame -> [requestKeyFrame]
 *
 * Requires CAMERA permission and IPCConfig(enableVideo = true). Encoder params
 * here must match the IPCConfig video params (1280x720@15fps, 1Mbps).
 */
class CameraStreamer(private val context: Context) {

    companion object {
        private const val TAG = "P2PIPC-Cam"
        // Live preview over a P2P/relay link: keep the bitrate low so the
        // network keeps up with the encoder (otherwise frames queue and latency
        // grows without bound). 640x480 keyframes are ~1/3 the size of 720p.
        // 必须用摄像头支持的标准采集尺寸。480x360 会让 Camera2 createCaptureSession
        // 报 "Unsupported set of inputs/outputs" → 一帧都不出。640x480 通用支持。
        private const val WIDTH = 1280
        private const val HEIGHT = 720
        private const val FPS = 15
        private const val BITRATE = 1500 * 1000      // 2 Mbps (~250KB/s) — 720p 的够用甜点,relay 扛得住
        private const val GOP_SECONDS = 1            // key frame every 1s (shorter -> cheaper resync)
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        // Front camera (user-facing) for the photo frame; set false for back camera.
        private const val USE_FRONT_CAMERA = true
        // Extra CCW rotation (0/90/180/270) for the GL stage to correct this device's
        // front-sensor orientation. If the picture comes out rotated, try 90 -> 270 -> 180.
        private const val MIRROR_ROTATION_DEG = 90
        // Burn current time (yyyy-MM-dd HH:mm:ss) into the picture, bottom-left.
        private const val SHOW_TIME_OVERLAY = true
        // Audio: capture PCM16 mic, encode to G.711 µ-law before appending. The Tuya
        // live player decodes AUDIO_MAIN as G.711, so raw PCM = loud static; µ-law
        // matches. Native must declare audio_codec = TUYA_CODEC_AUDIO_G711U to pair.
        private const val ENABLE_AUDIO = true
        // ABR bitrate as % of BITRATE, indexed by SDK load level (0 best .. 5 worst).
        // Level 0 = full quality; higher levels shed bitrate to keep the link drained.
        private val LOAD_BITRATE_PCT = intArrayOf(100, 80, 60, 45, 33, 25)
    }

    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null       // encoder input (GL renders into this)
    private var glMirror: GlMirrorPipeline? = null   // camera -> GL flip-X -> encoder
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var spsPps: ByteArray? = null            // cached codec config to prepend to each IDR
    @Volatile private var running = false

    // append-cadence instrumentation: lets us verify frames are fed to the ring
    // buffer at a steady ~1000/FPS ms interval (even) vs. bursty/uneven.
    private var lastAppendMs = 0L
    private var appendCount = 0L
    @Volatile private var currentLoadLevel = -1   // ABR: last applied load level (-1 = unset)

    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    @Volatile private var audioRunning = false

    @Synchronized
    fun start() {
        if (running) {
            Log.i(TAG, "start ignored: already running")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CAMERA permission not granted; cannot stream")
            return
        }
        running = true
        lastAppendMs = 0L
        appendCount = 0L
        currentLoadLevel = -1
        bgThread = HandlerThread("p2p-cam").apply { start() }
        bgHandler = Handler(bgThread!!.looper)
        try {
            startEncoder()
            // Front camera is mirrored by nature; insert a GPU flip-X stage so the
            // viewer sees an un-mirrored picture. Back camera needs no flip.
            inputSurface?.let {
                glMirror = GlMirrorPipeline(
                    it, WIDTH, HEIGHT,
                    mirrorX = USE_FRONT_CAMERA,
                    rotationDegrees = MIRROR_ROTATION_DEG,
                    timeOverlay = SHOW_TIME_OVERLAY,
                ).also { gl -> gl.start() }
            }
            openCamera()
            startAudio()
            dumpStreamParams()
            Log.i(TAG, "camera streaming started ${WIDTH}x$HEIGHT@${FPS}fps")
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            stop()
        }
    }

    /** Print every A/V parameter handed to the encoder + SDK, for debugging the stream. */
    private fun dumpStreamParams() {
        Log.i(TAG, "================ A/V STREAM PARAMS ================")
        Log.i(TAG, "[VIDEO] codec=H.264/AVC  resolution=${WIDTH}x${HEIGHT}  fps=$FPS")
        Log.i(TAG, "[VIDEO] bitrate=${BITRATE} bps (${BITRATE / 1000} kbps)  rateControl=CBR")
        Log.i(TAG, "[VIDEO] gop=${GOP_SECONDS}s (I-frame interval)  profile=default(Baseline)")
        Log.i(TAG, "[VIDEO] front=$USE_FRONT_CAMERA  mirrorX=$USE_FRONT_CAMERA  rotationDeg=$MIRROR_ROTATION_DEG  timeOverlay=$SHOW_TIME_OVERLAY")
        Log.i(TAG, "[VIDEO] pts=encoder-clock(us)  timestamp=wall-clock(ms)  append=ring_buffer_with_timestamp")
        if (ENABLE_AUDIO) {
            Log.i(TAG, "[AUDIO] enabled  capture=PCM16/8000Hz/mono  encode=G.711 µ-law (G711U)")
            Log.i(TAG, "[AUDIO] frame=320 samples (~40ms)  pcm=640B -> ulaw=320B")
            Log.i(TAG, "[AUDIO] NOTE: native media_info MUST declare audio_codec=TUYA_CODEC_AUDIO_G711U(105) to match")
        } else {
            Log.i(TAG, "[AUDIO] disabled")
        }
        Log.i(TAG, "==================================================")
    }

    /** PCM 8kHz/16bit/mono mic capture -> appendAudioFrame. ~40ms (320 samples) per frame. */
    private fun startAudio() {
        if (!ENABLE_AUDIO) {
            Log.i(TAG, "audio disabled (raw PCM -> static); streaming video only")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted; streaming video without audio")
            return
        }
        val sampleRate = 8000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 4096))
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            record.release()
            return
        }
        audioRecord = record
        audioRunning = true
        record.startRecording()
        audioThread = Thread({
            val pcm = ByteArray(640)          // 320 samples * 2 bytes = 40ms @ 8kHz -> ~25 fps
            val ulaw = ByteArray(320)         // G.711 µ-law: 1 byte/sample
            while (audioRunning) {
                val n = record.read(pcm, 0, pcm.size)
                if (n > 0) {
                    val samples = n / 2
                    pcm16ToMuLaw(pcm, samples, ulaw)
                    val data = if (samples == ulaw.size) ulaw else ulaw.copyOf(samples)
                    ThingIPC.getInstance().appendAudioFrame(data, System.nanoTime() / 1000)
                }
            }
        }, "p2p-mic").also { it.start() }
    }

    /**
     * PCM 16-bit little-endian -> G.711 µ-law. Standard (Sun reference) encoder:
     * 16-bit signed sample -> 8-bit µ-law byte. [pcm] holds [sampleCount] samples
     * (2 bytes each), output written to [out] (1 byte each).
     */
    private fun pcm16ToMuLaw(pcm: ByteArray, sampleCount: Int, out: ByteArray) {
        val BIAS = 0x84
        val CLIP = 32635
        for (i in 0 until sampleCount) {
            // little-endian signed 16-bit
            var sample = (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)
            val sign = (sample shr 8) and 0x80
            if (sign != 0) sample = -sample
            if (sample > CLIP) sample = CLIP
            sample += BIAS
            var exponent = 7
            var mask = 0x4000
            while (exponent > 0 && (sample and mask) == 0) {
                exponent--
                mask = mask shr 1
            }
            val mantissa = (sample shr (exponent + 3)) and 0x0F
            out[i] = ((sign or (exponent shl 4) or mantissa).inv() and 0xFF).toByte()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        audioRunning = false
        runCatching { audioThread?.join(500) }
        audioThread = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { session?.close() }
        session = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        // Tear down GL before the encoder so it stops drawing into the input surface.
        runCatching { glMirror?.release() }
        glMirror = null
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        spsPps = null
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
        Log.i(TAG, "camera streaming stopped")
    }

    /** Ask the encoder to emit an IDR right now (SDK requests it for new viewers). */
    fun requestKeyFrame() {
        runCatching {
            encoder?.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            })
        }.onFailure { Log.e(TAG, "requestKeyFrame failed", it) }
    }

    /**
     * ABR: the SDK reported a new network load level (0 best .. 5 worst). Lower
     * the encoder bitrate as the link congests so frames don't back up and skip;
     * raise it back as the link recovers. This is the closed loop that lets the
     * stream stay smooth over a jittery relay (what real IPCs do).
     */
    fun onLoadAdjust(level: Int) {
        val lv = level.coerceIn(0, LOAD_BITRATE_PCT.size - 1)
        if (lv == currentLoadLevel) return
        currentLoadLevel = lv
        val target = (BITRATE.toLong() * LOAD_BITRATE_PCT[lv] / 100).toInt()
        runCatching {
            encoder?.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, target)
            })
            Log.i(TAG, "ABR: load level=$lv -> bitrate=${target / 1000} kbps (base ${BITRATE / 1000})")
        }.onFailure { Log.e(TAG, "onLoadAdjust setParameters failed", it) }
    }

    private fun startEncoder() {
        val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, GOP_SECONDS)
            // CBR keeps the per-frame size steady (no big VBR keyframe spikes)
            // which streams more smoothly over a bandwidth-limited relay link.
            setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            // realtime priority — favour low latency over compression quality.
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(c: MediaCodec, index: Int) { /* surface input */ }

            override fun onOutputBufferAvailable(c: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                try {
                    val buf = c.getOutputBuffer(index)
                    if (buf == null || info.size <= 0) return
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val data = ByteArray(info.size)
                    buf.get(data)

                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // SPS/PPS — cache, don't send on its own
                        spsPps = data
                        return
                    }
                    val isKey = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    // Prepend SPS/PPS to every IDR so a viewer joining mid-stream can decode.
                    val frame = if (isKey && spsPps != null) spsPps!! + data else data
                    // pts = encoder clock (µs); timestamp = wall clock (ms) for playback time / sync
                    val now = System.currentTimeMillis()
                    val dt = if (lastAppendMs == 0L) 0 else (now - lastAppendMs)
                    lastAppendMs = now
                    // 塞帧节奏日志: +Δms 应稳定在 ~67ms(15fps)。忽大忽小=不均匀。
                    Log.i(TAG, "APPEND #${appendCount++} +${dt}ms ${if (isKey) "KEY" else "P  "} size=${frame.size}")
                    ThingIPC.getInstance().appendVideoFrame(
                        frame, isKey, info.presentationTimeUs, now)
                } catch (e: Exception) {
                    Log.e(TAG, "encode output error", e)
                } finally {
                    runCatching { c.releaseOutputBuffer(index, false) }
                }
            }

            override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "encoder error", e)
            }

            override fun onOutputFormatChanged(c: MediaCodec, format: MediaFormat) { /* no-op */ }
        }, bgHandler)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        encoder = codec
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = pickBackCamera(cm)
        Log.i(TAG, "opening camera id=$cameraId")
        cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession(device)
            }
            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "camera disconnected")
                device.close()
                cameraDevice = null
            }
            override fun onError(device: CameraDevice, error: Int) {
                Log.e(TAG, "camera open error=$error")
                device.close()
                cameraDevice = null
            }
        }, bgHandler)
    }

    private fun pickBackCamera(cm: CameraManager): String {
        val want = if (USE_FRONT_CAMERA) CameraCharacteristics.LENS_FACING_FRONT
                   else CameraCharacteristics.LENS_FACING_BACK
        for (id in cm.cameraIdList) {
            val facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (facing == want) return id
        }
        return cm.cameraIdList.firstOrNull() ?: "0"
    }

    private fun createSession(device: CameraDevice) {
        // Camera renders into the GL pipeline's SurfaceTexture (flipped to the
        // encoder). Falls back to the encoder surface directly if GL is absent.
        val surface = glMirror?.inputSurface ?: inputSurface ?: return
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(surface)
            // Cap the sensor to FPS so it doesn't deliver 30fps (which the encoder
            // would happily encode, doubling the packet rate and stalling the relay).
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(FPS, FPS))
        }
        @Suppress("DEPRECATION")
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                session = s
                runCatching {
                    s.setRepeatingRequest(request.build(), null, bgHandler)
                }.onFailure { Log.e(TAG, "setRepeatingRequest failed", it) }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) {
                Log.e(TAG, "capture session config failed")
            }
        }, bgHandler)
    }
}
