#!/usr/bin/env python3
"""Generates the 18x18 status-effect icons for the mod's custom MobEffects.

Everything here is simple procedural vector-style drawing with Pillow, matching the size and
transparent-background convention of vanilla's own `assets/minecraft/textures/mob_effect/*.png`
icons, so the custom effects sit in the HUD/inventory effect list like any other potion effect.

Usage: python3 tools/generate_effect_icons.py
Output: src/main/resources/assets/stormweapon/textures/mob_effect/*.png
"""

import os

from PIL import Image, ImageDraw

SIZE = 18
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "stormweapon", "textures", "mob_effect",
)


def new_canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def save(image, name):
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, f"{name}.png")
    image.save(path)
    print(f"wrote {path}")


def draw_snowflake(draw, cx, cy, radius, color, width=1):
    import math
    for i in range(6):
        angle = math.radians(60 * i - 90)
        x2 = cx + radius * math.cos(angle)
        y2 = cy + radius * math.sin(angle)
        draw.line([(cx, cy), (x2, y2)], fill=color, width=width)
        # short side barbs near the tip, giving the arm a crystalline look
        bx = cx + radius * 0.6 * math.cos(angle)
        by = cy + radius * 0.6 * math.sin(angle)
        for side in (-1, 1):
            barb_angle = angle + side * math.radians(35)
            bx2 = bx + radius * 0.28 * math.cos(barb_angle)
            by2 = by + radius * 0.28 * math.sin(barb_angle)
            draw.line([(bx, by), (bx2, by2)], fill=color, width=width)


def blizzard_chill():
    image = new_canvas()
    draw = ImageDraw.Draw(image)
    draw_snowflake(draw, SIZE / 2, SIZE / 2, 7.0, (216, 238, 250, 255), width=1)
    draw.ellipse([7.5, 7.5, 10.5, 10.5], fill=(255, 255, 255, 255))
    save(image, "blizzard_chill")


def blizzard_frailty():
    image = new_canvas()
    draw = ImageDraw.Draw(image)
    # A frost-pale heart, cracked down the middle to read as "weakened".
    fill = (150, 190, 214, 255)
    outline = (90, 130, 156, 255)
    heart = [
        (9, 15), (3, 9.5), (3, 6), (5.5, 4), (9, 6.5),
        (12.5, 4), (15, 6), (15, 9.5),
    ]
    draw.polygon(heart, fill=fill, outline=outline)
    draw.line([(9, 5.5), (7.5, 9), (9.5, 10.5), (8, 14.5)], fill=(235, 245, 250, 255), width=1)
    save(image, "blizzard_frailty")


def fog_chill():
    image = new_canvas()
    draw = ImageDraw.Draw(image)
    rows = [(5.5, 10), (9, 12), (12.5, 8)]
    shades = [(150, 150, 155, 235), (180, 180, 185, 220), (205, 205, 210, 200)]
    for (y, span), shade in zip(rows, shades):
        x0 = SIZE / 2 - span / 2
        x1 = SIZE / 2 + span / 2
        draw.line([(x0, y), (x1, y)], fill=shade, width=2)
    save(image, "fog_chill")


def cherry_blossom_blessing():
    image = new_canvas()
    draw = ImageDraw.Draw(image)
    import math
    cx, cy = SIZE / 2, SIZE / 2
    petal_color = (247, 168, 205, 255)
    petal_outline = (214, 120, 163, 255)
    for i in range(5):
        angle = math.radians(72 * i - 90)
        px = cx + 4.4 * math.cos(angle)
        py = cy + 4.4 * math.sin(angle)
        draw.ellipse([px - 3.1, py - 3.1, px + 3.1, py + 3.1], fill=petal_color, outline=petal_outline)
    draw.ellipse([cx - 2.1, cy - 2.1, cx + 2.1, cy + 2.1], fill=(255, 226, 120, 255))
    save(image, "cherry_blossom_blessing")


if __name__ == "__main__":
    blizzard_chill()
    blizzard_frailty()
    fog_chill()
    cherry_blossom_blessing()
