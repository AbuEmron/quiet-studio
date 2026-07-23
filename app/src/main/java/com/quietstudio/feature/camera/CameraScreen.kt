package com.quietstudio.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.ui.components.SheetHeader
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import kotlinx.coroutines.delay
import java.io.File

/**
 * In-app camera recorder (CameraX). Records video with audio to app-private
 * storage, front/rear flip, live elapsed time, then imports the clip into a
 * new project. Fully on-device.
 */
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onClipReady: (String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    val capture = remember { CameraCapture(context) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var frontLens by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { capture.release() } }

    // (Re)bind whenever we have permission or flip the lens.
    LaunchedEffect(hasCamera, frontLens) {
        if (hasCamera) {
            capture.bind(
                lifecycleOwner, previewView,
                lens = if (frontLens) CameraCapture.Lens.FRONT else CameraCapture.Lens.BACK,
            )
            viewModel.setLensFront(frontLens)
        }
    }

    // Elapsed-time ticker while recording.
    LaunchedEffect(ui.recording) {
        if (ui.recording) {
            val start = System.currentTimeMillis()
            while (true) {
                viewModel.setElapsed(System.currentTimeMillis() - start)
                delay(200)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    ) {
        SheetHeader("Record", Icons.AutoMirrored.Rounded.ArrowBack, onBack)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasCamera) {
                PermissionPrompt { permissionLauncher.launch(Manifest.permission.CAMERA) }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .background(Color.Black, RoundedCornerShape(16.dp)),
                ) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                    if (ui.recording) {
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color(0xCCB3261E), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(Color.White, CircleShape))
                            Spacer(Modifier.size(6.dp))
                            Text(fmtElapsed(ui.elapsedMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (ui.importing) {
                        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Violet)
                                Spacer(Modifier.height(10.dp))
                                Text("Bringing it into the editor…", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        ui.error?.let {
            Text(
                it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    .clickable { viewModel.setError(null) },
            )
        }

        if (hasCamera) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Flip
                Box(
                    Modifier.size(52.dp).background(Color(0x22FFFFFF), CircleShape)
                        .clickable(enabled = capture.canFlip && !ui.recording) { frontLens = !frontLens },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Cameraswitch, "Flip camera", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                // Record / stop
                RecordButton(recording = ui.recording, enabled = !ui.importing) {
                    if (ui.recording) {
                        capture.stop()
                    } else {
                        val out = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
                        viewModel.setRecording(true)
                        capture.start(out, withAudio = hasAudio) { event ->
                            when (event) {
                                is VideoRecordEvent.Finalize -> {
                                    viewModel.setRecording(false)
                                    if (event.hasError()) {
                                        viewModel.setError("Recording failed (${event.error})")
                                    } else {
                                        viewModel.importClip(out, onClipReady)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                Box(Modifier.size(52.dp)) // spacer to balance the flip button
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(74.dp)
            .border(3.dp, Color.White, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (recording) 30.dp else 58.dp)
                .background(
                    Color(0xFFE23B3B),
                    if (recording) RoundedCornerShape(8.dp) else CircleShape,
                ),
        )
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Record on-device",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Quiet Studio needs the camera to record video. The recording and everything " +
                "made from it stays on this device — nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Allow camera") }
    }
}

private fun fmtElapsed(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
