package com.quietstudio.feature.record

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.ui.components.ConfirmDot
import com.quietstudio.ui.components.ProgressRing
import com.quietstudio.ui.components.SheetHeader
import com.quietstudio.ui.theme.CardHigh
import com.quietstudio.ui.theme.Danger
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import com.quietstudio.ui.theme.VioletSoft

@Composable
fun RecordScreen(
    existingProjectId: String?,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importAudio(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SheetHeader(
            title = "Record",
            leftIcon = Icons.Rounded.Close,
            onLeft = { viewModel.discard(); onBack() },
        ) {
            if (ui.stage == RecordViewModel.Stage.REVIEW && ui.busyLabel == null) {
                ConfirmDot(onClick = { viewModel.finish(onDone) }, icon = Icons.Rounded.Check)
            }
        }

        Spacer(Modifier.weight(0.32f))

        Text(
            formatMs(ui.durationMs),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            when {
                ui.transcribing -> "Transcribing…"
                ui.busyLabel != null -> "${ui.busyLabel}…"
                ui.stage == RecordViewModel.Stage.RECORDING -> "Recording…"
                ui.stage == RecordViewModel.Stage.REVIEW -> "Ready — polish or confirm"
                else -> "Tap the ring to start"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (ui.error != null) Danger else VioletSoft,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
        )
        ui.error?.let {
            Text(
                it, color = Danger, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(4.dp),
            )
        }

        Spacer(Modifier.height(22.dp))

        LiveWaveform(
            amplitudes = ui.waveform,
            live = ui.stage == RecordViewModel.Stage.RECORDING,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 22.dp),
        )

        Spacer(Modifier.height(24.dp))

        // DSP chips row (mock: Noise Reduction / Auto Normalize / Trim Silence)
        val chipsEnabled = ui.stage == RecordViewModel.Stage.REVIEW && ui.busyLabel == null
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DspChip(Icons.Rounded.AutoFixHigh, "Noise Reduction", chipsEnabled,
                Modifier.weight(1f), viewModel::applyNoiseReduction)
            DspChip(Icons.Rounded.GraphicEq, "Auto Normalize", chipsEnabled,
                Modifier.weight(1f), viewModel::applyNormalize)
            DspChip(Icons.Rounded.ContentCut, "Trim Silence", chipsEnabled,
                Modifier.weight(1f), viewModel::applyTrimSilence)
        }

        Spacer(Modifier.weight(0.4f))

        // transport: undo · ring · redo
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(
                Icons.AutoMirrored.Rounded.Undo,
                enabled = ui.canUndo, onClick = viewModel::undo,
            )
            Spacer(Modifier.width(30.dp))
            val ringProgress by animateFloatAsState(
                targetValue = when (ui.stage) {
                    RecordViewModel.Stage.RECORDING -> ((ui.durationMs % 60000) / 60000f)
                    RecordViewModel.Stage.REVIEW -> 1f
                    else -> 0f
                },
                label = "ring",
            )
            ProgressRing(progress = ringProgress, size = 108.dp) {
                Box(
                    Modifier
                        .size(84.dp)
                        .background(
                            Brush.linearGradient(listOf(VioletSoft, Violet)),
                            CircleShape,
                        )
                        .clickable {
                            when (ui.stage) {
                                RecordViewModel.Stage.RECORDING -> viewModel.stopRecording()
                                RecordViewModel.Stage.READY ->
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                RecordViewModel.Stage.REVIEW -> viewModel.discardAndRerecord()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.busyLabel != null || ui.transcribing) {
                        CircularProgressIndicator(
                            Modifier.size(30.dp), strokeWidth = 3.dp, color = Color.White,
                        )
                    } else {
                        Icon(
                            when (ui.stage) {
                                RecordViewModel.Stage.RECORDING -> Icons.Rounded.Pause
                                else -> Icons.Rounded.Mic
                            },
                            "Record / stop", tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(30.dp))
            RoundIcon(
                Icons.AutoMirrored.Rounded.Redo,
                enabled = ui.canRedo, onClick = viewModel::redo,
            )
        }

        Spacer(Modifier.height(26.dp))

        // bottom scrubber strip (mock's small waveform pill)
        MiniStrip(
            amplitudes = ui.waveform,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.68f)
                .height(38.dp),
        )

        // import path, quiet at the bottom
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { importPicker.launch(arrayOf("audio/*", "video/*")) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Upload, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            Text(
                "  Import audio instead",
                style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DspChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(CardHigh, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, label,
            tint = if (enabled) VioletSoft else TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White else TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(CardHigh, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = if (enabled) Color.White else TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LiveWaveform(amplitudes: List<Float>, live: Boolean, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(if (live) 1f else 0.75f, label = "glow")
    Canvas(modifier) {
        val n = amplitudes.size.coerceAtLeast(1)
        val w = size.width / n
        val mid = size.height / 2
        if (amplitudes.isEmpty()) {
            drawRoundRect(
                Color(0xFF262633), Offset(0f, mid - 1.dp.toPx()),
                Size(size.width, 2.dp.toPx()), CornerRadius(1.dp.toPx()),
            )
        }
        amplitudes.forEachIndexed { i, a ->
            val h = (a * size.height * 0.95f).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF9D7DFF), Color(0xFF7C5CFC), Color(0xFF5B3BD6)),
                    startY = mid - h / 2, endY = mid + h / 2,
                ),
                topLeft = Offset(i * w + w * 0.25f, mid - h / 2),
                size = Size(w * 0.5f, h),
                cornerRadius = CornerRadius(w * 0.25f),
                alpha = glow * (0.45f + 0.55f * a),
            )
        }
    }
}

@Composable
private fun MiniStrip(amplitudes: List<Float>, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(CardHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
    ) {
        val n = 60
        val w = size.width / n
        val mid = size.height / 2
        for (i in 0 until n) {
            val a = if (amplitudes.isEmpty()) 0.12f
            else amplitudes[(i * amplitudes.size / n).coerceAtMost(amplitudes.size - 1)]
            val h = (a * size.height * 0.8f).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                Color(0xFF6B6B7A),
                Offset(i * w + w * 0.3f, mid - h / 2),
                Size(w * 0.4f, h),
                CornerRadius(w * 0.2f),
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
