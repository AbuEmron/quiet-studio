// Quiet Studio — minimal JNI bridge over whisper.cpp
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <sstream>
#include "whisper.h"

#define TAG "whisper_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_quietstudio_transcription_whisper_WhisperBridge_initContext(
        JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) {
        LOGE("failed to load model");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_quietstudio_transcription_whisper_WhisperBridge_freeContext(
        JNIEnv *, jobject, jlong ctxPtr) {
    if (ctxPtr) whisper_free(reinterpret_cast<whisper_context *>(ctxPtr));
}

JNIEXPORT jstring JNICALL
Java_com_quietstudio_transcription_whisper_WhisperBridge_transcribe(
        JNIEnv *env, jobject, jlong ctxPtr, jfloatArray pcm, jstring language, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (!ctx) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());

    const char *lang = env->GetStringUTFChars(language, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 4;
    params.translate = false;
    params.language = (lang && std::string(lang) != "auto") ? lang : nullptr;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.no_context = true;
    params.suppress_blank = true;
    // Word-level timing: with token timestamps on and max_len=1 split on word
    // boundaries, whisper emits one segment per WORD with tight t0/t1. The
    // output format below is unchanged — each line simply carries one word —
    // so the Kotlin side needs no JNI change, and CueSegmenter groups words
    // into caption-sized cues using their exact boundaries.
    params.token_timestamps = true;
    params.max_len = 1;
    params.split_on_word = true;

    int rc = whisper_full(ctx, params, samples.data(), (int) samples.size());
    env->ReleaseStringUTFChars(language, lang);
    if (rc != 0) {
        LOGE("whisper_full failed: %d", rc);
        return env->NewStringUTF("");
    }

    std::ostringstream out;
    int nseg = whisper_full_n_segments(ctx);
    for (int i = 0; i < nseg; i++) {
        int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10;  // cs -> ms
        int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        const char *text = whisper_full_get_segment_text(ctx, i);
        out << t0 << '\t' << t1 << '\t' << (text ? text : "") << '\n';
    }
    return env->NewStringUTF(out.str().c_str());
}

} // extern "C"
