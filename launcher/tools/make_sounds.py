"""Writes the retro launcher's 8-bit sound effects into res/raw/ as tiny square-wave WAVs.

Run from the launcher folder: python tools/make_sounds.py
"""
import os
import struct
import wave

RATE = 22050
VOLUME = 0.28
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res", "raw")

C5, D5, E5, F5, G5, A5, B5 = 523.25, 587.33, 659.25, 698.46, 783.99, 880.0, 987.77
C6, E6, G6 = 1046.5, 1318.5, 1568.0


def square(freq, ms, duty=0.5):
    n = int(RATE * ms / 1000)
    period = RATE / freq if freq else 1
    out = []
    for i in range(n):
        if not freq:
            out.append(0.0)
            continue
        v = 1.0 if (i % period) / period < duty else -1.0
        fade = min(1.0, i / 60, (n - i) / 120)  # ramps avoid clicks at note edges
        out.append(v * VOLUME * fade)
    return out


def tones(*notes):
    samples = []
    for freq, ms in notes:
        samples += square(freq, ms)
    return samples


def write(name, samples):
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(1)
        w.setframerate(RATE)
        w.writeframes(bytes(int(128 + s * 127) for s in samples))
    print(f"{path}  {os.path.getsize(path)} bytes")


SOUNDS = {
    "sfx_move": tones((A5, 28)),
    "sfx_select": tones((E5, 55), (B5, 90)),
    "sfx_back": tones((E5, 50), (C5, 70)),
    "sfx_error": tones((110, 70), (0, 30), (110, 110)),
    "sfx_parent": tones((C5, 60), (G5, 60), (C6, 110)),
    "sfx_start": tones((C5, 90), (E5, 90), (G5, 90), (C6, 140), (0, 50), (G5, 70), (C6, 260)),
    "sfx_warn": tones((B5, 90), (0, 70), (B5, 90), (0, 70), (B5, 160)),
    "sfx_sleep": tones((G6, 80), (E6, 80), (C6, 80), (G5, 90), (E5, 100), (C5, 260)),
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, samples in SOUNDS.items():
        write(name, samples)
