package com.quietstudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.quietstudio.core.media.SceneRenderer
import com.quietstudio.core.model.VisualConfig

/** Small procedural preview of a VisualConfig (project/template thumbnails). */
@Composable
fun VisualThumb(visual: VisualConfig, modifier: Modifier = Modifier, timeMs: Long = 4000) {
    val key = visual.hashCode()
    val renderer = remember(key) { SceneRenderer(135, 240) }
    val bmp = remember(key) {
        android.graphics.Bitmap.createBitmap(135, 240, android.graphics.Bitmap.Config.ARGB_8888)
    }
    val cv = remember(key) { android.graphics.Canvas(bmp) }
    Canvas(modifier) {
        cv.drawColor(android.graphics.Color.BLACK)
        renderer.drawFrame(cv, visual, timeMs, 30000)
        drawContext.canvas.nativeCanvas.drawBitmap(
            bmp, null, android.graphics.RectF(0f, 0f, size.width, size.height), null,
        )
    }
}
