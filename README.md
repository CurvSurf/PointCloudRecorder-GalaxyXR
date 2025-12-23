# Point Cloud Recorder - Galaxy XR

## Overview

PointCloudRecorder_GalaxyXR is a sample application developed by CurvSurf for recording point clouds using Galaxy XR devices. The application visualizes the depth map of the scene the user is observing (obtained via Android XR ARCore) in the upper-left corner of the screen, reconstructs a point cloud from the depth data, accumulates it over time, and allows the result to be exported as a file for further analysis.
<!--이 애플리케이션은 사용자가 보고 있는 장면의 depth map (Android XR ARCore를 통해 얻을 수 있습니다)을 화면 좌측 상단에 시각화하고 depth 데이터로부터 point cloud를 계산하여 복원하고 이를 수집한 뒤 파일로 export 할 수 있는 기능을 제공합니다.-->

**TL;DR**
Android XR currently exposes depth data suitable for visualization, but not enough synchronized and calibrated information for reliable metric point cloud reconstruction.

> **Note**
> This sample project was developed and tested specifically for **Galaxy XR**.
> Although it may run on other Android XR-compatible devices, correct behavior **cannot be guaranteed**.
<!--> 이 샘플 프로젝트는 Galaxy XR에서의 사용을 위해 개발하고 테스트 되었으며, Android XR을 지원하는 다른 기기에서 동작할 수도 있지만 제대로 동작할 것을 보장하지 않습니다.-->

## Background and Motivation

Originally, this project was started as a counterpart to [the sample projects](https://github.com/CurvSurf/FindSurface-visionOS?tab=readme-ov-file#samples) we had previously developed for **Apple Vision Pro**. From our perspective, Apple Vision Pro does not expose any developer-accessible means to obtain *useful* raw 3D measurement data from the user's real environment, *such as raw feature points, depth maps, or point clouds*, other than meshes.

As a result, we were only able to test **FindSurface** using mesh vertices in a very limited manner (see [here](https://github.com/CurvSurf/FindSurface-RealityKit-visionOS-Real-Time), if you are interested).

When we learned that **Galaxy XR** would ship with a depth sensor, we immediately obtained the device upon release and began this experimental sample project.

<!-- 우리는 당초에 이 프로젝트를 우리가 이전에 개발한 Apple Vision Pro에 대응되는 버전으로서 개발하려고 착수했습니다. CurvSurf에게 있어서 Apple의 Vision Pro는, Mesh를 제외하고, 사용자의 실제 환경에 대하여 "우리에게 유용한" 3차원 측정값(e.g., raw feature points, depth map, or point clouds)을 얻을 수 있는 어떠한 수단도 개발자들에게 공개하지 않았으므로 mesh의 vertices를 아주 제한적인 수준으로 활용하여 FindSurface의 동작을 테스트 했습니다. (이 부분에 관심이 있다면 [여기]()를 참조하세요.) 그래서 Galaxy XR에 depth sensor (비록 indirect 방식이더라도)가 탑재된다는 소식에 우리는 Galaxy XR을 출시하자마자 확보하여 이 테스트용 샘플 프로젝트를 시작했습니다. -->

## Implementation Details

### Acquiring the Depth Map

Using the Android XR API, `DepthMap` data can be obtained for **left**, **right**, or **mono** views via independent `StateFlow`s. Galaxy XR provides left and right depth maps (mono is not available).
<!-- Android XR의 API를 이용해서 `DepthMap`을 각각 left, right, 또는 mono로 얻어올 수 있으며 StateFlow로 비동기적이고 독립적으로 제공됩니다. Galaxy XR은 left와 right를 지원합니다. (mono는 제공하지 않음.) -->

```kotlin
DepthMap.left(session)?.state?.collect { state -> 

    val depthMap = state.rawDepthMap ?: return
    val confidenceMap = state.rawConfidenceMap ?: return

    val width = state.width
    val height = state.height

    ...
}
```

Each flow provides both **raw** and **smoothed** versions of the depth and confidence maps.

- **Raw** depth maps may contain invalid or missing pixels where measurements failed.
- **Smoothed** depth maps fill all pixels but introduce interpolated (and therefore potentially incorrect) values.

<!-- 또한 각 flow에서 주어지는 depth map과 confidence map은 raw 버전과 smooth 버전을 제공합니다. raw 버전은 다양한 이유로 측정값을 확보하는데 실패하는 지점의 픽셀은 비어있을 수 있는 반면에(유효하지 않은 값), smooth 버전은 모든 픽셀이 채워지는 대신, interpolation으로 인해 부정확한 데이터가 추가됩니다.  -->

Since **FindSurface** works better with sparse but *accurate* data than with dense *noisy* one, we chose to use the **raw** depth maps.

<!-- FindSurface는 geometry를 감지하고 추출하는데에 있어서 '많고 부정확한' 데이터보다는 '적고 정확한' 데이터만으로도 충분하므로 raw를 사용했습니다. -->

Below is a screen recording demonstrating visualization of both depth maps:
<!-- 아래 영상은 두 depth map을 시각화하는 테스트를 화면 녹화로 촬영한 것입니다. -->

[![video](https://img.youtube.com/vi/9xtaKmCywrg/maxresdefault.jpg)](https://www.youtube.com/watch?v=9xtaKmCywrg)
***Fig 1.** Galaxy XR raw depth map visualization (click the image to watch)*

One notable observation is that:

- The left depth map covers the full field of view.
- The right depth map contains shadowed regions on the right side where depth values are missing.

This suggests that the right depth map may be a reprojection of left-based depth data into the right eye's view (note: this is just a hypothesis; we did not verify it conclusively). Artifacts can be observed on the right side of the left arm around 9 seconds, and on the left corridor wall at 15 seconds (the thumbnail shows it).

<!-- 한 가지 눈여겨 볼 점은 left의 depth map은 scene에 대한 시야각의 전체 depth data를 얻을 수 있는 반면에, right는 사물의 우측에 depth값이 없는 음영지역이 발생합니다. 이는 left를 기준으로 얻은 깊이 데이터를 right에 대응되도록 다시 투영한 데이터일 것이라는 추정을 가능케 합니다. (주의: 추정이 사실인지 정말로 확인하지는 않음) 영상 9초부터 왼팔의 오른쪽과 15초부터 복도 왼편 벽에 생기는 artifact가 발생하는 것을 확인할 수 있습니다. -->

For this reason, we exclusively used the left depth map.
<!-- 우리는 음영이 없는 left만을 사용하였습니다. -->

### Point Cloud Reconstruction

As is well known, reconstructing a point cloud defined in world coordinate space from a depth map requires:
<!-- 아시다시피, depth map에서 point cloud를 계산하려면 다음과 같은 정보가 필요합니다: -->

- Camera (or sensor) **intrinsic parameters** (focal length, principal point)
- Depth map **resolution**
- Camera (or sensor) **pose** (i.e., inverse view matrix)
<!-- - 카메라 혹은 센서의 intrinsic parameters (i.e., 초점거리, principal point)
- depth map의 해상도
- 카메라 혹은 센서의 위치와 자세 (i.e., inverse view matrix) -->

The **resolution** is provided by `DepthMap`, and the **pose** can be obtained from `RenderViewpoint`. However, the **intrinsic parameters** are not provided. They are not included in `DepthMap.State`, nor are they documented in the official Android XR documentation.

In short, Android XR currently provides sufficient data for rendering, but not for metric depth-based reconstruction.

`RenderViewpoint`, like `DepthMap`, provides **left**, **right**, or **mono** views. On Galaxy XR, left and right viewpoints are available, and the following data can be obtained:

- Pose of each eye in world coordinates
- Pose of each eye in device coordinates
- Four field-of-view angles: left, right, up, down

From these angles, it is clear that the projection transform is not necessarily symmetric about the center of projection. In fact, for Galaxy XR, both `RenderViewpoint`s are vertically symmetric but horizontally asymmetric.

#### Working assumption: Asymmetric FOV

According to [the official Android XR documentation](https://developer.android.com/reference/kotlin/androidx/xr/arcore/RenderViewpoint), this data is intended for rendering and represents the view of the left and right eyes, or a mono view.

The actual values of the angles obtained from `RenderViewpoint` were:

<!-- 두 번째는 `DepthMap`에서 주어지고 세 번째는 `RenderViewpoint`로부터 얻어낼 수 있었지만, 첫 번째는 그렇지 않았습니다. `DepthMap.State` 자료구조에도 없고, 공식 문서에서도 언급된 내용이 없습니다. 다만 `RenderViewpoint`도 `DepthMap`처럼 left, right 혹은 mono를 제공하는데, Galaxy XR에서는 left와 right가 제공되었고, 이로부터 얻을 수 있는 데이터는 world coordinate space에서의 각 눈의 위치와 자세(`Pose`), device (Galaxy XR) coordinate space에서 각 눈의 위치와 자세, 그리고 fieldOfView에서 주어지는 left, right, up, down에 해당하는 네 개의 angles입니다. (참고: 네 개의 각도에서 유추할 수 있듯이, 이 fov 값들이 말해주는 projection transform은 center of projection을 기준으로 대칭이 아닐 수 있으며, Galaxy XR에서 left의 `RenderViewpoint`는 실제로 수직 방향은 대칭이지만 수평 방향은 그렇지 않았습니다.) [Google의 공식 개발자 문서](https://developer.android.com/reference/kotlin/androidx/xr/arcore/RenderViewpoint)에서 이 데이터는 렌더링에 사용되며 왼쪽 눈이나 오른쪽 눈, 혹은 모노 뷰와 같은 시점을 대표한다고 말하고 있습니다. 이 데이터로부터 실제로 얻은 렌더링을 위한 투영 frustum의 fov 각도는 비대칭 center of projection을 기준으로 다음과 같은 값을 가졌습니다: -->

```kotlin
val fovAngleLeft = -0.95099926f
val fovAngleRight = 0.6959626f
val fovAngleUp = 0.9175058f
val fovAngleDown = -0.9175058f
```

These values are in radians. Converted to degrees, they correspond to approximately:
- Horizontal FOV: ~95°
- Vertical FOV: ~105°
<!-- 이 값들은 radian이며 degree로 환산하면 수평 시야각은 약 95도, 수직 시야각은 약 105도가 나옵니다. -->

Using these values, we constructed a projection frustum and derived intrinsic-like parameters as follows:

```kotlin
val fov = viewpointFlow.value.fieldOfView
val near = 0.01f
val far = 10f
val left = tan(fovAngleLeft) * near
val right = tan(fovAngleRight) * near
val bottom = tan(fovAngleDown) * near
val top = tan(fovAngleUp) * near

Matrix.frustumM(
    projectionMatrix.data, 0,
    left, right, bottom, top, near, far
)

val fx = width * near / (right - left)
val fy = height * near / (top - bottom)
val ppx = 0.5f * width - (right + left) / (right - left) * width / 2
val ppy = 0.5f * height - (top + bottom) / (top - bottom) * height / 2
```

Since no API explicitly exposes data intended for point cloud reconstruction from the depth sensor, we used `RenderViewpoint` parameters as a substitute, exported the resulting point cloud, and tested it in [our Web-based analysis tool](https://developers.curvsurf.com/WebDemo/).
<!-- depth sensor와 관련하여 point cloud 복원에 사용할 수 있는 데이터라고 명시되었거나 달리 사용할 데이터를 제공하는 API가 없으므로 위와 같이 RenderViewpoint의 데이터를 사용하여 point cloud를 계산하여 파일로 export하여 WebDemo에서 테스트 해 보았습니다.  -->

#### Paleolithic Methods: Symmetric FOV

Separately, we attempted a deliberately naive and informal approach to estimate FOV angles for the depth map based on:

- The depth map resolution is square, so maybe the FOV angles in horizontal and vertical are the same.
- The depth sensor projection is symmetric.

The procedure was as follows:
![Don't laugh, I know it's ridiculous](images/primitive-triangle.png)
***Fig 2.** We had to make do with what we had.*

1. Wearing the Galaxy XR headset, one extended both arms evenly while holding a tape measure, adjusting the tape so that both hands were just barely visible at the horizontal edges of the depth map.
2. This forms a triangle between the two hands and the headset. We then put both hands against a wall and measured the distance from the wall to the headset.
3. From this geometry, we estimated rough horizontal and vertical FOV values since we have both base and height of the triangle.

<!-- 그리고 그와 별개로 depth map이 가로 세로의 크기가 같다는 점과 depth sensor의 투영이 대칭일거라는 단순하고 naive한 가정에서 출발하여 직접 fov를 구하려는 시도를 해 보았습니다. 그 방식은 다음과 같습니다.
1) 직접 Galaxy XR을 착용한 채로 줄자를 들고 양 팔을 고르게 뻗은 상태에서 줄자를 잡아당겨서 depth map의 시야 끝에 줄자를 붙잡은 두 손끝이 겨우 보일 정도를 맞춥니다. 
2) 그러면 제 두 손끝과 Galaxy XR이 삼각형을 이루게 되고, 저는 이 삼각형에서 두 손끝을 벽에 가져다 댄 채로 벽으로부터 Galaxy XR까지의 거리를 잽니다. 
3) 그렇게 하면 아주 rough하게 depth map에서 사용한 수평 시야각과 수직 시야각을 잴 수 있는 값이 나옵니다.  -->

Naturally, this method is extremely crude and unreliable. However, repeated measurements yielded values roughly between 85°~95°, and a 90° symmetric FOV produced point clouds that looked reasonably plausible.

<!-- 당연히 이 방식은 매우 원시적이고 믿을 수 없습니다. 그러나 여러번의 측정에서 대략적으로 85~95도 사이의 각도가 나왔고 이로부터 수직/수평 FOV가 90도일때의 point cloud 또한 그럴싸하게 나옵니다.  -->

Both reconstruction approaches produce point clouds that clearly contain distortions. Unfortunately, we have no ground-truth reference that we can validate correctness.

We cannot determine:
- Which approach (if either) is correct
- Whether one is correct but distorted due to potential sensor limitations
- Or whether both are simply using incorrect parameters

At the time of writing, Android XR and Galaxy XR lack sufficient public documentation to resolve this uncertainty. (If you happen to know more, we would greatly appreciate any insights.)

<!-- 중요한 점은 두 방식에서 얻은 point cloud 모두 일종의 왜곡이 포함되어 있지만, 우리는 Galaxy XR에서 ground truth로 비교할 수 있는 결과물을 얻을 수 없기 때문에 어느쪽이 옳은지, 심지어 둘 중 하나라도 옳기는 한지 조차 알 수 없습니다. 둘 중 하나가 옳지만 단순히 depth sensor의 한계로 인해 왜곡이 보정되지 않은 데이터가 주어진 것인지 알 수 없고, 아니면 단순히 둘 다 잘못된 매개변수를 사용했기 때문에 왜곡이 나타나는 것일수도 있습니다. 어쨌든, Android XR과 Galaxy XR에 대해서는 공식 문서로서 아직 공개된 것이 많지 않기 때문에 이와 관련된 정보를 찾을 수 없었습니다. (아시는 분이 있다면 제보해주시면 감사하겠습니다.) -->

### Visualization Results

| symmetric | asymmetric |
|:---------:|:----------:|
| ![symmetric](images/symmetric.gif) | ![asymmetric](images/asymmetric.gif) |
| ![symmetric](images/symmetric.png) | ![asymmetric](images/asymmetric.png) |

***Table 1.** Comparison of point cloud reconstructions using symmetric and asymmetric projection assumptions. Despite different projection models, both reconstructions exhibit similar surface distortions and fail to preserve the true spherical geometry.*

In both reconstruction approaches, the resulting point clouds failed to faithfully represent the surface geometry of a **spherical object**.

A surface that should have been smooth and uniformly curved appeared instead **slightly deformed and noticeably uneven** (See the gif images above). When processed by FindSurface, the point cloud did not yield a single sphere with the correct radius. Instead, two distinct spheres with different radii were detected, neither of which matched the real-world value.

We believe this behavior is more likely attributable to distortion inherent to the depth sensor itself, rather than inaccuracies in the intrinsic parameters used during reconstruction.

If the dominant source of error had been incorrect intrinsic parameters (e.g., focal length or principal point), the resulting distortion would be expected to exhibit a systematic and distance-dependent pattern. Such errors typically manifest as global deformations where points bend or drift coherently according to a predictable rule, which is similar to a shear or a stretching along a particular axis.

To minimize the impact of intrinsic-related distortion, the spherical object was deliberately positioned near the center of the field of view, where projection errors caused by incorrect intrinsics are generally least pronounced. Despite this precaution, the reconstructed surface of the sphere remained irregular and noisy.

For this reason, we consider it highly likely that the observed deformation originates from limitations of the depth sensing process itself, rather than from the choice of projection or reconstruction parameters.

<!-- 두 경우에서, 점 구름은 구형(spherical) 물체의 표면에서 원래의 기하를 표현하는데 실패했습니다. 구면이어야 할 표면은 약간 찌그러진 듯이 다소 울퉁불퉁했으며, FindSurface는 이 점 구름 데이터에서 서로 반지름이 다른(어느것도 원래의 값과 일치하지 않는) 두 개의 구를 검출했습니다. 이는 앞서 이야기한 intrinsic parameter의 부정확성과 별개로 depth sensor의 왜곡으로 인해 생기는 것으로 추정됩니다. 만약 파라미터와 관련된 왜곡이었다면 모든 점들이 거리에 따라 특정한 규칙을 가지고 구부러졌을 것이며 이것은 마치 shear 또는 특정한 축으로 잡아당겨진 것과 같은 형태로 이루어졌을 것입니다. 그러한 왜곡을 최소화하기 위해서 공을 시야의 중심에 두었음에도 불구하고 공 표면이 울퉁불퉁하게 스캔된 것은 센서 자체의 한계일 가능성이 매우 높다고 생각합니다. -->

### Filtering, Accumulation, and Synchronization Issues

Given the inherent distortion in the raw depth data, we concluded that directly using the data without filtering was not meaningful.
<!-- 위에서 본 대로 depth map에는 왜곡이 포함되어 있고, 우리는 이러한 데이터를 날것 그대로 사용하는 것은 의미가 없다고 판단했습니다. -->

Previously, we released FindSurface sample projects that runs on Google Android smartphones and Apple iPhones/iPads without LiDAR. These projects accumulate feature points to build a point cloud and detect geometry in real time. This approach is based on the fact that even though individual samples are noisy, the accumulated data tends to converge toward the true geometry under a roughly Gaussian error distribution (see [here (Android)]() and [here (iOS)](), if you are interested).
<!-- 우리는 최근에 Android Smartphone과 LiDAR가 탑재되지 않은 Apple iPhone/iPad에서도 사용할 수 있는 FindSurface의 sample project를 공개한 적이 있습니다. 이 프로젝트에서는 device의 motion tracking에서 산출되는 feature point를 수집/누적하여 point cloud를 생성함과 동시에 FindSurface를 이용해 real-time으로 geometry를 검출합니다. 이렇게 하면 매 시점에 얻는 데이터는 조금 부정확하더라도 누적된 데이터는 gaussian 확률 분포로 원래의 geometry를 대표할 가능성이 높습니다. -->

Inspired by this approach, we applied **Random sampling** to the depth map and used foveated sampling, collecting more samples near the center of the view.
<!-- 이와 유사한 방식으로 우리는 depth map에서 랜덤하게 depth sample을 수집하고 이를 시야 중심에 가까울 수록 더 많은 샘플을 얻는 foveated sampling을 사용했습니다. -->

![result](images/accumulated.png)
***Fig 3.** Accumulated point cloud generated using random and foveated sampling from depth maps. While planar surfaces become more stable through accumulation, temporal misalignment between depth maps and viewpoints introduces visible ghosting and spatial offsets.*

However, another significant issue emerged.
<!-- 이렇게 얻은 데이터는 또 다른 문제점을 가지고 있었습니다. -->

`DepthMap` and `RenderViewpoint` are published independently using `StateFlow`s with no synchronization mechanism.
- `DepthMap` updates roughly every 130~170 ms, randomly stalling up to 700 ms. (Maybe due to the garbage collector?)
- Left and right depth maps are delivered asynchronously.
- `RenderViewpoint`, by contrast, updates consistently at 16-17 ms.
<!-- DepthMap과 RenderViewpoint는 StateFlow를 이용해 각자 독립된 주기로 데이터를 publish합니다. collect하는 주기를 기록해보면 DepthMap은 약 150ms의 주기를 갖다가 랜덤하게 700ms까지 지연되기도 합니다. (Garbage Collector일까요? 아니면...) left와 right 모두 비동기적으로 데이터를 보냅니다. 그래서 어떤 경우에는 left가 700ms동안 데이터를 전혀 보내지 않고 있는 동안 right로부터만 네 번의 depth map이 도착하기도 합니다. 그에 반해 RenderViewpoint는 약 16-17ms의 주기로 꾸준히 데이터를 보내오는 것 같습니다. -->

Crucially, `DepthMap` provides no timestamp, making it impossible to determine when a given depth map was captured relative to the viewpoint. (`PerceptionState` might be the key to the sync issue, but no publicly documented way to obtain it.)

![result](images/accumulated.gif)
***Fig 4.** The three-layered appearance of the spherical surface in the point cloud (ghosting artifacts) strongly suggests that samples acquired at different times were not consistently associated with the correct camera pose.*

As a result, we were forced to associate each depth map with the most recently published `RenderViewpoint`. This led to visible ghosting artifacts, where samples from the same surface appeared slightly offset.
<!-- 이러한 상황에서 가장 난처한 점은 이 둘 사이의 연결고리가 없다는 것입니다. DepthMap에는 timestamp가 제공되지 않으므로 이 depth map이 어느 시점에 확보된 것인지 알 수 없습니다. 때문에 우리는 Depth map이 collect되는 그 시점에서 가장 최근에 발행된 RenderViewpoint를 직접 조회해 사용하는 수 밖에 없습니다. 실제로 우리가 얻은 데이터에는 마치 잔상처럼 동일한 곡면에 대한 샘플이 약간의 오프셋을 두고 얻어진 것을 확인할 수 있었습니다. 이렇게 되면 장치를 착용하고 점 구름을 수집하는 사람의 움직임에 따라 점 데이터의 오차가 매우 크게 벌어집니다. 우리는 이 시점에서 Galaxy XR에서 FindSurface를 활용하는 샘플앱 개발을 포기하고 이 문서를 공개하기로 결정했습니다. -->

Assuming a maximum walking speed of 1.2 m/s, even a 17 ms mismatch results in a positional error of approximately 20 cm. While this is a simplified worst-case estimate, combined with the previously discussed issues, the result were unsatisfactory.
<!-- 장치를 착용하고 이동하는 최대 속도를 1.2 m/s 라고 가정하고 depth map이 항상 별도의 delay없이 데이터가 확보되는 대로 주어진다고 가정하면 depth map과 viewpoint 사이의 위치 오차는 1.2 m/s * 0.017s = 0.204m이므로, 즉, 측정 샘플의 오차는 20.4cm까지도 벌어질 수 있습니다. 단순하게 과장된 수치이지만 후술할 사항들과 종합하면 꽤나 만족스럽지 못한 것은 사실입니다. -->

At this point, we decided to abandon further attempts at integrating **FindSurface** on this project and instead publish our findings here.

It is possible that the APIs used in this experiment were never intended to support metric point cloud reconstruction, and that the observed limitations reflect design constraints rather than implementation flaws.

## Desired Improvements

Android XR is still under active development (as of mid-December 2025, version 1.0.0-alphaXX). This platform currently lacks several capabilities necessary for deeper investigation and visualization.

Some of the following limitations are critical, while others are merely wishlist items:
<!-- Android XR은 아직 한창 개발중에 있으며(이 글을 작성하는 2025년 12월 중순 기준 1.0.0-alphaXX 단계) 따라서 우리가 확인하고자 하는 것들을 시각화하는데 필요한 도구가 제공되지 않았고, 그로인해 이 프로젝트는 위에서 설명한 것들 말고도 여러가지 한계점들을 가지고 있습니다. 우리는 Android XR과 Galaxy XR을 지켜보고 있으며 추후 개선사항에 따라서 상황이 달라진다면 이 작업을 다시 고려해 볼 생각이 있습니다. 다음은 그러한 한계점들의 목록이며, 어떤 것들은 반드시 개선되어야만 한다고 우리가 생각하는 것들이고, 어떤 것들은 필수는 아니고 그저 희망사항에 가깝습니다. -->

- There is no way to create a mesh entity directly from memory; only loading glTF files is supported. As a result, we could not visualize point clouds directly in 3D space and had to overlay them onto the depth map.

- Depth map updates are slow and irregular. While not entirely unusable, this makes immersive depth-based experiences in dynamic environments extremely difficult.

- Exposing motion-tracking feature points as a public API would be highly desirable. These points are already generated internally for tracking device motions so it would likely not be technically difficult to make the API public. It would unlock significant potential when combined with FindSurface (Again, see []() and [](), if interested).

<!-- - Gltf 포맷의 파일을 로딩하는 수단만 제공할 뿐 메모리에서 바로 Mesh를 그려내는 entity를 만들 수단이 없습니다. 따라서 3차원 공간상에 point cloud를 시각화 할 도구가 없으므로 깊이 맵 위에 overlay하여 그리는 수 밖에 없었습니다. -->

<!-- - Depth map의 데이터 제공 주기는 매우 느리고 규칙적이지 않습니다. 사용이 불가능한 수준은 아니지만 적어도 동적인 환경(스스로 움직이는 사물, 착용자 외 다른 인물이 있는)에서는 depth map에 의존하는 컨텐츠를 몰입감 있게 만들기는 어려울 것입니다.  -->

<!-- - motion tracking에서 산출되는 point cloud (feature point)가 공개된다면 아주 바람직할 것입니다. 이 데이터는 장치의 움직임을 추적하기 위해 이미 만들어지고 있을 겁니다. 이 데이터는 FindSurface와 함께 사용될 때 가장 큰 잠재력을 발휘할 수 있습니다. (무슨 뜻인지 궁금하다면 [여기](TODO)를 참조하세요.) -->


## Closing Remarks

We are not Android XR experts, and it is entirely possible that we have overlooked mistakes or misunderstood certain aspects of the platform. If you notice any errors, or if you have additional information that could help clarify these issues, please feel free to open an issue and share your insights.

Thank you for reading.
<!-- 우리는 Android XR 개발에 대해서 전문가가 아니므로 우리가 미처 보지 못하고 지나친 실수들이 있을 수 있습니다. 혹시라도 어떤 실수나 우리가 보지 못한 새로운 정보를 발견하셨고 그것을 우리와 공유하고 싶다면 issue에 자유롭게 남겨주세요.  -->