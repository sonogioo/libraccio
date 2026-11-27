package it.libraccio.app.room3d

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

class LibraryGLView(context: Context) : GLSurfaceView(context) {

    interface Listener {
        fun onBookTapped(id: String)
        fun onPulledOut(id: String, coverRect: RectF)
        fun onReturned()
    }

    var listener: Listener? = null

    val renderer: LibraryRenderer = LibraryRenderer(object : LibraryRenderer.Callbacks {
        override fun onBookTapped(id: String) { post { listener?.onBookTapped(id) } }
        override fun onPulledOut(id: String, coverRect: RectF) { post { listener?.onPulledOut(id, coverRect) } }
        override fun onReturned() { post { listener?.onReturned() } }
        override fun requestRender() { this@LibraryGLView.requestRender() }
    })

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.zoom(detector.scaleFactor)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { renderer.tap(e.x, e.y); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { renderer.doubleTap(e.x, e.y); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (e2.pointerCount >= 2) renderer.pan(dx, dy) else renderer.orbit(dx, dy)
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
}

/** Chiede 4x MSAA; se non disponibile ripiega su una configurazione semplice. */
private class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
        val attempts = listOf(
            intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE),
            intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE),
            intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE),
        )
        for (attrs in attempts) {
            val num = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            if (egl.eglChooseConfig(display, attrs, configs, 1, num) && num[0] > 0 && configs[0] != null) {
                return configs[0]!!
            }
        }
        throw IllegalStateException("Nessuna configurazione EGL compatibile")
    }
}
