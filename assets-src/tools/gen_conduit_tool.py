"""Draws the Conduit Tool's 32x32 item icon.

Simple on purpose: a pair of steel jaws on a diagonal copper-gripped handle, the way vanilla draws
its tools, with the handle at the bottom left and the working end at the top right. The handheld
item model turns a diagonal icon upright in the hand, so the direction matters.

Run from anywhere: python -I gen_conduit_tool.py [--all]
Without --all it writes only the 26.1.2 NeoForge build; --all writes every build's item texture folder.
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
STEEL = hexc("6A7282")
STEEL_LIGHT = hexc("9AA3B4")
STEEL_DARK = hexc("3E4452")
COPPER = hexc("C86F4A")
COPPER_LIGHT = hexc("E39A6E")
COPPER_DARK = hexc("8E4B32")
GEM = hexc("D6E8FF")


def blob(cells, x, y, size):
    """Adds a square of the given size, its top-left corner at (x, y)."""
    for dy in range(size):
        for dx in range(size):
            cells.add((x + dx, y + dy))


def diagonal(cells, x0, y0, x1, y1, thickness):
    """A thick line of whole pixels from (x0, y0) to (x1, y1)."""
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        blob(cells, x0 + round((x1 - x0) * i / steps), y0 + round((y1 - y0) * i / steps), thickness)


def paint(im, cells, face, light, dark):
    """Fills the cells with an outline, a light top-left edge and a dark bottom-right edge."""
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in cells and 0 <= x + dx < 32 and 0 <= y + dy < 32:
                    im.putpixel((x + dx, y + dy), OUTLINE)
    for (x, y) in cells:
        colour = face
        if (x - 1, y - 1) not in cells or (x, y - 1) not in cells:
            colour = light
        elif (x + 1, y + 1) not in cells or (x + 1, y) not in cells:
            colour = dark
        im.putpixel((x, y), colour)


def draw():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

    # Handle: a copper grip running from the bottom left up to the middle.
    grip = set()
    diagonal(grip, 4, 26, 15, 15, 3)
    paint(im, grip, COPPER, COPPER_LIGHT, COPPER_DARK)

    # Head: two steel jaws opening towards the top right, with a bolt between them.
    head = set()
    diagonal(head, 14, 16, 22, 8, 3)
    diagonal(head, 22, 8, 27, 4, 2)
    diagonal(head, 22, 8, 27, 11, 2)
    paint(im, head, STEEL, STEEL_LIGHT, STEEL_DARK)

    # A Moon Crystal chip on the bolt, the one spot of colour.
    im.putpixel((19, 12), GEM)
    im.putpixel((20, 12), GEM)
    return im


def main():
    builds = BUILDS if "--all" in sys.argv[1:] else BUILDS[:1]
    icon = draw()
    for version, loader in builds:
        folder = ROOT / "versions" / version / loader / TEXTURES
        icon.save(folder / "conduit_tool.png")
        print("wrote", folder / "conduit_tool.png")


if __name__ == "__main__":
    main()
