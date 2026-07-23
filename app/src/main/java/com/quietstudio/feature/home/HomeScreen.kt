package com.quietstudio.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.MotionEffect
import com.quietstudio.core.model.VisualConfig
import com.quietstudio.feature.templates.BUILT_IN_TEMPLATES
import com.quietstudio.ui.components.GlowMicButton
import com.quietstudio.ui.components.VisualThumb
import com.quietstudio.ui.theme.Highlight
import com.quietstudio.ui.theme.QuietGradients
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import com.quietstudio.ui.theme.VioletSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onProjects: () -> Unit,
    onMusic: () -> Unit,
    onVisuals: () -> Unit,
    onTemplates: () -> Unit,
    onExports: () -> Unit,
    onSettings: () -> Unit,
    onOpenProject: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recent by viewModel.recentProjects.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        /* ------------------------------- hero ------------------------------ */
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(QuietGradients.heroSky),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = onExports) {
                        Icon(Icons.Rounded.WorkspacePremium, "Exports", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = Color.White)
                    }
                }
                Text("Quiet Studio", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "Your voice. Your story.",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Beautiful videos. Made simple.",
                    style = MaterialTheme.typography.bodyMedium, color = Highlight,
                )
                Spacer(Modifier.height(30.dp))
                GlowMicButton(size = 112.dp, onClick = onRecord)
                Spacer(Modifier.height(18.dp))
                Text("Tap to Record", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }

        /* --------------------------- quick nav card ------------------------ */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickNav(Icons.Rounded.Folder, "Projects", onProjects)
            QuickNav(Icons.Rounded.MusicNote, "Music", onMusic)
            QuickNav(Icons.Rounded.Wallpaper, "Visuals", onVisuals)
            QuickNav(Icons.Rounded.Style, "Templates", onTemplates)
        }

        /* ---------------------------- recent list --------------------------- */
        SectionHeader("Recent Projects", onSeeAll = onProjects)
        if (recent.isEmpty()) {
            Text(
                "Your projects will appear here.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            recent.take(4).forEach { p ->
                var menu by remember(p.id) { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .clickable { onOpenProject(p.id) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VisualThumb(
                        p.content.visual,
                        Modifier
                            .size(width = 46.dp, height = 46.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            p.title, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                        )
                        Text(
                            fmtDur(p.content.narration.durationMs) + "  ·  " +
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(p.updatedAt)),
                            style = MaterialTheme.typography.labelMedium, color = TextSecondary,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, "More", tint = TextSecondary)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = { viewModel.duplicate(p.id); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = { viewModel.delete(p.id); menu = false },
                            )
                        }
                    }
                }
            }
        }

        /* --------------------------- templates strip ------------------------ */
        SectionHeader("Templates", onSeeAll = onTemplates)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(BUILT_IN_TEMPLATES, key = { it.id }) { t ->
                Column(
                    Modifier
                        .width(96.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .clickable { viewModel.useBuiltIn(t); onRecord() }
                        .padding(6.dp),
                ) {
                    VisualThumb(
                        t.content.visual,
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Text(
                        t.name, style = MaterialTheme.typography.titleSmall, color = Color.White,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                    )
                    Text(
                        t.tagline, style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }

        /* ------------------------- feature highlights ----------------------- */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureCard(
                Icons.Rounded.ClosedCaption, "Auto Subtitles", "Accurate captions in seconds",
                Modifier.weight(1f), onClick = onSettings, // Whisper model setup lives there
            )
            FeatureCard(
                Icons.Rounded.GraphicEq, "Auto Ducking", "Lowers music under your voice",
                Modifier.weight(1f), onClick = onMusic,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureCard(
                Icons.Rounded.HighQuality, "High Quality Export", "Cinematic quality every time",
                Modifier.weight(1f), onClick = onProjects, // export is configured per project
            )
            FeatureCard(
                Icons.Rounded.Bolt, "Ultra Fast", "Optimized rendering for speed",
                Modifier.weight(1f), onClick = onExports,
            )
        }

        Text(
            "On-device · No accounts · No analytics",
            style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 20.dp),
        )
        Spacer(Modifier.height(96.dp)) // room above bottom bar
    }
}

@Composable
private fun QuickNav(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, label, tint = VioletSoft, modifier = Modifier.size(24.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
        Text(
            "See All", style = MaterialTheme.typography.labelMedium, color = Violet,
            modifier = Modifier.clickable { onSeeAll() },
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Violet.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = VioletSoft, modifier = Modifier.size(21.dp))
        }
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = Color.White,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
