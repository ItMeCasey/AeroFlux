"""Builds the art of the creative-only blocks: the Creative Flux Tank's frame texture, its block and
item models, block state and client item. The frame is the tank frame of the other tiers drawn at
32x32 like the current art, in the purple of creative-only things, with a band of Aero and Moon
Flux colors across it; the item model adds a full core of flux so the item reads as an endless tank.

Run: python -I gen_creative.py [resources/assets/aeroflux folder]
The folder defaults to the 26.1.2 NeoForge build. 1.20.1 has no items/ folder; skip that file there.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "..", "versions", "mc26.1.2", "neoforge", "src", "main", "resources",
                           "assets", "aeroflux")
NS = "aeroflux"
SIZE = 32

PURPLE = (0x8E, 0x3F, 0xBF, 255)
PURPLE_LIGHT = (0xC7, 0x7D, 0xDB, 255)
PURPLE_DARK = (0x4F, 0x1F, 0x70, 255)
AERO = (0xFF, 0xF2, 0xA8, 255)
MOON = (0xD6, 0xE8, 0xFF, 255)


def frame_texture():
    """A bevelled purple plate with corner rivets and an Aero/Moon checker band through the middle."""
    im = Image.new("RGBA", (SIZE, SIZE), PURPLE)
    d = ImageDraw.Draw(im)
    # Faint diagonal grain, so the flat purple does not look like a missing texture.
    for i in range(-SIZE, SIZE, 6):
        d.line((i, 0, i + SIZE, SIZE), fill=(0x84, 0x39, 0xB3, 255))
    d.rectangle((0, 0, SIZE - 1, 1), fill=PURPLE_LIGHT)
    d.rectangle((0, 0, 1, SIZE - 1), fill=PURPLE_LIGHT)
    d.rectangle((0, SIZE - 2, SIZE - 1, SIZE - 1), fill=PURPLE_DARK)
    d.rectangle((SIZE - 2, 0, SIZE - 1, SIZE - 1), fill=PURPLE_DARK)
    for x, y in ((4, 4), (SIZE - 6, 4), (4, SIZE - 6), (SIZE - 6, SIZE - 6)):
        d.rectangle((x, y, x + 1, y + 1), fill=PURPLE_LIGHT)
        d.point((x + 1, y + 1), fill=PURPLE_DARK)
    for x in range(6, SIZE - 6, 2):
        color = AERO if (x // 2) % 2 else MOON
        d.rectangle((x, 15, x + 1, 16), fill=color)
    return im


def core_texture():
    """The flux inside the item: Aero gold at the top fading to Moon blue at the bottom."""
    im = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        t = y / (SIZE - 1)
        color = tuple(round(AERO[i] * (1 - t) + MOON[i] * t) for i in range(3)) + (230,)
        for x in range(SIZE):
            shade = 0.92 if (x + y) % 7 == 0 else 1.0
            im.putpixel((x, y), tuple(round(c * shade) for c in color[:3]) + (color[3],))
    return im


def box(frm, to, texture):
    return {"from": frm, "to": to, "faces": {side: {"texture": texture}
                                             for side in ("north", "south", "east", "west", "up", "down")}}


def tank_elements():
    """The same frame and glass as the tiered tanks (see gen_assets.tank_model)."""
    els = []
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        els.append(box([x, 0, z], [x + 2, 16, z + 2], "#frame"))
    for y in (0, 14):
        els.append(box([2, y, 0], [14, y + 2, 2], "#frame"))
        els.append(box([2, y, 14], [14, y + 2, 16], "#frame"))
        els.append(box([0, y, 2], [2, y + 2, 14], "#frame"))
        els.append(box([14, y, 2], [16, y + 2, 14], "#frame"))
    els.append(box([2, 2, 2], [14, 14, 14], "#glass"))
    return els


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    name = "creative_flux_tank"
    tex_dir = os.path.join(out, "textures", "block")
    os.makedirs(tex_dir, exist_ok=True)
    frame_texture().save(os.path.join(tex_dir, "frame_creative.png"))
    core_texture().save(os.path.join(tex_dir, "creative_tank_core.png"))

    textures = {"frame": f"{NS}:block/frame_creative", "glass": f"{NS}:block/tank_glass",
                "particle": f"{NS}:block/frame_creative"}
    # The block draws only frame and glass; the flux inside comes from the tank renderer.
    write_json(os.path.join(out, "models", "block", name + ".json"),
               {"textures": textures, "elements": tank_elements()})
    write_json(os.path.join(out, "blockstates", name + ".json"),
               {"variants": {"": {"model": f"{NS}:block/{name}"}}})

    # The item has no renderer, so its model holds a full core of flux behind the glass.
    with open(os.path.join(out, "models", "item", "copper_tank.json"), encoding="utf-8") as f:
        display = json.load(f).get("display", {})
    item = {"textures": dict(textures, core=f"{NS}:block/creative_tank_core"),
            "elements": [box([2.25, 2.25, 2.25], [13.75, 13.75, 13.75], "#core")] + tank_elements(),
            "gui_light": "side", "display": display}
    write_json(os.path.join(out, "models", "item", name + ".json"), item)
    write_json(os.path.join(out, "items", name + ".json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    print("wrote", name, "to", os.path.abspath(out))


if __name__ == "__main__":
    main()
