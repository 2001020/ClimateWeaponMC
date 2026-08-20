#!/usr/bin/env python3
"""Generates the original tileable storm cloud textures used by StormCloudRenderer.

Everything here is procedural: seeded value-noise fBm on a wrapping lattice, so the
resulting images tile seamlessly and contain no third-party or game artwork.

Usage: python3 tools/generate_storm_textures.py
Output: src/main/resources/assets/stormweapon/textures/environment/*.png
"""

import math
import os
import struct
import zlib

SIZE = 256
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "stormweapon", "textures", "environment",
)


def hash2(ix, iy, period, seed):
    h = (ix % period) * 374761393 + (iy % period) * 668265263 + seed * 1442695040888963407
    h &= 0xFFFFFFFFFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFFFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFFFF) / float(0xFFFFFF)


def smooth(t):
    return t * t * (3.0 - 2.0 * t)


def value_noise(x, y, period, seed):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = smooth(x - ix), smooth(y - iy)
    v00 = hash2(ix, iy, period, seed)
    v10 = hash2(ix + 1, iy, period, seed)
    v01 = hash2(ix, iy + 1, period, seed)
    v11 = hash2(ix + 1, iy + 1, period, seed)
    return (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy


def fbm(u, v, base_period, octaves, seed, gain=0.5, lacunarity=2.0):
    total, amplitude, norm = 0.0, 1.0, 0.0
    period = base_period
    for octave in range(octaves):
        total += amplitude * value_noise(u * period, v * period, period, seed + octave * 7919)
        norm += amplitude
        amplitude *= gain
        period = int(period * lacunarity)
    return total / norm


def clamp01(value):
    return 0.0 if value < 0.0 else (1.0 if value > 1.0 else value)


def write_png(path, pixels, width=SIZE, height=SIZE):
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(pixels[y * stride:(y + 1) * stride])

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


def build(name, seed, base_period, octaves, coverage, contrast, swirl, tint):
    pixels = bytearray(SIZE * SIZE * 4)
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = x / SIZE, y / SIZE
            if swirl > 0.0:
                # Wrapping domain warp: keeps the tile seamless while breaking up straight edges.
                wu = fbm(u, v, base_period, 2, seed + 991)
                wv = fbm(u, v, base_period, 2, seed + 4441)
                u = (u + swirl * (wu - 0.5)) % 1.0
                v = (v + swirl * (wv - 0.5)) % 1.0
            n = fbm(u, v, base_period, octaves, seed)
            detail = fbm(u, v, base_period * 4, 2, seed + 13337)
            n = clamp01(n * 0.78 + detail * 0.22)
            alpha = clamp01((n - (1.0 - coverage)) * contrast)
            alpha = alpha * alpha * (3.0 - 2.0 * alpha)
            # Luminance keeps a little internal structure so vertex tint is not flat.
            lum = 0.72 + 0.28 * n
            index = (y * SIZE + x) * 4
            pixels[index + 0] = int(clamp01(lum * tint[0]) * 255.0)
            pixels[index + 1] = int(clamp01(lum * tint[1]) * 255.0)
            pixels[index + 2] = int(clamp01(lum * tint[2]) * 255.0)
            pixels[index + 3] = int(alpha * 255.0)
    write_png(os.path.join(OUT_DIR, name), pixels)
    print("wrote", name)


def build_rain_streak(name, width=32, height=64):
    """Long vertical rain streak.

    u runs across the streak width, v runs from the head (v=0) to the tail (v=1).
    The core stays well above the 0.1 alpha cutout of the entity pipelines so a
    streak never disappears entirely; the soft flanks provide the wet sheen.
    """
    pixels = bytearray(width * height * 4)
    for y in range(height):
        v = (y + 0.5) / height
        # Head is a compact bright cap, the tail stretches out and thins away.
        head = clamp01(v / 0.14)
        tail = clamp01((1.0 - v) / 0.55)
        along = clamp01(min(head, 0.35 + 0.65 * tail))
        for x in range(width):
            across = abs((x + 0.5) / width - 0.5) * 2.0
            flank = clamp01(1.0 - across ** 1.35)
            core = clamp01(1.0 - across * 2.6)
            shape = clamp01(flank * 0.55 + core * 0.65) * along
            lum = 0.62 + 0.38 * core
            index = (y * width + x) * 4
            pixels[index + 0] = int(clamp01(lum * 0.80) * 255.0)
            pixels[index + 1] = int(clamp01(lum * 0.88) * 255.0)
            pixels[index + 2] = int(clamp01(lum * 1.00) * 255.0)
            pixels[index + 3] = int(clamp01(shape) * 255.0)
    write_png(os.path.join(OUT_DIR, name), pixels, width, height)
    print("wrote", name)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    # Upper anvil sheet: broad, thin, fast moving, plenty of gaps.
    build("storm_cloud_upper.png", 20260815, 4, 5, 0.68, 2.4, 0.10, (0.94, 0.96, 1.0))
    # Middle deck: denser and more clumped.
    build("storm_cloud_mid.png", 71828182, 6, 5, 0.70, 2.8, 0.18, (0.92, 0.95, 1.0))
    # Rotating storm base: heavy coverage, large soft cells, few holes.
    build("storm_cloud_base.png", 31415926, 3, 4, 0.72, 3.2, 0.26, (0.90, 0.94, 1.0))
    # Sky wash used to close the gaps overhead at the storm core.
    build("storm_sky_wash.png", 16180339, 2, 3, 0.88, 3.4, 0.12, (0.90, 0.93, 1.0))
    # Single wind-driven rain streak billboard used by StormRainRenderer.
    build_rain_streak("storm_rain_streak.png")


if __name__ == "__main__":
    main()
