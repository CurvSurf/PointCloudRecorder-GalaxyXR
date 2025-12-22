package com.curvsurf.pointcloudrecorder_galaxyxr.helpers

import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector3.Companion.distance
import kotlin.math.cos

/**
 * Tracks the camera (device)’s position and orientation to verify whether it has moved
 *   a specific distance or rotated to a particular angle.
 */
class CameraMotionDetector(
    val minDistance: Float = 0.10f,
    minAngleRadian: Float = (10.0 * Math.PI / 180.0).toFloat()
) {
    private var position: Vector3 = Vector3()
    private var direction: Vector3 = Vector3()

    val minCosineAngle: Float = cos(minAngleRadian.toDouble()).toFloat()

    fun hasCameraMovedEnough(cameraPose: Pose): Boolean {
        val position = Vector3(cameraPose.translation)
        val direction = Vector3(cameraPose.backward)

        val positionChanged = distance(position, this.position) > minDistance
        val directionChanged = direction.dot(this.direction) < minCosineAngle

        if (positionChanged or directionChanged) {
            this.position = position
            this.direction = direction
            return true
        } else {
            return false
        }
    }
}