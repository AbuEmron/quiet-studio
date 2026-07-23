package com.quietstudio.feature.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Wraps CameraX video capture for the in-app recorder.
 *
 * Everything stays on-device: frames go to a PreviewView and the recording is
 * written to an app-private file. Video stabilization is requested through
 * Camera2 interop where the device supports it and is silently ignored where
 * it doesn't.
 */
class CameraCapture(private val context: Context) {

    enum class Lens { BACK, FRONT }

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    var lens: Lens = Lens.BACK
        private set

    val hasFront: Boolean get() = provider?.hasCamera(selectorFor(Lens.FRONT)) == true
    val hasBack: Boolean get() = provider?.hasCamera(selectorFor(Lens.BACK)) == true

    private fun selectorFor(l: Lens): CameraSelector = when (l) {
        Lens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        Lens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    /**
     * Binds preview + video capture to [owner]. Safe to call again to rebind
     * (e.g. after flipping the lens). [quality] defaults to 1080p, falling back
     * to the nearest lower quality the device offers.
     */
    @SuppressLint("RestrictedApi")
    fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        lens: Lens = this.lens,
        quality: Quality = Quality.FHD,
        onReady: (() -> Unit)? = null,
    ) {
        this.lens = lens
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            provider = cameraProvider

            val previewBuilder = Preview.Builder()
            // Ask for video stabilization; a no-op on devices without it.
            runCatching {
                Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
                )
            }
            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, selectorFor(lens), preview, capture)
            }
            onReady?.invoke()
        }, ContextCompat.getMainExecutor(context))
    }

    val canFlip: Boolean get() = hasFront && hasBack

    fun flipTarget(): Lens = if (lens == Lens.BACK) Lens.FRONT else Lens.BACK

    /**
     * Starts recording with audio to [outputFile]. [withAudio] must reflect the
     * RECORD_AUDIO grant. Events are delivered on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun start(outputFile: File, withAudio: Boolean, onEvent: (VideoRecordEvent) -> Unit) {
        val capture = videoCapture ?: return
        val options = androidx.camera.video.FileOutputOptions.Builder(outputFile).build()
        val pending = capture.output.prepareRecording(context, options)
        if (withAudio) runCatching { pending.withAudioEnabled() }
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            onEvent(event)
        }
    }

    fun stop() {
        recording?.stop()
        recording = null
    }

    fun release() {
        runCatching { recording?.stop() }
        recording = null
        runCatching { provider?.unbindAll() }
    }

    // Keep MediaStore import referenced so ProGuard/lint keep the option types
    // available if a future SAF path is added; harmless.
    @Suppress("unused")
    private fun keepTypes(): Class<*> = MediaStoreOutputOptions::class.java
}
