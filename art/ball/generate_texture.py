"""Bake the curved panel layout and pressed-leather detail into the ball's UV map."""

from pathlib import Path

import numpy as np
from PIL import Image


WIDTH, HEIGHT = 1024, 512
OUTPUT = Path(__file__).resolve().parents[2] / "core/src/main/resources/textures/ball.png"


def rotate(a, b, angle):
    c, s = np.cos(angle), np.sin(angle)
    return a * c - b * s, a * s + b * c


def smooth(lo, hi, value):
    t = np.clip((value - lo) / (hi - lo), 0, 1)
    return t * t * (3 - 2 * t)


def dimples(a, b):
    # Stagger the rows and wrap each cell around its own center.
    row = np.floor(b + 0.5)
    dx = np.remainder(a + 0.5 * row + 0.5, 1) - 0.5
    dy = b - row
    r = np.hypot(dx, dy)
    recess = 1 - smooth(0.13, 0.30, r)
    edge = smooth(0.20, 0.27, r) * (1 - smooth(0.27, 0.36, r))
    return -0.056 * recess + 0.018 * edge


def make_texture():
    # Keep this projection in sync with BallRenderer.spherePoint.
    longitude = -2 * np.pi * (np.arange(WIDTH)[None, :] + 0.5) / WIDTH
    latitude = np.pi * (np.arange(HEIGHT)[:, None] + 0.5) / HEIGHT
    x = np.sin(latitude) * np.cos(longitude)
    y = np.broadcast_to(np.cos(latitude), x.shape)
    z = np.sin(latitude) * np.sin(longitude)

    # Warp the six sides of a cube on the sphere. The edges meet continuously at junctions
    # and the gradual twist makes the colors read as curved panels from any view.
    px, pz = rotate(x, z, 0.47 + 1.08 * y)
    py, pz = rotate(y, pz, 0.28 - 0.54 * px)
    px, py = rotate(px, py, 0.43 + 0.28 * pz)
    faces = np.stack((px, -px, py, -py, pz, -pz), axis=-1)
    panel = np.argmax(faces, axis=-1)
    nearest = np.partition(faces, -2, axis=-1)[..., -2:]
    distance = (nearest[..., 1] - nearest[..., 0]) * 0.707

    palette = np.array([
        [250, 194, 45], [241, 178, 35],
        [39, 101, 190], [31, 88, 178],
        [247, 244, 226], [235, 241, 242],
    ], dtype=float)
    # The pressed groove stays crisp up close and softens into the bordering colors at distance.
    groove = smooth(0.0035, 0.016, distance)
    lip = smooth(0.011, 0.021, distance) * (1 - smooth(0.024, 0.044, distance))
    panel_roll = smooth(0.014, 0.12, distance)

    # Blend three projections on the sphere so the leather pores do not converge at the poles.
    weights = np.stack((x ** 4, y ** 4, z ** 4), axis=-1)
    pores = np.stack((
        dimples(z * 53 + 0.17, y * 53),
        dimples(x * 53 + 0.41, z * 53),
        dimples(x * 53, y * 53 + 0.32),
    ), axis=-1)
    grain = np.sum(weights * pores, axis=-1) / np.sum(weights, axis=-1)
    leather = 0.008 * np.sin(87 * x + 31 * z) * np.sin(75 * y - 43 * z)
    surface = 0.96 + 0.04 * panel_roll + 0.027 * lip + grain + leather
    color = palette[panel] * surface[..., None]
    seam = np.array([28, 39, 57])
    color = seam * (1 - groove[..., None]) + color * groove[..., None]
    return Image.fromarray(np.uint8(np.clip(color + 0.5, 0, 255)))


if __name__ == "__main__":
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    make_texture().save(OUTPUT, optimize=True)
