package com.quietstudio.feature.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.Codec
import com.quietstudio.core.model.MotionEffect
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.Resolution
import com.quietstudio.core.media.scenes.SceneCatalog
import com.quietstudio.core.model.SceneryThemes
import com.quietstudio.core.model.SubtitleAnimation
import com.quietstudio.core.model.SubtitleCue
import com.quietstudio.core.model.SubtitlePosition
import com.quietstudio.core.model.SubtitleStyle
import com.quietstudio.feature.music.TrackArt
import com.quietstudio.ui.components.ConfirmDot
import com.quietstudio.ui.theme.CardHigh
import com.quietstudio.ui.theme.Highlight
import com.quietstudio.ui.theme.QuietGradients
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import com.quietstudio.ui.theme.VioletSoft

/* ========================= CAPTIONS (Edit·Style·Animation) ================ */

@Composable
fun CaptionsSheet(
    content: ProjectContent,
    ui: EditorViewModel.UiState,
    vm: EditorViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.padding(bottom = 20.dp)) {
        SheetTitleRow("Captions", onClose)

        // live caption preview card (mock: dark card, gold emphasis word)
        val previewCue = content.cues.firstOrNull { ui.positionMs in it.startMs until it.endMs }
            ?: content.cues.firstOrNull()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Color(0xFF0F0F15), RoundedCornerShape(16.dp))
                .padding(vertical = 26.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val text = previewCue?.text ?: "Your captions appear here"
            val emphasized = text.split(" ").maxByOrNull { it.length }
            Text(
                buildAnnotatedCaption(text, emphasized, content.subtitleStyle.highlightEveryNthWord > 0),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[tab]),
                    color = Violet,
                )
            },
        ) {
            listOf("Edit", "Style", "Animation").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i, onClick = { onTab(i) },
                    text = {
                        Text(
                            label, style = MaterialTheme.typography.labelLarge,
                            color = if (tab == i) Color.White else TextSecondary,
                        )
                    },
                )
            }
        }

        when (tab) {
            0 -> CaptionsEditTab(content, ui, vm)
            1 -> CaptionsStyleTab(content, vm)
            2 -> CaptionsAnimationTab(content, vm)
        }
    }
}

@Composable
private fun CaptionsEditTab(content: ProjectContent, ui: EditorViewModel.UiState, vm: EditorViewModel) {
    LazyColumn(Modifier.height(360.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton(
                    if (ui.transcribing) "Transcribing…" else "Auto-generate",
                    Icons.Rounded.AutoAwesome,
                    enabled = !ui.transcribing,
                ) { vm.transcribeNow() }
                PillButton("Add line", Icons.Rounded.Add) {
                    vm.addCue(content.cues.lastOrNull()?.id)
                }
            }
        }
        items(content.cues, key = { it.id }) { cue ->
            CueRow(
                cue = cue,
                active = ui.positionMs in cue.startMs until cue.endMs,
                onSeek = { vm.seekTo(cue.startMs) },
                onChange = vm::updateCue,
                onDelete = { vm.deleteCue(cue.id) },
            )
        }
    }
}

@Composable
private fun CaptionsStyleTab(content: ProjectContent, vm: EditorViewModel) {
    val s = content.subtitleStyle
    LazyColumn(Modifier.height(420.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            SettingRow("Font") {
                FontSelector(s.fontId) { vm.updateStyle(s.copy(fontId = it)) }
            }
        }
        item {
            SettingRow("Size") {
                Stepper(
                    value = s.sizeSp.toInt(),
                    onDown = { vm.updateStyle(s.copy(sizeSp = (s.sizeSp - 2).coerceAtLeast(14f))) },
                    onUp = { vm.updateStyle(s.copy(sizeSp = (s.sizeSp + 2).coerceAtMost(44f))) },
                )
            }
        }
        item {
            SettingRow("Color") {
                SwatchRow(
                    listOf(0xFFFFFFFF, 0xFFF5C044, 0xFFEC4899, 0xFF2DD4BF, 0xFFB9BBC6, 0xFF0D0D12),
                    s.colorArgb,
                ) { vm.updateStyle(s.copy(colorArgb = it)) }
            }
        }
        item {
            SettingRow("Outline") {
                Segmented(
                    listOf("NONE" to "None", "LIGHT" to "Light", "BOLD" to "Bold"),
                    s.outlineMode,
                ) { vm.updateStyle(s.copy(outlineMode = it)) }
            }
        }
        item {
            SettingRow("Shadow") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = s.shadowRadius, valueRange = 0f..16f,
                        onValueChange = { vm.updateStyle(s.copy(shadowRadius = it)) },
                        modifier = Modifier.width(120.dp),
                        colors = quietSlider(),
                    )
                    Spacer(Modifier.width(8.dp))
                    QuietSwitch(s.shadow) { vm.updateStyle(s.copy(shadow = it)) }
                }
            }
        }
        item {
            SettingRow("Background") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(s.backgroundOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium, color = TextSecondary,
                    )
                    Slider(
                        value = s.backgroundOpacity, valueRange = 0f..1f,
                        onValueChange = { vm.updateStyle(s.copy(backgroundOpacity = it)) },
                        modifier = Modifier.width(110.dp).padding(start = 6.dp),
                        colors = quietSlider(),
                    )
                    Spacer(Modifier.width(8.dp))
                    QuietSwitch(s.backgroundPill) { vm.updateStyle(s.copy(backgroundPill = it)) }
                }
            }
        }
        item {
            // Presets write the same normalized anchor the drag gesture writes,
            // so chips, drag, preview and export all share one coordinate.
            // A custom-dragged spot away from the presets selects no chip.
            val anchorPos = s.anchor()
            val selectedPreset = when {
                anchorPos.first != 0.5f -> ""
                anchorPos.second == SubtitleStyle.ANCHOR_TOP_Y -> SubtitlePosition.TOP.name
                anchorPos.second == 0.5f -> SubtitlePosition.CENTER.name
                anchorPos.second == SubtitleStyle.ANCHOR_LOWER_THIRD_Y -> SubtitlePosition.BOTTOM.name
                else -> ""
            }
            SettingRow("Position") {
                Segmented(
                    listOf(
                        SubtitlePosition.TOP.name to "Top",
                        SubtitlePosition.CENTER.name to "Mid",
                        SubtitlePosition.BOTTOM.name to "Low",
                    ),
                    selectedPreset,
                ) { key ->
                    val y = when (key) {
                        SubtitlePosition.TOP.name -> SubtitleStyle.ANCHOR_TOP_Y
                        SubtitlePosition.BOTTOM.name -> SubtitleStyle.ANCHOR_LOWER_THIRD_Y
                        else -> 0.5f
                    }
                    vm.updateStyle(s.copy(position = key, posX = 0.5f, posY = y))
                }
            }
            Text(
                "Or drag the caption anywhere on the preview.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            SettingRow("Justify") {
                Segmented(
                    listOf("LEFT" to "Left", "CENTER" to "Center", "RIGHT" to "Right"),
                    s.justify,
                ) { vm.updateStyle(s.copy(justify = it)) }
            }
        }
        item {
            Text(
                "MARGINS", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            MarginSlider("X", s.marginXPct) { vm.updateStyle(s.copy(marginXPct = it)) }
            MarginSlider("Y", s.marginYPct) { vm.updateStyle(s.copy(marginYPct = it)) }
        }
        item {
            SettingRow("ALL CAPS") { QuietSwitch(s.allCaps) { vm.updateStyle(s.copy(allCaps = it)) } }
        }
    }
}

@Composable
private fun CaptionsAnimationTab(content: ProjectContent, vm: EditorViewModel) {
    val s = content.subtitleStyle
    Column(Modifier.padding(vertical = 10.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SubtitleAnimation.entries) { anim ->
                val selected = s.animation == anim.name
                Box(
                    Modifier
                        .background(
                            if (selected) Violet.copy(alpha = 0.18f) else CardHigh,
                            RoundedCornerShape(12.dp),
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Violet, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable { vm.updateStyle(s.copy(animation = anim.name)) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        anim.name.lowercase().replace('_', ' ')
                            .replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else TextSecondary,
                    )
                }
            }
        }
        SettingRow("Highlight key word") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwatchRow(
                    listOf(0xFFF5C044, 0xFF9D7DFF, 0xFFEC4899, 0xFF2DD4BF),
                    s.highlightColorArgb,
                ) { vm.updateStyle(s.copy(highlightColorArgb = it, highlightEveryNthWord = 1)) }
                Spacer(Modifier.width(8.dp))
                QuietSwitch(s.highlightEveryNthWord > 0) {
                    vm.updateStyle(s.copy(highlightEveryNthWord = if (it) 1 else 0))
                }
            }
        }
        SettingRow("Words per line") {
            Stepper(
                value = s.maxWordsPerLine,
                onDown = { vm.updateStyle(s.copy(maxWordsPerLine = (s.maxWordsPerLine - 1).coerceAtLeast(2))) },
                onUp = { vm.updateStyle(s.copy(maxWordsPerLine = (s.maxWordsPerLine + 1).coerceAtMost(9))) },
            )
        }
    }
}

/* ================================ VISUAL ================================= */

@Composable
fun VisualSheet(content: ProjectContent, vm: EditorViewModel, onClose: () -> Unit) {
    val v = content.visual
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.updateVisual(v.copy(kind = BackgroundKind.IMAGE.name, sourceUri = it.toString())) }
    }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.updateVisual(v.copy(kind = BackgroundKind.VIDEO.name, sourceUri = it.toString())) }
    }

    LazyColumn(Modifier.height(480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SheetTitleRow("Background & Effects", onClose) }
        item {
            SettingLabel("Background")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    listOf(
                        BackgroundKind.ANIMATED.name to "Animated",
                        BackgroundKind.SCENERY.name to "Scenery",
                        BackgroundKind.GRADIENT.name to "Gradient",
                        BackgroundKind.MOTION.name to "Motion",
                        BackgroundKind.PARTICLES.name to "Particles",
                        BackgroundKind.SOLID.name to "Solid",
                        BackgroundKind.IMAGE.name to "Image",
                        BackgroundKind.VIDEO.name to "Video",
                    )
                ) { (kind, label) ->
                    val selected = v.kind == kind
                    Box(
                        Modifier
                            .background(
                                if (selected) Violet.copy(alpha = 0.18f) else CardHigh,
                                RoundedCornerShape(12.dp),
                            )
                            .then(
                                if (selected) Modifier.border(1.dp, Violet, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .clickable {
                                when (kind) {
                                    BackgroundKind.IMAGE.name -> pickImage.launch(arrayOf("image/*"))
                                    BackgroundKind.VIDEO.name -> pickVideo.launch(arrayOf("video/*"))
                                    BackgroundKind.ANIMATED.name -> {
                                        // Land on a playable scene immediately; keep the
                                        // current one if the project already had one.
                                        val current = vm.animatedScenes.scenes
                                            .firstOrNull { vm.animatedScenes.uriFor(it) == v.sourceUri }
                                        if (current != null) vm.updateVisual(v.copy(kind = kind))
                                        else vm.animatedScenes.scenes.firstOrNull()
                                            ?.let { vm.setAnimatedScene(it) }
                                    }
                                    else -> vm.updateVisual(v.copy(kind = kind))
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            label, style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else TextSecondary,
                        )
                    }
                }
            }
        }
        if (v.kind == BackgroundKind.ANIMATED.name) {
            item {
                SettingLabel("Animated scene")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.animatedScenes.scenes) { scene ->
                        val uri = vm.animatedScenes.uriFor(scene)
                        val selected = v.sourceUri == uri
                        Box(
                            Modifier
                                .background(
                                    if (selected) Violet.copy(alpha = 0.18f) else CardHigh,
                                    RoundedCornerShape(12.dp),
                                )
                                .then(
                                    if (selected) Modifier.border(1.dp, Violet, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .clickable { vm.setAnimatedScene(scene) }
                                .padding(horizontal = 13.dp, vertical = 9.dp),
                        ) {
                            Text(
                                scene.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else TextSecondary,
                            )
                        }
                    }
                }
                Text(
                    "Hand-animated looping scenes — play muted behind your video and burn " +
                        "into the export.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        if (v.kind == BackgroundKind.SCENERY.name) {
            item {
                SettingLabel("Scene")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // AUTO + the authored catalog; a legacy theme from an older
                    // project stays selectable while it's the current choice.
                    val sceneIds = buildList {
                        add(SceneryThemes.AUTO)
                        addAll(SceneCatalog.ALL.map { it.id })
                        if (v.sceneryTheme in SceneryThemes.ALL) add(v.sceneryTheme)
                    }
                    items(sceneIds) { theme ->
                        val selected = v.sceneryTheme == theme
                        Box(
                            Modifier
                                .background(
                                    if (selected) Violet.copy(alpha = 0.18f) else CardHigh,
                                    RoundedCornerShape(12.dp),
                                )
                                .then(
                                    if (selected) Modifier.border(1.dp, Violet, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .clickable { vm.updateVisual(v.copy(sceneryTheme = theme)) }
                                .padding(horizontal = 13.dp, vertical = 9.dp),
                        ) {
                            Text(
                                when (theme) {
                                    SceneryThemes.AUTO -> "Match music"
                                    "MEADOW" -> "Sunrise meadow"
                                    "DUSK" -> "Golden dusk"
                                    "NIGHT" -> "Firefly night"
                                    "RAIN" -> "Gentle rain"
                                    "COAST" -> "Quiet coast"
                                    "SNOW" -> "First snow"
                                    else -> SceneCatalog.byId(theme)?.name ?: theme
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else TextSecondary,
                            )
                        }
                    }
                }
                Text(
                    "Every project grows its own scene — same theme, different hills, " +
                        "clouds and light each time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
        item {
            SettingLabel("Palette")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(GRADIENT_PRESETS) { preset ->
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    preset.map { Color(it) }
                                ),
                                CircleShape,
                            )
                            .then(
                                if (v.gradientColors == preset)
                                    Modifier.border(2.dp, Violet, CircleShape)
                                else Modifier
                            )
                            .clickable { vm.updateVisual(v.copy(gradientColors = preset)) },
                    )
                }
            }
        }
        item {
            SettingLabel("Motion")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MotionEffect.entries) { m ->
                    val selected = v.motion == m.name
                    Box(
                        Modifier
                            .background(
                                if (selected) Violet.copy(alpha = 0.18f) else CardHigh,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { vm.updateVisual(v.copy(motion = m.name)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            m.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else TextSecondary,
                        )
                    }
                }
            }
        }
        item {
            SheetSlider("Motion intensity", v.motionIntensity, 0f..1f) {
                vm.updateVisual(v.copy(motionIntensity = it))
            }
            SheetSlider("Film grain", v.filmGrain, 0f..1f) {
                vm.updateVisual(v.copy(filmGrain = it))
            }
            SheetSlider("Vignette", v.vignette, 0f..1f) {
                vm.updateVisual(v.copy(vignette = it))
            }
            if (v.kind == BackgroundKind.PARTICLES.name) {
                SheetSlider("Particle density", v.particleDensity, 0f..1f) {
                    vm.updateVisual(v.copy(particleDensity = it))
                }
            }
        }
        item {
            var packName by remember { mutableStateOf("") }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = packName, onValueChange = { packName = it },
                    placeholder = { Text("Name this look…", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                TextButton(onClick = {
                    if (packName.isNotBlank()) { vm.saveVisualPack(packName); packName = "" }
                }) { Text("Save pack", color = VioletSoft) }
            }
        }
    }
}

internal val GRADIENT_PRESETS: List<List<Long>> = listOf(
    listOf(0xFF2A1E4F, 0xFF8A4C63, 0xFF12101C),          // dusk (hero)
    listOf(0xFF10131C, 0xFF2C2237, 0xFF0B0B0F),          // midnight plum
    listOf(0xFF0B1B1E, 0xFF14444C, 0xFF091012),          // deep teal
    listOf(0xFF1C1410, 0xFF4C3520, 0xFF120D08),          // amber dusk
    listOf(0xFF101418, 0xFF2E4057, 0xFF0A0C10),          // slate blue
    listOf(0xFF161016, 0xFF52333F, 0xFF0F0A0D),          // wine
    listOf(0xFF0E1210, 0xFF29452E, 0xFF080B08),          // forest
    listOf(0xFF1A1408, 0xFF6B5B2A, 0xFF0F0C06),          // gold hour
)

/* ================================ MUSIC ================================== */

@Composable
fun MusicSheet(content: ProjectContent, vm: EditorViewModel, onClose: () -> Unit) {
    val m = content.music
    var mood by remember { mutableStateOf<String?>(null) }
    Column {
        SheetTitleRow("Music", onClose) {
            IconButton(onClick = vm::surpriseMe) {
                Icon(Icons.Rounded.Casino, "Surprise me", tint = VioletSoft)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf<String?>(null) + vm.musicLibrary.moods) { mo ->
                val selected = mood == mo
                Box(
                    Modifier
                        .background(
                            if (selected) Violet.copy(alpha = 0.18f) else CardHigh,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { mood = mo }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text(
                        mo ?: "All", style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else TextSecondary,
                    )
                }
            }
        }
        LazyColumn(
            Modifier.height(330.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
        ) {
            items(vm.musicLibrary.search(mood = mood), key = { it.id }) { track ->
                val selected = track.id == m.trackId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(
                            if (selected) CardHigh else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Violet.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable { vm.selectTrack(track) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackArt(track, Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "${track.mood} · ${track.bpm} BPM · ${track.key}",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary,
                        )
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = VioletSoft, modifier = Modifier.size(17.dp))
                }
            }
        }
        TextButton(
            onClick = { vm.selectTrack(null) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        ) { Text("No music", color = TextSecondary) }
        Spacer(Modifier.height(14.dp))
    }
}

/* =============================== VOLUME ================================== */

@Composable
fun VolumeSheet(content: ProjectContent, vm: EditorViewModel, onClose: () -> Unit) {
    val m = content.music
    Column(Modifier.padding(bottom = 26.dp)) {
        SheetTitleRow("Volume & Ducking", onClose)
        SheetSlider("Music volume", m.volume, 0f..1f) { vm.updateMusic(m.copy(volume = it)) }
        SheetSlider("Ducking depth", -m.duckingDb, 0f..24f, "-${(-m.duckingDb).toInt()} dB") {
            vm.updateMusic(m.copy(duckingDb = -it))
        }
        SheetSlider("Duck attack", m.duckAttackMs.toFloat(), 40f..500f, "${m.duckAttackMs} ms") {
            vm.updateMusic(m.copy(duckAttackMs = it.toInt()))
        }
        SheetSlider("Duck release", m.duckReleaseMs.toFloat(), 100f..1500f, "${m.duckReleaseMs} ms") {
            vm.updateMusic(m.copy(duckReleaseMs = it.toInt()))
        }
        SheetSlider("Fade in", m.fadeInMs.toFloat(), 0f..4000f, "${m.fadeInMs / 100 / 10f}s") {
            vm.updateMusic(m.copy(fadeInMs = it.toInt()))
        }
        SheetSlider("Fade out", m.fadeOutMs.toFloat(), 0f..5000f, "${m.fadeOutMs / 100 / 10f}s") {
            vm.updateMusic(m.copy(fadeOutMs = it.toInt()))
        }
    }
}

/* =============================== EXPORT ================================== */

@Composable
fun ExportSheet(
    content: ProjectContent,
    vm: EditorViewModel,
    onExports: () -> Unit,
    onClose: () -> Unit,
) {
    val e = content.export
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> vm.enqueueExport(); onClose(); onExports() }

    Column(Modifier.padding(bottom = 26.dp)) {
        SheetTitleRow("Export", onClose)
        SettingRow("Resolution") {
            Segmented(Resolution.entries.map { it.name to it.label.substringBefore(" ·") }, e.resolution) {
                vm.updateExportConfig(e.copy(resolution = it))
            }
        }
        SettingRow("Frame rate") {
            Segmented(listOf("30" to "30 fps", "60" to "60 fps"), e.fps.toString()) {
                vm.updateExportConfig(e.copy(fps = it.toInt()))
            }
        }
        SettingRow("Codec") {
            Segmented(Codec.entries.map { it.name to it.label }, e.codec) {
                vm.updateExportConfig(e.copy(codec = it))
            }
        }
        SheetSlider("Bitrate", e.bitrateMbps.toFloat(), 4f..40f, "${e.bitrateMbps} Mbps") {
            vm.updateExportConfig(e.copy(bitrateMbps = it.toInt()))
        }

        var templateName by remember { mutableStateOf("") }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = templateName, onValueChange = { templateName = it },
                placeholder = { Text("Save style as template…", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f), singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Violet.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            TextButton(onClick = {
                if (templateName.isNotBlank()) { vm.saveAsTemplate(templateName); templateName = "" }
            }) { Text("Save", color = VioletSoft) }
        }

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.enqueueExport(); onClose(); onExports()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
        ) {
            Text("Render video", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Text(
            "Renders in the background — keep creating.",
            style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(4.dp),
        )
    }
}

/* ============================ shared pieces ============================== */

@Composable
private fun SheetTitleRow(title: String, onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(CardHigh, CircleShape)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge, color = Color.White,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        trailing()
        ConfirmDot(onClick = onClose, icon = Icons.Rounded.Check)
    }
}

@Composable
private fun CueRow(
    cue: SubtitleCue,
    active: Boolean,
    onSeek: () -> Unit,
    onChange: (SubtitleCue) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(cue.id) { mutableStateOf(cue.text) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(
                if (active) CardHigh else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            fmtCue(cue.startMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) VioletSoft else TextSecondary,
            modifier = Modifier
                .clickable { onSeek() }
                .padding(6.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onChange(cue.copy(text = it)) },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, "Delete line", tint = TextSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .background(CardHigh, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = VioletSoft, modifier = Modifier.size(15.dp))
        Text(
            "  $label", style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Color.White else TextSecondary,
        )
    }
}

private val FONT_OPTIONS = listOf(
    "poppins_bold" to "Poppins Bold",
    "poppins_semibold" to "Poppins Semi",
    "poppins_medium" to "Poppins Med",
    "inter" to "Inter",
    "serif" to "Serif",
    "mono" to "Mono",
)

@Composable
private fun FontSelector(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.width(216.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(FONT_OPTIONS) { (id, label) ->
            val sel = id == selected
            Box(
                Modifier
                    .background(
                        if (sel) Violet.copy(alpha = 0.18f) else CardHigh,
                        RoundedCornerShape(10.dp),
                    )
                    .then(
                        if (sel) Modifier.border(1.dp, Violet, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    color = if (sel) Color.White else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium, color = Color.White,
            modifier = Modifier.weight(1f),
        )
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = Color.White,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun Stepper(value: Int, onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        Modifier.background(CardHigh, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Remove, "Less", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, color = Color.White)
        IconButton(onClick = onUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Add, "More", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun SwatchRow(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            Box(
                Modifier
                    .size(26.dp)
                    .background(Color(c), CircleShape)
                    .then(
                        if (selected == c) Modifier.border(2.dp, Violet, CircleShape)
                        else Modifier.border(1.dp, Color(0xFF33333F), CircleShape)
                    )
                    .clickable { onSelect(c) },
            )
        }
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.background(CardHigh, RoundedCornerShape(11.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier
                    .background(if (sel) Violet else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    color = if (sel) Color.White else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuietSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Violet,
            uncheckedTrackColor = Color(0xFF2A2A36),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun MarginSlider(axis: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(axis, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(18.dp))
        Slider(
            value = value, valueRange = 0f..20f, onValueChange = onChange,
            modifier = Modifier.weight(1f), colors = quietSlider(),
        )
        Text(
            "${value.toInt()}%", style = MaterialTheme.typography.labelMedium,
            color = TextSecondary, modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun SheetSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String = "${(value * 100).toInt()}%",
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Slider(value = value, valueRange = range, onValueChange = onChange, colors = quietSlider())
    }
}

@Composable
private fun quietSlider() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Violet,
    inactiveTrackColor = Color(0xFF2A2A36),
)

private fun buildAnnotatedCaption(text: String, emphasized: String?, highlight: Boolean) =
    androidx.compose.ui.text.buildAnnotatedString {
        text.split(" ").forEachIndexed { i, w ->
            if (i > 0) append(" ")
            if (highlight && w == emphasized) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = Highlight))
                append(w)
                pop()
            } else append(w)
        }
    }

private fun fmtCue(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
}
