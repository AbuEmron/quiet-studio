package com.quietstudio.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.core.media.SceneRenderer
import com.quietstudio.core.media.SubtitlePainter
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.ui.components.SheetHeader
import com.quietstudio.ui.theme.CardHigh
import com.quietstudio.ui.theme.QuietGradients
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import com.quietstudio.ui.theme.VioletSoft

enum class EditorSheet { NONE, CAPTIONS, VISUAL, MUSIC, VOLUME, EXPORT }

/**
 * Edit Project — reference layout: preview + right tool rail, filmstrip
 * timeline with playhead, waveform track, bottom edit toolbar. Every tool
 * opens a bottom sheet; everything autosaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onExports: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val content by viewModel.contentState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(EditorSheet.NONE) }
    var captionsTab by remember { mutableStateOf(0) } // 0 Edit · 1 Style · 2 Animation
    var renameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                title = ui.project?.title ?: "Edit Project",
                leftIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onLeft = { viewModel.pause(); onBack() },
            ) {
                androidx.compose.material3.IconButton(onClick = { renameDialog = true }) {
                    Icon(
                        Icons.Rounded.Settings, "Project settings",
                        tint = TextSecondary, modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    Modifier
                        .background(QuietGradients.primary, RoundedCornerShape(12.dp))
                        .clickable { sheet = EditorSheet.EXPORT }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("Export", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }

            content?.let { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    /* ------------------------- preview -------------------------- */
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f)
                            .align(Alignment.CenterVertically)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        PreviewSurface(
                            content = c,
                            positionMs = ui.positionMs,
                            durationMs = ui.durationMs,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { viewModel.togglePlay() }
                                },
                        )
                        // timecode chip
                        Text(
                            "${fmt(ui.positionMs)} / ${fmt(ui.durationMs)}",
                            style = MaterialTheme.typography.labelSmall, color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                        // play state hint
                        if (!ui.playing) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(52.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow, "Play",
                                    tint = Color.White, modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }

                    /* ------------------------- tool rail ------------------------- */
                    Column(
                        Modifier
                            .width(118.dp)
                            .fillMaxSize()
                            .padding(start = 10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RailItem(Icons.Rounded.ClosedCaption, "Captions", true) {
                            captionsTab = 0; sheet = EditorSheet.CAPTIONS
                        }
                        RailItem(Icons.Rounded.FormatPaint, "Styles") {
                            captionsTab = 1; sheet = EditorSheet.CAPTIONS
                        }
                        RailItem(Icons.Rounded.Animation, "Animation") {
                            captionsTab = 2; sheet = EditorSheet.CAPTIONS
                        }
                        RailItem(Icons.Rounded.VerticalAlignCenter, "Position") {
                            captionsTab = 1; sheet = EditorSheet.CAPTIONS
                        }
                        RailItem(Icons.Rounded.AutoAwesome, "Effects") { sheet = EditorSheet.VISUAL }
                        RailItem(Icons.Rounded.Wallpaper, "Background") { sheet = EditorSheet.VISUAL }
                        RailItem(
                            Icons.Rounded.MusicNote, "Music",
                            subtitle = viewModel.musicLibrary.byId(c.music.trackId)?.title ?: "None",
                        ) { sheet = EditorSheet.MUSIC }
                        RailItem(
                            Icons.AutoMirrored.Rounded.VolumeUp, "Volume",
                            subtitle = "Auto Ducking",
                        ) { sheet = EditorSheet.VOLUME }
                    }
                }

                /* --------------------------- timeline --------------------------- */
                FilmstripTimeline(
                    content = c,
                    positionMs = ui.positionMs,
                    durationMs = ui.durationMs,
                    onSeek = viewModel::seekTo,
                )
                WaveformTrack(
                    envelope = viewModel.waveformPreview,
                    positionMs = ui.positionMs,
                    durationMs = ui.durationMs,
                    onSeek = viewModel::seekTo,
                    playing = ui.playing,
                    onTogglePlay = viewModel::togglePlay,
                )

                /* ------------------------- bottom toolbar ------------------------ */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolbarItem(Icons.Rounded.ContentCut, "Split") {
                        viewModel.splitCueAtPlayhead()
                    }
                    ToolbarItem(Icons.Rounded.Tune, "Trim") {
                        viewModel.toast("Re-trim silence from the Record screen")
                    }
                    ToolbarItem(Icons.Rounded.Delete, "Delete") {
                        viewModel.deleteCueAtPlayhead()
                    }
                    ToolbarItem(Icons.Rounded.ContentCopy, "Duplicate") {
                        viewModel.duplicateProject()
                    }
                    ToolbarItem(Icons.Rounded.Speed, "Speed") {
                        viewModel.toast("Speed control arrives in v1.1")
                    }
                    ToolbarItem(Icons.Rounded.AutoAwesome, "Filters") { sheet = EditorSheet.VISUAL }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (renameDialog) {
        var name by remember { mutableStateOf(ui.project?.title ?: "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Project name") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.renameProject(name)
                    renameDialog = false
                }) { Text("Save", color = VioletSoft) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { renameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    /* ------------------------------ sheets ------------------------------- */
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (sheet != EditorSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { sheet = EditorSheet.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            content?.let { c ->
                when (sheet) {
                    EditorSheet.CAPTIONS -> CaptionsSheet(
                        c, ui, viewModel,
                        tab = captionsTab, onTab = { captionsTab = it },
                        onClose = { sheet = EditorSheet.NONE },
                    )
                    EditorSheet.VISUAL -> VisualSheet(c, viewModel) { sheet = EditorSheet.NONE }
                    EditorSheet.MUSIC -> MusicSheet(c, viewModel) { sheet = EditorSheet.NONE }
                    EditorSheet.VOLUME -> VolumeSheet(c, viewModel) { sheet = EditorSheet.NONE }
                    EditorSheet.EXPORT -> ExportSheet(c, viewModel, onExports) { sheet = EditorSheet.NONE }
                    EditorSheet.NONE -> Unit
                }
            }
        }
    }
}

/* ------------------------------ components -------------------------------- */

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) Violet.copy(alpha = 0.16f) else CardHigh,
                RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, label,
            tint = if (selected) VioletSoft else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it, style = MaterialTheme.typography.labelSmall,
                    color = VioletSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolbarItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(19.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = TextSecondary, modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Filmstrip: procedural frames of the project's visual across the timeline. */
@Composable
private fun FilmstripTimeline(
    content: ProjectContent,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val key = content.visual.hashCode()
    val renderer = remember(key) { SceneRenderer(64, 114) }
    val frames = 8
    val bitmaps = remember(key) {
        (0 until frames).map { i ->
            val bmp = android.graphics.Bitmap.createBitmap(64, 114, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            cv.drawColor(android.graphics.Color.BLACK)
            renderer.drawFrame(cv, content.visual, durationMs * i / frames, durationMs.coerceAtLeast(1))
            bmp
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(48.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(durationMs) {
                    detectTapGestures { o ->
                        onSeek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs))
                    }
                },
        ) {
            val fw = size.width / frames
            bitmaps.forEachIndexed { i, bmp ->
                drawContext.canvas.nativeCanvas.drawBitmap(
                    bmp, null,
                    android.graphics.RectF(i * fw, 0f, (i + 1) * fw, size.height), null,
                )
            }
            // playhead
            val x = if (durationMs > 0) size.width * positionMs / durationMs else 0f
            drawLine(
                Color.White, Offset(x, -4.dp.toPx()), Offset(x, size.height + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun WaveformTrack(
    envelope: List<Float>,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    playing: Boolean,
    onTogglePlay: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(CardHigh, RoundedCornerShape(8.dp))
                .clickable { onTogglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Play", tint = VioletSoft, modifier = Modifier.size(16.dp),
            )
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(34.dp)
                .padding(start = 8.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { o ->
                        onSeek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs))
                    }
                },
        ) {
            val n = 90
            val w = size.width / n
            val mid = size.height / 2
            val playX = if (durationMs > 0) size.width * positionMs / durationMs else 0f
            for (i in 0 until n) {
                val a = if (envelope.isEmpty()) 0.15f
                else envelope[(i * envelope.size / n).coerceAtMost(envelope.size - 1)]
                val h = (a * size.height).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    if (i * w < playX) Violet else Color(0xFF3A3A4A),
                    Offset(i * w + w * 0.25f, mid - h / 2),
                    androidx.compose.ui.geometry.Size(w * 0.5f, h),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.25f),
                )
            }
        }
    }
}

/** Live preview: the same SceneRenderer + SubtitlePainter as export. */
@Composable
fun PreviewSurface(
    content: ProjectContent,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember { SceneRenderer(540, 960) }
    val subtitles = remember { SubtitlePainter(540, 960, context) }
    val sourceUri = content.visual.sourceUri
    LaunchedEffect(sourceUri, content.visual.kind) {
        if (BackgroundKind.valueOf(content.visual.kind) == BackgroundKind.IMAGE && sourceUri != null) {
            val bmp = runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(sourceUri))
                    ?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
            renderer.setSourceBitmap(sourceUri, bmp)
        } else renderer.setSourceBitmap(null, null)
    }
    val bmp = remember {
        android.graphics.Bitmap.createBitmap(540, 960, android.graphics.Bitmap.Config.ARGB_8888)
    }
    val cv = remember { android.graphics.Canvas(bmp) }
    Canvas(modifier.background(Color.Black)) {
        cv.drawColor(android.graphics.Color.BLACK)
        renderer.drawFrame(cv, content.visual, positionMs, durationMs)
        subtitles.draw(cv, content.cues, content.subtitleStyle, positionMs)
        drawContext.canvas.nativeCanvas.drawBitmap(
            bmp, null,
            android.graphics.RectF(0f, 0f, size.width, size.height), null,
        )
    }
}

internal fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
