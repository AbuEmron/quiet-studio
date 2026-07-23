package com.quietstudio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietstudio.ui.theme.Ink
import com.quietstudio.ui.theme.QuietGradients
import com.quietstudio.ui.theme.Rose
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet

/**
 * The glowing mic button — the app's signature control.
 *
 * The halo is a pure radial gradient that fades to fully transparent at its
 * own edge, so it reads as a soft concentric ring hugging the button and can
 * never show a bounding box. (The previous effect — a sweep gradient pushed
 * through `blur()` and rotated — rendered its rectangular blur layer as a
 * spinning amber/purple square, and `blur` is a no-op below API 31, which made
 * the hard square edge worse on older phones.) A slow breathing pulse keeps it
 * alive without any rotation; radial symmetry means there is nothing to spin.
 */
@Composable
fun GlowMicButton(size: Dp, onClick: () -> Unit, iconScale: Float = 0.42f) {
    val breath = rememberInfiniteTransition(label = "glow")
    val pulse by breath.animateFloat(
        0.82f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    // Transparent under the button, brightest just past its rim, gone by the
    // halo's edge — contained, and works identically on every API level.
    val halo = Brush.radialGradient(
        0.00f to Color.Transparent,
        0.58f to Violet.copy(alpha = 0.55f),
        0.74f to Rose.copy(alpha = 0.30f),
        1.00f to Color.Transparent,
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * 1.5f)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = pulse
                }
                .background(halo, CircleShape),
        )
        Box(
            Modifier
                .size(size)
                .background(QuietGradients.primary, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Mic, "Record",
                tint = Color.White,
                modifier = Modifier.size(size * iconScale),
            )
        }
    }
}

enum class QuietTab { HOME, PROJECTS, MUSIC, SETTINGS }

/** Bottom navigation from the mock: 4 destinations + raised center mic. */
@Composable
fun QuietBottomBar(
    current: QuietTab?,
    onTab: (QuietTab) -> Unit,
    onRecord: () -> Unit,
) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF121218))
                .navigationBarsPadding()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(Icons.Rounded.Home, "Home", current == QuietTab.HOME,
                Modifier.weight(1f)) { onTab(QuietTab.HOME) }
            BottomItem(Icons.Rounded.Folder, "Projects", current == QuietTab.PROJECTS,
                Modifier.weight(1f)) { onTab(QuietTab.PROJECTS) }
            Box(Modifier.weight(1f))
            BottomItem(Icons.Rounded.MusicNote, "Music", current == QuietTab.MUSIC,
                Modifier.weight(1f)) { onTab(QuietTab.MUSIC) }
            BottomItem(Icons.Rounded.Settings, "Settings", current == QuietTab.SETTINGS,
                Modifier.weight(1f)) { onTab(QuietTab.SETTINGS) }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp),
        ) {
            GlowMicButton(size = 58.dp, onClick = onRecord)
        }
    }
}

@Composable
private fun BottomItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, label,
            tint = if (selected) Violet else TextSecondary,
            modifier = Modifier.size(23.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Violet else TextSecondary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Rounded dark search field from the mock. */
@Composable
fun QuietSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(19.dp))
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = Violet.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Circular progress ring (record screen pause button). */
@Composable
fun ProgressRing(
    progress: Float,
    size: Dp,
    stroke: Dp = 5.dp,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            drawArc(
                color = Color(0xFF262633),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Violet, Color(0xFF9D7DFF), Violet)),
                startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
            )
        }
        content()
    }
}

/** Small "screen header" used across secondary screens: ✕/‹ · title · ✓/action. */
@Composable
fun SheetHeader(
    title: String,
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onLeft: () -> Unit,
    right: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(Color(0xFF1E1E28), CircleShape)
                .clickable { onLeft() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(leftIcon, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) { right() }
    }
}

/** Violet filled circular action (the ✓ confirm chip). */
@Composable
fun ConfirmDot(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(36.dp)
            .background(Violet, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
