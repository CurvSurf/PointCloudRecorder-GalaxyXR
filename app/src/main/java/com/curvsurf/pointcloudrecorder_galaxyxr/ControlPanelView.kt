package com.curvsurf.pointcloudrecorder_galaxyxr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ControlPanelView(onSizeDetermined: (width: Int, height: Int) -> Unit,
                     viewModel: AppViewModel = viewModel()) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()

    TransparentBorderedPanel(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .onGloballyPositioned { layoutCoordinates ->
                val size = layoutCoordinates.size
                onSizeDetermined(size.width, size.height)
            }
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MapDisplayPicker(displayMode = uiState.mapDisplayMode,
                             onDisplayModeChanged = viewModel::setMapDisplayMode)

            RecordingSwitch(checked = uiState.recordingEnabled,
                            onClick = viewModel::setRecordingEnabled)

            PointCloudVisibilitySwitch(checked = uiState.pointsVisible,
                                       onClick = viewModel::setPointVisible)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.DarkGray.copy(alpha = 0.3f)
                )
            ) {
                TextButton(
                    onClick = viewModel::clearPoints
                ) {
                    Text(text = "Clear All Points", color = Color.White)
                }
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.DarkGray.copy(alpha = 0.3f)
                )
            ) {
                TextButton(onClick = viewModel::exportPointCloud) {
                    if (uiState.exporting) {
                        LinearProgressIndicator(progress = { exportProgress })
                    } else {
                        Text(text = "Export Points to XYZ", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapDisplayPicker(modifier: Modifier = Modifier,
                     displayMode: MapDisplayMode,
                     onDisplayModeChanged: (MapDisplayMode) -> Unit) {
    val items = MapDisplayMode.entries

    @Composable
    fun ItemLabel(displayMode: MapDisplayMode) {
        when (displayMode) {
            MapDisplayMode.DEPTH_MAP -> Text(text = "Depth")
            MapDisplayMode.CONFIDENCE_MAP -> Text(text = "Confidence")
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray.copy(alpha = 0.3f)
        ),
        modifier = modifier
    ) {
        SingleChoiceSegmentedButtonRow {
            items.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = displayMode == mode,
                    onClick = { onDisplayModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, items.size)) {
                    ItemLabel(mode)
                }
            }
        }
    }
}

@Composable
private fun RecordingSwitch(checked: Boolean,
                            onClick: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Recording", color = Color.White)
            Switch(checked = checked, onCheckedChange = onClick)
        }
    }
}

@Composable
private fun PointCloudVisibilitySwitch(checked: Boolean,
                                       onClick: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Show Point Cloud", color = Color.White)
            Switch(checked = checked, onCheckedChange = onClick)
        }
    }
}

@Composable
private fun TransparentBorderedPanel(modifier: Modifier = Modifier,
                                     cornerRadius: Dp = 24.dp,
                                     borderWidth: Dp = 3.dp,
                                     borderColor: Color = Color.White,
                                     backgroundColor: Color = Color.Black.copy(alpha = 0.2f),
                                     contentPadding: PaddingValues = PaddingValues(24.dp),
                                     content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .border(width = borderWidth, color = borderColor, shape = shape)
            .background(color = backgroundColor, shape = shape)
            .clip(shape)
            .padding(contentPadding)
    ) {
        content()
    }
}