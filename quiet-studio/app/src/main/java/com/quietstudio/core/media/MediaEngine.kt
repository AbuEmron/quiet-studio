package com.quietstudio.core.media

import android.content.Context
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.core.model.ProjectContent
import java.io.File

/**
 * Abstraction over the rendering backend.
 *
 * The default implementation is [Media3ExportEngine] (MediaCodec-based, ships
 * with the app, fully on-device). An FFmpeg-kit implementation can be dropped
 * in behind this same interface later (see gradle.properties flag) without
 * touching any caller — same for a future desktop-companion renderer.
 */
interface MediaEngine {
    /**
     * Renders a complete project to [outFile] (MP4).
     * @param onProgress 0..1 callback, called from an arbitrary thread.
     */
    suspend fun exportProject(
        context: Context,
        project: ProjectContent,
        musicTrack: MusicTrack?,
        outFile: File,
        onProgress: (Float) -> Unit,
    ): Result<File>

    suspend fun cancel()
}
