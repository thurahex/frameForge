package com.example.frameforge.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Non-destructive editing: the source files are never modified. */
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val displayName: String,
    val sourceDurationMs: Long,
    val inMs: Long = 0L,
    val outMs: Long = sourceDurationMs,
    val muted: Boolean = false,
    val grayscale: Boolean = false
) {
    val editedDurationMs: Long get() = (outMs - inMs).coerceAtLeast(0)

    @OptIn(UnstableApi::class)
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(Uri.parse(sourceUri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(inMs)
                .setEndPositionMs(outMs)
                .build()
        ).build()
}

/** Stores a single autosaved draft on this device. Saved media permissions survive app restarts. */
class ProjectStore(context: Context) {
    private val prefs = context.getSharedPreferences("frameforge_project", Context.MODE_PRIVATE)

    fun load(): List<VideoClip> = try {
        val arr = JSONArray(prefs.getString("clips", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val item = arr.getJSONObject(i)
            val sourceDuration = item.getLong("sourceDurationMs")
            val end = item.optLong("outMs", sourceDuration)
            if (sourceDuration < 500L || end <= item.optLong("inMs", 0L)) null
            else VideoClip(
                id = item.getString("id"),
                sourceUri = item.getString("sourceUri"),
                displayName = item.optString("displayName", "Video"),
                sourceDurationMs = sourceDuration,
                inMs = item.optLong("inMs", 0L),
                outMs = end,
                muted = item.optBoolean("muted", false),
                grayscale = item.optBoolean("grayscale", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(clips: List<VideoClip>) {
        val arr = JSONArray()
        clips.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("sourceUri", c.sourceUri)
                put("displayName", c.displayName)
                put("sourceDurationMs", c.sourceDurationMs)
                put("inMs", c.inMs)
                put("outMs", c.outMs)
                put("muted", c.muted)
                put("grayscale", c.grayscale)
            })
        }
        prefs.edit().putString("clips", arr.toString()).apply()
    }
}
