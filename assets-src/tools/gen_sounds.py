"""Synthesizes the AeroFlux machine sounds (no samples, no paid service): seamless 4 s mono loops for
each machine plus shared start, stop and done one-shots. Writes WAVs to a scratch folder and encodes
Ogg Vorbis with ffmpeg into assets-src/aeroflux_sounds/assets/aeroflux/sounds/machine, with sounds.json.

Loops are built from circular (FFT-filtered) noise and sine frequencies that fit a whole number of
cycles into the loop, so the end joins the start with no click. Run: python gen_sounds.py
"""
import json
import os
import subprocess
import tempfile

import numpy as np
from scipy.io import wavfile

SR = 44100
T = 4.0
N = int(SR * T)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "aeroflux_sounds", "assets", "aeroflux")
SND = os.path.join(OUT, "sounds", "machine")
os.makedirs(SND, exist_ok=True)
t = np.arange(N) / SR


def per(f):
    """Snap a frequency to a whole number of cycles per loop."""
    return max(1, round(f * T)) / T


def noise(lo, hi, seed, n=N):
    """Circular band-limited noise, unit RMS."""
    rng = np.random.default_rng(seed)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= ((f >= lo) & (f <= hi))
    x = np.fft.irfft(spec, n)
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-9)


def sine(f, phase=0.0, n=N):
    return np.sin(2 * np.pi * per(f) * np.arange(n) / SR + phase)


def events(rate, seed, make, n=N):
    """Scatter short sounds at random times (wrapping around the loop edge)."""
    rng = np.random.default_rng(seed)
    out = np.zeros(n)
    for _ in range(int(rate * n / SR)):
        s = make(rng)
        at = int(rng.integers(0, n))
        idx = (at + np.arange(len(s))) % n
        out[idx] += s
    return out


def burst(rng, ms, lo, hi, decay=6.0, amp=1.0):
    m = int(SR * ms / 1000)
    x = noise(lo, hi, int(rng.integers(1, 1 << 30)), 4096)[:m] if m <= 4096 else noise(lo, hi, 7, m)
    return amp * x * np.exp(-decay * np.arange(len(x)) / len(x))


def ping(freq, ms, decay=5.0, drop=0.0, amp=1.0):
    m = int(SR * ms / 1000)
    tt = np.arange(m) / SR
    f = freq * (1 - drop * tt / (m / SR))
    return amp * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-decay * tt / (m / SR))


def lowpass(x, fc):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 / (1 + (f / fc) ** 4)
    return np.fft.irfft(spec, len(x))


def norm(x, peak=0.5):
    x = x - np.mean(x)
    return x * (peak / (np.max(np.abs(x)) + 1e-9))


# ------------------------------------------------------------------ the loops
def furnace():
    rumble = noise(35, 220, 1) * 0.55
    hum = sine(55) * 0.25 + sine(110) * 0.1
    hiss = noise(2500, 7000, 2) * 0.06 * (0.6 + 0.4 * sine(0.5))
    crack = events(15, 3, lambda r: burst(r, r.integers(8, 30), 600, 6000, 7, r.uniform(0.3, 1.2)))
    pop = events(3, 4, lambda r: burst(r, 40, 200, 1500, 5, 1.4))
    return norm(rumble + hum + hiss + crack * 0.5 + pop * 0.5)


def grinder():
    motor = sine(96) * 0.35 + sine(192) * 0.2 + sine(288) * 0.1
    motor *= 1 + 0.05 * sine(7)
    pulses = (np.sin(2 * np.pi * per(14) * t) > -0.2).astype(float)
    grit = noise(700, 4200, 5) * (0.35 + 0.65 * pulses) * 0.5
    grit += noise(3000, 9000, 6) * 0.08
    clink = events(1.5, 7, lambda r: ping(r.uniform(2200, 3600), 60, 7, 0, 0.5))
    return norm(motor + grit + clink)


def smelter():
    roar = noise(60, 420, 8) * 0.55 * (0.8 + 0.2 * sine(0.25))
    sizzle = noise(3500, 8500, 9) * 0.07
    blup = events(8, 10, lambda r: ping(r.uniform(180, 420), 70, 6, -1.4, 0.8))
    glow = sine(73) * 0.2
    return norm(roar + sizzle + glow + blup * 0.55)


def sawmill():
    whirr = sine(430) * 0.18 + sine(860) * 0.1 + sine(1290) * 0.05
    cut_env = np.clip(np.sin(2 * np.pi * per(1) * t - 0.5), 0, 1) ** 2        # one pass per second
    cut = noise(900, 5500, 11) * cut_env * 0.7
    dip = 1 - 0.25 * cut_env
    whirr = whirr * dip
    chunk = events(1, 12, lambda r: burst(r, 90, 80, 600, 4, 0.9))
    motor = noise(60, 300, 13) * 0.18
    return norm(whirr + cut + motor + chunk * 0.35)


def infuser():
    base = sum(sine(f, p) * a for f, p, a in ((432, 0, .3), (433.5, 1.3, .25), (648, 0.4, .15), (864, 2.1, .1)))
    base *= 0.75 + 0.25 * sine(0.5)
    air = noise(300, 1400, 14) * 0.12 * (0.6 + 0.4 * sine(0.25, 1.0))
    sparkle = events(3.5, 15, lambda r: ping(r.uniform(1800, 4200), 380, 6, 0, 0.45))
    return norm(base + air + sparkle * 0.6)


def enchanter():
    chord = sum(sine(f, p) * a * (1 + 0.06 * sine(v)) for f, p, a, v in
                ((220, 0, .22, 5), (277.2, .7, .18, 5.5), (329.6, 1.4, .18, 4.5), (493.9, 2.0, .1, 6)))
    swirl = noise(500, 3500, 16) * 0.12 * (0.5 + 0.5 * np.sin(2 * np.pi * per(0.25) * t))
    notes = [523.3, 587.3, 659.3, 784.0, 880.0, 1046.5]
    chimes = events(5, 17, lambda r: ping(r.choice(notes) * 2, 450, 5.5, 0, 0.5))
    return norm(chord + swirl + chimes * 0.55)


def disenchanter():
    drone = sine(55) * 0.35 + sine(55.75) * 0.3 + sine(82.4) * 0.15 + sine(116.5) * 0.12
    drone *= 0.8 + 0.2 * sine(0.5)
    rev = events(2.5, 18, lambda r: ping(r.uniform(700, 1600), 520, 5, 0.5, 0.5)[::-1])
    low = noise(100, 700, 19) * 0.14 * (0.6 + 0.4 * sine(0.25))
    tick = events(2, 20, lambda r: burst(r, 25, 400, 3000, 9, 0.6))
    return norm(drone + rev * 0.55 + low + tick * 0.25)


def grower():
    hum = (sine(120) * 0.18 + sine(240) * 0.06) * (0.8 + 0.2 * sine(0.5))
    drips = events(1.6, 21, lambda r: ping(r.uniform(900, 1600), 140, 7, 0.5, 0.7))
    leaves = noise(3500, 8500, 22) * 0.05 * (0.5 + 0.5 * sine(0.75, 0.4))
    wind = noise(150, 600, 23) * 0.1
    return norm(hum + drips * 0.5 + leaves + wind, 0.4)


# ------------------------------------------------------------------ one-shots
def start():
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    f = 70 + 330 * (tt / 0.9) ** 1.6
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5
    air = noise(300, 4000, 30, n) * 0.18 * (tt / 0.9)
    env = np.minimum(1, tt / 0.04) * np.exp(-1.2 * np.maximum(0, tt - 0.6))
    return norm((tone + air) * env, 0.5)


def stop():
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    f = 380 - 300 * (tt / 0.9) ** 0.7
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5
    air = noise(200, 2500, 31, n) * 0.14 * (1 - tt / 0.9)
    env = np.minimum(1, tt / 0.02) * np.exp(-3.0 * tt)
    return norm((tone + air) * env, 0.5)


def done():
    n = int(SR * 1.1)
    out = np.zeros(n)
    for at, f in ((0.0, 880), (0.14, 1318.5)):
        s = ping(f, 900, 4.5, 0, 0.5)
        k = int(SR * at)
        out[k:k + len(s)] += s[:n - k]
    out += np.roll(out, int(SR * 0.09)) * 0.3
    return norm(out, 0.45)


LOOPS = {"furnace": furnace, "grinder": grinder, "smelter": smelter, "sawmill": sawmill,
         "infuser": infuser, "enchanter": enchanter, "disenchanter": disenchanter, "grower": grower}
SHOTS = {"start": start, "stop": stop, "done": done}
NAMES = {"furnace": "Furnace", "grinder": "Grinder", "smelter": "Alloy Smelter", "sawmill": "Sawmill",
         "infuser": "Infuser", "enchanter": "Enchanter", "disenchanter": "Disenchanter",
         "grower": "Plant Grower"}


def encode(name, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + ".wav")
        wavfile.write(wav, SR, pcm)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4",
                        os.path.join(SND, name + ".ogg")], check=True)


def main():
    report = {}
    for k, fn in LOOPS.items():
        x = fn()
        report[k] = {"seam_jump": float(abs(x[0] - x[-1])), "rms": float(np.sqrt(np.mean(x ** 2))),
                     "peak": float(np.max(np.abs(x)))}
        encode(f"{k}_loop", x)
    for k, fn in SHOTS.items():
        encode(k, fn())
    sounds = {}
    for k in LOOPS:
        sounds[f"machine.{k}.loop"] = {"subtitle": f"subtitles.aeroflux.machine.{k}",
                                       "sounds": [{"name": f"aeroflux:machine/{k}_loop", "attenuation_distance": 14}]}
    for k in SHOTS:
        sounds[f"machine.{k}"] = {"subtitle": f"subtitles.aeroflux.machine.{k}",
                                  "sounds": [{"name": f"aeroflux:machine/{k}", "attenuation_distance": 16}]}
    with open(os.path.join(OUT, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=2)
    lang = {f"subtitles.aeroflux.machine.{k}": f"{NAMES[k]} running" for k in LOOPS}
    lang.update({"subtitles.aeroflux.machine.start": "Machine powers up",
                 "subtitles.aeroflux.machine.stop": "Machine powers down",
                 "subtitles.aeroflux.machine.done": "Machine finishes"})
    with open(os.path.join(HERE, "..", "aeroflux_sounds", "lang_en_us_sounds.json"), "w", encoding="utf-8") as f:
        json.dump(lang, f, indent=2)
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
