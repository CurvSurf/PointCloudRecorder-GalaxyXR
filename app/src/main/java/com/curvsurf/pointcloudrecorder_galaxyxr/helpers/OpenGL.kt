package com.curvsurf.pointcloudrecorder_galaxyxr.helpers


import android.content.Context
import android.opengl.GLES32
import java.nio.ByteBuffer
import kotlin.apply
import kotlin.collections.set
import kotlin.io.bufferedReader
import kotlin.io.readText
import kotlin.io.use
import kotlin.let
import kotlin.ranges.until

/**
 * OpenGL API-related utility helper class.
 */
object OpenGL {

    private val intArray = IntArray(3)

    fun getIntegerv(pname: Int): Int {
        GLES32.glGetIntegerv(pname, intArray, 0)
        return intArray[0]
    }

    fun getIntegeri_v(target: Int, index: Int): Int {
        GLES32.glGetIntegeri_v(target, index, intArray, 0)
        return intArray[0]
    }

    fun getUniformiv(program: Int, location: Int): Int {
        GLES32.glGetUniformiv(program, location, intArray, 0)
        return intArray[0]
    }

    fun createShader(shaderType: Int, shaderSource: String): Int {

        val shader = GLES32.glCreateShader(shaderType)
        GLES32.glShaderSource(shader, shaderSource)
        GLES32.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] == 0) {
            val log = GLES32.glGetShaderInfoLog(shader)
            GLES32.glDeleteShader(shader)
            throw kotlin.RuntimeException("Shader compilation failed: $log")
        }

        return shader
    }

    fun createProgram(vararg shaders: Int): Int {

        val program = GLES32.glCreateProgram()
        for (shader in shaders) {
            GLES32.glAttachShader(program, shader)
        }
        val status = IntArray(1)

        GLES32.glLinkProgram(program)
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES32.glGetProgramInfoLog(program)
            GLES32.glDeleteProgram(program)
            throw kotlin.RuntimeException("Program link failed: $log")
        }

        GLES32.glValidateProgram(program)
        GLES32.glGetProgramiv(program, GLES32.GL_VALIDATE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES32.glGetProgramInfoLog(program)
            GLES32.glDeleteProgram(program)
            throw kotlin.RuntimeException("Program validation failed: $log")
        }

        return program
    }

    fun queryAttributeLocations(program: Int): Map<String, Int> {
        val attributeCount = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_ATTRIBUTES, this, 0) }[0]
        val maxNameLength = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, this, 0) }[0]
        val nameBuffer = ByteArray(maxNameLength)

        val attributeLocations = mutableMapOf<String, Int>()
        for (index in 0 until attributeCount) {
            GLES32.glGetActiveAttrib(program, index, maxNameLength, intArray, 0, intArray, 1, intArray, 2, nameBuffer, 0)
            val name = String(nameBuffer, 0, intArray[0])
            val location = GLES32.glGetAttribLocation(program, name)
            attributeLocations[name] = location
        }
        return attributeLocations
    }

    fun queryUniformLocations(program: Int): Map<String, Int> {
        val uniformCount = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORMS, this, 0) }[0]
        val maxNameLength = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORM_MAX_LENGTH, this, 0) }[0]
        val nameBuffer = ByteArray(maxNameLength)

        val uniformLocations = mutableMapOf<String, Int>()
        for (index in 0 until uniformCount) {
            GLES32.glGetActiveUniform(program, index, maxNameLength, intArray, 0, intArray, 1, intArray, 2, nameBuffer, 0)
            val name = String(nameBuffer, 0, intArray[0])
            val location = GLES32.glGetUniformLocation(program, name)
            uniformLocations[name] = location
        }
        return uniformLocations
    }

    fun queryUniformBlockBindings(program: Int): Map<String, Int> {
        val uniformBlockCount = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORM_BLOCKS, this, 0) }[0]
        val maxNameLength = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH, this, 0) }[0]
        val nameBuffer = ByteArray(maxNameLength)

        val uniformBlockBindings = mutableMapOf<String, Int>()
        for (index in 0 until uniformBlockCount) {
            GLES32.glGetActiveUniformBlockName(program, index, maxNameLength, intArray, 0, nameBuffer, 0)
            val name = String(nameBuffer, 0, intArray[0])
            val binding = intArray.apply { GLES32.glGetActiveUniformBlockiv(program, index, GLES32.GL_UNIFORM_BLOCK_BINDING, intArray, 0) }[0]
            uniformBlockBindings[name] = binding
        }
        return uniformBlockBindings
    }

    fun queryUniformBlockUniformOffsets(program: Int, index: Int): Map<String, Int> {
        val uniformCount = intArray.apply { GLES32.glGetActiveUniformBlockiv(program, index, GLES32.GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS, this, 0) }[0]

        if (uniformCount == 0) return emptyMap()

        val uniformIndices = IntArray(uniformCount).apply { GLES32.glGetActiveUniformBlockiv(program, index, GLES32.GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES, this, 0) }
        val uniformOffsets = IntArray(uniformCount).apply { GLES32.glGetActiveUniformsiv(program, uniformCount, uniformIndices, 0, GLES32.GL_UNIFORM_OFFSET, this, 0) }

        val maxNameLength = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORM_MAX_LENGTH, this, 0) }[0]

        val nameBuffer = ByteArray(maxNameLength)
        val result = mutableMapOf<String, Int>()

        for (i in 0 until uniformCount) {
            val uniformIndex = uniformIndices[i]
            val nameLength = intArray.apply { GLES32.glGetActiveUniform(program, uniformIndex, maxNameLength, this, 0, this, 1, this, 2, nameBuffer, 0) }[0]
            val name = String(nameBuffer, 0, nameLength)

            val offset = uniformOffsets[i]
            result[name] = offset
        }

        return result
    }

    data class UniformBlockQuery(
        val binding: Int,
        val size: Int,
        val uniformOffsets: Map<String, Int>
    )

    fun queryUniformBlocks(program: Int): Map<String, UniformBlockQuery> {
        val uniformBlockCount = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORM_BLOCKS, this, 0) }[0]
        val maxNameLength = intArray.apply { GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH, this, 0) }[0]
        val nameBuffer = ByteArray(maxNameLength)

        val queries = mutableMapOf<String, UniformBlockQuery>()
        for (index in 0 until uniformBlockCount) {
            GLES32.glGetActiveUniformBlockName(program, index, maxNameLength, intArray, 0, nameBuffer, 0)
            val name = String(nameBuffer, 0, intArray[0])
            val binding = intArray.apply { GLES32.glGetActiveUniformBlockiv(program, index, GLES32.GL_UNIFORM_BLOCK_BINDING, intArray, 0) }[0]
            val uniformOffsets = queryUniformBlockUniformOffsets(program, index)
            val size = intArray.apply { GLES32.glGetActiveUniformBlockiv(program, index, GLES32.GL_UNIFORM_BLOCK_DATA_SIZE, this, 0) }[0]
            val query = UniformBlockQuery(binding, size, uniformOffsets)
            queries[name] = query
        }

        return queries
    }

    fun queryShaderStorageBlockBindingIndex(program: Int, name: String): Int? {
        val resourceIndex = GLES32.glGetProgramResourceIndex(
            program,
            GLES32.GL_SHADER_STORAGE_BLOCK,
            name)

        if (resourceIndex == GLES32.GL_INVALID_INDEX) return null

        val props = intArrayOf(GLES32.GL_BUFFER_BINDING)
        return intArray.apply {
            GLES32.glGetProgramResourceiv(
                program,
                GLES32.GL_SHADER_STORAGE_BLOCK,
                resourceIndex,
                1, props, 0,
                1, this, 0,
                this, 1
            )
        }[1]
    }

    data class WorkgroupSize(val x: Int, val y: Int, val z: Int) {
        fun dispatchCompute(totalWorkX: Int, totalWorkY: Int, totalWorkZ: Int = 1) {
            val groupsX = (totalWorkX + x - 1) / x
            val groupsY = (totalWorkY + y - 1) / y
            val groupsZ = (totalWorkZ + z - 1) / z
            GLES32.glDispatchCompute(groupsX, groupsY, groupsZ)
        }
    }

    fun queryLocalWorkgroupSize(computeProgram: Int): WorkgroupSize {
        GLES32.glGetProgramiv(computeProgram, GLES32.GL_COMPUTE_WORK_GROUP_SIZE, intArray, 0)
        return WorkgroupSize(x = intArray[0], y = intArray[1], z = intArray[2])
    }

    fun createBuffer(): Int {
        return intArray.apply { GLES32.glGenBuffers(1, this, 0) }[0]
    }

    fun createBuffer(target: Int, size: Int, data: ByteBuffer? = null, usage: Int): Int {
        val buffer = createBuffer()
        GLES32.glBindBuffer(target, buffer)
        GLES32.glBufferData(target, size, data, usage)
        GLES32.glBindBuffer(target, 0)
        return buffer
    }

    fun createVertexArray(): Int {
        return intArray.apply { GLES32.glGenVertexArrays(1, this, 0) }[0]
    }

    fun createTexture(target: Int, minFilter: Int, magFilter: Int, wrapS: Int, wrapT: Int, wrapR: Int? = null): Int {

        val texture = intArray.apply { GLES32.glGenTextures(1, this, 0) }[0]
        GLES32.glBindTexture(target, texture)
        GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MIN_FILTER, minFilter)
        GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MAG_FILTER, magFilter)
        GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_WRAP_S, wrapS)
        GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_WRAP_T, wrapT)
        wrapR?.let { GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_WRAP_R, it) }
        return texture
    }

    fun createTexture(target: Int, filter: Int, wrap: Int, wrapR: Int? = null): Int {
        return createTexture(target, filter, filter, wrap, wrap, wrapR)
    }
}

fun Context.loadShaderSourceFromAssets(fileName: String): String {
    return assets.open(fileName).bufferedReader().use { it.readText() }
}