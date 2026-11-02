"""Draws the Flux Configurator's 32x32 item icon.

A handheld tablet in the tools' colors: dark Fluxsteel body with a copper rim, a screen showing
an unfolded side-config cube in the side panel's face colors (blue import, orange export, green
both, red blocked), the blue-white Moon Crystal gem of the concept art, and a copy and a paste
button. Drawn pixel by pixel rather than cut from the concept, so it stays crisp at 32 px.

Run from anywhere: python -I gen_configurator.py [--all]
Without --all it writes only the 26.1.2 NeoForge build, where new items start; --all writes every
build's item texture folder once the item has been ported.
"""
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILDS = [("mc26.1.2", "neoforge"), ("mc26.1.2", "forge"), ("mc1.21.1", "neoforge"),
          ("mc1.21.1", "forge"), ("mc1.20.1", "forge"), ("mc1.20.1", "neoforge")]
TEXTURES = "src/main/resources/assets/aeroflux/textures/item"


def hexc(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


OUTLINE = hexc("15171B")
COPPER = hexc("C86F4A")
COPPER_LIGHT = hexc("E39A6E")
COPPER_DARK = hexc("8E4B32")
STEEL = hexc("3A3F4A")
STEEL_LIGHT = hexc("5A6070")
STEEL_DARK = hexc("22262E")
SCREEN_EDGE = hexc("0E141A")
SCREEN = hexc("1C2630")
SCREEN_GLOW = hexc("2E4A63")
IMPORT = hexc("4A8FE0")
EXPORT = hexc("E8963A")
BOTH = hexc("5DBB63")
BLOCKED = hexc("D9534F")
GEM = hexc("D6E8FF")
GEM_LIGHT = hexc("FFFFFF")
GEM_DARK = hexc("8FB0E0")
BUTTON = hexc("8A8F96")
BUTTON_LIGHT = hexc("B0B5BC")
BUTTON_DARK = hexc("5A5F68")


def rect(im, x0, y0, x1, y1, color):
    """Fills a rectangle, both corners included."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), color)


def bevel(im, x0, y0, x1, y1, face, light, dark):
    """A flat block with a light top-left edge and a dark bottom-right edge."""
    rect(im, x0, y0, x1, y1, face)
    rect(im, x0, y0, x1, y0, light)
    rect(im, x0, y0, x0, y1, light)
    rect(im, x0, y1, x1, y1, dark)
    rect(im, x1, y0, x1, y1, dark)


def body(im):
    # Black outline with the corners cut, so the tablet reads as rounded.
    rect(im, 6, 1, 25, 30, OUTLINE)
    for x, y in ((6, 1), (25, 1), (6, 30), (25, 30)):
        im.putpixel((x, y), (0, 0, 0, 0))
    bevel(im, 7, 2, 24, 29, COPPER, COPPER_LIGHT, COPPER_DARK)
    bevel(im, 8, 3, 23, 28, STEEL, STEEL_LIGHT, STEEL_DARK)
    # A few darker specks on the steel, so it matches the worn metal of the other tools.
    for y in range(4, 28):
        for x in range(9, 23):
            if (x * 7 + y * 3) % 11 == 0:
                im.putpixel((x, y), STEEL_DARK)
    # The copper rim gets the same wear.
    for y in range(3, 29):
        for x in (7, 24):
            if (y * 5 + x) % 9 == 0:
                im.putpixel((x, y), COPPER_DARK)


def screen(im):
    rect(im, 9, 4, 22, 16, SCREEN_EDGE)
    rect(im, 10, 5, 21, 15, SCREEN)
    rect(im, 10, 5, 21, 5, SCREEN_GLOW)
    # The unfolded cube of the side panel: top, then left-front-right-back, then bottom.
    cells = {
        (1, 0): IMPORT,
        (0, 1): BOTH, (1, 1): EXPORT, (2, 1): BOTH, (3, 1): BLOCKED,
        (1, 2): EXPORT,
    }
    for (cx, cy), color in cells.items():
        x = 11 + cx * 3 - (1 if cx == 3 else 0)
        y = 7 + cy * 3
        rect(im, x, y, x + 1, y + 1, color)


def gem(im):
    # A small diamond under the screen.
    rows = [(15, 16), (14, 17), (13, 18), (14, 17), (15, 16)]
    for i, (x0, x1) in enumerate(rows):
        rect(im, x0, 18 + i, x1, 18 + i, GEM)
    rect(im, 13, 20, 13, 20, GEM_DARK)
    rect(im, 14, 21, 17, 21, GEM_DARK)
    rect(im, 15, 22, 16, 22, GEM_DARK)
    im.putpixel((14, 19), GEM_LIGHT)
    im.putpixel((15, 18), GEM_LIGHT)


def buttons(im):
    bevel(im, 10, 24, 14, 26, BUTTON, BUTTON_LIGHT, BUTTON_DARK)
    bevel(im, 17, 24, 21, 26, BUTTON, BUTTON_LIGHT, BUTTON_DARK)
    # Copy: two little squares; paste: a down arrow.
    im.putpixel((11, 25), STEEL_DARK)
    im.putpixel((13, 25), STEEL_DARK)
    im.putpixel((19, 25), STEEL_DARK)
    im.putpixel((18, 25), STEEL_DARK)
    im.putpixel((20, 25), STEEL_DARK)


def draw():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    body(im)
    screen(im)
    gem(im)
    buttons(im)
    return im


def main():
    builds = BUILDS if "--all" in sys.argv[1:] else BUILDS[:1]
    icon = draw()
    for version, loader in builds:
        folder = ROOT / "versions" / version / loader / TEXTURES
        icon.save(folder / "flux_configurator.png")
        print("wrote", folder / "flux_configurator.png")


if __name__ == "__main__":
    main()
