package com.curvsurf.pointcloudrecorder_galaxyxr.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.FloatBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * Exports the accumulated point cloud so far as an XYZ‑format file.
 * Once the conversion is finished, a share activity is launched to share or send the resulting file.
 */
suspend fun exportPointCloud(context: Context,
                             pointBuffer: FloatBuffer, pointCount: Int,
                             confidenceThreshold: Float,
                             progress: (Float) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val filename = "pointcloud_${getCurrentTimestampString()}.xyz"
            val file = File(context.cacheDir, filename)
            BufferedWriter(FileWriter(file)).use { writer ->
                for (i in 0 until pointCount) {
                    val baseIndex = i * 4
                    val x = pointBuffer.get(baseIndex)
                    val y = pointBuffer.get(baseIndex + 1)
                    val z = pointBuffer.get(baseIndex + 2)
                    val confidence = pointBuffer.get(baseIndex + 3)

                    if (confidence >= confidenceThreshold) {
                        writer.write("$x $y $z\n")
                        writer.newLine()
                    }
                    progress((i + 1).toFloat() / pointCount.toFloat())
                }
            }

            withContext(Dispatchers.Main) {
                shareFile(context, file)
            }
        } catch (e: Exception) {
            Log.e("FileExport", "Failed to save file", e)
        }
    }
}

private fun getCurrentTimestampString(): String {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    return now.format(formatter)
}

private fun shareFile(context: Context, file: File) {
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, file)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Sharing exported point cloud"))
}