"""Render footsteps and loaded plants from the archived source recordings."""

import argparse
from functools import lru_cache
import subprocess
from zipfile import ZipFile

import numpy as np

from mix_variations import RATE, ROOT, band, locate_ffmpeg, peak, wav


def fades(signal, attack, release):
    a = round(attack * RATE)
    r = round(release * RATE)
    signal[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    signal[-r:] *= np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    return signal


def render(ffmpeg):
    with ZipFile(ROOT / "art/audio/source-recordings.zip") as recordings:
        @lru_cache(maxsize=None)
        def decode(name):
            pcm = subprocess.check_output(
                [ffmpeg, "-v", "error", "-i", "pipe:0", "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                input=recordings.read(name + ".mp3"),
            )
            return np.frombuffer(pcm, "<f4")

        def clip(source, moment, duration, low, high, lead=.075):
            audio = band(decode(source), low, high)
            start = round((moment - lead) * RATE)
            block = RATE // 200
            onset_window = audio[start:start + 22 * block]
            energy = np.sqrt(np.mean(onset_window.reshape(-1, block) ** 2, axis=1))
            onset = np.flatnonzero(energy > .21 * energy.max())[0]
            begin = start + max(0, onset * block - round(.009 * RATE))
            return peak(audio[begin:begin + round(duration * RATE)], 1)

        # Six distinct takes per surface. The wood banks represent different floor and shoe tones.
        steps = {
            "footstep": ("steps", (2.64, 4.19, 5.88, 10.80, 16.62, 26.50), 100, 2800, .235),
            "footstep_wood": ("steps", (7.88, 9.69, 18.48, 20.11, 21.91, 24.00), 82, 3500, .26),
            "footstep_sand": ("sand", (7.07, 8.61, 12.72, 13.84, 14.44, 18.50), 140, 3750, .25),
            "footstep_concrete": ("stone", (2.99, 4.19, 5.39, 7.19, 8.36, 10.16), 140, 3950, .21),
        }
        variants = {}
        for name, (source, moments, low, high, duration) in steps.items():
            for i, moment in enumerate(moments):
                length = duration + (.0, .019, -.012)[i % 3]
                raw = clip(source, moment, length, low, high)
                shaped = np.tanh(1.25 * raw) + .17 * band(raw, low, min(650, high))
                soft = fades(shaped, .011 + .002 * (i % 3), .09 + .018 * (i % 3))
                variants[name, i] = soft
                suffix = "" if i == 0 else f"-{i + 1}"
                wav(name + suffix, soft, (.43, .39, .44, .41, .45, .40)[i])

        plants = {
            "plant": ("footstep", "squeaks", (2.26, 3.40, .31), 390, 4200, .18, .34),
            "plant_wood": ("footstep_wood", "squeaks", (.31, 2.72, 3.13), 380, 4300, .18, .38),
            "plant_sand": ("footstep_sand", "sand", (4.63, 5.20, 15.71), 230, 3850, .24, .48),
            "plant_roof": ("footstep_concrete", "stone", (5.39, 8.97, 13.16), 390, 4350, .18, .31),
        }
        for name, (footstep, source, moments, low, high, duration, amount) in plants.items():
            for i, moment in enumerate(moments):
                foot = peak(variants[footstep, (i * 2 + 1)], 1)
                drag = fades(clip(source, moment, duration, low, high), .018, .095)
                offset = round((.018 + .007 * i) * RATE)
                contact = np.zeros(max(len(foot), offset + len(drag)))
                contact[:len(foot)] += foot
                contact[offset:offset + len(drag)] += amount * drag
                suffix = "" if i == 0 else f"-{i + 1}"
                wav(name + suffix, contact, .58)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg")
    render(locate_ffmpeg(parser.parse_args().ffmpeg))
