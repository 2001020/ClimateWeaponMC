"""Generates the ClimateWeapon mod icon as pixel art.

Drawn on a small low-resolution canvas (no anti-aliasing) and then scaled up
with nearest-neighbour resampling, so every "pixel" in the final 1024x1024
PNG is a crisp, uniform block -- the standard technique for pixel-art icons.
"""

from PIL import Image, ImageDraw

GRID = 64          # logical pixel-art resolution
SCALE = 16          # 64 * 16 = 1024
SIZE = GRID * SCALE

# Palette
SKY_STORM_TOP = (26, 30, 46)
SKY_STORM_MID = (42, 48, 74)
SKY_STORM_LOW = (61, 70, 102)
SKY_FOG_TOP = (74, 66, 46)
SKY_FOG_MID = (138, 118, 70)
SKY_FOG_LOW = (214, 190, 128)
CLOUD_DARK = (34, 38, 56)
CLOUD_MID = (52, 58, 82)
LIGHTNING = (255, 240, 150)
LIGHTNING_CORE = (255, 255, 255)
FOG_BAND_1 = (232, 214, 150)
FOG_BAND_2 = (246, 232, 190)
GROUND = (24, 22, 20)
GROUND_LIGHT = (40, 36, 32)
PAD = (58, 54, 50)
MISSILE_BODY = (222, 224, 220)
MISSILE_SHADE = (168, 172, 176)
MISSILE_NOSE = (196, 60, 52)
MISSILE_FIN = (196, 60, 52)
MISSILE_FIN_DARK = (150, 42, 38)
WINDOW = (120, 210, 230)
FLAME_OUT = (255, 176, 40)
FLAME_MID = (255, 224, 90)
FLAME_CORE = (255, 255, 220)
BORDER = (14, 15, 22)


def band_fill(draw, y0, y1, color):
    draw.rectangle([0, y0, GRID, y1], fill=color)


def main():
    img = Image.new("RGB", (GRID, GRID), SKY_STORM_TOP)
    d = ImageDraw.Draw(img)

    # --- Split sky: storm (left) vs fog (right), diagonal seam ---
    for y in range(GRID):
        for x in range(GRID):
            seam = 30 + (y - 20) * 0.15
            if y < 6:
                base = SKY_STORM_TOP if x < seam else SKY_FOG_TOP
            elif y < 26:
                base = SKY_STORM_MID if x < seam else SKY_FOG_MID
            else:
                base = SKY_STORM_LOW if x < seam else SKY_FOG_LOW
            img.putpixel((x, y), base)

    # --- Storm clouds across the top ---
    cloud_blobs = [
        (6, 6, 20, 12), (16, 4, 34, 11), (2, 9, 16, 14),
        (30, 6, 44, 12), (40, 4, 56, 11), (50, 8, 62, 13),
    ]
    for (x0, y0, x1, y1) in cloud_blobs:
        d.rectangle([x0, y0, x1, y1], fill=CLOUD_MID)
    for (x0, y0, x1, y1) in cloud_blobs[:3]:
        d.rectangle([x0 + 1, y0 + 1, x1 - 2, y1 - 3], fill=CLOUD_DARK)

    # --- Lightning bolt, upper-left (storm side) ---
    bolt = [
        (16, 13), (20, 13), (17, 19), (21, 19),
        (15, 27), (19, 20), (15, 20),
    ]
    d.polygon(bolt, fill=LIGHTNING)
    d.line([(18, 15), (18, 18)], fill=LIGHTNING_CORE, width=1)

    # --- Fog bands, lower-right (fog side) ---
    fog_bands = [
        (38, 30, 62, 32), (42, 34, 63, 36),
        (36, 38, 60, 40), (44, 45, 63, 47),
    ]
    for i, (x0, y0, x1, y1) in enumerate(fog_bands):
        d.rectangle([x0, y0, x1, y1], fill=FOG_BAND_1 if i % 2 == 0 else FOG_BAND_2)

    # --- Ground strip ---
    band_fill(d, 54, GRID, GROUND)
    d.rectangle([0, 54, GRID, 55], fill=GROUND_LIGHT)
    d.rectangle([20, 55, 44, 58], fill=PAD)
    d.rectangle([18, 58, 46, 60], fill=PAD)

    # --- Central missile, launching upward astride the seam ---
    cx = 32
    # exhaust flame
    d.polygon([(cx - 4, 54), (cx + 4, 54), (cx + 2, 47), (cx - 2, 47)], fill=FLAME_OUT)
    d.polygon([(cx - 3, 53), (cx + 3, 53), (cx + 1, 48), (cx - 1, 48)], fill=FLAME_MID)
    d.polygon([(cx - 1, 52), (cx + 1, 52), (cx, 49)], fill=FLAME_CORE)

    # body
    d.rectangle([cx - 3, 22, cx + 3, 47], fill=MISSILE_BODY)
    d.rectangle([cx, 22, cx + 3, 47], fill=MISSILE_SHADE)
    # nose cone
    d.polygon([(cx - 3, 22), (cx + 3, 22), (cx, 13)], fill=MISSILE_NOSE)
    # window
    d.ellipse([cx - 2, 28, cx + 2, 32], fill=WINDOW)
    # rings
    d.rectangle([cx - 3, 36, cx + 3, 37], fill=MISSILE_FIN_DARK)
    # fins
    d.polygon([(cx - 3, 40), (cx - 8, 47), (cx - 3, 45)], fill=MISSILE_FIN)
    d.polygon([(cx + 3, 40), (cx + 8, 47), (cx + 3, 45)], fill=MISSILE_FIN_DARK)

    # --- Rounded badge mask + border ---
    mask = Image.new("L", (GRID, GRID), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, GRID - 1, GRID - 1], radius=10, fill=255)
    img.putalpha(mask)

    border = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    bd = ImageDraw.Draw(border)
    bd.rounded_rectangle([0, 0, GRID - 1, GRID - 1], radius=10, outline=BORDER, width=2)
    img = Image.alpha_composite(img, border)

    big = img.resize((SIZE, SIZE), Image.NEAREST)
    out_path = "/Users/starrail/Desktop/软件项目/ClimateWeaponMC/dist/climateweapon_icon_1024.png"
    big.save(out_path)
    print("saved", out_path, big.size)


if __name__ == "__main__":
    main()
