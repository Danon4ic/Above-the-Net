"""Render the new effect takes and venue beds from the game's credited recordings."""

import argparse
from functools import lru_cache
from pathlib import Path
import shutil
import subprocess
import wave

import numpy as np
from scipy.signal import butter, sosfilt


ROOT = Path(__file__).resolve().parents[2]
AUDIO = ROOT / "core/src/main/resources/audio"
RATE = 32000


def locate_ffmpeg(option):
    if option:
        return option
    installed = shutil.which("ffmpeg")
    if installed:
        return installed
    bundled = next((ROOT / "build/audio-tools/imageio_ffmpeg/binaries").glob("ffmpeg*"), None)
    if bundled:
        return str(bundled)
    raise SystemExit("Install FFmpeg or pass --ffmpeg PATH")


def band(signal, low, high):
    return sosfilt(butter(2, [low, high], btype="bandpass", fs=RATE, output="sos"), signal, axis=0)


def peak(signal, level):
    return signal * (level / max(float(np.max(np.abs(signal))), 1e-9))


def tail(signal, seconds):
    count = min(len(signal), round(seconds * RATE))
    return np.array(signal[:count], copy=True)


def envelope(signal, attack, release):
    a = min(len(signal) // 3, round(attack * RATE))
    r = min(len(signal) // 2, round(release * RATE))
    if a:
        signal[:a] *= np.linspace(0, 1, a).reshape((-1,) + (1,) * (signal.ndim - 1))
    if r:
        signal[-r:] *= np.linspace(1, 0, r).reshape((-1,) + (1,) * (signal.ndim - 1))
    return signal


def wav(name, signal, level):
    data = np.clip(peak(signal, level), -1, 1)
    with wave.open(str(AUDIO / (name + ".wav")), "wb") as file:
        file.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        file.writeframes((data * 32767).astype("<i2").tobytes())


def pull(signal, speed):
    return np.interp(np.arange(0, len(signal) - 1, speed), np.arange(len(signal)), signal)


def smoothed_repeat(signal, seconds):
    hop = len(signal) * 3 // 4
    texture = np.zeros(round(seconds * RATE) + len(signal))
    window = np.hanning(len(signal))
    for start in range(0, round(seconds * RATE), hop):
        texture[start:start + len(signal)] += signal * window
    return tail(texture, seconds)


def close_loop(signal, seconds):
    fade = round(seconds * RATE)
    weight = np.linspace(0, 1, fade)[:, None]
    join = signal[-fade:] * (1 - weight) + signal[:fade] * weight
    return np.concatenate((join, signal[fade:-fade]))


def render(ffmpeg):
    @lru_cache(maxsize=None)
    def decode(name, channels=1):
        data = subprocess.check_output([ffmpeg, "-v", "error", "-i", str(AUDIO / name),
                                        "-ac", str(channels), "-ar", str(RATE), "-f", "f32le", "-"])
        return np.frombuffer(data, "<f4").reshape(-1, channels)

    def take(name, low, high, speed=1):
        return peak(band(pull(decode(name + ".wav")[:, 0], speed), low, high), 1)

    def effect(name, seconds, level, layers, release=0.045):
        result = np.zeros(round(seconds * RATE))
        for asset, low, high, speed, volume, offset, duration in layers:
            piece = tail(take(asset, low, high, speed), duration)
            envelope(piece, 0.0015, 0.022)
            start = round(offset * RATE)
            size = min(len(piece), len(result) - start)
            result[start:start + size] += piece[:size] * volume
        wav(name, envelope(result, 0.0015, release), level)

    # Each family varies the transient and the body separately. All impacts are mono for panning.
    effects = {
        "forearm_pass-3": (.20, .61, [("forearm_pass-2",100,2700,1.04,.84,0,.19), ("overhead_set",1000,5000,.95,.20,.004,.08)]),
        "forearm_hard": (.23, .76, [("forearm_pass",95,4200,.94,.92,0,.22), ("spike_hard-2",1300,6500,1.07,.32,.003,.09)]),
        "forearm_hard-2": (.22, .74, [("forearm_pass-2",90,3900,.92,.9,0,.21), ("block",650,7500,1.1,.33,.006,.085)]),
        "overhead_set-2": (.17, .51, [("overhead_set",150,2600,.93,.82,0,.16), ("serve_toss",750,4500,1.13,.24,.010,.095)]),
        "spike_soft-2": (.17, .55, [("spike_soft",250,5800,1.13,.91,0,.155), ("serve_toss",650,2900,1.0,.24,.011,.12)]),
        "spike_roll": (.26, .60, [("spike_soft",170,2800,.94,.81,0,.21), ("serve_toss",540,4700,.77,.47,.025,.22)]),
        "spike_roll-2": (.25, .59, [("overhead_set",160,3700,.84,.74,0,.17), ("spike_soft",550,4500,.88,.51,.025,.21)]),
        "block-2": (.20, .73, [("block",190,6000,1.06,.89,0,.19), ("spike_hard-2",115,750,1.0,.32,.008,.09)]),
        "dig-2": (.24, .63, [("dig",81,2700,1.02,.78,0,.23), ("serve_toss",790,4800,.86,.29,.010,.15)]),
        "dig_one_arm": (.20, .64, [("forearm_pass",170,6100,1.1,.83,0,.19), ("overhead_set",120,2700,1.02,.20,.005,.11)]),
        "dig_one_arm-2": (.19, .62, [("spike_soft",220,5600,1.01,.64,0,.17), ("forearm_pass-2",130,2500,1.03,.49,.004,.16)]),
        "ball_tape": (.24, .63, [("ball_net",140,1900,1.2,.54,0,.23), ("block",160,5700,1.06,.68,0,.106)]),
        "ball_tape-2": (.22, .60, [("ball_net",160,3100,1.32,.65,0,.22), ("ball_floor",130,4100,1.18,.42,.004,.12)]),
        "landing-2": (.29, .63, [("landing",85,4300,.92,.82,0,.25), ("footstep-3",115,3600,1.08,.31,.026,.15)]),
        "ball_roof": (.17, .67, [("ball_floor",170,6100,1.13,.85,0,.16), ("footstep_concrete",420,4900,1.13,.21,.003,.07)]),
        "ball_roof-2": (.19, .68, [("ball_floor-2",135,6400,1.11,.85,0,.18), ("footstep_concrete-2",380,5400,1.02,.19,.005,.09)]),
        "landing_roof": (.25, .67, [("footstep_concrete",120,4600,.92,.70,0,.23), ("landing",75,1100,.98,.43,.012,.20)]),
        "landing_roof-2": (.27, .66, [("footstep_concrete-3",95,5100,.86,.67,0,.25), ("landing-2",80,980,1.03,.45,.017,.23)]),
    }
    for name, (length, level, layers) in effects.items():
        effect(name, length, level, layers)

    # Each row gives the existing take count, the body and the transient of the new takes.
    # Higher slots open the attack on stronger contacts. The tail length also varies by technique.
    banks = {
        "serve_underhand": (1, 3, "forearm_pass", 145, 3500, .19, .66),
        "serve_float": (1, 3, "block", 175, 5500, .15, .71),
        "serve_power": (1, 3, "spike_hard", 95, 6300, .24, .77),
        "serve_jump_float": (1, 3, "forearm_pass-2", 200, 5600, .16, .72),
        "serve_jump_topspin": (1, 3, "spike_hard-2", 95, 7000, .24, .78),
        "forearm_pass": (3, 4, "block", 120, 4200, .21, .66),
        "forearm_hard": (2, 3, "serve_power", 90, 5200, .24, .76),
        "overhead_set": (2, 4, "serve_toss", 185, 4200, .17, .53),
        "spike_hard": (2, 4, "serve_jump_topspin", 85, 7300, .26, .85),
        "spike_soft": (2, 3, "overhead_set", 280, 6200, .14, .58),
        "spike_roll": (2, 3, "spike_soft", 135, 4200, .24, .63),
        "block": (2, 3, "ball_floor", 190, 6600, .17, .74),
        "dig": (2, 3, "forearm_pass-2", 95, 3600, .24, .65),
        "dig_one_arm": (2, 3, "spike_soft", 180, 5600, .17, .66),
    }
    added_takes = 0
    for name, (existing, count, transient, low, high, duration, level) in banks.items():
        for slot in range(existing + 1, count + 1):
            sharp = slot / count
            length = duration * (1.04 if slot % 2 == 0 else .84)
            layers = [
                (name, low, high * (.72 + .28 * sharp), .90 + .15 * sharp, .82, 0, length),
                (transient, low * 2, high, 1.02 + .18 * sharp, .22 + .14 * sharp, .003 * (1 + slot % 2), .074),
            ]
            effect(f"{name}-{slot}", length, level, layers, .056 if slot % 2 == 0 else .029)
            added_takes += 1

    for suffix, speed in (("", 0.91), ("-2", 1.08)):
        cloth = smoothed_repeat(take("serve_toss", 210, 3900, speed), .46)
        wood = smoothed_repeat(take("footstep_wood-3", 360, 4400, speed), .46)
        sand = smoothed_repeat(take("footstep_sand-2", 180, 3800, speed), .52)
        second_grain = smoothed_repeat(take("footstep_sand-3", 320, 5000, 1 / speed), .52)
        wav("dive_slide" + suffix, envelope(cloth + .30 * wood, .025, .23), .38)
        wav("dive_slide_sand" + suffix, envelope(sand + .37 * second_grain, .020, .29), .45)
        roof = smoothed_repeat(take("footstep_concrete-2", 280, 5500, speed), .25)
        wav("brake_sand" + suffix, envelope((sand + .45 * second_grain)[:int(.24 * RATE)], .008, .15), .42)
        wav("brake_roof" + suffix, envelope(roof, .006, .14), .39)
        roof_grain = smoothed_repeat(take("footstep_concrete-3", 350, 5700, speed), .46)
        wav("dive_slide_roof" + suffix, envelope(.64 * roof_grain + .36 * cloth, .018, .22), .42)

    # Original, dry cues. A single soft tap signifies a teammate. Pitched intervals signify UI.
    cues = {
        "team_receive_cue": ((260, 0, .102),),
        "team_receive_cue-2": ((284, 0, .093),),
        "action_change": ((523, 0, .086), (659, .045, .101)),
        "action_change-2": ((494, 0, .079), (659, .045, .104)),
        "mode_change": ((392, 0, .132), (587, .071, .176)),
        "mode_change-2": ((392, 0, .128), (622, .072, .162)),
        "replay_start": ((523, 0, .102), (392, .065, .131), (587, .13, .18)),
    }
    for name, notes in cues.items():
        signal = np.zeros(round(max(start + length for _, start, length in notes) * RATE))
        for frequency, start, length in notes:
            offset = round(start * RATE)
            t = np.arange(min(round(length * RATE), len(signal) - offset)) / RATE
            phase = 2 * np.pi * frequency * t
            note = np.sin(phase) + .27 * np.sin(2.03 * phase) + .08 * np.sin(3.04 * phase)
            note *= np.exp(-3.5 * t / length)
            signal[offset:offset + len(t)] += envelope(note, .004, .025)
        wav(name, signal, .49 if name.startswith("team") else .58)

    source = decode("crowd_ambient.ogg", 2)
    ambience = {
        # name: filter, amount of room return, air texture, playback offset (seconds)
        "crowd_beach": (170, 4300, .035, .095, 3.1),
        "crowd_gym": (220, 4800, .20, .017, 0.7),
        "crowd_palace": (105, 3000, .32, .026, 2.2),
        "crowd_rooftop": (240, 3300, .065, .15, 4.6),
    }
    random = np.random.default_rng(813)
    low_wash = peak(band(random.normal(size=source.shape), 90, 900), 1)
    for name, (low, high, room, air, offset) in ambience.items():
        bed = band(np.roll(source, int(offset * RATE), axis=0), low, high)
        bed = peak(bed, .59)
        bed += room * np.roll(bed[:, ::-1], round(.044 * RATE), axis=0)
        bed += room * .48 * np.roll(bed, round(.096 * RATE), axis=0)
        bed += air * low_wash
        bed = peak(close_loop(bed, .32), .61)
        output = AUDIO / (name + ".ogg")
        subprocess.run([ffmpeg, "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "2",
                        "-i", "-", "-c:a", "libvorbis", "-q:a", "3", str(output)],
                       input=bed.astype("<f4").tobytes(), check=True)
    print(f"Rendered {len(effects) + added_takes + len(cues) + 10} effects and {len(ambience)} venue beds")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg")
    render(locate_ffmpeg(parser.parse_args().ffmpeg))
