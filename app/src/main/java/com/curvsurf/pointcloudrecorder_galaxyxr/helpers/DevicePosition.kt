package com.curvsurf.pointcloudrecorder_galaxyxr.helpers

import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion.Companion.slerp
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.scene

fun calculateHeadWorldPose(
    session: Session,
    leftPose: Pose,
    rightPose: Pose
): Pose {
    val perceptionSpace = session.scene.perceptionSpace
    val activitySpace = session.scene.activitySpace

    val leftWorld = perceptionSpace.transformPoseTo(leftPose, activitySpace)
    val rightWorld = perceptionSpace.transformPoseTo(rightPose, activitySpace)

    val headX = (leftWorld.translation.x + rightWorld.translation.x) * 0.5f
    val headY = (leftWorld.translation.y + rightWorld.translation.y) * 0.5f
    val headZ = (leftWorld.translation.z + rightWorld.translation.z) * 0.5f
    val centerPosition = Vector3(headX, headY, headZ)

    val centerRotation = slerp(leftWorld.rotation, rightWorld.rotation, 0.5f)

    return Pose(centerPosition, centerRotation)
}