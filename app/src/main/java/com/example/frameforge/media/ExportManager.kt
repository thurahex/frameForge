package com.example.frameforge.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.Effects
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.frameforge.data.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Runs Media3 transformations entirely on the phone, then publishes an MP4 to Movies/FrameForge. */
@OptIn(UnstableApi::class)
class ExportManager(private val context: Context) {
    private var transformer: Transformer? = null
    private var tempFile: File? = null
    private var active = false

    fun export(
        clips: List<VideoClip>,
        onComplete: suspend (Uri) -> Unit,
        onFailure: (String) -> Unit,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        check(!active) { "Export is already running" }
        require(clips.isNotEmpty()) { "Add a video first" }
        val draftFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        tempFile = draftFile
        active = true
        try {
            val items = clips.map { clip ->
                EditedMediaItem.Builder(clip.toMediaItem())
                    .setRemoveAudio(clip.muted)
                    .setEffects(
                        Effects(
                            emptyList(),
                            if (clip.grayscale) listOf(RgbFilter.createGrayscaleFilter()) else emptyList()
                        )
                    )
                    .build()
            }
            val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
                .addItems(items)
                .build()
            val composition = Composition.Builder(listOf(sequence)).build()
            val newTransformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        active = false
                        transformer = null
                        scope.launchSafe {
                            try {
                                val uri = publishToGallery(context, draftFile)
                                onComplete(uri)
                            } catch (e: Exception) {
                                onFailure("Exported, but saving to Gallery failed: ${e.message}")
                            } finally {
                                draftFile.delete()
                                tempFile = null
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        active = false
                        transformer = null
                        draftFile.delete()
                        tempFile = null
                        onFailure("Export failed: ${exception.message}")
                    }
                }).build()
            transformer = newTransformer
            newTransformer.start(composition, draftFile.absolutePath)
        } catch (e: Exception) {
            active = false
            transformer = null
            draftFile.delete()
            tempFile = null
            onFailure("Cannot start export: ${e.message}")
        }
    }

    fun progress(): Int? {
        val instance = transformer ?: return null
        val holder = ProgressHolder()
        return if (instance.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE)
            holder.progress else null
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
        active = false
        tempFile?.delete()
        tempFile = null
    }

    companion object {
        private suspend fun publishToGallery(context: Context, input: File): Uri =
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "FrameForge_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FrameForge")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Gallery refused to create output file")
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        input.inputStream().use { it.copyTo(output) }
                    } ?: error("Cannot write Gallery video")
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }, null, null)
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }
    }
}

/** Launch callbacks on the Activity's Compose scope without blocking the export listener. */
private fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) =
    launch { block() }
