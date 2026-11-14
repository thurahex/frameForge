package com.example.frameforge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbFilter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.frameforge.data.ProjectStore
import com.example.frameforge.data.VideoClip
import com.example.frameforge.media.ExportManager
import com.example.frameforge.media.MediaTools
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val Night = Color(0xFF0B0E17)
private val SurfaceNight = Color(0xFF181E2C)
private val Violet = Color(0xFF91A0FF)
private val Secondary = Color(0xFF9AA5BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Violet,
                    background = Night,
                    surface = SurfaceNight,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                FrameForgeScreen()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FrameForgeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ProjectStore(context) }
    val exportManager = remember { ExportManager(context) }

    var clips by remember { mutableStateOf(store.load()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var undoStack by remember { mutableStateOf<List<List<VideoClip>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<VideoClip>>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastExport by remember { mutableStateOf<Uri?>(null) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            // Initialize the shader pipeline *before* prepare, as required by ExoPlayer.
            setVideoEffects(emptyList())
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var playheadMs by remember { mutableStateOf(0L) }
    val selectedClip = clips.getOrNull(selectedIndex)
    val totalDuration = clips.sumOf { it.editedDurationMs }

    fun edit(next: List<VideoClip>) {
        if (next == clips || exporting) return
        undoStack = (undoStack + listOf(clips)).takeLast(30)
        redoStack = emptyList()
        clips = next
        selectedIndex = selectedIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
    }

    fun changeSelected(transform: (VideoClip) -> VideoClip) {
        val clip = clips.getOrNull(selectedIndex) ?: return
        edit(clips.toMutableList().apply { this[selectedIndex] = transform(clip) })
    }

    fun seekTimeline(positionMs: Long) {
        if (clips.isEmpty()) return
        var left = positionMs.coerceIn(0L, (totalDuration - 1).coerceAtLeast(0L))
        for (index in clips.indices) {
            val duration = clips[index].editedDurationMs
            if (left < duration || index == clips.lastIndex) {
                selectedIndex = index
                player.seekTo(index, left.coerceAtMost((duration - 1).coerceAtLeast(0)))
                return
            }
            left -= duration
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            scope.launch {
                val imported = mutableListOf<VideoClip>()
                val errors = mutableListOf<String>()
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        imported += MediaTools.importVideo(context, uri)
                    } catch (e: Exception) {
                        errors += (e.message ?: "Could not import ${uri.lastPathSegment}")
                    }
                }
                if (imported.isNotEmpty()) {
                    val before = clips.size
                    edit(clips + imported)
                    selectedIndex = before
                }
                importing = false
                if (errors.isNotEmpty()) {
                    message = "Import issue" to errors.take(3).joinToString("\n")
                }
            }
        }
    }

    LaunchedEffect(clips) {
        // All edits are instructions; only export generates new media.
        store.save(clips)
        player.pause()
        if (clips.isEmpty()) {
            player.clearMediaItems()
            playheadMs = 0L
        } else {
            val index = selectedIndex.coerceIn(0, clips.lastIndex)
            player.setMediaItems(clips.map(VideoClip::toMediaItem))
            player.setVideoEffects(if (clips[index].grayscale)
                listOf(RgbFilter.createGrayscaleFilter()) else emptyList())
            player.volume = if (clips[index].muted) 0f else 1f
            player.prepare()
            player.seekTo(index, 0L)
        }
    }

    DisposableEffect(player, clips) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                clips.getOrNull(index)?.let { clip ->
                    selectedIndex = index
                    player.volume = if (clip.muted) 0f else 1f
                    player.setVideoEffects(
                        if (clip.grayscale) listOf(RgbFilter.createGrayscaleFilter()) else emptyList()
                    )
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                message = "Playback issue" to (error.message ?: "The phone cannot decode this file")
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            exportManager.cancel()
        }
    }

    LaunchedEffect(player, clips) {
        while (true) {
            val index = player.currentMediaItemIndex
            if (index in clips.indices) {
                val prior = clips.take(index).sumOf { it.editedDurationMs }
                playheadMs = prior + player.currentPosition.coerceAtLeast(0)
            }
            delay(120)
        }
    }

    LaunchedEffect(exporting) {
        while (exporting) {
            exportProgress = exportManager.progress()
            delay(250)
        }
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceNight).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("FRAMEFORGE", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                    Text("ON-DEVICE VIDEO STUDIO", color = Secondary, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Button(
                    enabled = clips.isNotEmpty() && !exporting && !importing,
                    onClick = {
                        exporting = true
                        exportProgress = 0
                        exportManager.export(
                            clips = clips,
                            scope = scope,
                            onComplete = { uri ->
                                exporting = false
                                exportProgress = 100
                                lastExport = uri
                                message = "Export complete" to "Saved to Movies / FrameForge as MP4."
                            },
                            onFailure = { reason ->
                                exporting = false
                                exportProgress = null
                                message = "Export error" to reason
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (clips.isEmpty()) {
                EmptyProject(importing = importing, onImport = { picker.launch(arrayOf("video/*")) })
            } else {
                Surface(color = Color.Black, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            } },
                            modifier = Modifier.fillMaxSize(),
                            update = { view -> if (view.player !== player) view.player = player }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Violet)
                    }
                    Text(formatTime(playheadMs), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("  /  ${formatTime(totalDuration)}", fontSize = 12.sp, color = Secondary)
                    Spacer(Modifier.weight(1f))
                    Text("${clips.size} clip${if (clips.size == 1) "" else "s"}", color = Secondary, fontSize = 12.sp)
                }
                Slider(
                    value = playheadMs.coerceIn(0L, totalDuration).toFloat(),
                    onValueChange = { seekTimeline(it.toLong()) },
                    valueRange = 0f..totalDuration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("TIMELINE", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        if (undoStack.isNotEmpty() && !exporting) {
                            redoStack = redoStack + listOf(clips)
                            clips = undoStack.last()
                            undoStack = undoStack.dropLast(1)
                        }
                    }, enabled = undoStack.isNotEmpty() && !exporting) {
                        Icon(Icons.Default.Undo, "Undo")
                    }
                    IconButton(onClick = {
                        if (redoStack.isNotEmpty() && !exporting) {
                            undoStack = undoStack + listOf(clips)
                            clips = redoStack.last()
                            redoStack = redoStack.dropLast(1)
                        }
                    }, enabled = redoStack.isNotEmpty() && !exporting) {
                        Icon(Icons.Default.Redo, "Redo")
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("video/*")) },
                        enabled = !importing && !exporting,
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                        Text("Add clip")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    clips.forEachIndexed { index, clip ->
                        TimelineClip(clip, isSelected = index == selectedIndex) {
                            selectedIndex = index
                            player.seekTo(index, 0L)
                        }
                    }
                }

                selectedClip?.let { clip ->
                    ClipControls(
                        clip = clip,
                        positionMs = if (player.currentMediaItemIndex == selectedIndex) player.currentPosition else 0L,
                        canEdit = !exporting,
                        onTrim = { start, end -> changeSelected { it.copy(inMs = start, outMs = end) } },
                        onMute = { changeSelected { it.copy(muted = !it.muted) } },
                        onGrayscale = { changeSelected { it.copy(grayscale = !it.grayscale) } },
                        onSplit = {
                            val splitAt = clip.inMs + if (player.currentMediaItemIndex == selectedIndex)
                                player.currentPosition else 0L
                            if (splitAt - clip.inMs >= 300 && clip.outMs - splitAt >= 300) {
                                val first = clip.copy(outMs = splitAt)
                                val second = clip.copy(id = UUID.randomUUID().toString(), inMs = splitAt)
                                edit(clips.toMutableList().apply {
                                    this[selectedIndex] = first
                                    add(selectedIndex + 1, second)
                                })
                                selectedIndex++
                            } else message = "Cannot split" to "Move the playhead inside the clip, at least 0.3 seconds from its edges."
                        },
                        onMoveLeft = {
                            if (selectedIndex > 0) {
                                val index = selectedIndex
                                edit(clips.toMutableList().apply { add(index - 1, removeAt(index)) })
                                selectedIndex = index - 1
                            }
                        },
                        onMoveRight = {
                            if (selectedIndex < clips.lastIndex) {
                                val index = selectedIndex
                                edit(clips.toMutableList().apply { add(index + 1, removeAt(index)) })
                                selectedIndex = index + 1
                            }
                        },
                        canMoveLeft = selectedIndex > 0,
                        canMoveRight = selectedIndex < clips.lastIndex,
                        onRemove = { edit(clips.toMutableList().apply { removeAt(selectedIndex) }) }
                    )
                }
            }

            if (importing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading selected videos…", color = Secondary)
                }
            }
            if (exporting) {
                Surface(color = SurfaceNight, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Exporting your video", fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(
                            progress = { ((exportProgress ?: 0).toFloat() / 100f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${exportProgress?.let { "$it%" } ?: "Preparing…"}", color = Secondary, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { exportManager.cancel(); exporting = false }) { Text("Cancel") }
                        }
                    }
                }
            }
            Text(
                "Autosaved on this device · Original footage is never changed · MP4 output",
                fontSize = 11.sp, color = Secondary,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 18.dp)
            )
        }
    }

    message?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(onClick = { message = null }) { Text("OK") }
            },
            dismissButton = {
                if (title == "Export complete" && lastExport != null) OutlinedButton(onClick = {
                    val uri = lastExport
                    message = null
                    if (uri != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                }) { Text("Open video") }
            }
        )
    }
}

@Composable
private fun EmptyProject(importing: Boolean, onImport: () -> Unit) {
    Surface(color = SurfaceNight, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(52.dp), tint = Violet)
            Text("Create something great", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Import one or several clips to start a new edit. Everything runs locally.",
                color = Secondary, fontSize = 13.sp)
            Button(onClick = onImport, enabled = !importing) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Import videos")
            }
        }
    }
}

@Composable
private fun TimelineClip(clip: VideoClip, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val frames by produceState<List<ImageBitmap>>(
        initialValue = emptyList(),
        key1 = clip.sourceUri, key2 = clip.inMs, key3 = clip.outMs
    ) { value = MediaTools.thumbnails(context, clip, 4) }
    Surface(
        color = if (isSelected) Color(0xFF2A3456) else SurfaceNight,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Violet else Color(0xFF343D50)),
        modifier = Modifier.width(176.dp).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(6.dp)) {
            Row(Modifier.fillMaxWidth().height(62.dp)) {
                if (frames.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF343B4D)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = Secondary)
                    }
                } else {
                    frames.forEach { frame ->
                        Image(
                            bitmap = frame,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).height(62.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(clip.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(formatTime(clip.editedDurationMs), color = Secondary, fontSize = 10.sp)
        }
    }
}

@Composable
@kotlin.OptIn(ExperimentalMaterial3Api::class)
private fun ClipControls(
    clip: VideoClip,
    positionMs: Long,
    canEdit: Boolean,
    onTrim: (Long, Long) -> Unit,
    onMute: () -> Unit,
    onGrayscale: () -> Unit,
    onSplit: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onRemove: () -> Unit
) {
    var trimRange by remember(clip.id, clip.inMs, clip.outMs) {
        mutableStateOf(clip.inMs.toFloat()..clip.outMs.toFloat())
    }
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceNight) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CLIP EDITOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(clip.editedDurationMs), fontSize = 12.sp, color = Secondary)
            }
            HorizontalDivider(color = Color(0xFF30384B))
            Text("Trim in / out", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            RangeSlider(
                value = trimRange,
                onValueChange = { new ->
                    val start = new.start.coerceIn(0f, (clip.sourceDurationMs - 300).toFloat())
                    val end = new.endInclusive.coerceIn(start + 300f, clip.sourceDurationMs.toFloat())
                    trimRange = start..end
                },
                onValueChangeFinished = {
                    onTrim(trimRange.start.toLong(), trimRange.endInclusive.toLong())
                },
                valueRange = 0f..clip.sourceDurationMs.toFloat(),
                enabled = canEdit
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("IN ${formatTime(trimRange.start.toLong())}", fontSize = 11.sp, color = Secondary)
                Text("OUT ${formatTime(trimRange.endInclusive.toLong())}", fontSize = 11.sp, color = Secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterChip(
                    selected = clip.muted, onClick = onMute, enabled = canEdit,
                    label = { Text(if (clip.muted) "Muted" else "Mute audio") }
                )
                FilterChip(
                    selected = clip.grayscale, onClick = onGrayscale, enabled = canEdit,
                    label = { Text("B&W filter") }
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSplit, enabled = canEdit) {
                    Icon(Icons.Default.ContentCut, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Split")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveLeft, enabled = canEdit && canMoveLeft) {
                    Icon(Icons.Default.ArrowBack, "Move clip left")
                }
                IconButton(onClick = onMoveRight, enabled = canEdit && canMoveRight) {
                    Icon(Icons.Default.ArrowForward, "Move clip right")
                }
                IconButton(onClick = onRemove, enabled = canEdit) {
                    Icon(Icons.Default.DeleteOutline, "Remove clip", tint = Color(0xFFFF858F))
                }
            }
            Text("Trim · Split · Reorder · Mute · Grayscale · Undo / Redo",
                color = Secondary, fontSize = 11.sp)
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1_000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
