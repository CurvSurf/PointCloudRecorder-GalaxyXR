package com.curvsurf.pointcloudrecorder_galaxyxr.helpers

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

enum class PatternType {
    POISSON_UNIFORM, // Blue noise
    POISSON_FOVEATED // Variable Density Blue Noise
}

class SamplingPatternManager(
    val width: Int, val height: Int,
    angleLeft: Float, angleRight: Float,
    angleUp: Float, angleDown: Float,
    val coneAngle: Float = Math.toRadians(30.0).toFloat(),
    val patternCount: Int = 10,         // Number of patterns to generate in advance
    val targetSampleCount: Int = 250    // Number of sample points in the pattern
) {
    private val uniformPatterns = ArrayList<IntArray>()
    private val foveatedPatterns = ArrayList<IntArray>()

    private var indexUniform = 0
    private var indexFoveated = 0

    private val tangentAngleLeft = tan(angleLeft)
    private val tangentAngleRight = tan(angleRight)
    private val tangentAngleUp = tan(angleUp)
    private val tangentAngleDown = tan(angleDown)

    private val fx = width / (tangentAngleRight - tangentAngleLeft)
    private val fy = height / (tangentAngleUp - tangentAngleDown)
    private val cx = -fx * tangentAngleLeft
    private val cy = height + (fy * tangentAngleDown)

    init {
        repeat(patternCount) { seed ->
            uniformPatterns.add(generatePoissonDisk(seed, PatternType.POISSON_UNIFORM))
            foveatedPatterns.add(generatePoissonDisk(seed, PatternType.POISSON_FOVEATED))
        }
    }

    fun getNextUniformPattern(): IntArray {
        val p = uniformPatterns[indexUniform]
        indexUniform = (indexUniform + 1) % patternCount
        return p
    }

    fun getNextFoveatedPattern(): IntArray {
        val p = foveatedPatterns[indexFoveated]
        indexFoveated = (indexFoveated + 1) % patternCount
        return p
    }

    private fun generatePoissonDisk(seed: Int, type: PatternType): IntArray {
        val rng = Random(seed)

        val selectedXs = FloatArray(targetSampleCount)
        val selectedYs = FloatArray(targetSampleCount)
        var count = 0

        val resultIndices = ArrayList<Int>(targetSampleCount)

        val radiusPxX = fx * tan(coneAngle * 0.5f)
        val radiusPxY = fy * tan(coneAngle * 0.5f)
        val area = (Math.PI * radiusPxX * radiusPxY).toFloat()

        val baseRadius = sqrt(area / targetSampleCount) * 0.8f

        var attempts = 0
        val maxAttempts = targetSampleCount * 100

        while (count < targetSampleCount && attempts < maxAttempts) {
            attempts++

            val r = sqrt(rng.nextFloat())
            val theta = rng.nextFloat() * 2 * Math.PI

            val offsetX = (r * radiusPxX * cos(theta)).toFloat()
            val offsetY = (r * radiusPxY * sin(theta)).toFloat()

            val x = cx + offsetX
            val y = cy + offsetY

            val ix = x.toInt()
            val iy = y.toInt()

            if (ix !in 0 until width || iy !in 0 until height) continue

            val requiredDistance = if (type == PatternType.POISSON_UNIFORM) {
                baseRadius
            } else {
                baseRadius * (0.4f + 1.2f * r)
            }
            val requiredDistanceSquared = requiredDistance * requiredDistance

            var isValid = true
            for (i in 0 until count) {
                val dx = x - selectedXs[i]
                val dy = y - selectedYs[i]
                if (dx * dx + dy * dy < requiredDistanceSquared) {
                    isValid = false
                    break
                }
            }

            if (isValid) {
                selectedXs[count] = x
                selectedYs[count] = y
                count++

                resultIndices.add(iy * width + ix)
                attempts = 0
            }
        }

        return resultIndices.toIntArray()
    }
}