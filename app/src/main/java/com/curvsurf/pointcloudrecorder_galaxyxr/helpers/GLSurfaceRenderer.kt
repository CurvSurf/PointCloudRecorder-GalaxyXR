package com.curvsurf.pointcloudrecorder_galaxyxr.helpers

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val EGL_OPENGL_ES3_BIT = 0x0040

/**
 * It’s a utility class that creates an EGL context from the View’s Surface
 *   and then invokes the renderer interface’s methods on the OpenGL thread.
 */
class GLSurfaceRenderer(private val surface: Surface,
                        private val renderer: Renderer): Thread() {

    interface Renderer {
        fun init()
        fun resize(width: Int, height: Int)
        fun update()
        fun render()
        fun release()

        companion object {
            fun merge(vararg renderers: Renderer): Renderer = object: Renderer {
                override fun init() {
                    for (it in renderers) { it.init() }
                }

                override fun resize(width: Int, height: Int) {
                    for (it in renderers) { it.resize(width, height) }
                }

                override fun update() {
                    for (it in renderers) { it.update() }
                }

                override fun render() {
                    for (it in renderers) { it.render() }
                }

                override fun release() {
                    for (it in renderers) { it.release() }
                }
            }
        }
    }

    private lateinit var eglDisplay: EGLDisplay
    private lateinit var eglContext: EGLContext
    private lateinit var eglSurface: EGLSurface

    private fun initializeEGLContext() {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay,
            version, 0, version, 1)

        val configAttributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay,
            configAttributes, 0,
            configs, 0, 1, numConfigs, 0)
        val eglConfig = configs[0]

        val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig,
            EGL14.EGL_NO_CONTEXT, contextAttributes, 0)

        val surfaceAttributes = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay,
            eglConfig, surface,
            surfaceAttributes, 0)

        this.eglDisplay = eglDisplay
        this.eglContext = eglContext
        this.eglSurface = eglSurface
    }

    private fun releaseEGLContext() {
        EGL14.eglMakeCurrent(eglDisplay,
            EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        surface.release()
    }

    fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
    }

    override fun run() {
        initializeEGLContext()
        EGL14.eglMakeCurrent(eglDisplay,
            eglSurface, eglSurface, eglContext)
        renderer.init()

        while (!isInterrupted) {
            renderer.update()
            renderer.render()
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            try { sleep(16) }
            catch (e: InterruptedException) { break }
        }

        renderer.release()
        releaseEGLContext()
    }
}

fun createSurfaceView(context: Context, renderer: GLSurfaceRenderer.Renderer): SurfaceView {
    val glSurfaceView = SurfaceView(context)
    var surfaceRenderer: GLSurfaceRenderer? = null
    glSurfaceView.holder.addCallback(object: SurfaceHolder.Callback {
        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            surfaceRenderer?.resize(width, height)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceRenderer = GLSurfaceRenderer(holder.surface, renderer)
            surfaceRenderer?.start()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceRenderer?.interrupt()
            surfaceRenderer = null
        }
    })
    return glSurfaceView
}

@Composable
fun OpenGLSurfaceView(
    modifier: Modifier = Modifier,
    context: Context,
    renderer: GLSurfaceRenderer.Renderer) {
    AndroidView(
        modifier = modifier,
        factory = {
            var surfaceRenderer: GLSurfaceRenderer? = null
            SurfaceView(context).apply {
                background = null
                holder.setFormat(PixelFormat.TRANSLUCENT)
                holder.addCallback(object: SurfaceHolder.Callback {

                    override fun surfaceCreated(holder: SurfaceHolder) {
                        surfaceRenderer = GLSurfaceRenderer(holder.surface, renderer)
                        surfaceRenderer?.start()
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        surfaceRenderer?.resize(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        surfaceRenderer?.interrupt()
                        surfaceRenderer = null
                    }
                })
            }
        }
    )
}