"""
Starfallen sound synthesizer. Every sound effect and the boss theme are generated from scratch
with numpy/scipy and written as Ogg Vorbis files plus sounds.json.

    python3 tools/soundgen.py
"""
import json
import os

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/starfallen/sounds")
RNG = np.random.default_rng(1234)

# ============================================================================== synth toolkit


def ns(d):
    return max(1, int(round(SR * d)))


def tm(d):
    return np.arange(ns(d)) / SR


def fr(f, d):
    """Per-sample frequency array from a scalar, an array or a function of time."""
    if callable(f):
        return np.asarray(f(tm(d)), float)
    if np.ndim(f) == 0:
        return np.full(ns(d), float(f))
    return np.asarray(f, float)


def glide(f0, f1, d, curve="exp"):
    x = tm(d) / d
    if curve == "exp":
        return f0 * (f1 / f0) ** x
    return f0 + (f1 - f0) * x


def osc(f, d, shape="sine", ph0=0.0):
    ph = np.cumsum(fr(f, d)) / SR + ph0
    frac = ph % 1.0
    if shape == "sine":
        return np.sin(2 * np.pi * ph)
    if shape == "saw":
        return 2 * frac - 1
    if shape == "square":
        return np.where(frac < 0.5, 1.0, -1.0)
    if shape == "tri":
        return 4 * np.abs(frac - 0.5) - 1
    raise ValueError(shape)


def fm(fc, ratio, index, d, index_env=None):
    """Two-operator FM: great for bells and metallic tones."""
    f = fr(fc, d)
    mod = np.sin(2 * np.pi * np.cumsum(f * ratio) / SR)
    idx = index * (index_env if index_env is not None else 1.0)
    return np.sin(2 * np.pi * np.cumsum(f) / SR + idx * mod)


def noise(d, seed=None):
    r = np.random.default_rng(seed) if seed is not None else RNG
    return r.standard_normal(ns(d)) * 0.35


def _sos(kind, fc, order=2):
    if kind == "band":
        lo, hi = fc
        lo = max(20.0, lo)
        hi = min(SR * 0.45, hi)
        return signal.butter(order, [lo, hi], "bandpass", fs=SR, output="sos")
    return signal.butter(order, min(max(fc, 20.0), SR * 0.45), kind, fs=SR, output="sos")


def lp(x, fc, order=2):
    return signal.sosfilt(_sos("low", fc, order), x)


def hp(x, fc, order=2):
    return signal.sosfilt(_sos("high", fc, order), x)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos("band", (lo, hi), order), x)


def sweep(x, fcs, kind="low", block=256, width=0.5):
    """Time-varying filter by block processing. fcs is a per-sample cutoff (or band centre) array."""
    out = np.zeros_like(x)
    zi = None
    fcs = np.broadcast_to(fcs, x.shape)
    for i in range(0, len(x), block):
        fc = float(np.mean(fcs[i:i + block]))
        if kind == "band":
            sos = _sos("band", (fc * (1 - width), fc * (1 + width)))
        else:
            sos = _sos(kind, fc)
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        out[i:i + block], zi = signal.sosfilt(sos, x[i:i + block], zi=zi)
    return out


def formant(x, vowel):
    """Crude vowel filter: sum of three resonant band-passes."""
    table = {
        "a": ((730, 1.0), (1090, 0.5), (2440, 0.25)),
        "o": ((570, 1.0), (840, 0.45), (2410, 0.2)),
        "u": ((300, 1.0), (870, 0.35), (2240, 0.15)),
        "e": ((530, 1.0), (1840, 0.5), (2480, 0.3)),
        "aw": ((500, 1.0), (700, 0.7), (2300, 0.2)),
    }
    out = np.zeros_like(x)
    for f, g in table[vowel]:
        out += bp(x, f * 0.88, f * 1.12) * g
    return out


def env_exp(d, tau):
    return np.exp(-tm(d) / tau)


def env_ad(d, attack, tau=None, power=1.0):
    t = tm(d)
    e = np.where(t < attack, (t / max(attack, 1e-6)) ** power, 1.0)
    if tau is not None:
        e = e * np.where(t < attack, 1.0, np.exp(-(t - attack) / tau))
    return e


def adsr(d, a, dc, s, r):
    t = tm(d)
    e = np.ones_like(t) * s
    e = np.where(t < a, t / max(a, 1e-6), e)
    e = np.where((t >= a) & (t < a + dc), 1 - (1 - s) * (t - a) / max(dc, 1e-6), e)
    rel_start = d - r
    e = np.where(t >= rel_start, e * np.clip((d - t) / max(r, 1e-6), 0, 1), e)
    return e


def hump(d, peak=0.5, power=2.0):
    """Rises to 1 at `peak` (fraction of d) then falls."""
    x = tm(d) / d
    return np.where(x < peak, (x / peak) ** power, ((1 - x) / (1 - peak)) ** power)


def fade(x, fin=0.004, fout=0.03):
    x = x.copy()
    a, b = min(len(x), ns(fin)), min(len(x), ns(fout))
    x[:a] *= np.linspace(0, 1, a)
    x[len(x) - b:] *= np.linspace(1, 0, b)
    return x


def dist(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def pad_to(x, n):
    return x if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])


def mixdown(*parts):
    """parts: (offset_seconds, array[, gain])."""
    length = max(ns(p[0]) + len(p[1]) for p in parts)
    out = np.zeros(length)
    for p in parts:
        at, x = ns(p[0]) if p[0] > 0 else 0, p[1]
        g = p[2] if len(p) > 2 else 1.0
        out[at:at + len(x)] += x * g
    return out


def reverb(x, length=1.8, decay=0.45, mix=0.3, lpf=7000, seed=7, predelay=0.012):
    r = np.random.default_rng(seed)
    t = tm(length)
    ir = r.standard_normal(len(t)) * np.exp(-t / decay)
    ir = lp(ir, lpf)
    ir[:ns(predelay)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(x, ir)
    dry = pad_to(x, len(wet))
    return dry * (1 - mix) + wet * mix * 1.6


def crackles(d, count, lo=1500, hi=6000, tau=0.004, seed=3, density=None):
    r = np.random.default_rng(seed)
    out = np.zeros(ns(d))
    for _ in range(count):
        t0 = r.random() if density is None else density(r)
        at = int(min(max(t0, 0), 0.999) * len(out))
        click = bp(r.standard_normal(ns(tau * 6)), lo, hi) * env_exp(tau * 6, tau)
        seg = out[at:at + len(click)]
        seg += click[:len(seg)] * (0.4 + r.random() * 0.8)
    return out


def pluck(freq, d, damp=0.996, seed=5):
    """Karplus-Strong string."""
    r = np.random.default_rng(seed)
    period = max(2, int(SR / freq))
    buf = r.uniform(-1, 1, period)
    out = np.zeros(ns(d))
    for i in range(len(out)):
        out[i] = buf[i % period]
        buf[i % period] = damp * 0.5 * (buf[i % period] + buf[(i + 1) % period])
    return out


def bell(freq, d, bright=3.0, tau=0.6, ratio=3.5):
    idx_env = env_exp(d, tau * 0.35)
    return fm(freq, ratio, bright, d, idx_env) * env_ad(d, 0.002, tau)


def note(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def normalize(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m < 1e-9 else x / m * peak


def trim_tail(x, thresh=0.003):
    idx = np.nonzero(np.abs(x) > thresh)[0]
    if len(idx) == 0:
        return x
    end = min(len(x), idx[-1] + ns(0.05))
    return x[:end]


WRITTEN = {}


def write(name, x, peak=0.9, stereo=False):
    if stereo:
        x = x / (np.max(np.abs(x)) + 1e-9) * peak
        data = x.T.astype(np.float32)
    else:
        x = trim_tail(normalize(x, peak))
        x = fade(x, 0.002, 0.12)
        data = x.astype(np.float32)
    os.makedirs(OUT, exist_ok=True)
    # libsndfile's Vorbis encoder can crash on very large single writes, so feed it in chunks
    with sf.SoundFile(os.path.join(OUT, name + ".ogg"), "w", SR, 2 if stereo else 1, format="OGG", subtype="VORBIS") as fh:
        for i in range(0, len(data), 8192):
            fh.write(data[i:i + 8192])
    WRITTEN[name] = len(data) / SR


# ============================================================================== sound effects


def meteor_fall(v=0):
    d = 4.0
    t = tm(d)
    rise = (t / d) ** 1.6
    whistle = osc(glide(2200 - v * 200, 480, d) * (1 + 0.01 * np.sin(2 * np.pi * 7 * t)), d) * (0.2 + 0.6 * rise)
    roar = sweep(noise(d, 10 + v), 300 + 2600 * rise, "low") * (0.1 + 1.4 * rise)
    rumble = lp(noise(d, 20 + v), 120) * 3 * rise
    crk = crackles(d, 70, 800, 5000, 0.003, 30 + v, density=lambda r: r.random() ** 0.5) * rise
    x = whistle * 0.5 + roar + rumble + crk * 0.5
    x *= np.clip((d - t) / 0.25, 0, 1)
    return reverb(x, 1.5, 0.35, 0.2)


def meteor_impact(v=0):
    d = 3.2
    boom = osc(glide(95, 32, 0.9), 0.9) * env_exp(0.9, 0.4)
    boom = dist(pad_to(boom, ns(d)) * 1.5, 2.5)
    blast = lp(noise(d, 40 + v), 3200) * env_exp(d, 0.18) * 2.2
    body = lp(noise(d, 41 + v), 420) * env_ad(d, 0.01, 0.9) * 3.0
    debris = crackles(d, 55, 900, 5000, 0.004, 42 + v, density=lambda r: 0.05 + r.random() ** 2 * 0.7)
    x = boom * 1.1 + blast + body + debris * 0.6
    return reverb(x, 2.6, 0.7, 0.3, 5000)


def starfall_begin():
    d = 5.5
    t = tm(d)
    chord = sum(osc(note(m) * (1 + det), d) for m in (62, 65, 69, 76, 81) for det in (-0.003, 0.003))
    chord = lp(chord, 3000) * env_ad(d, 1.8, 2.5) * 0.25
    swell = sweep(noise(d, 50), 200 + 5000 * (t / d) ** 2, "band", width=0.3) * hump(d, 0.75) * 1.2
    r = np.random.default_rng(51)
    sparkles = np.zeros(ns(d))
    for _ in range(26):
        at = ns(0.4 + r.random() * 4.5)
        b = bell(note(r.choice([81, 84, 86, 88, 93, 96])), 1.2, 2.0, 0.35) * 0.18
        sparkles[at:at + len(b)] += b[:len(sparkles) - at]
    x = chord + swell + sparkles
    return reverb(x, 3.5, 1.2, 0.45)


def star_chime(v=0):
    d = 2.0
    seq = [(0.0, 88), (0.07, 93), (0.14, 96), (0.24, 100)] if v == 0 else [(0.0, 86), (0.08, 91), (0.16, 98)]
    x = mixdown(*[(at, bell(note(m), d - at, 2.5, 0.55), 0.35) for at, m in seq])
    x += hp(noise(len(x) / SR, 60 + v), 6000) * env_exp(len(x) / SR, 0.25) * 0.15
    return reverb(x, 2.0, 0.7, 0.4)


def wisp_ambient(v):
    r = np.random.default_rng(70 + v)
    d = 1.2
    parts = []
    for k in range(3):
        f = note(r.choice([84, 86, 88, 91, 93, 96]))
        dd = 0.5 + r.random() * 0.4
        tone = osc(f * (1 + 0.012 * np.sin(2 * np.pi * 6 * tm(dd))), dd) * env_ad(dd, 0.03, 0.18)
        tone += osc(f * 2.01, dd) * env_ad(dd, 0.01, 0.08) * 0.3
        parts.append((k * 0.18 + r.random() * 0.05, tone, 0.4))
    x = mixdown(*parts)
    x += bp(noise(len(x) / SR, 80 + v), 3000, 8000) * hump(len(x) / SR, 0.3) * 0.12
    return reverb(pad_to(x, ns(d)), 1.5, 0.5, 0.45)


def wisp_hurt():
    d = 0.4
    x = osc(glide(2300, 800, d), d) * env_exp(d, 0.12)
    x += osc(glide(3400, 1300, d), d) * env_exp(d, 0.06) * 0.4
    x += hp(noise(d, 90), 4000) * env_exp(d, 0.05) * 0.5
    return reverb(x, 0.8, 0.25, 0.25)


def wisp_death():
    d = 1.6
    seq = [96, 93, 91, 88, 84, 81]
    x = mixdown(*[(i * 0.09, bell(note(m), 0.8, 1.5, 0.3), 0.3) for i, m in enumerate(seq)])
    x = pad_to(x, ns(d))
    x += hp(noise(d, 95), 5000) * hump(d, 0.3) * 0.2
    return reverb(x, 2.0, 0.6, 0.45)


def golem_ambient(v):
    d = 1.8
    t = tm(d)
    r = np.random.default_rng(100 + v)
    grind_mod = np.abs(lp(r.standard_normal(ns(d)), 18)) * 8
    grind = bp(noise(d, 101 + v), 140, 700) * grind_mod
    growl = lp(osc(52 + v * 6 + 4 * np.sin(2 * np.pi * 1.3 * t), d, "saw"), 260) * hump(d, 0.4, 1.2)
    sizzle = hp(noise(d, 102 + v), 5000) * 0.08 + crackles(d, 18, 2000, 7000, 0.002, 103 + v) * 0.35
    x = (grind * 0.8 + growl * 0.9 + sizzle) * adsr(d, 0.15, 0.2, 0.8, 0.5)
    return reverb(x, 1.0, 0.3, 0.15)


def golem_hurt(v=0):
    d = 0.7
    crack = hp(noise(d, 110 + v), 900) * env_exp(d, 0.05) * 1.6
    thud = osc(glide(140, 55, d), d) * env_exp(d, 0.14)
    grunt = lp(osc(glide(90, 60, d), d, "saw"), 400) * env_ad(d, 0.02, 0.18) * 0.7
    x = crack + thud * 1.2 + grunt + crackles(d, 10, 1500, 5000, 0.003, 111 + v) * 0.6
    return reverb(x, 0.8, 0.25, 0.15)


def golem_death():
    d = 3.0
    parts = [(0.0, golem_hurt(1), 1.0)]
    for i in range(5):
        parts.append((0.35 + i * 0.28, hp(noise(0.4, 120 + i), 600) * env_exp(0.4, 0.06), 0.8 - i * 0.1))
    rumble = lp(noise(d, 130), 180) * env_ad(d, 0.2, 0.9) * 3.0
    sizzle = hp(noise(d, 131), 4500) * env_ad(d, 0.4, 1.0) * 0.35
    thud = osc(glide(80, 30, 1.0), 1.0) * env_exp(1.0, 0.3)
    parts += [(0.0, rumble), (0.0, sizzle), (1.9, thud, 1.3)]
    return reverb(mixdown(*parts), 1.8, 0.5, 0.25)


def golem_slam():
    d = 1.6
    thud = dist(osc(glide(75, 28, d), d) * env_exp(d, 0.33) * 1.6, 2.0)
    blast = lp(noise(d, 140), 900) * env_exp(d, 0.2) * 2.0
    debris = crackles(d, 30, 800, 4500, 0.004, 141, density=lambda r: 0.05 + r.random() ** 2 * 0.6)
    return reverb(thud + blast + debris * 0.5, 1.6, 0.5, 0.25, 4000)


def stalker_ambient(v):
    d = 2.6
    t = tm(d)
    drone = (osc(55 + v * 3, d) + osc(55.8 + v * 3, d) + osc(77.8 + v * 4, d) * 0.5) * hump(d, 0.5, 1.0) * 0.5
    whisper_env = np.abs(lp(np.random.default_rng(150 + v).standard_normal(ns(d)), 6)) * 10
    whisper = formant(noise(d, 151 + v), "e" if v else "u") * whisper_env * hump(d, 0.5, 1.0)
    ring = osc(1480 + 30 * np.sin(2 * np.pi * 0.7 * t), d) * hump(d, 0.6, 2) * 0.05
    x = drone * 0.7 + whisper * 1.2 + ring
    return reverb(x, 2.5, 0.9, 0.5)


def stalker_scream():
    d = 1.5
    t = tm(d)
    pitch = 1 + 0.35 * np.sin(np.pi * np.clip(t / 0.35, 0, 1)) - 0.45 * np.clip((t - 0.35) / 1.1, 0, 1)
    vib = 1 + 0.03 * np.sin(2 * np.pi * 23 * t)
    tones = sum(osc(f * pitch * vib, d, "saw") * g for f, g in ((690, 1), (733, 0.9), (1041, 0.6), (1395, 0.4)))
    x = bp(tones, 400, 6000) * 0.6 + hp(noise(d, 160), 2500) * 0.6
    x = dist(x * adsr(d, 0.03, 0.2, 0.8, 0.7), 3.0)
    x += formant(noise(d, 161), "a") * adsr(d, 0.02, 0.3, 0.5, 0.6) * 1.5
    return reverb(x, 2.0, 0.7, 0.4)


def stalker_hurt():
    d = 0.55
    r = np.random.default_rng(170)
    steps = np.repeat(r.uniform(180, 1400, 40), ns(0.03))[:ns(d)]
    steps = pad_to(steps, ns(d))
    x = osc(steps, d, "square") * 0.4 + hp(noise(d, 171), 1500) * 0.6
    gate = np.repeat(r.random(40) > 0.3, ns(0.015))[:ns(d)]
    x = dist(x * pad_to(gate.astype(float), ns(d)) * env_exp(d, 0.2), 2.5)
    return reverb(x, 0.9, 0.3, 0.25)


def stalker_death():
    d = 2.4
    t = tm(d)
    swell = hp(noise(d, 180), 800) * (t / d) ** 3 * 1.5
    swell *= np.where(t < d * 0.7, 1.0, 0.0)
    screech = osc(glide(1400, 180, 1.4, "exp"), 1.4, "saw") * env_ad(1.4, 0.05, 0.5)
    x = mixdown((0.0, swell), (0.2, bp(screech, 300, 5000), 0.7), (1.68, stalker_hurt(), 0.8))
    return reverb(x, 2.5, 0.9, 0.45)


def jelly_ambient(v):
    d = 1.8
    r = np.random.default_rng(190 + v)
    parts = []
    for k in range(5):
        dd = 0.18 + r.random() * 0.12
        f0 = 250 + r.random() * 250
        parts.append((0.1 + k * 0.22 + r.random() * 0.08, osc(glide(f0, f0 * 2.2, dd), dd) * env_ad(dd, 0.01, 0.07), 0.5))
    x = pad_to(mixdown(*parts), ns(d))
    hum = osc(110 * (1 + 0.02 * np.sin(2 * np.pi * 3 * tm(d))), d, "tri") * hump(d, 0.5, 1) * 0.12
    x = lp(x, 2200) + hum
    return reverb(x, 1.6, 0.6, 0.4)


def jelly_zap():
    d = 0.9
    t = tm(d)
    r = np.random.default_rng(200)
    crack_gate = (r.random(ns(d)) > 0.992).astype(float)
    crack = hp(signal.lfilter([1], [1, -0.97], crack_gate * r.standard_normal(ns(d))), 1500) * 2.5
    buzz = hp(osc(120, d, "square") + osc(240.5, d, "saw") * 0.5, 900) * 0.4
    buzz *= (0.6 + 0.4 * (np.sin(2 * np.pi * 37 * t) > 0))
    zap = osc(glide(3800, 900, 0.25), 0.25) * env_exp(0.25, 0.08)
    x = mixdown((0.0, (crack + buzz) * env_ad(d, 0.01, 0.3)), (0.0, zap, 0.5))
    return reverb(dist(x, 1.8), 0.8, 0.25, 0.2)


def starseer_ambient(v):
    d = 2.2
    t = tm(d)
    f = (150 if v == 0 else 175) * (1 + 0.012 * np.sin(2 * np.pi * 5.2 * t)) * (1 + 0.04 * t / d)
    voice = formant(osc(f, d, "saw") + osc(f * 1.005, d, "saw"), "u" if v == 0 else "o") * hump(d, 0.45, 1.3)
    whisper = formant(noise(d, 210 + v), "e") * hump(d, 0.6, 2) * 0.8
    x = voice * 1.4 + whisper
    return reverb(x, 2.2, 0.8, 0.45)


def starseer_cast():
    d = 1.6
    t = tm(d)
    rise = osc(glide(280, 1600, d * 0.7), d * 0.7) * env_ad(d * 0.7, 0.4, 0.6) * 0.4
    whoosh = sweep(noise(d, 220), 400 + 4000 * (t / d), "band", width=0.4) * hump(d, 0.55)
    stab = mixdown(*[(0.0, bell(note(m), 1.0, 2.0, 0.4), 0.25) for m in (74, 77, 81, 86)])
    x = mixdown((0.0, rise), (0.0, whoosh), (0.9, stab))
    x += crackles(len(x) / SR, 25, 4000, 9000, 0.002, 221) * 0.3
    return reverb(x, 2.0, 0.6, 0.4)


def starseer_hurt():
    d = 0.6
    f = glide(260, 190, d)
    x = formant(osc(f, d, "saw") + osc(f * 1.01, d, "saw"), "a") * env_ad(d, 0.02, 0.2)
    return reverb(x * 1.5, 1.0, 0.35, 0.3)


def starseer_death():
    d = 2.8
    f = glide(240, 90, d)
    voice = formant(osc(f, d, "saw") + osc(f * 1.007, d, "saw") + osc(f * 1.5, d, "saw") * 0.5, "a") * env_ad(d, 0.05, 1.0)
    sparkle = crackles(d, 60, 5000, 10000, 0.002, 230) * env_ad(d, 0.4, 1.2)
    x = voice * 1.5 + sparkle * 0.5
    return reverb(x, 3.0, 1.1, 0.5)


def mimic_reveal():
    d = 0.6
    t = tm(d)
    r = np.random.default_rng(240)
    jitter = 1 + 0.25 * lp(r.standard_normal(ns(d)), 25) * 6
    creak = bp(osc(95 * jitter * (1 + t), d, "saw"), 300, 2200) * env_ad(d, 0.1, 0.4)
    gd = 1.0
    growl = lp(osc(70 + 25 * lp(np.random.default_rng(241).standard_normal(ns(gd)), 30) * 8, gd, "saw"), 700)
    growl = dist(growl * adsr(gd, 0.08, 0.2, 0.7, 0.4) * 1.5, 2.0) + formant(noise(gd, 242), "aw") * adsr(gd, 0.05, 0.2, 0.6, 0.4) * 1.2
    x = mixdown((0.0, creak, 0.8), (0.45, mimic_chomp(), 0.8), (0.5, growl, 1.0))
    return reverb(x, 1.2, 0.4, 0.25)


def mimic_chomp():
    d = 0.5
    click = mixdown((0.0, bp(noise(0.05, 250), 900, 3500) * env_exp(0.05, 0.008)),
                    (0.03, bp(noise(0.05, 251), 700, 2500) * env_exp(0.05, 0.01), 0.7))
    body = osc(240, 0.2) * env_exp(0.2, 0.04)
    crunch = bp(noise(d, 252), 300, 3000) * env_ad(d, 0.03, 0.08) * 0.8
    x = mixdown((0.0, click, 1.2), (0.0, body, 0.6), (0.04, crunch))
    return x


def ray_ambient(v):
    d = 2.8
    t = tm(d)
    x = t / d
    base = 260 + v * 60
    contour = base * (1 + 0.45 * np.sin(np.pi * x) - 0.2 * np.sin(3 * np.pi * x + v))
    contour *= (1 + 0.01 * np.sin(2 * np.pi * 5.5 * t))
    song = osc(contour, d) + osc(contour * 2, d) * 0.35 + osc(contour * 3.01, d) * 0.12
    song = lp(song, 2500) * adsr(d, 0.4, 0.4, 0.8, 0.8)
    return reverb(song * 0.6, 3.5, 1.4, 0.55)


def ray_boost():
    d = 1.3
    t = tm(d)
    whoosh = sweep(noise(d, 260), 300 * (3000 / 300) ** (t / d), "band", width=0.45) * hump(d, 0.35) * 1.8
    chime = mixdown(*[(0.1 + i * 0.06, bell(note(m), 0.8, 1.8, 0.3), 0.18) for i, m in enumerate((81, 88, 93))])
    x = mixdown((0.0, whoosh), (0.0, chime))
    return reverb(x, 1.6, 0.5, 0.35)


def big_roar(d, base, seed, vowel="aw", drop=0.35):
    t = tm(d)
    pitch = base * (1 + 0.15 * np.sin(np.pi * np.clip(t / (d * 0.3), 0, 1))) * (1 - drop * np.clip((t - d * 0.3) / (d * 0.7), 0, 1))
    vib = 1 + 0.02 * np.sin(2 * np.pi * 9 * t)
    saw = sum(osc(pitch * vib * m, d, "saw") * g for m, g in ((1.0, 1), (1.007, 0.9), (1.5, 0.5), (0.5, 0.8), (2.01, 0.35)))
    voice = formant(saw, vowel) * 2.0
    rasp = formant(noise(d, seed), vowel) * 1.6
    sub = osc(pitch * 0.5, d) * 0.6
    env = adsr(d, 0.15, 0.3, 0.8, d * 0.3)
    return dist((voice + rasp + sub) * env, 2.2)


def astraeon_roar():
    x = big_roar(3.6, 78, 300)
    shimmer = hp(noise(3.6, 301), 6000) * hump(3.6, 0.3) * 0.15
    return reverb(x + shimmer, 3.5, 1.3, 0.4, 5000)


def astraeon_ambient(v):
    d = 3.2
    t = tm(d)
    drone = sum(osc(f * (1 + 0.003 * np.sin(2 * np.pi * 0.3 * t)), d) * g for f, g in ((44 + v * 3, 1), (66 + v * 4.5, 0.6), (88.6 + v * 6, 0.3)))
    pulse = 0.65 + 0.35 * np.sin(2 * np.pi * 1.1 * t) ** 2
    growl = formant(noise(d, 310 + v), "o") * 0.6
    shimmer = osc(1760 + 40 * np.sin(2 * np.pi * 0.5 * t), d) * 0.02
    x = (drone * pulse + growl + shimmer) * hump(d, 0.5, 1.0)
    return reverb(x, 3.0, 1.2, 0.45)


def astraeon_hurt(v=0):
    x = big_roar(0.8, 105 + v * 12, 320 + v, "a", 0.25)
    return reverb(x, 1.5, 0.5, 0.3)


def astraeon_death():
    d = 7.0
    roar = big_roar(3.5, 90, 330, "a", 0.6)
    t = tm(2.0)
    suck = hp(noise(2.0, 331), 400) * (t / 2.0) ** 3 * 2.5
    boom = meteor_impact(2)
    chord = sum(bell(note(m), 3.5, 1.2, 1.4) for m in (74, 78, 81, 86, 90)) * 0.2
    pad = sum(osc(note(m) * (1 + det), 3.5) for m in (62, 66, 69, 74) for det in (-0.004, 0.004)) * env_ad(3.5, 0.5, 1.4) * 0.1
    x = mixdown((0.0, roar), (2.6, suck), (4.6, boom), (4.65, chord), (4.65, lp(pad, 3000)))
    return reverb(x, 4.0, 1.6, 0.45)


def astraeon_beam_charge():
    d = 2.6
    t = tm(d)
    f = 180 * (2400 / 180) ** ((t / d) ** 1.4)
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(4 + 26 * (t / d) ** 2) / SR) ** 2
    whine = (osc(f, d) + osc(f * 1.5, d) * 0.4 + osc(f * 0.5, d, "saw") * 0.2) * trem
    rush = sweep(noise(d, 340), 300 + 6000 * (t / d) ** 2, "band", width=0.3) * (t / d) ** 2 * 1.5
    x = (whine * 0.5 + rush) * np.clip(t / 0.3, 0, 1)
    return reverb(x, 1.6, 0.6, 0.3)


def astraeon_beam():
    d = 2.8
    t = tm(d)
    core = sum(osc(f, d, "saw") * g for f, g in ((110, 1), (110.8, 0.9), (220.5, 0.5), (330.9, 0.3)))
    core = lp(core, 2400) * (0.75 + 0.25 * np.sin(2 * np.pi * 31 * t))
    hiss = hp(noise(d, 350), 3000) * 0.6
    sub = osc(55, d) * 0.7
    x = dist((core * 0.8 + hiss + sub) * adsr(d, 0.05, 0.2, 0.85, 0.6), 1.8)
    return reverb(x, 2.0, 0.6, 0.3)


def astraeon_slam():
    d = 2.4
    thud = dist(osc(glide(62, 24, d), d) * env_exp(d, 0.45) * 2.0, 2.5)
    blast = lp(noise(d, 360), 1400) * env_exp(d, 0.25) * 2.4
    debris = crackles(d, 50, 700, 4500, 0.005, 361, density=lambda r: 0.05 + r.random() ** 2 * 0.7)
    return reverb(thud + blast + debris * 0.6, 2.8, 0.9, 0.35, 4000)


def astraeon_awaken():
    d = 6.0
    beat = lambda: osc(glide(70, 40, 0.35), 0.35) * env_exp(0.35, 0.09) * 1.4
    t = tm(d)
    swell = sum(osc(note(m) * (1 + det), d, "saw") for m in (38, 45, 50, 53) for det in (-0.004, 0.004))
    swell = sweep(swell, 150 + 2500 * (t / d) ** 2, "low") * (t / d) ** 2 * 0.35
    roar = big_roar(2.2, 70, 370, "o", 0.2)
    hit = mixdown((0.0, astraeon_slam(), 0.8), *[(0.0, bell(note(m), 3.0, 1.5, 1.0), 0.15) for m in (62, 69, 74, 77)])
    x = mixdown((0.0, beat()), (0.35, beat(), 0.8), (1.5, beat()), (1.85, beat(), 0.8), (0.0, swell), (3.0, roar, 0.9), (4.8, hit))
    return reverb(x, 3.5, 1.3, 0.4)


def hammer_slam():
    d = 1.8
    thud = dist(osc(glide(85, 32, d), d) * env_exp(d, 0.3) * 1.5, 2.0)
    ring = sum(osc(f, d) * env_exp(d, tau) * g for f, tau, g in ((523, 0.8, 0.4), (1391, 0.5, 0.25), (2213, 0.35, 0.18), (3109, 0.2, 0.1)))
    blast = lp(noise(d, 380), 1500) * env_exp(d, 0.15) * 1.8
    debris = crackles(d, 25, 800, 4000, 0.004, 381, density=lambda r: 0.05 + r.random() ** 2 * 0.5)
    return reverb(thud + ring + blast + debris * 0.5, 1.8, 0.6, 0.3)


def blade_dash():
    d = 0.6
    t = tm(d)
    swish = sweep(noise(d, 390), 700 * (4500 / 700) ** (t / d), "band", width=0.4) * hump(d, 0.3) * 2.0
    shimmer = osc(glide(2600, 3800, d), d) * hump(d, 0.3) * 0.08
    return reverb(swish + shimmer, 0.9, 0.3, 0.25)


def rift_blink():
    d = 0.8
    t = tm(d)
    f = np.where(t < 0.25, 300 * (2200 / 300) ** (t / 0.25), 2200 * (180 / 2200) ** ((t - 0.25) / 0.55))
    tone = osc(f, d, "saw") * env_ad(d, 0.01, 0.25)
    comb = tone.copy()
    for k, g in ((37, 0.7), (83, 0.5), (151, 0.35)):
        comb[k:] += tone[:-k] * g * np.sin(2 * np.pi * 3 * t[k:])
    zap = hp(noise(d, 400), 3000) * env_exp(d, 0.05)
    return reverb(bp(comb, 200, 7000) * 0.5 + zap * 0.7, 1.4, 0.5, 0.4)


def singularity_hum():
    d = 2.0
    t = tm(d)
    # frequencies are whole cycles over the clip so it tiles smoothly
    x = lp(osc(55, d, "saw") + osc(55.5, d, "saw"), 320) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * t) ** 2)
    x += osc(110, d) * 0.4 + osc(27.5, d) * 0.5
    x += hp(noise(d, 410), 5000) * 0.03
    return x * np.clip(np.minimum(t, d - t) / 0.15, 0, 1)


def singularity_collapse():
    d = 1.4
    t = tm(d)
    suck = hp(noise(d, 420), 300) * (t / d) ** 3 * 2.0
    suck += osc(glide(90, 900, d), d) * (t / d) ** 2 * 0.5
    x = mixdown((0.0, suck), (d, meteor_impact(3), 0.9))
    return x


def scythe_throw():
    d = 1.1
    t = tm(d)
    spin = bp(noise(d, 430), 900, 3200) * (0.5 + 0.5 * np.sin(2 * np.pi * 13 * t)) ** 2 * env_ad(d, 0.05, 0.5) * 2.0
    shing = hp(noise(0.3, 431), 3500) * env_exp(0.3, 0.05) + osc(3200, 0.3) * env_exp(0.3, 0.08) * 0.3
    return reverb(mixdown((0.0, shing), (0.0, spin)), 1.2, 0.4, 0.3)


def tether_fire():
    d = 0.7
    zip_ = osc(glide(2400, 350, 0.3), 0.3) * env_exp(0.3, 0.1)
    twang = pluck(165, d, 0.994, 440) * env_exp(d, 0.25)
    click = bp(noise(0.03, 441), 1500, 6000) * env_exp(0.03, 0.005)
    return reverb(mixdown((0.0, click), (0.0, zip_, 0.5), (0.02, twang, 0.8)), 1.0, 0.3, 0.2)


def star_bolt(v=0):
    d = 0.55
    f0 = 1900 + v * 250
    pew = fm(glide(f0, 520, d), 2.0, 1.5, d, env_exp(d, 0.1)) * env_exp(d, 0.14)
    sparkle = hp(noise(d, 450 + v), 6000) * env_exp(d, 0.08) * 0.4
    return reverb(pew + sparkle, 1.0, 0.35, 0.3)


def totem_nova():
    d = 3.2
    chord = sum(bell(note(m), d, 2.2, 1.2) for m in (74, 78, 81, 86, 90)) * 0.2
    whoosh = sweep(noise(1.2, 460), 400 * (6000 / 400) ** (tm(1.2) / 1.2), "band", width=0.4) * hump(1.2, 0.2) * 1.5
    boom = osc(glide(110, 45, 0.8), 0.8) * env_exp(0.8, 0.3)
    x = mixdown((0.0, chord), (0.0, whoosh), (0.0, boom, 0.8))
    return reverb(x, 3.5, 1.4, 0.5)


def double_jump():
    d = 0.5
    puff = bp(noise(d, 470), 500, 2200) * hump(d, 0.15) * 1.6
    ping = bell(note(91), d, 1.2, 0.2) * 0.25
    return reverb(puff + ping, 0.8, 0.3, 0.3)


def spike_trap():
    d = 0.7
    shing = hp(noise(d, 480), 3000) * env_exp(d, 0.04) * 1.4
    ring = sum(osc(f, d) * env_exp(d, 0.18) * g for f, g in ((2550, 0.3), (3720, 0.2), (5100, 0.1)))
    clank = bp(noise(0.12, 481), 400, 1500) * env_exp(0.12, 0.02)
    return reverb(mixdown((0.0, shing), (0.0, ring), (0.0, clank, 1.2)), 0.8, 0.25, 0.2)


def flame_jet():
    d = 1.3
    t = tm(d)
    roar = sweep(noise(d, 490), 400 + 1400 * hump(d, 0.2, 1), "low") * adsr(d, 0.04, 0.2, 0.7, 0.5) * 2.2
    whoomph = osc(glide(120, 60, 0.4), 0.4) * env_exp(0.4, 0.12)
    crk = crackles(d, 45, 1500, 6000, 0.002, 491) * 0.5
    return reverb(mixdown((0.0, roar), (0.0, whoomph), (0.05, crk)), 1.0, 0.3, 0.2)


def seal_break():
    d = 3.2
    shatter = crackles(1.5, 160, 2500, 11000, 0.008, 500, density=lambda r: r.random() ** 2 * 0.6)
    pings = mixdown(*[(np.random.default_rng(501 + i).random() * 0.6, osc(3000 + i * 431 % 5000, 0.5) * env_exp(0.5, 0.12), 0.12) for i in range(18)])
    chord = sum(osc(note(m) * (1 + det), d) for m in (50, 57, 62, 69, 74) for det in (-0.003, 0.003)) * env_ad(d, 0.05, 1.2) * 0.12
    boom = osc(glide(80, 35, 1.2), 1.2) * env_exp(1.2, 0.35)
    x = mixdown((0.0, shatter, 1.2), (0.0, pings), (0.0, lp(chord, 4000)), (0.0, boom, 1.2))
    return reverb(x, 3.0, 1.1, 0.4)


def brazier_ignite():
    d = 1.6
    t = tm(d)
    whoomph = sweep(noise(0.6, 510), 180 * (2200 / 180) ** (tm(0.6) / 0.6), "low") * hump(0.6, 0.25) * 2.5
    crk = crackles(d, 40, 1500, 6000, 0.002, 511, density=lambda r: 0.1 + r.random() * 0.9) * 0.5
    chime = mixdown(*[(0.1 + i * 0.07, bell(note(m), 1.2, 1.8, 0.4), 0.2) for i, m in enumerate((79, 83, 86))])
    return reverb(mixdown((0.0, whoomph), (0.0, crk), (0.0, chime)), 1.8, 0.6, 0.35)


def telescope_gaze():
    d = 3.5
    pad = sum(osc(note(m) * (1 + det), d) for m in (60, 64, 67, 71, 76) for det in (-0.003, 0.003))
    pad = lp(pad, 2500) * env_ad(d, 1.0, 1.5) * 0.12
    r = np.random.default_rng(520)
    parts = [(0.0, pad)]
    for _ in range(14):
        parts.append((0.3 + r.random() * 2.6, bell(note(r.choice([84, 88, 91, 95, 96])), 1.0, 1.6, 0.3), 0.12))
    return reverb(mixdown(*parts), 3.5, 1.3, 0.5)


def egg_hatch():
    d = 1.6
    cracks = mixdown(*[(i * 0.16, bp(noise(0.08, 530 + i), 1200, 5000) * env_exp(0.08, 0.012), 1.0) for i in range(3)])
    chirp = mixdown((0.0, osc(glide(1800, 3200, 0.12), 0.12) * env_ad(0.12, 0.01, 0.05)),
                    (0.16, osc(glide(2000, 3600, 0.14), 0.14) * env_ad(0.14, 0.01, 0.06)))
    sparkle = mixdown(*[(i * 0.05, bell(note(m), 1.0, 1.6, 0.35), 0.18) for i, m in enumerate((84, 88, 91, 96))])
    return reverb(mixdown((0.0, cracks), (0.55, sparkle), (0.8, chirp, 0.5)), 1.8, 0.6, 0.35)


def crystal_break(v=0):
    d = 0.9
    r = np.random.default_rng(540 + v)
    parts = [(0.0, crackles(0.3, 60, 3000, 11000, 0.006, 541 + v, density=lambda q: q.random() ** 2), 1.0)]
    for i in range(7):
        f = 2400 + r.random() * 4200
        parts.append((r.random() * 0.12, (osc(f, 0.6) + osc(f * 2.76, 0.6) * 0.4) * env_exp(0.6, 0.12), 0.18))
    return reverb(mixdown(*parts), 1.2, 0.4, 0.3)


# ============================================================================== boss theme

BPM = 150
BEAT = 60.0 / BPM
BAR = BEAT * 4


def render_boss_theme():
    """
    "The Devouring Star" - 32 bars in D minor, looping seamlessly.
    A: pulse (drums, bass, pad)  B: the theme  C: the theme an octave up with choir  D: breakdown and build.
    """
    bars = 32
    length = bars * BAR
    total = ns(length + 4.0)
    L = np.zeros(total)
    R = np.zeros(total)

    def put(x, at, gain=1.0, pan=0.0):
        i = ns(at)
        seg = min(len(x), total - i)
        lg = np.cos((pan + 1) * np.pi / 4) * np.sqrt(2)
        rg = np.sin((pan + 1) * np.pi / 4) * np.sqrt(2)
        L[i:i + seg] += x[:seg] * gain * lg
        R[i:i + seg] += x[:seg] * gain * rg

    # chord progression, 2 bars per chord: Dm Bb Gm A
    prog = [(50, (62, 65, 69)), (46, (62, 65, 70)), (43, (62, 67, 70)), (45, (61, 64, 69))]

    def chord_at(bar):
        return prog[(bar // 2) % 4]

    # --- drums
    def kick():
        d = 0.45
        return dist(osc(glide(140, 42, d), d) * env_exp(d, 0.11) * 1.6, 1.8)

    def snare(seed):
        d = 0.3
        return bp(noise(d, seed), 900, 7000) * env_exp(d, 0.07) * 1.2 + osc(190, d) * env_exp(d, 0.05) * 0.6

    def hat(seed, open_=False):
        d = 0.25 if open_ else 0.06
        return hp(noise(d, seed), 7000) * env_exp(d, 0.08 if open_ else 0.015) * 0.6

    def taiko(f=90):
        d = 0.8
        return osc(glide(f, f * 0.6, d), d) * env_exp(d, 0.22) * 1.2 + lp(noise(d, 7), 500) * env_exp(d, 0.05)

    def crash(seed):
        d = 2.5
        return hp(noise(d, seed), 4000) * env_exp(d, 0.6) * 0.5

    K, S = kick(), snare(11)
    for bar in range(bars):
        sec = bar // 8
        t0 = bar * BAR
        if sec == 3 and bar < 30:
            # breakdown: taiko hits only, building
            for b in (0, 1.5, 2.5):
                put(taiko(80 if b == 0 else 110), t0 + b * BEAT, 0.9, -0.3 if b else 0.0)
            if bar >= 28:
                for s in range(8):
                    put(snare(20 + s), t0 + s * BEAT / 2, 0.3 + 0.1 * s)
            continue
        kicks = (0, 1.5, 2, 3.5) if sec == 0 else (0, 0.75, 1.5, 2, 2.5, 3.5)
        if sec == 2:
            kicks = tuple(i * 0.5 for i in range(8))
        for b in kicks:
            put(K, t0 + b * BEAT, 0.9)
        for b in (1, 3):
            put(S, t0 + b * BEAT, 0.8, 0.1)
        for h in range(8):
            put(hat(30 + h, open_=(h == 7)), t0 + h * BEAT / 2, 0.5 if h % 2 else 0.8, 0.4)
        if bar % 8 == 0:
            put(crash(40 + bar), t0, 0.8, -0.2)
        if bar % 4 == 3:
            put(taiko(100), t0 + 3 * BEAT, 0.6, -0.4)
            put(taiko(130), t0 + 3.5 * BEAT, 0.6, 0.4)

    # --- bass: 8th-note ostinato
    def bass_note(m, d):
        f = note(m)
        x = osc(f, d, "saw") + osc(f * 1.004, d, "saw") + osc(f / 2, d, "square") * 0.5
        return lp(x, 700) * adsr(d, 0.005, 0.08, 0.7, 0.04)

    for bar in range(bars):
        root, _ = chord_at(bar)
        pattern = [0, 0, 12, 0, 0, 12, 0, 7] if bar // 8 != 3 else [0, None, None, None, 0, None, None, None]
        for i, off in enumerate(pattern):
            if off is None:
                continue
            put(bass_note(root - 12 + off, BEAT / 2 * 0.95), bar * BAR + i * BEAT / 2, 0.55)

    # --- pad: detuned saws, one chord every 2 bars
    for bar in range(0, bars, 2):
        _, tones = chord_at(bar)
        d = BAR * 2
        pad = sum(osc(note(m - 12) * (1 + det), d, "saw") for m in tones for det in (-0.005, 0.0, 0.005))
        pad = lp(pad, 1400 if bar // 8 != 2 else 2200) * adsr(d, 0.4, 0.5, 0.8, 0.4)
        put(pad, bar * BAR, 0.08, -0.35)
        put(pad, bar * BAR + 0.012, 0.08, 0.35)

    # --- choir in sections C and D
    for bar in range(16, 32, 2):
        _, tones = chord_at(bar)
        d = BAR * 2
        voice = sum(formant(osc(note(m) * (1 + 0.006 * np.sin(2 * np.pi * 5 * tm(d) + k)), d, "saw"), "a" if bar < 24 else "o")
                    for k, m in enumerate(tones))
        put(voice * adsr(d, 0.5, 0.4, 0.9, 0.5), bar * BAR, 0.35, 0.0)

    # --- arpeggio (sections B and C): plucky 16ths with echo
    for bar in range(8, 24):
        _, tones = chord_at(bar)
        seq = [tones[0], tones[1], tones[2], tones[1] + 12, tones[2], tones[1], tones[0] + 12, tones[2]]
        for i in range(16):
            m = seq[i % 8] + 12
            d = BEAT / 4
            x = (osc(note(m), d * 2, "square") * 0.5 + osc(note(m) * 2, d * 2) * 0.3) * env_exp(d * 2, 0.06)
            x = lp(x, 3500)
            at = bar * BAR + i * d
            pan = 0.5 if i % 2 else -0.5
            put(x, at, 0.12, pan)
            put(x, at + BEAT * 0.75, 0.05, -pan)

    # --- lead melody (sections B and C)
    theme = [
        [(74, 2), (69, 1), (74, 1)], [(77, 3), (76, 1)], [(74, 2), (72, 1), (70, 1)], [(77, 4)],
        [(79, 2), (77, 1), (76, 1)], [(74, 2), (70, 2)], [(69, 2), (73, 2)], [(76, 2), (69, 2)],
    ]
    theme_high = [
        [(81, 2), (77, 1), (74, 1)], [(81, 1), (82, 1), (81, 1), (77, 1)], [(82, 2), (77, 1), (74, 1)], [(82, 1), (84, 1), (82, 1), (81, 1)],
        [(79, 2), (82, 2)], [(86, 2), (82, 2)], [(81, 2), (79, 1), (77, 1)], [(76, 2), (73, 2)],
    ]

    def lead_note(m, d, bright=1.0):
        t = tm(d)
        vib = 1 + 0.008 * np.sin(2 * np.pi * 5.5 * t) * np.clip((t - 0.15) / 0.2, 0, 1)
        f = note(m) * vib
        x = osc(f, d, "saw") + osc(f * 1.006, d, "saw") * 0.8 + osc(f * 0.5, d, "square") * 0.3
        cutoff = 900 + 3200 * bright * env_ad(d, 0.03, 0.35) + 600
        x = sweep(x, cutoff, "low", block=128)
        return x * adsr(d, 0.02, 0.15, 0.75, 0.08)

    for sec_start, mel, gain in ((8, theme, 0.22), (16, theme_high, 0.2)):
        for i, bar_notes in enumerate(mel):
            beat = 0
            for m, dur in bar_notes:
                at = (sec_start + i) * BAR + beat * BEAT
                x = lead_note(m, dur * BEAT * 0.98)
                put(x, at, gain, 0.05)
                put(x, at + BEAT * 0.5, gain * 0.25, -0.5)
                beat += dur
    # section C doubles the theme an octave below for weight
    for i, bar_notes in enumerate(theme_high):
        beat = 0
        for m, dur in bar_notes:
            put(lead_note(m - 12, dur * BEAT * 0.98, 0.6), (16 + i) * BAR + beat * BEAT, 0.12, -0.1)
            beat += dur

    # --- breakdown bells: the motif, slow and eerie
    for i, (m, beat) in enumerate(((74, 0), (69, 2), (77, 4), (76, 6), (74, 8), (72, 10), (70, 12), (69, 14))):
        at = 24 * BAR + beat * BEAT * 2
        put(bell(note(m + 12), 2.5, 2.0, 1.0), at, 0.12, -0.3 + 0.1 * i)

    # --- riser into the loop point
    rt = 4 * BAR
    riser = sweep(noise(rt, 99), 200 * (6000 / 200) ** (tm(rt) / rt), "band", width=0.4) * (tm(rt) / rt) ** 2
    put(riser, 28 * BAR, 0.5)

    # glue: gentle reverb, then fold the tail over the loop start for a seamless repeat
    Lr = reverb(L, 2.2, 0.8, 0.22, 6000, seed=1)
    Rr = reverb(R, 2.2, 0.8, 0.22, 6000, seed=2)
    n = ns(length)
    out = np.zeros((2, n))
    for ch, sig in enumerate((Lr, Rr)):
        body = sig[:n].copy()
        tail = sig[n:]
        body[:len(tail)] += tail[:n]
        out[ch] = body
    out = np.tanh(out / np.max(np.abs(out)) * 1.3) / np.tanh(1.3)
    return out


# ============================================================================== sounds.json

EVENTS = {}


def event(name, files, subtitle=True, attenuation=None, stream=False):
    entries = []
    for f in files:
        e = {"name": f"starfallen:{f}"}
        if attenuation:
            e["attenuation_distance"] = attenuation
        if stream:
            e["stream"] = True
        entries.append(e if (attenuation or stream) else f"starfallen:{f}")
    body = {"sounds": entries}
    if subtitle:
        body["subtitle"] = f"subtitles.starfallen.{name}"
    EVENTS[name] = body


def gen(name, fn, variants=1, peak=0.9, attenuation=None, subtitle=True):
    files = []
    for v in range(variants):
        fname = name if variants == 1 else f"{name}{v + 1}"
        x = fn(v) if _takes_arg(fn) else fn()
        write(fname, x, peak)
        files.append(fname)
    event(name, files, subtitle, attenuation)


def _takes_arg(fn):
    import inspect
    return len(inspect.signature(fn).parameters) > 0


def main():
    gen("meteor_fall", meteor_fall, 2, 0.8, 128)
    gen("meteor_impact", meteor_impact, 2, 0.95, 128)
    gen("starfall_begin", starfall_begin, 1, 0.8)
    gen("star_chime", star_chime, 2, 0.7)
    gen("wisp_ambient", wisp_ambient, 3, 0.5)
    gen("wisp_hurt", wisp_hurt, 1, 0.6)
    gen("wisp_death", wisp_death, 1, 0.6)
    gen("golem_ambient", golem_ambient, 2, 0.7)
    gen("golem_hurt", golem_hurt, 2, 0.85)
    gen("golem_death", golem_death, 1, 0.9)
    gen("golem_slam", golem_slam, 1, 0.95, 32)
    gen("stalker_ambient", stalker_ambient, 2, 0.6)
    gen("stalker_scream", stalker_scream, 1, 0.9, 32)
    gen("stalker_hurt", stalker_hurt, 1, 0.75)
    gen("stalker_death", stalker_death, 1, 0.8)
    gen("jelly_ambient", jelly_ambient, 2, 0.55)
    gen("jelly_zap", jelly_zap, 1, 0.75)
    gen("starseer_ambient", starseer_ambient, 2, 0.6)
    gen("starseer_cast", starseer_cast, 1, 0.8)
    gen("starseer_hurt", starseer_hurt, 1, 0.75)
    gen("starseer_death", starseer_death, 1, 0.85)
    gen("mimic_reveal", mimic_reveal, 1, 0.95)
    gen("mimic_chomp", mimic_chomp, 1, 0.85)
    gen("ray_ambient", ray_ambient, 2, 0.55, 32)
    gen("ray_boost", ray_boost, 1, 0.8)
    gen("astraeon_roar", astraeon_roar, 1, 0.95, 96)
    gen("astraeon_ambient", astraeon_ambient, 2, 0.7, 48)
    gen("astraeon_hurt", astraeon_hurt, 2, 0.85, 48)
    gen("astraeon_death", astraeon_death, 1, 0.95, 128)
    gen("astraeon_beam_charge", astraeon_beam_charge, 1, 0.85, 64)
    gen("astraeon_beam", astraeon_beam, 1, 0.9, 64)
    gen("astraeon_slam", astraeon_slam, 1, 0.95, 64)
    gen("astraeon_awaken", astraeon_awaken, 1, 0.95, 128)
    gen("hammer_slam", hammer_slam, 1, 0.95, 32)
    gen("blade_dash", blade_dash, 1, 0.8)
    gen("rift_blink", rift_blink, 1, 0.8)
    gen("singularity_hum", singularity_hum, 1, 0.7, 24)
    gen("singularity_collapse", singularity_collapse, 1, 0.95, 48)
    gen("scythe_throw", scythe_throw, 1, 0.8)
    gen("tether_fire", tether_fire, 1, 0.8)
    gen("star_bolt", star_bolt, 2, 0.7)
    gen("totem_nova", totem_nova, 1, 0.95, 48)
    gen("double_jump", double_jump, 1, 0.6)
    gen("spike_trap", spike_trap, 1, 0.8)
    gen("flame_jet", flame_jet, 1, 0.8)
    gen("seal_break", seal_break, 1, 0.95, 64)
    gen("brazier_ignite", brazier_ignite, 1, 0.8)
    gen("telescope_gaze", telescope_gaze, 1, 0.7)
    gen("egg_hatch", egg_hatch, 1, 0.8)
    gen("crystal_break", crystal_break, 2, 0.75)

    theme = render_boss_theme()
    write("boss_music", theme, 0.85, stereo=True)
    event("boss_music", ["boss_music"], subtitle=False, stream=True)

    with open(os.path.join(os.path.dirname(OUT), "sounds.json"), "w") as fh:
        json.dump(EVENTS, fh, indent=2)
    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"{len(WRITTEN)} sound files, {len(EVENTS)} events, {total / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
