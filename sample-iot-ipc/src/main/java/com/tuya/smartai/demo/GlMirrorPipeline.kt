package com.tuya.smartai.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inserts a GPU stage between the camera and the encoder so the picture can be
 * horizontally mirrored (front camera "un-mirror") at ~zero cost:
 *
 *   Camera2 -> [inputSurface] SurfaceTexture(OES) -> GL flip-X -> [encoderSurface]
 *
 * The camera renders into [inputSurface]; every frame is drawn onto the encoder's
 * EGL window surface with the X axis flipped, preserving the SurfaceTexture
 * timestamp as the encoder pts. All GL work runs on a dedicated thread.
 */
class GlMirrorPipeline(
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val mirrorX: Boolean = true,
    /** Extra CCW rotation in degrees (0/90/180/270) to correct sensor orientation. */
    private val rotationDegrees: Int = 0,
    /** Burn the current wall-clock time into the picture (bottom-left). */
    private val timeOverlay: Boolean = true,
) {
    companion object {
        private const val TAG = "P2PIPC-GL"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        // Plain full-screen triangle strip. x,y = clip-space position; s,t = texcoord.
        // Mirror + rotation are applied to the texcoords via a matrix (see initGl),
        // NOT by touching positions, so orientation stays well-defined.
        private val QUAD = floatArrayOf(
            //  x,    y,   s,   t
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )

        private const val VERTEX_SHADER =
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vTexCoord = (uSTMatrix * aTexCoord).xy;\n" +
            "}\n"

        private const val FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
            "}\n"

        // Overlay (time watermark): plain 2D textured quad, no transform.
        private const val OVERLAY_VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vTexCoord = aTexCoord;\n" +
            "}\n"

        private const val OVERLAY_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTexCoord);\n" +
            "}\n"

        private const val OVERLAY_W = 512        // watermark bitmap size (px); 8:1 so the
        private const val OVERLAY_H = 64         // full "yyyy-MM-dd HH:mm:ss" fits one line
        private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    private val thread = HandlerThread("p2p-gl").apply { start() }
    private val handler = Handler(thread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var texId = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uSTMatrix = 0

    private lateinit var vertexBuf: FloatBuffer
    private val stMatrix = FloatArray(16)       // SurfaceTexture transform (per frame)
    private val adjustMatrix = FloatArray(16)   // constant mirror + rotation (texcoord space)
    private val texMatrix = FloatArray(16)      // adjustMatrix * stMatrix, fed to shader

    // Time-watermark overlay (2D textured quad blended over the camera frame).
    private var overlayProgram = 0
    private var overlayTexId = 0
    private var overlayPos = 0
    private var overlayTex = 0
    private lateinit var overlayVertexBuf: FloatBuffer
    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null
    private var overlayPaint: Paint? = null
    private var lastOverlaySecond = -1L

    private var surfaceTexture: SurfaceTexture? = null
    /** Surface the camera renders into. Valid after [start]. */
    @Volatile var inputSurface: Surface? = null
        private set

    /** Set up EGL + GL on the GL thread and create the camera-facing surface. */
    fun start() {
        val latch = java.util.concurrent.CountDownLatch(1)
        handler.post {
            try {
                initEgl()
                initGl()
                val st = SurfaceTexture(texId).apply {
                    setDefaultBufferSize(width, height)
                    setOnFrameAvailableListener({ requestDraw() }, handler)
                }
                surfaceTexture = st
                inputSurface = Surface(st)
            } catch (e: Exception) {
                Log.e(TAG, "GL init failed", e)
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    private fun requestDraw() {
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
            // texMatrix = adjust(mirror+rotate) applied AFTER the SurfaceTexture transform
            Matrix.multiplyMM(texMatrix, 0, adjustMatrix, 0, stMatrix, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            vertexBuf.position(0)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, vertexBuf)
            GLES20.glEnableVertexAttribArray(aPosition)
            vertexBuf.position(2)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertexBuf)
            GLES20.glEnableVertexAttribArray(aTexCoord)

            GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, texMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            if (timeOverlay) drawTimeOverlay()

            // Preserve the camera frame timestamp as the encoder pts (ns).
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, st.timestamp)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        } catch (e: Exception) {
            Log.e(TAG, "GL draw failed", e)
        }
    }

    /** Re-render the time bitmap once per second, then blend it over the frame. */
    private fun drawTimeOverlay() {
        val now = System.currentTimeMillis()
        val sec = now / 1000
        if (sec != lastOverlaySecond) {
            lastOverlaySecond = sec
            val bmp = overlayBitmap ?: return
            val cv = overlayCanvas ?: return
            val paint = overlayPaint ?: return
            cv.drawColor(0, PorterDuff.Mode.CLEAR)            // transparent background
            cv.drawText(TIME_FMT.format(Date(now)), 8f, OVERLAY_H * 0.72f, paint)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(overlayProgram)
        overlayVertexBuf.position(0)
        GLES20.glVertexAttribPointer(overlayPos, 2, GLES20.GL_FLOAT, false, 16, overlayVertexBuf)
        GLES20.glEnableVertexAttribArray(overlayPos)
        overlayVertexBuf.position(2)
        GLES20.glVertexAttribPointer(overlayTex, 2, GLES20.GL_FLOAT, false, 16, overlayVertexBuf)
        GLES20.glEnableVertexAttribArray(overlayTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)
        val cfgAttr = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, cfgAttr, 0, cfgs, 0, 1, n, 0)
        val ctxAttr = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, cfgs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, cfgs[0], encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun initGl() {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix")

        vertexBuf = ByteBuffer.allocateDirect(QUAD.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(QUAD); position(0)
            }

        // Mirror + rotation in texcoord space, about the (0.5,0.5) centre so the
        // image stays framed. Applied AFTER the SurfaceTexture transform each frame.
        Matrix.setIdentityM(adjustMatrix, 0)
        Matrix.translateM(adjustMatrix, 0, 0.5f, 0.5f, 0f)
        if (rotationDegrees != 0) {
            Matrix.rotateM(adjustMatrix, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        }
        if (mirrorX) {
            Matrix.scaleM(adjustMatrix, 0, -1f, 1f, 1f)
        }
        Matrix.translateM(adjustMatrix, 0, -0.5f, -0.5f, 0f)

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        texId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        if (timeOverlay) initOverlay()
    }

    private fun initOverlay() {
        overlayProgram = buildProgram(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER)
        overlayPos = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        overlayTex = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")

        // Bottom-left placement, small margin. Clip space (-1..1). Width ~45% of
        // frame, height keeps the bitmap's 512:96 aspect. Texcoord t=0 -> screen
        // top of the quad so the text reads upright (independent of camera flip).
        val left = -0.94f
        val bottom = -0.92f
        val w = 0.6f                                  // clip-space width (~30% of frame)
        // Keep the bitmap's aspect: convert clip width -> px -> height px -> clip height.
        val wPix = w / 2f * width
        val hPix = wPix * OVERLAY_H / OVERLAY_W
        val h = hPix / height * 2f
        val right = left + w
        val top = bottom + h
        val verts = floatArrayOf(
            //  x,     y,      s,  t
            left, bottom, 0f, 1f,
            right, bottom, 1f, 1f,
            left, top, 0f, 0f,
            right, top, 1f, 0f,
        )
        overlayVertexBuf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(verts); position(0) }

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        overlayTexId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        overlayBitmap = Bitmap.createBitmap(OVERLAY_W, OVERLAY_H, Bitmap.Config.ARGB_8888)
        overlayCanvas = Canvas(overlayBitmap!!)
        overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = OVERLAY_H * 0.7f
            setShadowLayer(4f, 2f, 2f, Color.BLACK)   // dark shadow for legibility on any background
            // Shrink the font until the full "yyyy-MM-dd HH:mm:ss" fits the bitmap width.
            val maxW = OVERLAY_W - 12f
            val tw = measureText("0000-00-00 00:00:00")
            if (tw > maxW) textSize = textSize * maxW / tw
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = loadShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("program link failed: $log")
        }
        return p
    }

    private fun loadShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("shader compile failed: $log")
        }
        return s
    }

    /** Tear down on the GL thread, then stop the thread. */
    fun release() {
        handler.post {
            runCatching { inputSurface?.release() }
            inputSurface = null
            runCatching { surfaceTexture?.release() }
            surfaceTexture = null
            runCatching { overlayBitmap?.recycle() }
            overlayBitmap = null
            overlayCanvas = null
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
                runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
                runCatching { EGL14.eglReleaseThread() }
                runCatching { EGL14.eglTerminate(eglDisplay) }
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        thread.quitSafely()
    }
}
