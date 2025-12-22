package com.curvsurf.pointcloudrecorder_galaxyxr.render_passes

import android.content.Context
import android.opengl.GLES32
import androidx.xr.runtime.math.Matrix4
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.OpenGL
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.loadShaderSourceFromAssets
import java.nio.FloatBuffer

class PointCloudRenderPass {

    private val lock = Any()
    private var pointBuffer: FloatBuffer? = null
    private var pointCount: Int = 0
    private var dirtyBegin: Int = 0
    private var dirtyCount: Int = 0

    fun updatePoints(pointBuffer: FloatBuffer, pointCount: Int,
                     dirtyBegin: Int, dirtyCount: Int) {
        synchronized(lock) {
            this.pointBuffer = pointBuffer
            this.pointCount = pointCount
            this.dirtyBegin = dirtyBegin
            this.dirtyCount = dirtyCount
        }
    }

    private var program: Int = 0
    private var projectionMatrixLocation: Int = 0
    private var viewProjectionMatrixLocation: Int = 0
    private var vertexArray: Int = 0
    private var vertexBuffer: Int = 0

    fun init(context: Context) {
        val vertexShaderSource = context.loadShaderSourceFromAssets("shaders/point_cloud.vert")
        val fragmentShaderSource = context.loadShaderSourceFromAssets("shaders/point_cloud.frag")
        val vertexShader = OpenGL.createShader(GLES32.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = OpenGL.createShader(GLES32.GL_FRAGMENT_SHADER, fragmentShaderSource)
        program = OpenGL.createProgram(vertexShader, fragmentShader)
        GLES32.glDeleteShader(vertexShader)
        GLES32.glDeleteShader(fragmentShader)

        val attributes = OpenGL.queryAttributeLocations(program)
        val positionLocation = attributes["in_position"]!!
        val confidenceLocation = attributes["in_confidence"]!!

        val renderUniforms = OpenGL.queryUniformLocations(program)
        projectionMatrixLocation = renderUniforms["projection_matrix"]!!
        viewProjectionMatrixLocation = renderUniforms["view_projection_matrix"]!!

        vertexBuffer = OpenGL.createBuffer()
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vertexBuffer)
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, 4 * 4 * 100_000, null, GLES32.GL_STREAM_DRAW)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)

        vertexArray = OpenGL.createVertexArray()
        GLES32.glBindVertexArray(vertexArray)
        GLES32.glEnableVertexAttribArray(positionLocation)
        GLES32.glVertexAttribFormat(positionLocation, 3, GLES32.GL_FLOAT, false, 0)
        GLES32.glVertexAttribBinding(positionLocation, 0)
        GLES32.glEnableVertexAttribArray(confidenceLocation)
        GLES32.glVertexAttribFormat(confidenceLocation, 1, GLES32.GL_FLOAT, false, 12)
        GLES32.glVertexAttribBinding(confidenceLocation, 0)
        GLES32.glBindVertexBuffer(0, vertexBuffer, 0, 16)
        GLES32.glBindVertexArray(0)
    }

    fun update() {
        fun uploadSubData(srcBuffer: FloatBuffer, startIndex: Int, count: Int) {
            if (count <= 0) return

            val tempBuffer = srcBuffer.duplicate()
            tempBuffer.position(startIndex * 4)
            GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, startIndex * 16, count * 16, tempBuffer)
        }

        synchronized(lock) {
            val pointBuffer = pointBuffer ?: return
            if (dirtyCount == 0) return

            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vertexBuffer)
            val dirtyEnd = dirtyBegin + dirtyCount
            if (dirtyEnd > 100_000) {
                uploadSubData(pointBuffer, dirtyBegin, 100_000 - dirtyCount)
                uploadSubData(pointBuffer, 0, dirtyEnd - 100_000)
            } else {
                uploadSubData(pointBuffer, dirtyBegin, dirtyCount)
            }
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
            dirtyCount = 0
        }
    }

    var projectionMatrix: Matrix4 = Matrix4.Identity
    var viewProjectionMatrix: Matrix4 = Matrix4.Identity

    fun render() {
        GLES32.glEnable(GLES32.GL_DEPTH_TEST)
        GLES32.glUseProgram(program)
        GLES32.glBindVertexArray(vertexArray)
        GLES32.glUniformMatrix4fv(projectionMatrixLocation, 1, false, projectionMatrix.data, 0)
        GLES32.glUniformMatrix4fv(viewProjectionMatrixLocation, 1, false, viewProjectionMatrix.data, 0)
        GLES32.glDrawArrays(GLES32.GL_POINTS, 0, pointCount)
        GLES32.glBindVertexArray(0)
        GLES32.glUseProgram(0)
        GLES32.glDisable(GLES32.GL_DEPTH_TEST)
    }

    fun release() {
        if (program != 0) {
            GLES32.glDeleteProgram(program)
            program = 0
        }
        if (vertexArray != 0) {
            GLES32.glDeleteVertexArrays(1, intArrayOf(vertexArray), 0)
            vertexArray = 0
        }
        if (vertexBuffer != 0) {
            GLES32.glDeleteBuffers(1, intArrayOf(vertexBuffer), 0)
            vertexBuffer = 0
        }
    }
}