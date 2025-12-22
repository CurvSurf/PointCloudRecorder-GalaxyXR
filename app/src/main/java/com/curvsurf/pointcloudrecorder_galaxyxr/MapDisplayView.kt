package com.curvsurf.pointcloudrecorder_galaxyxr

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.GLSurfaceRenderer
import com.curvsurf.pointcloudrecorder_galaxyxr.helpers.OpenGLSurfaceView
import java.util.Locale

@SuppressLint("RestrictedApi")
@Composable
fun MapDisplayView(renderer: GLSurfaceRenderer.Renderer,
                   viewModel: AppViewModel = viewModel()) {

    val context = LocalContext.current
    val pointCount by viewModel.pointCount.collectAsStateWithLifecycle()

    Box {
        OpenGLSurfaceView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            context = context,
            renderer = renderer
        )

        Text(
            modifier = Modifier.align(Alignment.TopStart),
            text = String.format(locale = Locale.US, "Points: %6d pts.", pointCount)
        )
    }
}