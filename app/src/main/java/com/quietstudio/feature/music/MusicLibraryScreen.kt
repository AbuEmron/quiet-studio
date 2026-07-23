package com.quietstudio.feature.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Spa
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.ui.components.QuietSearchField
import com.quietstudio.ui.components.SheetHeader
import com.quietstudio.ui.theme.CardHigh
import com.quietstudio.ui.theme.Ember
import com.quietstudio.ui.theme.Highlight
import com.quietstudio.ui.theme.Lime
import com.quietstudio.ui.theme.Rose
import com.quietstudio.ui.theme.Sky
import com.quietstudio.ui.theme.Teal
import com.quietstudio.ui.theme.TextSecondary
import com.quietstudio.ui.theme.Violet
import com.quietstudio.ui.theme.VioletSoft

private data class MoodCardSpec(
    val label: String,
    val tag: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val MOOD_CARDS = listOf(
    MoodCardSpec("Soulful", "soulful", Highlight, Icons.Rounded.MusicNote),
    MoodCardSpec("Fusion", "fusion", Ember, Icons.Rounded.Album),
    MoodCardSpec("Hip Hop", "hiphop", Sky, Icons.Rounded.Radio),
    MoodCardSpec("Lo-fi", "lofi", Rose, Icons.Rounded.Headphones),
    MoodCardSpec("Jazz", "jazz", Color(0xFFF472B6), Icons.Rounded.LibraryMusic),
    MoodCardSpec("Psychedelic", "psychedelic", VioletSoft, Icons.Rounded.AllInclusive),
    MoodCardSpec("Ambient", "ambient", Teal, Icons.Rounded.Spa),
    MoodCardSpec("Chill", "chill", Lime, Icons.Rounded.SentimentSatisfied),
)

@Composable
fun MusicLibraryScreen(
    onBack: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.engine.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.engine.isPlaying.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }

    val tracks = viewModel.library.search(query = query, tag = tag)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 86.dp), // clear the floating bottom nav
    ) {
        SheetHeader("Music Library", Icons.AutoMirrored.Rounded.ArrowBack, onBack) {
            IconButton(onClick = viewModel::shuffle) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = VioletSoft)
            }
        }

        QuietSearchField(
            value = query, onValueChange = { query = it },
            placeholder = "Search music…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Moods", style = MaterialTheme.typography.titleLarge,
                        color = Color.White, modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (tag == null) "See All" else "Clear",
                        style = MaterialTheme.typography.labelMedium, color = Violet,
                        modifier = Modifier.clickable { tag = null },
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    MOOD_CARDS.chunked(4).forEach { rowSpecs ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowSpecs.forEach { spec ->
                                MoodCard(
                                    spec, selected = tag == spec.tag,
                                    modifier = Modifier.weight(1f),
                                ) { tag = if (tag == spec.tag) null else spec.tag }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Featured Tracks",
                    style = MaterialTheme.typography.titleLarge, color = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp, bottom = 6.dp),
                )
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    active = nowPlaying?.id == track.id,
                    favorite = favorites.contains(track.id),
                    onClick = { viewModel.playOrPause(track) },
                    onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                )
            }
        }

        nowPlaying?.let { np ->
            NowPlayingBar(
                track = np, playing = isPlaying,
                onPause = { viewModel.playOrPause(np) },
            )
        }
    }
}

/* ------------------------------ pieces ----------------------------------- */

@Composable
private fun MoodCard(
    spec: MoodCardSpec,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(CardHigh, RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.border(1.5.dp, Violet, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            spec.icon, spec.label,
            tint = spec.color, modifier = Modifier.size(22.dp),
        )
        Text(
            spec.label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else TextSecondary,
            modifier = Modifier.padding(top = 7.dp), maxLines = 1,
        )
    }
}

/** Procedural per-track cover: gradient tile keyed to the track id. */
@Composable
fun TrackArt(track: MusicTrack, modifier: Modifier = Modifier) {
    val h = track.id.hashCode()
    val c1 = Color.hsv(((h ushr 3) % 360).toFloat().coerceAtLeast(0f), 0.55f, 0.55f)
    val c2 = Color.hsv(((h ushr 9) % 360).toFloat().coerceAtLeast(0f), 0.65f, 0.25f)
    Box(
        modifier.background(Brush.linearGradient(listOf(c1, c2)), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote, null,
            tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TrackRow(
    track: MusicTrack,
    active: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(
                if (active) CardHigh else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(14.dp),
            )
            .then(
                if (active) Modifier.border(1.dp, Violet.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TrackArt(track, Modifier.size(44.dp))
            if (active) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            Text(
                "${track.mood}  ·  ${track.bpm} BPM",
                style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            )
        }
        Text(fmtSec(track.durationSec), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "More", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (favorite) "Unfavorite" else "Favorite") },
                    leadingIcon = {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null, tint = Rose, modifier = Modifier.size(17.dp),
                        )
                    },
                    onClick = { onToggleFavorite(); menu = false },
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(track: MusicTrack, playing: Boolean, onPause: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardHigh)
            .border(1.5.dp, Violet, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArt(track, Modifier.size(40.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            Text(track.mood, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        if (playing) EqBars(Modifier.size(width = 22.dp, height = 18.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(38.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable { onPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Pause", tint = Color.White, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EqBars(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase",
    )
    Canvas(modifier) {
        val n = 4
        val w = size.width / n
        for (i in 0 until n) {
            val k = ((phase + i * 0.3f) % 1f)
            val h = size.height * (0.3f + 0.7f * k)
            drawRoundRect(
                Violet,
                Offset(i * w + w * 0.2f, size.height - h),
                Size(w * 0.5f, h),
                androidx.compose.ui.geometry.CornerRadius(w * 0.25f),
            )
        }
    }
}

private fun fmtSec(sec: Double): String {
    val s = sec.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
