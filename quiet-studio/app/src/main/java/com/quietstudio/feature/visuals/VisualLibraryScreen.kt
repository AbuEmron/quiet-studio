package com.quietstudio.feature.visuals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.MotionEffect
import com.quietstudio.core.model.SubtitleStyle
import com.quietstudio.core.model.VisualConfig
import com.quietstudio.core.model.VisualPack
import com.quietstudio.feature.editor.GRADIENT_PRESETS
import com.quietstudio.ui.components.QuietSearchField
import com.quietstudio.ui.components.SheetHeader
import com.quietstudio.ui.components.VisualThumb
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import com.quietstudio.ui.theme.VioletSoft
import java.util.UUID

private enum class VisualTab(val label: String, val icon: ImageVector) {
    GRADIENTS("Gradients", Icons.Rounded.Gradient),
    MOTION("Motion", Icons.Rounded.Waves),
    PARTICLES("Particles", Icons.Rounded.Grain),
    PACKS("My Packs", Icons.Rounded.Bookmark),
}

private data class CatalogItem(val name: String, val category: String, val visual: VisualConfig)

/** Built-in procedural visual catalog + saved packs — panel 6 layout. */
@Composable
fun VisualLibraryScreen(
    onBack: () -> Unit,
    viewModel: VisualLibraryViewModel = hiltViewModel(),
) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var tab by remember { mutableStateOf(VisualTab.GRADIENTS) }
    var saveCandidate by remember { mutableStateOf<CatalogItem?>(null) }

    val catalog = remember { buildCatalog() }
    val categories = listOf("All", "Warm", "Cool", "Dark", "Nature")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        SheetHeader("Visual Library", Icons.AutoMirrored.Rounded.ArrowBack, onBack)

        QuietSearchField(
            value = query, onValueChange = { query = it },
            placeholder = "Search visuals…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { cat ->
                val selected = category == cat
                Box(
                    Modifier
                        .background(
                            if (selected) Violet else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { category = cat }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        cat, style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else TextSecondary,
                    )
                }
            }
        }

        val items: List<Any> = when (tab) {
            VisualTab.PACKS -> packs
            else -> catalog.filter { item ->
                item.visual.kind == when (tab) {
                    VisualTab.GRADIENTS -> BackgroundKind.GRADIENT.name
                    VisualTab.MOTION -> BackgroundKind.MOTION.name
                    else -> BackgroundKind.PARTICLES.name
                } &&
                    (category == "All" || item.category == category) &&
                    (query.isBlank() || item.name.contains(query, true))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { item ->
                when (item) {
                    is VisualPack -> item.id
                    is CatalogItem -> item.name
                    else -> item.hashCode()
                }
            }) { item ->
                when (item) {
                    is CatalogItem -> CatalogCard(item) { saveCandidate = item }
                    is VisualPack -> PackCard(item) { viewModel.delete(item.id) }
                }
            }
        }

        // bottom tabs (Videos/Images in the mock map to procedural categories here)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF121218))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VisualTab.entries.forEach { t ->
                val selected = tab == t
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { tab = t }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Icon(
                        t.icon, t.label,
                        tint = if (selected) VioletSoft else TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        t.label, style = MaterialTheme.typography.labelSmall,
                        color = if (selected) VioletSoft else TextSecondary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }

    saveCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { saveCandidate = null },
            title = { Text("Save as pack?") },
            text = {
                Text(
                    "“${item.name}” becomes a reusable pack you can apply from any project's Background sheet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePack(
                        VisualPack(UUID.randomUUID().toString(), item.name, item.visual, SubtitleStyle())
                    )
                    saveCandidate = null
                }) { Text("Save", color = VioletSoft) }
            },
            dismissButton = {
                TextButton(onClick = { saveCandidate = null }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, onClick: () -> Unit) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Box {
            VisualThumb(
                item.visual,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 14f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            )
            Text(
                item.category,
                style = MaterialTheme.typography.labelSmall, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            item.name, style = MaterialTheme.typography.titleSmall, color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PackCard(pack: VisualPack, onDelete: () -> Unit) {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
    ) {
        VisualThumb(
            pack.visual,
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pack.name, style = MaterialTheme.typography.titleSmall, color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, "Delete", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun buildCatalog(): List<CatalogItem> {
    val names = listOf(
        "Dusk" to "Warm", "Midnight" to "Dark", "Deep Sea" to "Cool", "Amber" to "Warm",
        "Slate" to "Cool", "Wine" to "Dark", "Forest" to "Nature", "Gold Hour" to "Warm",
    )
    val gradients = GRADIENT_PRESETS.mapIndexed { i, colors ->
        CatalogItem(
            names[i % names.size].first, names[i % names.size].second,
            VisualConfig(
                kind = BackgroundKind.GRADIENT.name, gradientColors = colors,
                filmGrain = 0.25f, vignette = 0.4f,
            ),
        )
    }
    val motion = GRADIENT_PRESETS.take(4).mapIndexed { i, colors ->
        CatalogItem(
            "${names[i].first} Flow", names[i].second,
            VisualConfig(
                kind = BackgroundKind.MOTION.name, gradientColors = colors,
                motion = MotionEffect.DRIFT.name, filmGrain = 0.3f, vignette = 0.45f,
            ),
        )
    }
    val particles = listOf(
        CatalogItem("Fireflies", "Nature", VisualConfig(
            kind = BackgroundKind.PARTICLES.name,
            gradientColors = listOf(0xFF0E1210, 0xFF29452E, 0xFF080B08),
            particleColorArgb = 0xFFB8E986, particleDensity = 0.4f, vignette = 0.45f,
        )),
        CatalogItem("Embers", "Warm", VisualConfig(
            kind = BackgroundKind.PARTICLES.name,
            gradientColors = listOf(0xFF1C1410, 0xFF4C3520, 0xFF120D08),
            particleColorArgb = 0xFFF97316, particleDensity = 0.55f, vignette = 0.5f,
        )),
        CatalogItem("Starfield", "Dark", VisualConfig(
            kind = BackgroundKind.PARTICLES.name,
            gradientColors = listOf(0xFF0B0B12, 0xFF1A1A2E, 0xFF060609),
            particleColorArgb = 0xFFFFFFFF, particleDensity = 0.7f, vignette = 0.35f,
        )),
        CatalogItem("Violet Dust", "Cool", VisualConfig(
            kind = BackgroundKind.PARTICLES.name,
            gradientColors = listOf(0xFF14101F, 0xFF2A1E4F, 0xFF0B0912),
            particleColorArgb = 0xFF9D7DFF, particleDensity = 0.5f, vignette = 0.4f,
        )),
    )
    return gradients + motion + particles
}
