#!/usr/bin/env python3
"""
Quiet Studio — Original Instrumental Library Generator
======================================================
Procedurally composes fully original instrumental loops. Every musical
decision (progression, voicing, melody, rhythm, timbre) is derived from a
seeded generative grammar over general music theory — no reference to, or
reproduction of, any existing work.

Output: seamless-loop OGG Vorbis files + manifest.json for the Android app.
"""
import json, os, subprocess, sys
import numpy as np
from scipy.signal import fftconvolve, butter, lfilter

SR = 44100
OUT_WAV = os.path.expanduser("~/musicgen/wav")
OUT_OGG = os.path.expanduser("~/quiet-studio/app/src/main/assets/music")
os.makedirs(OUT_WAV, exist_ok=True)
os.makedirs(OUT_OGG, exist_ok=True)

try:
    import pyloudnorm as pyln
    METER = pyln.Meter(SR)
except Exception:
    METER = None

# ---------------------------------------------------------------- utilities
def midi_f(m): return 440.0 * 2 ** ((m - 69) / 12)

def env_ad(n, a, d, curve=4.0):
    """attack (samples) then exponential decay over remaining n samples"""
    a = max(1, min(a, n - 1))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a)
    t = np.arange(n - a) / SR
    e[a:] = np.exp(-curve * t / max(0.05, (n - a) / SR))
    return e

def env_adsr(n, a, r):
    a = max(1, min(a, n - 1)); r = max(1, min(r, n - a))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a) ** 1.5
    e[-r:] *= np.linspace(1, 0, r) ** 1.2
    return e

def lowpass(x, fc, order=2):
    b, a = butter(order, min(fc, SR / 2 - 100) / (SR / 2), "low")
    return lfilter(b, a, x)

def highpass(x, fc, order=2):
    b, a = butter(order, max(fc, 10) / (SR / 2), "high")
    return lfilter(b, a, x)

def saturate(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)

def add_wrap(buf, sig, start):
    """add sig into circular buffer at start (wraps => seamless loops)"""
    N = buf.shape[-1]
    idx = (start + np.arange(sig.shape[-1])) % N
    if buf.ndim == 2 and sig.ndim == 1:
        buf[0, idx] += sig; buf[1, idx] += sig
    elif buf.ndim == 2:
        buf[0, idx] += sig[0]; buf[1, idx] += sig[1]
    else:
        buf[idx] += sig

def circ_reverb(x, decay=1.8, tone=6000.0, wet=0.25, rng=None):
    """circular convolution reverb — tail folds around, loop stays seamless"""
    m = int(decay * SR)
    t = np.arange(m) / SR
    rng = rng or np.random.default_rng(7)
    ir = rng.standard_normal(m) * np.exp(-3.2 * t / decay)
    ir = lowpass(ir, tone); ir[:64] *= np.linspace(0.2, 1, 64)
    ir /= (np.sqrt(np.sum(ir ** 2)) + 1e-9)
    out = np.zeros_like(x)
    for ch in range(x.shape[0]):
        w = fftconvolve(x[ch], ir)
        N = x.shape[1]
        folded = w[:N].copy()
        tail = w[N:]
        for i in range(0, len(tail), N):
            seg = tail[i:i + N]
            folded[:len(seg)] += seg
        out[ch] = x[ch] * (1 - wet) + folded * wet
    return out

# ---------------------------------------------------------------- instruments
def tone_rhodes(f, dur, vel=0.8, rng=None):
    n = int(dur * SR); t = np.arange(n) / SR
    bell = np.sin(2 * np.pi * f * 4.02 * t) * np.exp(-t * 9) * 0.20 * vel
    body = (np.sin(2 * np.pi * f * t) +
            0.32 * np.sin(2 * np.pi * f * 2.001 * t) * np.exp(-t * 2.2) +
            0.06 * np.sin(2 * np.pi * f * 3.003 * t) * np.exp(-t * 4))
    trem = 1 + 0.06 * np.sin(2 * np.pi * 4.3 * t)
    y = (body * trem + bell) * env_ad(n, int(0.004 * SR), 1, 2.6) * vel
    return saturate(y * 0.8, 1.4)

def tone_pad(f, dur, vel=0.5, rng=None):
    n = int(dur * SR); t = np.arange(n) / SR
    y = np.zeros(n)
    rng = rng or np.random.default_rng(3)
    for det in (-0.006, -0.002, 0.003, 0.007):
        ph = rng.uniform(0, 2 * np.pi)
        for h, amp in ((1, 1.0), (2, 0.42), (3, 0.20), (4, 0.09), (5, 0.05)):
            y += amp * np.sin(2 * np.pi * f * (1 + det) * h * t + ph * h)
    lfo = 900 + 500 * np.sin(2 * np.pi * 0.11 * t + rng.uniform(0, 6))
    y = lowpass(y, float(np.mean(lfo)))
    y *= env_adsr(n, int(0.7 * SR), int(0.9 * SR)) * vel * 0.12
    return y

def tone_bass(f, dur, vel=0.85, pick=False):
    n = int(dur * SR); t = np.arange(n) / SR
    y = (np.sin(2 * np.pi * f * t) + 0.38 * np.sin(2 * np.pi * f * 2 * t) *
         np.exp(-t * 5) + 0.12 * np.sin(2 * np.pi * f * 3 * t) * np.exp(-t * 7))
    if pick:
        y += 0.15 * highpass(np.random.default_rng(int(f)).standard_normal(n), 1200) * np.exp(-t * 60)
    y *= env_ad(n, int(0.006 * SR), 1, 3.4) * vel
    return saturate(lowpass(y, 900), 1.8) * 0.9

def tone_pluck(f, dur, vel=0.7, damp=0.996, rng=None):
    """Karplus-Strong string"""
    n = int(dur * SR); period = max(2, int(SR / f))
    rng = rng or np.random.default_rng(int(f * 13) % 99991)
    buf = rng.uniform(-1, 1, period)
    buf = lowpass(buf, 5200)
    y = np.empty(n); prev = 0.0
    for i in range(n):
        v = buf[i % period]
        nv = damp * 0.5 * (v + prev)
        prev = v
        buf[i % period] = nv
        y[i] = v
    y *= env_adsr(n, 8, int(0.05 * SR)) * vel
    return y

def drum_kick(vel=1.0, boom=False):
    dur = 0.5 if not boom else 0.8
    n = int(dur * SR); t = np.arange(n) / SR
    fsweep = 40 + (130 - 40) * np.exp(-t * (26 if not boom else 15))
    ph = 2 * np.pi * np.cumsum(fsweep) / SR
    y = np.sin(ph) * np.exp(-t * (9 if not boom else 5.5))
    click = np.exp(-t * 300) * 0.35
    return saturate((y + click) * vel, 2.2) * 0.95

def drum_snare(vel=0.9, rimmy=False, rng=None):
    n = int(0.32 * SR); t = np.arange(n) / SR
    rng = rng or np.random.default_rng(11)
    noise = highpass(rng.standard_normal(n), 1400) * np.exp(-t * (26 if not rimmy else 44))
    tone = np.sin(2 * np.pi * 186 * t) * np.exp(-t * 30) * (0.7 if not rimmy else 0.25)
    return saturate((noise * 0.8 + tone) * vel, 1.6) * 0.8

def drum_hat(vel=0.5, open_=False, rng=None):
    n = int((0.30 if open_ else 0.07) * SR); t = np.arange(n) / SR
    rng = rng or np.random.default_rng(23)
    y = highpass(rng.standard_normal(n), 7800, 4) * np.exp(-t * (10 if open_ else 62))
    return y * vel * 0.55

def drum_shaker(vel=0.4, rng=None):
    n = int(0.13 * SR); t = np.arange(n) / SR
    rng = rng or np.random.default_rng(31)
    e = np.exp(-((t - 0.05) ** 2) / (2 * 0.018 ** 2))
    return highpass(rng.standard_normal(n), 5200, 4) * e * vel * 0.5

# ---------------------------------------------------------------- theory
MODES = {
    "major":      [0, 2, 4, 5, 7, 9, 11],
    "minor":      [0, 2, 3, 5, 7, 8, 10],
    "dorian":     [0, 2, 3, 5, 7, 9, 10],
    "mixolydian": [0, 2, 4, 5, 7, 9, 10],
    "lydian":     [0, 2, 4, 6, 7, 9, 11],
}
# degree-transition weights (functional-ish grammar, still free to wander)
TRANS = {
    0: [(3, 3), (5, 3), (1, 2), (4, 2), (2, 1), (6, 1)],
    1: [(4, 4), (6, 2), (0, 1), (2, 1)],
    2: [(5, 3), (3, 2), (0, 2)],
    3: [(4, 3), (0, 3), (1, 2), (6, 1)],
    4: [(0, 4), (5, 2), (3, 1)],
    5: [(1, 3), (3, 3), (4, 2), (0, 2)],
    6: [(0, 3), (4, 2), (5, 1)],
}

def gen_progression(rng, length, mode):
    scale = MODES[mode]
    deg = rng.choice([0, 5, 3])
    prog = []
    for _ in range(length):
        prog.append(deg)
        opts = TRANS[deg]
        w = np.array([o[1] for o in opts], float); w /= w.sum()
        deg = opts[rng.choice(len(opts), p=w)][0]
    if prog[-1] == prog[0] and length > 2:
        prog[-1] = TRANS[prog[0]][0][0]
    return prog

def chord_tones(root_deg, mode, rng, richness=0.7):
    scale = MODES[mode]
    def pc(d): return scale[d % 7] + 12 * (d // 7)
    tones = [pc(root_deg), pc(root_deg + 2), pc(root_deg + 4)]
    if rng.random() < richness: tones.append(pc(root_deg + 6))      # 7th
    if rng.random() < richness * 0.6: tones.append(pc(root_deg + 8))  # 9th
    if rng.random() < richness * 0.25: tones.append(pc(root_deg + 10)) # 11/13ish
    return tones

def voice_lead(tones, key_root, center=60, prev=None):
    notes = []
    for t in tones:
        m = key_root + t
        while m < center - 7: m += 12
        while m > center + 9: m -= 12
        notes.append(m)
    notes = sorted(set(notes))
    if prev:
        pc_prev = np.mean(prev)
        best = min(range(-1, 2), key=lambda s: abs(np.mean([n + 12 * s for n in notes]) - pc_prev))
        notes = [n + 12 * best for n in notes]
    return notes

# ---------------------------------------------------------------- composer
def compose(spec):
    rng = np.random.default_rng(spec["seed"])
    bpm, bars = spec["bpm"], spec["bars"]
    beat = 60.0 / bpm
    loop_len = bars * 4 * beat
    N = int(round(loop_len * SR))
    mode = spec["mode"]; key_root = spec["key_root"]
    energy = spec["energy"]

    chords_per_loop = spec.get("chords", 4 if bars <= 8 else 8)
    prog = gen_progression(rng, chords_per_loop, mode)
    chord_dur = loop_len / chords_per_loop

    stems = {k: np.zeros((2, N)) for k in
             ("keys", "pad", "bass", "melody", "drums", "texture")}
    swing = spec.get("swing", 0.55)
    inst = spec["instruments"]

    def pan(sig, p):  # p in [-1,1]
        l = np.sqrt(0.5 * (1 - p)); r = np.sqrt(0.5 * (1 + p))
        return np.vstack([sig * l, sig * r])

    def humanize(t0, amt=0.008):
        return max(0.0, t0 + rng.normal(0, amt))

    # ---- chords / keys + pad + bass
    prev_voicing = None
    for ci, deg in enumerate(prog):
        t0 = ci * chord_dur
        tones = chord_tones(deg, mode, rng, spec.get("richness", 0.75))
        voicing = voice_lead(tones, key_root, spec.get("center", 62), prev_voicing)
        prev_voicing = voicing

        if "keys" in inst:
            # comping pattern: sustained or syncopated stabs
            if rng.random() < 0.5 or energy <= 2:
                starts = [0.0]
                if rng.random() < 0.5: starts.append(chord_dur * 0.5 + beat * 0.25)
            else:
                starts = [0.0, beat * 1.5, beat * 2.5][:rng.integers(2, 4)]
            for s in starts:
                dur = min(chord_dur, chord_dur - s + beat * 0.5) * rng.uniform(0.8, 1.1)
                for ni, m in enumerate(voicing):
                    v = rng.uniform(0.5, 0.75) * (1 - 0.05 * ni)
                    sig = tone_rhodes(midi_f(m), max(0.4, dur), v, rng)
                    add_wrap(stems["keys"], pan(sig, rng.uniform(-0.35, 0.35)),
                             int(humanize(t0 + s) * SR))

        if "pad" in inst:
            for m in voicing[:3 + (energy > 2)]:
                sig = tone_pad(midi_f(m - 12 * (m > 66)), chord_dur * 1.15,
                               0.5 + 0.08 * energy, rng)
                add_wrap(stems["pad"], pan(sig, rng.uniform(-0.5, 0.5)), int(t0 * SR))

        if "bass" in inst:
            root_m = key_root + MODES[mode][deg % 7] - 24
            while root_m < 30: root_m += 12
            while root_m > 46: root_m -= 12
            if energy <= 2:
                events = [(0.0, chord_dur * 0.9, root_m)]
                if rng.random() < 0.4:
                    events.append((chord_dur - beat * 0.5, beat * 0.45,
                                   root_m + rng.choice([7, 12, -2])))
            else:
                grid = [0, 1.5, 2, 3, 3.5][:2 + energy]
                events = []
                for g in grid:
                    if rng.random() < 0.75:
                        pitch = root_m + rng.choice([0, 0, 0, 7, 10, 12, 3])
                        events.append((g * beat, beat * rng.uniform(0.4, 0.9), pitch))
            for s, d, m in events:
                if s >= chord_dur: continue
                sig = tone_bass(midi_f(m), d, rng.uniform(0.7, 0.9),
                                pick=spec.get("bass_pick", False))
                add_wrap(stems["bass"], pan(sig, 0.0), int(humanize(t0 + s, 0.005) * SR))

    # ---- melody (sparse motif, guitar or keys lead)
    if "melody" in inst:
        scale = MODES[mode]
        motif_len = rng.integers(3, 6)
        motif = np.cumsum(np.r_[0, rng.integers(-2, 3, motif_len - 1)])
        lead = spec.get("lead", "pluck")
        deg0 = rng.integers(0, 7)
        for bar in range(bars):
            if rng.random() > spec.get("melody_density", 0.45): continue
            t0 = bar * 4 * beat + rng.choice([0, 0.5, 1.0, 1.5]) * beat
            step_dur = beat * rng.choice([0.5, 0.75, 1.0])
            for k, dstep in enumerate(motif):
                d = int(deg0 + dstep)
                m = key_root + 12 + scale[d % 7] + 12 * (d // 7)
                while m > key_root + 31: m -= 12
                dur = step_dur * rng.uniform(1.1, 2.2)
                v = rng.uniform(0.35, 0.6)
                if lead == "pluck":
                    sig = tone_pluck(midi_f(m), max(0.5, dur), v, rng=rng)
                else:
                    sig = tone_rhodes(midi_f(m), max(0.5, dur), v, rng)
                add_wrap(stems["melody"], pan(sig, rng.uniform(-0.2, 0.6)),
                         int(humanize(t0 + k * step_dur) * SR))
            deg0 = int((deg0 + rng.integers(-2, 3)) % 7)

    # ---- drums
    if "drums" in inst and energy >= 1:
        style = spec.get("drum_style", "lofi")
        for bar in range(bars):
            bt = bar * 4 * beat
            # kick
            kicks = {"lofi": [0, 2.5], "boombap": [0, 1.75, 2.5],
                     "soft": [0], "half": [0]}[style]
            if rng.random() < 0.35: kicks = kicks + [3.25]
            for kt in kicks:
                add_wrap(stems["drums"], pan(drum_kick(rng.uniform(0.85, 1.0)), 0),
                         int(humanize(bt + kt * beat, 0.004) * SR))
            # snare on 2 & 4 (or just 3 for half-time)
            snares = [1, 3] if style != "half" else [2]
            for st in snares:
                add_wrap(stems["drums"],
                         pan(drum_snare(rng.uniform(0.75, 0.95),
                                        rimmy=spec.get("rim", False), rng=rng), 0.05),
                         int(humanize(bt + st * beat, 0.005) * SR))
            # hats w/ swing
            if energy >= 2:
                for e8 in range(8):
                    tt = e8 * 0.5
                    if e8 % 2 == 1: tt += (swing - 0.5) * 0.5
                    if rng.random() < 0.9:
                        add_wrap(stems["drums"],
                                 pan(drum_hat(rng.uniform(0.3, 0.55),
                                              open_=(e8 == 7 and rng.random() < 0.2),
                                              rng=rng), -0.15), int((bt + tt * beat) * SR))
            if spec.get("shaker", False):
                for e8 in range(8):
                    add_wrap(stems["drums"],
                             pan(drum_shaker(rng.uniform(0.25, 0.4), rng), 0.3),
                             int((bt + (e8 * 0.5 + 0.25) * beat) * SR))

    # ---- texture: vinyl, air
    if spec.get("vinyl", True):
        crackle = np.zeros(N)
        pops = rng.integers(0, N, int(loop_len * rng.uniform(6, 14)))
        crackle[pops] = rng.uniform(-1, 1, len(pops))
        crackle = lowpass(crackle, 7500) * 0.5
        hiss = lowpass(highpass(rng.standard_normal(N), 2000), 9000) * 0.012
        tex = crackle * 0.04 + hiss
        stems["texture"] += np.vstack([tex, np.roll(tex, 313)])
    if spec.get("air", False):
        airn = lowpass(rng.standard_normal(N), 600) * 0.02
        lfo = 0.5 + 0.5 * np.sin(2 * np.pi * np.arange(N) / N * spec.get("air_cycles", 2))
        stems["texture"] += np.vstack([airn * lfo, np.roll(airn * lfo, 977)])

    # ---- mix
    g = spec.get("gains", {})
    mix = (stems["keys"] * g.get("keys", 0.9) + stems["pad"] * g.get("pad", 1.0) +
           stems["bass"] * g.get("bass", 0.9) + stems["melody"] * g.get("melody", 0.8) +
           stems["drums"] * g.get("drums", 0.85) + stems["texture"] * g.get("texture", 1.0))

    # gentle glue: reverb (circular => seamless)
    mix = circ_reverb(mix, decay=spec.get("verb_decay", 1.6),
                      tone=spec.get("verb_tone", 5200),
                      wet=spec.get("verb_wet", 0.22), rng=rng)
    # lo-fi character
    if spec.get("lofi_filter", True):
        mix = np.vstack([lowpass(mix[0], spec.get("lp", 11000)),
                         lowpass(mix[1], spec.get("lp", 11000))])
        mix = np.vstack([highpass(mix[0], 38), highpass(mix[1], 38)])
    mix = saturate(mix * 1.1, spec.get("tape_drive", 1.35))
    return mix.astype(np.float64), N

# ---------------------------------------------------------------- mastering
def master(mix, target_lufs=-16.0):
    peak = np.max(np.abs(mix)) + 1e-9
    mix = mix / peak * 0.95
    mix = np.tanh(mix * 1.1) / np.tanh(1.1)            # gentle glue limiter
    if METER is not None:
        loud = METER.integrated_loudness(mix.T)
        gain = 10 ** ((target_lufs - loud) / 20)
    else:
        rms = np.sqrt(np.mean(mix ** 2)); gain = (10 ** (-19 / 20)) / (rms + 1e-9)
    mix = mix * gain
    peak = np.max(np.abs(mix))
    if peak > 0.98:                                    # keep true-peak headroom
        mix = mix / peak * 0.98
    return mix

def write_wav(path, mix):
    from scipy.io import wavfile
    wavfile.write(path, SR, (mix.T * 32767).astype(np.int16))

# ---------------------------------------------------------------- catalog
K = {"C": 60, "Db": 61, "D": 62, "Eb": 63, "E": 64, "F": 65,
     "Gb": 66, "G": 55, "Ab": 56, "A": 57, "Bb": 58, "B": 59}

def T(title, seed, mood, tags, bpm, key, mode, energy, bars, inst, **kw):
    d = dict(title=title, seed=seed, mood=mood, tags=tags, bpm=bpm,
             key=key, key_root=K[key], mode=mode, energy=energy, bars=bars * 2,
             instruments=inst)
    d.update(kw); return d

CATALOG = [
 T("Amber Hour",       101, "Reflective",   ["lofi","soulful","chill"],        72, "F",  "major", 2, 8,  ["keys","pad","bass","drums","melody"], drum_style="lofi", lead="pluck"),
 T("Paper Lanterns",   102, "Calm",         ["lofi","minimal","atmospheric"],  68, "D",  "dorian", 1, 8,  ["keys","pad","bass"], vinyl=True, verb_wet=0.3),
 T("Slow Orbit",       103, "Deep Focus",   ["ambient","minimal","focus"],     60, "A",  "lydian", 1, 8,  ["pad","bass","melody"], lead="keys", melody_density=0.3, air=True, lp=9000),
 T("Night Ferry",      104, "Late Night",   ["jazz","lofi","hiphop"],          66, "Eb", "minor", 2, 8,  ["keys","pad","bass","drums"], drum_style="lofi", rim=True, verb_decay=2.0),
 T("Morning Salt",     105, "Uplifting",    ["chill","fusion","hopeful"],      88, "G",  "major", 3, 8,  ["keys","bass","drums","melody"], drum_style="boombap", shaker=True, lead="pluck", lp=13000),
 T("Quiet Machinery",  106, "Creative",     ["hiphop","boombap","focus"],      84, "A",  "minor", 3, 8,  ["keys","bass","drums"], drum_style="boombap", tape_drive=1.6),
 T("Glass Garden",     107, "Peaceful",     ["ambient","atmospheric","minimal"],62,"Db", "major", 1, 8,  ["pad","melody"], lead="keys", melody_density=0.35, air=True, verb_wet=0.34, verb_decay=2.6, lp=8500),
 T("Vellum",           108, "Documentary",  ["cinematic","emotional","minimal"],70,"C",  "minor", 2, 8,  ["keys","pad","bass"], verb_decay=2.2, richness=0.6),
 T("Warm Static",      109, "Chill",        ["lofi","psychedelic","chill"],    74, "Bb", "dorian", 2, 8,  ["keys","pad","bass","drums","melody"], drum_style="lofi", lead="pluck", lp=10000),
 T("Longhand",         110, "Deep Thinking",["jazz","soulful","focus"],        64, "F",  "dorian", 2, 8,  ["keys","bass","drums"], drum_style="soft", rim=True, richness=0.9),
 T("Kite Weather",     111, "Hopeful",      ["fusion","uplifting","chill"],    92, "D",  "major", 3, 8,  ["keys","bass","drums","melody"], drum_style="boombap", lead="pluck", shaker=True, lp=12500),
 T("Low Tide Study",   112, "Deep Focus",   ["minimal","ambient","focus"],     58, "G",  "major", 1, 8,  ["pad","bass"], air=True, air_cycles=3, verb_wet=0.3, lp=8000),
 T("Copper Dusk",      113, "Late Night",   ["soulful","jazz","emotional"],    69, "Ab", "minor", 2, 8,  ["keys","pad","bass","drums"], drum_style="lofi", richness=0.85, verb_decay=1.9),
 T("Small Hours",      114, "Late Night",   ["lofi","minimal","chill"],        63, "E",  "minor", 1, 8,  ["keys","bass"], vinyl=True, verb_wet=0.28),
 T("Bloom Cycle",      115, "Inspirational",["cinematic","uplifting","emotional"],76,"C","major", 3, 8, ["keys","pad","bass","drums","melody"], drum_style="half", lead="keys", verb_decay=2.4, lp=12000),
 T("Ink & Water",      116, "Creative",     ["jazz","lofi","psychedelic"],     78, "Bb", "dorian", 3, 8,  ["keys","bass","drums","melody"], drum_style="lofi", lead="pluck", swing=0.6),
 T("Field Notes",      117, "Documentary",  ["cinematic","minimal","focus"],   72, "D",  "mixolydian",2,8,["keys","pad","bass","drums"], drum_style="soft", rim=True, richness=0.55),
 T("Sunprint",         118, "Uplifting",    ["soulful","fusion","hopeful"],    85, "Eb", "major", 3, 8,  ["keys","bass","drums","melody"], drum_style="boombap", lead="pluck", shaker=True),
 T("Attic Light",      119, "Reflective",   ["lofi","emotional","chill"],      70, "A",  "major", 2, 8,  ["keys","pad","bass","drums"], drum_style="lofi", verb_wet=0.26),
 T("Undercurrent",     120, "Deep Thinking",["ambient","psychedelic","focus"], 61, "F",  "minor", 1, 8,  ["pad","bass","melody"], lead="keys", melody_density=0.25, air=True, verb_decay=3.0, lp=7800),
 T("Terracotta",       121, "Soulful",      ["soulful","jazz","hiphop"],       80, "G",  "dorian", 3, 8,  ["keys","bass","drums","melody"], drum_style="boombap", lead="keys", richness=0.9, swing=0.58),
 T("First Light Loop", 122, "Morning Energy",["hopeful","chill","fusion"],     96, "C",  "lydian", 3, 8,  ["keys","bass","drums","melody"], drum_style="boombap", lead="pluck", shaker=True, lp=13500),
 T("Winter Radio",     123, "Calm",         ["lofi","minimal","emotional"],    65, "Db", "major", 1, 8,  ["keys","pad"], vinyl=True, lp=9500, verb_wet=0.3),
 T("Open Window",      124, "Peaceful",     ["ambient","chill","atmospheric"], 66, "E",  "major", 1, 8,  ["pad","melody","bass"], lead="keys", melody_density=0.3, air=True),
 T("Marginalia",       125, "Creative",     ["jazz","hiphop","focus"],         82, "B",  "dorian", 3, 8,  ["keys","bass","drums"], drum_style="lofi", swing=0.62, richness=0.85),
 T("Slow Cartography", 126, "Documentary",  ["cinematic","emotional","minimal"],68,"A",  "minor", 2, 8,  ["keys","pad","bass","drums"], drum_style="half", verb_decay=2.5),
 T("Honey & Smoke",    127, "Soulful",      ["soulful","lofi","emotional"],    73, "Bb", "major", 2, 8,  ["keys","pad","bass","drums"], drum_style="lofi", richness=0.95, tape_drive=1.5),
 T("Clear Pool",       128, "Deep Focus",   ["minimal","ambient","focus"],     57, "D",  "major", 1, 8,  ["pad","bass"], air=True, verb_wet=0.32, lp=7500, vinyl=False),
 T("Golden Ratio",     129, "Inspirational",["fusion","uplifting","cinematic"],90, "F",  "lydian", 3, 8,  ["keys","pad","bass","drums","melody"], drum_style="boombap", lead="keys", lp=12800),
 T("Afterglow Motel",  130, "Late Night",   ["psychedelic","lofi","chill"],    67, "Gb", "dorian", 2, 8,  ["keys","pad","bass","drums","melody"], drum_style="lofi", lead="pluck", verb_decay=2.2, lp=9800),
]

def main():
    manifest = []
    for i, spec in enumerate(CATALOG):
        tid = f"qs{spec['seed']}"
        print(f"[{i+1}/{len(CATALOG)}] {spec['title']} ({spec['mood']}, {spec['bpm']}bpm)", flush=True)
        mix, N = compose(spec)
        mix = master(mix)
        wav = os.path.join(OUT_WAV, f"{tid}.wav")
        write_wav(wav, mix)
        ogg = os.path.join(OUT_OGG, f"{tid}.ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
                        "-c:a", "libvorbis", "-q:a", "6", ogg], check=True)
        dur = N / SR
        lufs = METER.integrated_loudness(mix.T) if METER else None
        manifest.append({
            "id": tid, "title": spec["title"], "mood": spec["mood"],
            "tags": spec["tags"], "bpm": spec["bpm"],
            "key": f'{spec["key"]} {spec["mode"]}',
            "energy": spec["energy"], "durationSec": round(dur, 3),
            "file": f"music/{tid}.ogg", "loop": True,
            "lufs": round(lufs, 2) if lufs is not None else None,
            "instruments": spec["instruments"],
        })
    with open(os.path.join(OUT_OGG, "manifest.json"), "w") as f:
        json.dump({"version": 1, "tracks": manifest}, f, indent=2)
    print("DONE", len(manifest), "tracks")

if __name__ == "__main__":
    main()
