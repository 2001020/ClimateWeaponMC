"""Generates the Signal Connector item texture (16x16, standard Minecraft item size).

A handheld linking remote: dark casing, a lit cyan screen, an antenna, and a red
transmit button -- readable at 16x16 and distinct from the missile items.
"""

from PIL import Image

W = H = 16
T = (0, 0, 0, 0)

CASE_DARK = (44, 48, 56, 255)
CASE_MID = (72, 78, 88, 255)
CASE_LIGHT = (104, 112, 124, 255)
SCREEN_DIM = (24, 92, 104, 255)
SCREEN_LIT = (96, 226, 240, 255)
BUTTON = (198, 58, 48, 255)
BUTTON_DARK = (140, 36, 30, 255)
ANTENNA = (150, 158, 168, 255)
ANTENNA_TIP = (255, 226, 120, 255)
OUTLINE = (18, 20, 26, 255)

# 16x16 map. Legend:
#   . transparent   o outline    d case dark   m case mid   l case light
#   s screen dim    S screen lit b button      B button dark
#   a antenna       A antenna tip
ART = [
    "..........oAo...",
    "..........oao...",
    "..........oao...",
    "...oooooooao....",
    "..olmmmmmmao....",
    "..omsSSSSsmo....",
    "..omSSSSSSmo....",
    "..omsSSSSsmo....",
    "..olmmmmmmmo....",
    "..omdBbbBdmo....",
    "..omdbbbbdmo....",
    "..olmmmmmmmo....",
    "..omdmdmdmdo....",
    "..omdmdmdmdo....",
    "..olmmmmmmmo....",
    "...ooooooooo....",
]

PALETTE = {
    ".": T,
    "o": OUTLINE,
    "d": CASE_DARK,
    "m": CASE_MID,
    "l": CASE_LIGHT,
    "s": SCREEN_DIM,
    "S": SCREEN_LIT,
    "b": BUTTON,
    "B": BUTTON_DARK,
    "a": ANTENNA,
    "A": ANTENNA_TIP,
}


def main():
    img = Image.new("RGBA", (W, H), T)
    for y, row in enumerate(ART):
        for x, ch in enumerate(row[:W]):
            img.putpixel((x, y), PALETTE[ch])
    out = ("/Users/starrail/Desktop/软件项目/ClimateWeaponMC/src/main/resources/"
           "assets/stormweapon/textures/item/signal_connector.png")
    img.save(out)
    print("saved", out)


if __name__ == "__main__":
    main()
