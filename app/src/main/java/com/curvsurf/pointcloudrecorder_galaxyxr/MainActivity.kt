package com.curvsurf.pointcloudrecorder_galaxyxr

import android.annotation.SuppressLint
import android.opengl.GLES32
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.xr.arcore.DepthMap
import androidx.xr.arcore.Hand
import androidx.xr.arcore.RenderViewpoint
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureCalibrationRequired
import androidx.xr.runtime.SessionConfigureGooglePlayServicesLocationLibraryNotLinked
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.SessionCreateApkRequired
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.SessionCreateUnsupportedDevice
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Matrix4
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector2
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.SpatialCapability
import androidx.xr.scenecore.scene
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.CameraMotionDetector
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.GLSurfaceRenderer
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.Matrix4x4
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.SamplingPatternManager
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.WindowAnchor
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.calculateInitialLeftHandPosition
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.exportPointCloud
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.startHandPinchFollowWithFormula
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.startSphericalFollow
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.toMatrix4
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.toMatrix4x4
import com.curvsurf.pointcloudrecorder_galaxyxr.render_passes.ConfidenceMapRenderPass
import com.curvsurf.pointcloudrecorder_galaxyxr.render_passes.DepthMapRenderPass
import com.curvsurf.pointcloudrecorder_galaxyxr.render_passes.PointCloudRenderPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.getValue
import kotlin.math.tan

/**
 * NOTE: The resolution and field of view angles are values that have been queried for
 *   the left render viewpoint using ARCore API while using the Samsung Galaxy XR device.
 * You have to change it if you're trying different devices other than Galaxy XR
 *   or they changed the related camera parameters or the resolution.
 */
private const val depthMapWidth = 160
private const val depthMapHeight = 160
private const val fovAngleLeft = -0.95099926f
private const val fovAngleRight = 0.6959626f
private const val fovAngleUp = 0.9175058f
private const val fovAngleDown = -0.9175058f

/**
 * NOTE: The following intrinsic parameter values below here is totally heuristically determined,
 *   because, AFAIK, they never have revealed the actual intrinsic parameters
 *   of the depth sensor.
 *
 *  We assume that the fov is 90 degrees, the aspect ratio is 1,
 *    and the principal point is in the center of the image.
 */
private const val fx = depthMapWidth * 0.5f
private const val fy = depthMapHeight * 0.5f
private const val ppx = depthMapWidth * 0.5f
private const val ppy = depthMapHeight * 0.5f

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val AppViewModel.mapDisplayMode: MapDisplayMode
        get() = uiState.value.mapDisplayMode
    private val AppViewModel.pointsVisible: Boolean
        get() = uiState.value.pointsVisible
    private val AppViewModel.recordingEnabled: Boolean
        get() = uiState.value.recordingEnabled

    private lateinit var session: Session
    private lateinit var mapDisplayViewEntity: PanelEntity
    private lateinit var controlViewEntity: PanelEntity

    private val pointBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(Float.SIZE_BYTES * 4 * 100_000)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val exportBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(Float.SIZE_BYTES * 4 * 100_000)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var pointBufferIndex = 0
    private var pointCount = 0

    private val sampler = SamplingPatternManager(
        width = depthMapWidth, height = depthMapHeight,
        angleLeft = fovAngleLeft,
        angleRight = fovAngleRight,
        angleUp = fovAngleUp,
        angleDown = fovAngleDown
    )

    private val cameraMotionDetector = CameraMotionDetector()
    private var shouldExportPointCloud = false

    private val renderer = object: GLSurfaceRenderer.Renderer {

        val depthMapRenderPass = DepthMapRenderPass()
        val confidenceMapRenderPass = ConfidenceMapRenderPass()
        val pointCloudRenderPass = PointCloudRenderPass()

        override fun init() {
            depthMapRenderPass.init(this@MainActivity)
            confidenceMapRenderPass.init(this@MainActivity)
            pointCloudRenderPass.init(this@MainActivity)
        }

        override fun resize(width: Int, height: Int) {

        }

        override fun update() {
            depthMapRenderPass.update()
            confidenceMapRenderPass.update()
            pointCloudRenderPass.update()
        }

        override fun render() {
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT)
            when (viewModel.mapDisplayMode) {
                MapDisplayMode.DEPTH_MAP -> depthMapRenderPass.render()
                MapDisplayMode.CONFIDENCE_MAP -> confidenceMapRenderPass.render()
            }
            if (viewModel.pointsVisible) pointCloudRenderPass.render()
        }

        override fun release() {
            depthMapRenderPass.release()
            confidenceMapRenderPass.release()
            pointCloudRenderPass.release()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()
        createSession()

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(true) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        UIEvent.ExportPointCloud -> {
                            shouldExportPointCloud = true
                        }
                        UIEvent.ClearPoints -> {
                            pointBufferIndex = 0
                            pointCount = 0
                            renderer.pointCloudRenderPass.updatePoints(pointBuffer, pointCount, 0, 0)
                        }
                    }
                }
            }
        }

        session.scene.mainPanelEntity.setAlpha(0f)

        val leftDepth = DepthMap.left(session)?.state
        val leftViewpoint = RenderViewpoint.left(session)?.state
        val rightViewpoint = RenderViewpoint.right(session)?.state
        val leftHand = Hand.left(session)?.state

        lifecycleScope.launch {
            val leftDepth = leftDepth ?: return@launch
            val leftViewpoint = leftViewpoint ?: return@launch
            val rightViewpoint = rightViewpoint ?: return@launch
            val leftHand = leftHand ?: return@launch

            validateHardcodedValuesForGalaxyXR(leftDepth, leftViewpoint)

            createMapDisplayPanel()
            createControlPanel()

            launch {
                startCollectingPointCloudFrom(depthMapFlow = leftDepth, viewpointFlow = leftViewpoint)
            }

            launch {
                mapDisplayViewEntity.startSphericalFollow(
                    scope = this,
                    session = session,
                    leftFlow = leftViewpoint,
                    rightFlow = rightViewpoint,
                    anchor = WindowAnchor.CENTER,
                    offsetAngles = Vector2(15f, 15f)
                )
            }

            launch {
                controlViewEntity.startHandPinchFollowWithFormula(
                    scope = this,
                    session = session,
                    leftHandFlow = leftHand,
                    leftViewpointFlow = leftViewpoint,
                    rightViewpointFlow = rightViewpoint
                )
            }
        }
    }

    private suspend fun validateHardcodedValuesForGalaxyXR(
        leftDepth: StateFlow<DepthMap.State>,
        leftViewpoint: StateFlow<RenderViewpoint.State>
    ) {
        val firstDepthMap = leftDepth.first { it.width != 0 && it.height != 0 }
        val width = firstDepthMap.width
        val height = firstDepthMap.height
        require(width == depthMapWidth && height == depthMapHeight) {
            """Depth map resolution is incorrect. 
                |Maybe you're using a device other than GalaxyXR 
                |or they might have changed the resolution. 
                |Try changing the resolution in the code above MainActivity class.  
                |New resolution: ${width}x${height}
            """.trimMargin()
        }

        val firstViewpoint = leftViewpoint.first { it.fieldOfView.angleLeft != 0f }
        val angleLeft = firstViewpoint.fieldOfView.angleLeft
        val angleRight = firstViewpoint.fieldOfView.angleRight
        val angleUp = firstViewpoint.fieldOfView.angleUp
        val angleDown = firstViewpoint.fieldOfView.angleDown
        require(angleLeft == fovAngleLeft && angleRight == fovAngleRight &&
                angleUp == fovAngleUp && angleDown == fovAngleDown) {
            """Field of view angles for left render viewpoint are incorrect.
                Maybe you're using a device other than GalaxyXR
                or they might have changed the camera parameters.
                Try changing the parameters in the code above MainActivity class.
                New parameters: left: ${angleLeft}, right: ${angleRight}, up: ${angleUp}, down: ${angleDown}
            """.trimIndent()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = arrayOf(
            "android.permission.HAND_TRACKING",
            "android.permission.CAMERA",
            "android.permission.HEAD_TRACKING",
            "android.permission.SCENE_UNDERSTANDING_SPARSE",
            "android.permission.SCENE_UNDERSTANDING_FINE"
        )
        requestPermissions(permissions, 101)
    }

    private fun createSession() {

        val session = when (val result = Session.create(this)) {
            is SessionCreateApkRequired -> {
                Log.e("MainActivity", "Session requires the following Apk: ${result.requiredApk}")
                return
            }

            is SessionCreateUnsupportedDevice -> {
                Log.e("MainActivity", "Unsupported device.")
                return
            }

            is SessionCreateSuccess -> {
                result.session
            }
        }

        val supportPassthroughControl = session.scene.spatialCapabilities.contains(
            SpatialCapability.PASSTHROUGH_CONTROL
        )
        if (supportPassthroughControl) {
            session.scene.spatialEnvironment.preferredPassthroughOpacity = 1f
        }

        val newConfig = session.config.copy(
            handTracking = Config.HandTrackingMode.BOTH,
            headTracking = Config.HeadTrackingMode.LAST_KNOWN,
            depthEstimation = Config.DepthEstimationMode.RAW_ONLY
        )
        try {
            when (val result = session.configure(newConfig)) {
                is SessionConfigureSuccess -> {
                    Log.i("MainActivity", "Session succeeded to configure.")
                }
                is SessionConfigureCalibrationRequired -> {
                    Log.e("MainActivity", "Configure calibration required.")
                }

                is SessionConfigureGooglePlayServicesLocationLibraryNotLinked -> {
                    Log.e(
                        "MainActivity",
                        "Configure google play services location library not linked."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "There is a problem to configure the session.", e)
        }
        this.session = session
    }

    private fun createMapDisplayPanel() {
        val mapDisplayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity)
            background = null
            setContent {
                MapDisplayView(renderer)
            }
        }

        val resolution = FloatSize2d(width = 0.60f, height = 0.60f)
        val panelEntity = PanelEntity.create(
            session,
            mapDisplayView,
            resolution,
            "Depth Map Panel",
            Pose(Vector3(0f, 0f, -1.5f), Quaternion.Identity)
        )
        this.mapDisplayViewEntity = panelEntity
        session.scene.activitySpace.addChild(panelEntity)
    }

    private fun createControlPanel() {
        var currentResolution = IntSize2d(width = 500, height = 500)
        var panelEntity: PanelEntity? = null
        val controlView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity)
            background = null
            setContent {
                ControlPanelView(onSizeDetermined = { width, height ->
                    if (currentResolution.width != width || currentResolution.height != height) {
                        currentResolution = IntSize2d(width, height)
                        panelEntity?.sizeInPixels = currentResolution
                    }
                })
            }
        }

        val initialPose = calculateInitialLeftHandPosition()
        panelEntity = PanelEntity.create(
            session,
            controlView,
            FloatSize2d(0.3f, 1.0f),
            "Control Panel",
            initialPose
        )

        this.controlViewEntity = panelEntity
        session.scene.activitySpace.addChild(panelEntity)
    }

    private suspend fun startCollectingPointCloudFrom(
        depthMapFlow: StateFlow<DepthMap.State>,
        viewpointFlow: StateFlow<RenderViewpoint.State>
    ) {
        withContext(Dispatchers.Default) {

            val matrix = Matrix4x4()
            val cameraPoseMatrix = Matrix4(Matrix4.Identity)
            val viewMatrix = Matrix4(Matrix4.Identity)
            val projectionMatrix = Matrix4(Matrix4.Identity)
            val viewProjectionMatrix = Matrix4(Matrix4.Identity)
            var maxDepth = 0f

            depthMapFlow.collect { state ->
                /**
                 * We don’t use the smoothed versions because they introduce interpolated values,
                 *   which are basically fake data for our purpose.
                 */
                val depthMap = state.rawDepthMap?.duplicate() ?: return@collect
                val confidenceMap = state.rawConfidenceMap?.duplicate() ?: return@collect

                val width = state.width
                val height = state.height

                val cameraPose = viewpointFlow.value.pose
                val (m00, m01, m02, m03,
                     m10, m11, m12, m13,
                     m20, m21, m22, m23) = cameraPose.toMatrix4x4(matrix)

                val fov = viewpointFlow.value.fieldOfView
                val near = 0.01f
                val far = 10f
                val left = tan(fov.angleLeft) * near
                val right = tan(fov.angleRight) * near
                val bottom = tan(fov.angleDown) * near
                val top = tan(fov.angleUp) * near
                Matrix.frustumM(
                    projectionMatrix.data, 0,
                    left, right, bottom, top, near, far
                )

                val fx = 2f * near / (right - left)
                val fy = 2f * near / (top - bottom)
                val ppx = - (right + left) / (right - left) * width / 2
                val ppy = - (top + bottom) / (top - bottom) * height / 2
//                Matrix.perspectiveM(
//                    projectionMatrix.data, 0,
//                    90f, 1f,
//                    0.01f, 10f)1
                cameraPose.toMatrix4(cameraPoseMatrix)
                Matrix.invertM(viewMatrix.data, 0, cameraPoseMatrix.data, 0)
                Matrix.multiplyMM(
                    viewProjectionMatrix.data, 0,
                    projectionMatrix.data, 0,
                    viewMatrix.data, 0
                )

                if (cameraMotionDetector.hasCameraMovedEnough(cameraPose) &&
                    viewModel.recordingEnabled) {
                    val dirtyBegin = pointBufferIndex / 4
                    var dirtyCount = 0

                    val indices = sampler.getNextFoveatedPattern()
                    for (i in indices) {
                        val z = depthMap.get(i)
                        if (z <= 0f) continue

                        maxDepth = maxOf(maxDepth, z)

                        val confidence = (confidenceMap.get(i).toInt() and 0xFF).toFloat() / 255f
                        if (confidence <= 0.5f) continue

                        val u = i % width
                        val v = i / width

                        val vx = (u - ppx) * z / fx
                        val vy = (ppy - v) * z / fy
                        val vz = -z

                        val wx = m00 * vx + m01 * vy + m02 * vz + m03
                        val wy = m10 * vx + m11 * vy + m12 * vz + m13
                        val wz = m20 * vx + m21 * vy + m22 * vz + m23

                        pointBuffer.put(pointBufferIndex, wx); pointBufferIndex = (pointBufferIndex + 1) % 400_000
                        pointBuffer.put(pointBufferIndex, wy); pointBufferIndex = (pointBufferIndex + 1) % 400_000
                        pointBuffer.put(pointBufferIndex, wz); pointBufferIndex = (pointBufferIndex + 1) % 400_000
                        pointBuffer.put(pointBufferIndex, confidence); pointBufferIndex = (pointBufferIndex + 1) % 400_000
                        pointCount = minOf(pointCount + 1, 100_000)
                        dirtyCount++
                    }
                    withContext(Dispatchers.Main) { viewModel.setPointCount(pointCount) }
                    renderer.pointCloudRenderPass.updatePoints(pointBuffer, pointCount, dirtyBegin, dirtyCount)
                }
                renderer.depthMapRenderPass.updateDepthMap(depthMap, maxDepth, width, height)
                renderer.confidenceMapRenderPass.updateConfidenceMap(confidenceMap, width, height)
                renderer.pointCloudRenderPass.viewProjectionMatrix = viewProjectionMatrix
                renderer.pointCloudRenderPass.projectionMatrix = projectionMatrix

                if (shouldExportPointCloud) {
                    withContext(Dispatchers.Main) { viewModel.setExporting(true) }
                    shouldExportPointCloud = false

                    exportBuffer.rewind()
                    exportBuffer.put(pointBuffer)
                    exportPointCloud(this@MainActivity, exportBuffer, pointCount, 0.5f) {
                        viewModel.setExportProgress(it)
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.setExporting(false)
                    }
                }
            }
        }
    }

}
