# Quiet Studio

A private, fully offline production studio for one creator: record a voice
note, get synchronized styled subtitles, a cinematic 9:16 visual, an original
instrumental bed with automatic ducking, and a rendered MP4 for TikTok /
Reels / Shorts — in about two minutes, without anything leaving the phone.

No accounts. No ads. No analytics. No cloud.

## Building

Open the project in Android Studio (Ladybug or newer) and press **Run** —
or `./gradlew assembleDebug`. First build downloads dependencies and the
Android NDK (used by whisper.cpp).

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) builds the
debug APK automatically if you push this repo to GitHub.

### Build flags (`gradle.properties`)

| Flag | Default | Effect |
| --- | --- | --- |
| `quietstudio.enableWhisper` | `true` | Builds vendored whisper.cpp via NDK/CMake for on-device transcription. Set `false` for a pure-Kotlin build; subtitles then start empty and are typed/edited manually through the same interface. |
| `quietstudio.enableFfmpegKit` | `false` | Reserved switch for an FFmpeg-kit implementation of `MediaEngine`. The default export engine is Media3 Transformer and needs nothing extra. |

### Whisper model

Transcription needs a ggml Whisper model once: **Settings → Transcription →
Import file** (any `ggml-*.bin` you already have — fully offline) or the
one-tap download. That download is the only network call the app can make,
and only ever on your explicit tap.

## Architecture

Single Gradle module, strictly layered packages — each `core/*` package has
the shape of a future standalone module and communicates through interfaces,
so AI title generation, cloud sync, or a desktop companion can be added
without rewriting anything.

```
com.quietstudio
├── core
│   ├── model        # serializable domain objects (ProjectContent is the whole project)
│   ├── audio        # AudioRecord engine, WAV I/O, DSP (denoise/normalize/trim), undo history
│   ├── media        # SceneRenderer + SubtitlePainter (shared by preview AND export),
│   │                #   AudioMixdown (ducked music bed), MediaEngine ← Media3ExportEngine
│   ├── music        # MusicLibrary (manifest catalog), MusicEngine (loop/crossfade/duck player)
│   └── database     # Room: projects, versions, folders, templates, packs, music prefs, export jobs
├── transcription    # TranscriptionEngine ← WhisperTranscriptionEngine (JNI) / Manual fallback
├── data             # repositories (autosave + version snapshots live here)
├── feature          # Compose screens: home, record, editor, music, visuals,
│                    #   projects, templates, exports, settings
└── di               # Hilt wiring — swap any engine here
```

Key invariant: **the preview is the export.** `SceneRenderer` and
`SubtitlePainter` draw both the live editor canvas and every exported frame,
so there is never a "why does the render look different" moment.

`ProjectContent` (one serializable value) is the entire editable state —
which is what makes autosave, version history, duplication, templates and
future sync cheap.

## Music library

`app/src/main/assets/music/` ships 30 original instrumental loops
(40–67 s, seamless, −16 LUFS, OGG) across Reflective / Hopeful / Deep
Focus / Late Night / Morning Energy / Documentary / Ambient and more, with
a `manifest.json` catalog (mood, tags, bpm, key, energy, instrumentation).

Every track was composed procedurally by `tools/musicgen/compose.py` — a
seeded generative system over general music theory (functional chord
grammars, voice-leading, synthesized Rhodes/pads/bass/drums, tape and vinyl
character). No sampling, no imitation of any artist or work; the material
is original by construction and yours to use in your own videos.

**Adding tracks is additive only**: drop new `.ogg` files + manifest entries
(or re-run the generator with new seeds) — the engine and UI pick them up
with zero code changes. The catalog interface also supports imported
user tracks at runtime.

## Feature map

- **Record** — 48 kHz WAV, live waveform, platform NS/AEC, then one-tap
  spectral denoise, loudness normalize, silence trim; file-backed undo/redo.
- **Subtitles** — whisper.cpp on-device → caption-sized cues; edit text and
  timing; fonts, position, size, color, shadow, outline, backdrop pill,
  caps; fade/pop/slide/karaoke/typewriter animations; burned in at export.
- **Visuals** — animated gradients, drifting motion fields, particles,
  solid, image (slow zoom / Ken Burns / parallax / drift), looped video
  backgrounds; film grain + vignette; save any look as a reusable pack.
- **Music** — mood/tempo/energy/instrument search, favorites, shuffle,
  crossfading preview player, per-project volume + ducking envelope
  (attack/release) mirrored exactly in the export mix.
- **Export** — 1080×1920 or 4K, 30/60 fps, H.264/HEVC, bitrate control;
  offline audio mixdown + Media3 Transformer; queued and rendered in the
  background under a progress notification.
- **Projects** — autosave (1.2 s debounce) + pruned version snapshots,
  duplicate, folders, tags, search, restore any version.
- **Templates** — save a project's whole style; one tap starts a new
  recording wearing it.

## License / intent

Personal tool for its owner. Not for store release. The bundled music was
generated for this project and carries no third-party rights.
