package com.curvsurf.pointcloudrecorder_galaxyxr.render_passes

import android.content.Context
import android.opengl.GLES32
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.OpenGL
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.loadShaderSourceFromAssets
import java.nio.Buffer

class ConfidenceMapRenderPass {

    private val lock = Any()
    private var confidenceBuffer: Buffer? = null
    private var confidenceWidth: Int = 0
    private var confidenceHeight: Int = 0
    private var isDimensionSet = false

    fun updateConfidenceMap(buffer: Buffer?,
                            width: Int, height: Int) {
        synchronized(lock) {
            this.confidenceBuffer = buffer
            if (!isDimensionSet) {
                this.confidenceWidth = width
                this.confidenceHeight = height
                isDimensionSet = true
            }
        }
    }

    private var program: Int = 0
    private var texture: Int = 0
    private var textureStorageAllocated = false

    fun init(context: Context) {
        val vertexShaderSource = context.loadShaderSourceFromAssets("shaders/confidence_texture.vert")
        val fragmentShaderSource = context.loadShaderSourceFromAssets("shaders/confidence_texture.frag")
        val vertexShader = OpenGL.createShader(GLES32.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = OpenGL.createShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderSource)
        program = OpenGL.createProgram(vertexShader, fragmentShader)
        GLES32.glDeleteShader(vertexShader)
        GLES32.glDeleteShader(fragmentShader)

        val uniformLocations = OpenGL.queryUniformLocations(program)
        val confidenceTextureLocation = uniformLocations["confidence_texture"]!!
        GLES32.glProgramUniform1i(program, confidenceTextureLocation, 0)

        texture = OpenGL.createTexture(
            target = GLES32.GL_TEXTURE_2D,
            filter = GLES32.GL_NEAREST,
            wrap = GLES32.GL_CLAMP_TO_EDGE)
    }

    fun update() {
        synchronized(lock) {
            val buffer = confidenceBuffer ?: return
            if (confidenceWidth <= 0 || confidenceHeight <= 0) return

            if (textureStorageAllocated) {
                copyTexture(buffer, confidenceWidth, confidenceHeight)
            } else {
                createAndCopyTexture(buffer, confidenceWidth, confidenceHeight)
                textureStorageAllocated = true
            }

            confidenceBuffer = null
        }
    }

    fun render() {
        GLES32.glUseProgram(program)
        GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture)
        GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, 6)
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)
        GLES32.glUseProgram(0)
    }

    fun release() {
        if (program != 0) {
            GLES32.glDeleteProgram(program)
            program = 0
        }
        if (texture != 0) {
            GLES32.glDeleteTextures(1, intArrayOf(texture), 0)
            texture = 0
        }
    }

    private fun createAndCopyTexture(buffer: Buffer, width: Int, height: Int) {
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture)
        GLES32.glTexImage2D(
            GLES32.GL_TEXTURE_2D, 0,
            GLES32.GL_R8, width, height, 0,
            GLES32.GL_RED, GLES32.GL_UNSIGNED_BYTE, buffer.rewind()
        )
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)
    }

    private fun copyTexture(buffer: Buffer, width: Int, height: Int) {
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture)
        GLES32.glTexSubImage2D(
            GLES32.GL_TEXTURE_2D, 0,
            0, 0, width, height,
            GLES32.GL_RED, GLES32.GL_UNSIGNED_BYTE, buffer.rewind()
        )
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, 0)
    }
}