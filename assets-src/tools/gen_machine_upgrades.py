"""Draws the machine-only upgrade icons (Efficiency, Fortune, Silk Touch, Purity).

They share the pipe upgrades' tile: the same dark plate and copper rim, with a new symbol in the
middle. The empty plate is taken from the four existing upgrade icons, keeping at each pixel the
darkest of them, which removes their symbols because no two sit in quite the same place.

Run from anywhere; it writes into every version's item texture folder.
"""
import pathlib
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
TARGETS = [ROOT / "versions" / v / l / "src/main/resources/assets/aeroflux/textures/item"
           for v in ("mc26.1.2", "mc1.21.1", "mc1.20.1") for l in ("neoforge", "forge")]
SOURCE = TARGETS[0]

# One symbol per upgrade, as rows of pixel letters. "." is see-through to the plate.
SYMBOLS = {
    "efficiency_upgrade": [
        "........gg",
        "......gggg",
        "....gggggg",
        "..ggggggLg",
        ".gggggLggg",
        ".ggggLgggg",
        "gggLgggggg",
        "ggLgggggg.",
        "gLggggg...",
        "Lgg.......",
        "L.........",
    ],
    "fortune_upgrade": [
        "..WWWWWWW..",
        ".WbbWbWbbW.",
        "WbbbWbWbbbW",
        "WWWWWWWWWWW",
        ".WbbbbbbbW.",
        "..WbbbbbW..",
        "...WbbbW...",
        "....WbW....",
        ".....W.....",
    ],
    "silk_touch_upgrade": [
        "WWWWWWWWW.",
        ".sSsSsSs..",
        ".SsSsSsS..",
        ".sSsSsSs..",
        ".SsSsSsS..",
        ".sSsSsSsS.",
        "WWWWWWWWW.S",
        ".........S",
        "........S.",
    ],
    "purity_upgrade": [
        "....c....",
        "...ccc...",
        "...ccc...",
        "..ccccc..",
        ".ccWcccc.",
        ".cWcccccc",
        "ccWcccccc",
        "ccccccccc",
        "ccccccccc",
        ".ccccccc.",
        "..ccccc..",
    ],
}
COLORS = {
    "g": (111, 211, 106, 255), "L": (206, 245, 190, 255),
    "W": (242, 242, 242, 255), "b": (59, 99, 214, 255),
    "s": (237, 230, 218, 255), "S": (196, 186, 170, 255),
    "c": (127, 216, 242, 255),
}


def plate():
    """The empty tile: the darkest pixel of the four pipe upgrade icons at each spot."""
    icons = [Image.open(SOURCE / f"{n}_upgrade.png").convert("RGBA")
             for n in ("speed", "capacity", "filter", "priority")]
    out = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            pixels = [icon.getpixel((x, y)) for icon in icons]
            out.putpixel((x, y), min(pixels, key=lambda p: (p[3] == 0, sum(p[:3]))))
    return out


def draw(base, rows):
    icon = base.copy()
    height, width = len(rows), max(len(r) for r in rows)
    left, top = (32 - width) // 2, (32 - height) // 2
    for dy, row in enumerate(rows):
        for dx, letter in enumerate(row):
            if letter != ".":
                icon.putpixel((left + dx, top + dy), COLORS[letter])
    return icon


def main():
    base = plate()
    for name, rows in SYMBOLS.items():
        icon = draw(base, rows)
        for folder in TARGETS:
            icon.save(folder / f"{name}.png")
        print("wrote", name)


if __name__ == "__main__":
    main()
