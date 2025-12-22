package com.curvsurf.pointcloudrecorder_galaxyxr.helpers

import android.util.Log
import androidx.xr.arcore.Hand
import androidx.xr.arcore.HandJointType
import androidx.xr.arcore.RenderViewpoint
import androidx.xr.runtime.Session
import androidx.xr.runtime.TrackingState
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Quaternion.Companion.slerp
import androidx.xr.runtime.math.Vector2
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.lerp
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan
import kotlin.math.sqrt


enum class WindowAnchor {
    CENTER,
    TOP_LEFT,    TOP_CENTER,    TOP_RIGHT,
    CENTER_LEFT,                CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

fun PanelEntity.startSphericalFollow(
    scope: CoroutineScope,
    session: Session,
    leftFlow: StateFlow<RenderViewpoint.State>,
    rightFlow: StateFlow<RenderViewpoint.State>,
    distance: Float = 1.5f,
    smoothFactor: Float = 0.05f,
    anchor: WindowAnchor = WindowAnchor.CENTER,
    offsetAngles: Vector2 = Vector2(0f, 0f) // (Degrees)
) {
    scope.launch {
        while (isActive) {
            val headPose = calculateHeadWorldPose(
                session,
                leftFlow.value.pose,
                rightFlow.value.pose
            )

            val dimensions = this@startSphericalFollow.size
            val halfWidth = dimensions.width / 2f
            val halfHeight = dimensions.height / 2f

            val angleShiftX = Math.toDegrees(atan(halfWidth / distance).toDouble()).toFloat()
            val angleShiftY = Math.toDegrees(atan(halfHeight / distance).toDouble()).toFloat()

            val anchorAngleOffset = when (anchor) {
                WindowAnchor.CENTER -> Vector2(0f, 0f)
                WindowAnchor.TOP_LEFT -> Vector2(angleShiftX, -angleShiftY)
                WindowAnchor.TOP_CENTER -> Vector2(0f, -angleShiftY)
                WindowAnchor.TOP_RIGHT -> Vector2(-angleShiftX, -angleShiftY)
                WindowAnchor.CENTER_LEFT -> Vector2(angleShiftX, 0f)
                WindowAnchor.CENTER_RIGHT -> Vector2(-angleShiftX, 0f)
                WindowAnchor.BOTTOM_LEFT -> Vector2(angleShiftX, angleShiftY)
                WindowAnchor.BOTTOM_CENTER -> Vector2(0f, angleShiftY)
                WindowAnchor.BOTTOM_RIGHT -> Vector2(-angleShiftX, angleShiftY)
            }

            val targetYaw = offsetAngles.x + anchorAngleOffset.x
            val targetPitch = offsetAngles.y + anchorAngleOffset.y

            val rotationYaw = Quaternion.fromEulerAngles(0f, targetYaw, 0f)
            val rotationPitch = Quaternion.fromEulerAngles(targetPitch, 0f, 0f)

            val finalLocalRot = rotationYaw * rotationPitch
            val targetLocalPos = finalLocalRot * Vector3(0f, 0f, -distance)

            val targetWorldPos = headPose.translation + (headPose.rotation * targetLocalPos)

            val panelFacingRot = Quaternion.fromLookTowards(headPose.translation - targetWorldPos, Vector3.Up)

            val currentPose = this@startSphericalFollow.getPose()
            val newX = lerp(currentPose.translation.x, targetWorldPos.x, smoothFactor)
            val newY = lerp(currentPose.translation.y, targetWorldPos.y, smoothFactor)
            val newZ = lerp(currentPose.translation.z, targetWorldPos.z, smoothFactor)

            val newRot = slerp(currentPose.rotation, panelFacingRot, smoothFactor)

            this@startSphericalFollow.setPose(Pose(Vector3(newX, newY, newZ), newRot))

            delay(11)
        }
    }
}

fun PanelEntity.startHandPinchFollowWithFormula(
    scope: CoroutineScope,
    session: Session,
    leftHandFlow: StateFlow<Hand.State>,
    leftViewpointFlow: StateFlow<RenderViewpoint.State>,
    rightViewpointFlow: StateFlow<RenderViewpoint.State>,
    smoothFactor: Float = 0.1f
) {
    scope.launch {
        val pinchThresholdOn = 0.01f
        val pinchThresholdOff = 0.02f
        var isPinching = false

        while (isActive) {
            val handState = leftHandFlow.value

            val thumbLocal = handState.handJoints[HandJointType.HAND_JOINT_TYPE_THUMB_TIP]
            val middleLocal = handState.handJoints[HandJointType.HAND_JOINT_TYPE_MIDDLE_TIP]
            val wristLocal = handState.handJoints[HandJointType.HAND_JOINT_TYPE_WRIST]

            if (handState.trackingState == TrackingState.TRACKING &&
                thumbLocal != null && middleLocal != null && wristLocal != null) {

                val ps = session.scene.perceptionSpace
                val asSpace = session.scene.activitySpace

                val thumbWorld = ps.transformPoseTo(thumbLocal, asSpace)
                val middleWorld = ps.transformPoseTo(middleLocal, asSpace)
                val wristWorld = ps.transformPoseTo(wristLocal, asSpace)

                val dist = Vector3.distance(thumbWorld.translation, middleWorld.translation)
                if (isPinching) {
                    if (dist > pinchThresholdOff) isPinching = false
                } else {
                    if (dist < pinchThresholdOn) isPinching = true
                }

                if (isPinching) {

                    val wristPosition = wristWorld.translation

                    val pinchCenter = (thumbWorld.translation + middleWorld.translation) * 0.5f
                    val contactVector = pinchCenter - wristPosition
                    val contactDir = contactVector.toNormalized()

                    val localLeft = Vector3(-1f, 0f, 0f)
                    val outwardDir = (wristWorld.rotation * localLeft).toNormalized()

                    val headPose = calculateHeadWorldPose(
                        session,
                        leftViewpointFlow.value.pose,
                        rightViewpointFlow.value.pose
                    )
                    val localRight = Vector3(1f, 0f, 0f)
                    val rightDir = (headPose.rotation * localRight).toNormalized()

                    val diagonalDir = (outwardDir + contactDir).toNormalized()

                    val targetPos = wristPosition +
                            (diagonalDir * 0.30f) +
                            Vector3(0f, 0.2f, 0f)
//                            - (rightDir * 0.10f)

                    val directionToHead = headPose.translation - targetPos
                    val targetRot = Quaternion.fromLookTowards(directionToHead, Vector3.Up)

                    val currentPose = this@startHandPinchFollowWithFormula.getPose()
                    val newPos = Vector3.lerp(currentPose.translation, targetPos, smoothFactor)
                    val newRot = slerp(currentPose.rotation, targetRot, smoothFactor)

                    this@startHandPinchFollowWithFormula.setPose(Pose(newPos, newRot))
                }
            }
            delay(11)
        }
    }
}

fun calculateInitialLeftHandPosition(): Pose {
    val pos = Vector3(x = -0.1236074f, y = 0.059056167f, z = 0.6596145f)
    val rot = Quaternion(x = -0.052142136f, y = 0.16654396f, z = 0.008960566f, w = 0.98461366f)
    return Pose(pos, rot)
}