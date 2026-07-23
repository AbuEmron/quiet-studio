package com.quietstudio.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.quietstudio.core.media.scenes.AnimatedScenes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Grid/preview thumbnail for a bundled animated scene: its first frame,
 * decoded off the main thread and cached, over a gradient placeholder. No
 * player instances — 13 of these in a grid stay cheap.
 */
@Composable
fun AnimatedThumb(assetUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val frame by produceState<Bitmap?>(initialValue = cache[assetUri], assetUri) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            val path = AnimatedScenes.assetPath(assetUri) ?: return@withContext null
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    context.assets.openFd(path).use { afd ->
                        mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                    mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { mmr.release() }
                }
            }.getOrNull()?.also { cache[assetUri] = it }
        }
    }

    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFF241C3A), Color(0xFF141026)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        val f = frame
        if (f != null) {
            Image(
                bitmap = f.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Rounded.Movie, null, tint = Color(0x66FFFFFF))
        }
    }
}

/** Small process-lifetime cache of decoded first frames, keyed by asset URI. */
private val cache = HashMap<String, Bitmap>()
