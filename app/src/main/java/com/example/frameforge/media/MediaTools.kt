package com.example.frameforge.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.frameforge.data.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaTools {
    suspend fun importVideo(context: Context, uri: Uri): VideoClip = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("Cannot read video duration")
            require(duration >= 500L) { "Video must be at least 0.5 seconds long" }
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            require(hasVideo != "no") { "Please select a video, not audio" }
            VideoClip(
                sourceUri = uri.toString(),
                displayName = displayName(context, uri),
                sourceDurationMs = duration
            )
        } finally {
            retriever.release()
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: "Imported video"
    }

    /** Small thumbnails on an IO thread; never decode full 4K frames into UI memory. */
    suspend fun thumbnails(context: Context, clip: VideoClip, count: Int = 5): List<ImageBitmap> =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(clip.sourceUri))
                (0 until count).mapNotNull { i ->
                    val timeMs = clip.inMs + (clip.editedDurationMs * (i + 0.5) / count).toLong()
                    val raw = retriever.getFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    raw?.let {
                        val height = 80
                        val width = (it.width.toFloat() * height / it.height.coerceAtLeast(1)).toInt().coerceIn(40, 180)
                        val small: Bitmap = Bitmap.createScaledBitmap(it, width, height, true)
                        if (it !== small) it.recycle()
                        small.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                retriever.release()
            }
        }
}
