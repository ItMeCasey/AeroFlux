"""Builds AeroFlux's block and item art: 16x16 textures plus block/item model JSON.

Blocks are drawn from the concept art in ../../concept as cuboid models; item textures are
cut out of the concept pictures and shrunk to 16x16. Run with: python -I gen_assets.py
Output goes to ../aeroflux/ (assets-src is the source of truth, copied into each version).
"""
import json
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "aeroflux")
CONCEPT = os.path.join(HERE, "..", "..", "concept")
TEX_BLOCK = os.path.join(OUT, "textures", "block")
TEX_ITEM = os.path.join(OUT, "textures", "item")
MODEL_BLOCK = os.path.join(OUT, "models", "block")
MODEL_ITEM = os.path.join(OUT, "models", "item")
STATES = os.path.join(OUT, "blockstates")
NS = "aeroflux"

for d in (TEX_BLOCK, TEX_ITEM, MODEL_BLOCK, MODEL_ITEM, STATES):
    os.makedirs(d, exist_ok=True)


def hexc(value, alpha=255):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


AERO = hexc("FFF2A8")
MOON = hexc("D6E8FF")


def tex():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def rect(im, x0, y0, x1, y1, color):
    ImageDraw.Draw(im).rectangle((x0, y0, x1, y1), fill=color)


def save_block_tex(name, im):
    im.save(os.path.join(TEX_BLOCK, name + ".png"))


FACES = ("north", "south", "east", "west", "up", "down")


def complete(model):
    """Gives every solid element all six faces, so no side is ever left out. Flat planes
    (an element with zero thickness, such as a sprout) keep only the faces they declare."""
    for el in model.get("elements", []):
        if any(a == b for a, b in zip(el["from"], el["to"])):
            continue
        faces = el["faces"]
        fallback = faces.get("north") or next(iter(faces.values()))
        for side in FACES:
            faces.setdefault(side, {"texture": fallback["texture"]})
    return model


def write_json(path, data):
    if isinstance(data, dict) and "elements" in data:
        complete(data)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- tier materials
MATERIALS = {
    "copper": ("C86F4A", "E39A6E", "8E4B32"),
    "iron": ("B8BCC0", "D8DADD", "7A7E83"),
    "gold": ("E8B930", "FBE27A", "A9801A"),
    "diamond": ("4DD9D0", "9BF2EA", "2A9A94"),
    "netherite": ("4A4046", "6B6068", "2B2528"),
    "fluxium": ("3B404B", "5A6070", "22262E"),
}
TIERS = list(MATERIALS)


def frame_texture(material):
    base, light, dark = (hexc(c) for c in MATERIALS[material])
    im = tex()
    rect(im, 0, 0, 15, 15, base)
    rect(im, 0, 0, 15, 0, light)
    rect(im, 0, 0, 0, 15, light)
    rect(im, 0, 15, 15, 15, dark)
    rect(im, 15, 0, 15, 15, dark)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel((x, y), light)
    if material == "gold":
        for x, y in ((2, 2), (13, 13)):
            im.putpixel((x, y), hexc("9A6BD6"))
    if material == "fluxium":
        for x in range(3, 13):
            im.putpixel((x, 8), AERO if x % 2 else MOON)
    return im


def glass_texture():
    im = tex()
    rect(im, 0, 0, 15, 15, (200, 230, 255, 60))
    rect(im, 0, 0, 15, 0, (225, 242, 255, 150))
    rect(im, 0, 0, 0, 15, (225, 242, 255, 150))
    rect(im, 0, 15, 15, 15, (225, 242, 255, 150))
    rect(im, 15, 0, 15, 15, (225, 242, 255, 150))
    for i in range(3, 7):
        im.putpixel((i, i), (255, 255, 255, 170))
    return im


def dish_texture():
    im = tex()
    for x in range(16):
        for y in range(16):
            base = AERO if x < 8 else MOON
            shade = 0.88 + 0.12 * ((x * 7 + y * 3) % 5) / 4
            im.putpixel((x, y), tuple(int(c * shade) for c in base[:3]) + (255,))
    ImageDraw.Draw(im).rectangle((0, 0, 15, 15), outline=hexc("8E4B32"))
    for x, y in ((3, 3), (4, 3), (3, 4), (11, 3), (12, 4)):
        im.putpixel((x, y), (255, 255, 255, 255))
    return im


# ---------------------------------------------------------------- machine textures
def panel(base, light, dark, trim=None):
    base, light, dark = hexc(base), hexc(light), hexc(dark)
    im = tex()
    rect(im, 0, 0, 15, 15, base)
    for y in (3, 7, 11, 15):
        rect(im, 0, y, 15, y, dark)
    for y, offset in ((0, 0), (4, 4), (8, 0), (12, 4)):
        for x in range(offset, 16, 8):
            rect(im, x, y, x, min(y + 3, 15), dark)
    rect(im, 0, 0, 15, 0, light)
    if trim:
        trim = hexc(trim)
        for x0, y0 in ((0, 0), (13, 0), (0, 13), (13, 13)):
            rect(im, x0, y0, x0 + 2, y0 + 2, trim)
    return im


def stone_side(): return panel("55595F", "6E7279", "3A3D42", "C86F4A")
def dark_metal_side(): return panel("3E4249", "5A5F68", "26292E", "C86F4A")
def wood_side(): return panel("6B4A2B", "8A6540", "4A3119", "3A3D42")
def lectern_side(): return panel("4A3321", "6B4A2B", "2F1F12", "9A6BD6")
def obelisk_side(): return panel("3A3D42", "55595F", "26292E")


def front_furnace():
    im = stone_side()
    rect(im, 3, 8, 12, 12, hexc("26292E"))
    rect(im, 4, 9, 11, 11, AERO)
    im.putpixel((8, 4), AERO)
    for p in ((7, 4), (9, 4), (8, 3), (8, 5)):
        im.putpixel(p, hexc("E6B422"))
    return im


def front_grinder():
    im = dark_metal_side()
    rect(im, 3, 3, 12, 12, hexc("FFC857"))
    for x in range(3, 13, 3):
        rect(im, x, 3, x, 12, hexc("26292E"))
    rect(im, 3, 7, 12, 8, hexc("26292E"))
    rect(im, 5, 5, 6, 6, hexc("8E4B32"))
    rect(im, 9, 9, 10, 10, hexc("8E4B32"))
    return im


def top_grinder():
    im = dark_metal_side()
    rect(im, 4, 4, 11, 11, hexc("26292E"))
    rect(im, 5, 5, 10, 10, hexc("FFC857"))
    return im


def front_sawmill():
    im = wood_side()
    rect(im, 3, 6, 12, 11, hexc("26292E"))
    rect(im, 4, 7, 11, 10, hexc("8A8F96"))
    rect(im, 6, 8, 9, 9, AERO)
    return im


def top_sawmill():
    im = wood_side()
    rect(im, 2, 7, 13, 9, hexc("26292E"))
    rect(im, 3, 8, 12, 8, AERO)
    return im


def front_smelter():
    im = dark_metal_side()
    ImageDraw.Draw(im).ellipse((3, 4, 12, 13), fill=hexc("26292E"))
    ImageDraw.Draw(im).ellipse((4, 5, 11, 12), fill=hexc("FFE98A"))
    rect(im, 0, 7, 15, 7, hexc("C86F4A"))
    rect(im, 0, 12, 15, 12, hexc("C86F4A"))
    return im


def top_smelter():
    im = dark_metal_side()
    for x in (2, 6, 10):
        rect(im, x, 6, x + 3, 9, hexc("C86F4A"))
        rect(im, x + 1, 7, x + 2, 8, hexc("1A1C20"))
    return im


def side_infuser():
    im = stone_side()
    for p in ((7, 6), (8, 6), (6, 7), (6, 8), (7, 9), (8, 9)):
        im.putpixel(p, MOON)
    return im


def top_infuser():
    im = stone_side()
    ImageDraw.Draw(im).ellipse((2, 2, 13, 13), fill=hexc("26292E"))
    ImageDraw.Draw(im).ellipse((4, 4, 11, 11), fill=MOON)
    im.putpixel((7, 7), (255, 255, 255, 255))
    return im


def top_book(open_book):
    im = lectern_side()
    if open_book:
        rect(im, 1, 3, 14, 12, hexc("E8DDB5"))
        rect(im, 7, 3, 8, 12, hexc("6B4A2B"))
        for y in (5, 7, 9):
            rect(im, 3, y, 6, y, hexc("9A6BD6"))
            rect(im, 9, y, 12, y, hexc("9A6BD6"))
    else:
        rect(im, 1, 3, 14, 12, hexc("26292E"))
        rect(im, 1, 7, 14, 8, hexc("6B6F76"))
        rect(im, 7, 3, 8, 12, hexc("6B6F76"))
    return im


def front_enchanter(chained):
    im = lectern_side()
    rect(im, 5, 5, 10, 10, hexc("3A5BD9") if not chained else hexc("6B6F76"))
    rect(im, 6, 6, 9, 9, hexc("6C8CFF") if not chained else hexc("26292E"))
    return im


def soil_top():
    im = tex()
    rect(im, 0, 0, 15, 15, hexc("5B3A1E"))
    for x, y in ((2, 3), (5, 9), (9, 2), (12, 12), (4, 13), (13, 6)):
        im.putpixel((x, y), hexc("3D2410"))
    rect(im, 0, 0, 15, 0, hexc("C86F4A"))
    rect(im, 0, 15, 15, 15, hexc("C86F4A"))
    rect(im, 0, 0, 0, 15, hexc("C86F4A"))
    rect(im, 15, 0, 15, 15, hexc("C86F4A"))
    return im


def sprout():
    im = tex()
    for p in ((7, 8), (7, 9), (7, 10), (7, 11), (7, 12), (7, 13), (8, 13)):
        im.putpixel(p, hexc("3F8F2F"))
    for p in ((5, 8), (6, 7), (9, 7), (10, 6), (8, 9), (5, 9), (10, 7)):
        im.putpixel(p, hexc("5DBB3F"))
    return im


def sun_panel():
    im = tex()
    rect(im, 0, 0, 15, 15, AERO)
    for k in (5, 10):
        rect(im, k, 0, k, 15, hexc("E6B422"))
        rect(im, 0, k, 15, k, hexc("E6B422"))
    ImageDraw.Draw(im).rectangle((0, 0, 15, 15), outline=hexc("C86F4A"))
    return im


def moon_panel():
    im = tex()
    rect(im, 0, 0, 15, 15, MOON)
    ImageDraw.Draw(im).ellipse((4, 3, 12, 11), fill=hexc("8FB0E0"))
    ImageDraw.Draw(im).ellipse((6, 2, 14, 10), fill=MOON)
    ImageDraw.Draw(im).rectangle((0, 0, 15, 15), outline=hexc("C86F4A"))
    return im


def copper_plate(): return frame_texture("copper")


def ring_texture():
    im = tex()
    for x in range(16):
        base = AERO if x < 8 else MOON
        rect(im, x, 0, x, 15, base)
    rect(im, 0, 0, 15, 0, (255, 255, 255, 255))
    rect(im, 0, 15, 15, 15, hexc("B8B8A0"))
    return im


def node_texture():
    im = tex()
    rect(im, 0, 0, 15, 15, hexc("3A3D42"))
    return im


def book_inner():
    return top_book(True)


# ---------------------------------------------------------------- pipes
PIPES = {
    # name: (main, glyph pixels as (dx, dy) offsets inside a 4-wide strip, glyph color)
    "fluid": ("C9CED3", [(1, 0), (2, 1), (1, 1), (1, 2), (2, 2)], "3A8FD9"),
    "item": ("4B5057", [(1, 0), (2, 0), (1, 1), (2, 1), (1, 2), (2, 2)], "B88A5A"),
    "redstone": ("D8322C", [(1, 1), (2, 1), (1, 2), (2, 2)], "FF8A80"),
    "energy": ("E8731A", [(2, 0), (1, 1), (2, 1), (1, 2), (1, 3)], "FFE35A"),
    "gas": ("E6B422", [(1, 0), (2, 0), (2, 1), (1, 2), (1, 3), (2, 3)], "FFF4B0"),
    "me": ("8B4FD8", [(1, 0), (2, 1), (1, 2), (0, 1)], "E6D0FF"),
    "dense_me": ("5E2FA8", [(1, 0), (2, 0), (0, 1), (3, 1), (0, 2), (3, 2), (1, 3), (2, 3)], "C9A6FF"),
}


def pipe_texture(name):
    main, glyph, glyph_color = PIPES[name]
    main = hexc(main)
    light = tuple(min(255, int(c * 1.25)) for c in main[:3]) + (255,)
    dark = tuple(int(c * 0.72) for c in main[:3]) + (255,)
    im = tex()
    # long-side strip: columns 0-3, rows 0-15, tiles every 8 px
    rect(im, 0, 0, 3, 15, main)
    rect(im, 0, 0, 0, 15, light)
    rect(im, 3, 0, 3, 15, dark)
    for oy in (2, 10):
        for dx, dy in glyph:
            im.putpixel((dx, oy + dy), hexc(glyph_color))
    # end cap: 4x4 block at columns 4-7, rows 0-3
    rect(im, 4, 0, 7, 3, dark)
    rect(im, 5, 1, 6, 2, main)
    return im


def pipe_models():
    for name in PIPES:
        tex_ref = f"{NS}:block/pipe_{name}"
        for part, frm, to in (("core", [6, 6, 6], [10, 10, 10]), ("arm", [6, 6, 0], [10, 10, 6])):
            faces = {}
            for face in ("north", "south", "east", "west", "up", "down"):
                end = (part == "core") or face in ("north", "south")
                if part == "arm" and face == "south":
                    faces[face] = {"uv": [4, 0, 8, 4], "texture": "#t"}
                elif part == "core":
                    faces[face] = {"uv": [0, 0, 4, 4], "texture": "#t"}
                elif end:
                    faces[face] = {"uv": [4, 0, 8, 4], "texture": "#t"}
                else:
                    faces[face] = {"uv": [0, 0, 4, 6], "texture": "#t"}
            write_json(os.path.join(MODEL_BLOCK, f"pipe_{name}_{part}.json"), {
                "textures": {"t": tex_ref, "particle": tex_ref},
                "elements": [{"from": frm, "to": to, "faces": faces}],
            })


def connector_models():
    """Machine connector: 2 px deep, sized to the bounding box of the pipe layout for n pipes."""
    sizes = {1: (4, 4), 2: (10, 4), 3: (10, 10), 4: (10, 10), 5: (12, 12), 6: (12, 12)}
    for n, (w, h) in sizes.items():
        x0, y0 = 8 - w / 2, 8 - h / 2
        write_json(os.path.join(MODEL_BLOCK, f"connector_{n}.json"), {
            "textures": {"t": f"{NS}:block/pipe_node", "particle": f"{NS}:block/pipe_node"},
            "elements": [{
                "from": [x0, y0, 0], "to": [x0 + w, y0 + h, 2],
                "faces": {f: {"texture": "#t"} for f in ("north", "south", "east", "west", "up", "down")},
            }],
        })


# ---------------------------------------------------------------- block models
def faces_all(texture):
    return {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}


def box(frm, to, texture, **extra):
    element = {"from": frm, "to": to, "faces": faces_all(texture)}
    element.update(extra)
    return element


def collector_model(tier):
    return {
        "textures": {"frame": f"{NS}:block/frame_{tier}", "dish": f"{NS}:block/collector_dish",
                     "particle": f"{NS}:block/frame_{tier}"},
        "elements": [
            box([2, 0, 2], [14, 3, 14], "#frame"),
            box([5, 3, 5], [11, 9, 11], "#frame"),
            box([1, 9, 1], [15, 12, 15], "#frame"),
            {"from": [3, 12, 3], "to": [13, 12.2, 13],
             "faces": {"up": {"uv": [3, 3, 13, 13], "texture": "#dish"}}},
        ],
    }


def tank_model(tier):
    t = "#frame"
    els = []
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        els.append(box([x, 0, z], [x + 2, 16, z + 2], t))
    for y in (0, 14):
        els.append(box([2, y, 0], [14, y + 2, 2], t))
        els.append(box([2, y, 14], [14, y + 2, 16], t))
        els.append(box([0, y, 2], [2, y + 2, 14], t))
        els.append(box([14, y, 2], [16, y + 2, 14], t))
    els.append(box([2, 2, 2], [14, 14, 14], "#glass"))
    return {
        "textures": {"frame": f"{NS}:block/frame_{tier}", "glass": f"{NS}:block/tank_glass",
                     "particle": f"{NS}:block/frame_{tier}"},
        "elements": els,
    }


def orientable(front, side, top):
    return {"parent": "minecraft:block/orientable",
            "textures": {"front": f"{NS}:block/{front}", "side": f"{NS}:block/{side}",
                         "top": f"{NS}:block/{top}", "particle": f"{NS}:block/{side}"}}


def machine_models():
    models = {}
    models["flux_furnace"] = orientable("furnace_front", "machine_stone_side", "machine_stone_side")
    models["flux_grinder"] = orientable("grinder_front", "machine_metal_side", "grinder_top")
    models["alloy_smelter"] = orientable("smelter_front", "machine_metal_side", "smelter_top")

    models["flux_sawmill"] = {
        "textures": {"front": f"{NS}:block/sawmill_front", "top": f"{NS}:block/sawmill_top",
                     "side": f"{NS}:block/machine_wood_side", "blade": f"{NS}:block/machine_metal_side",
                     "particle": f"{NS}:block/machine_wood_side"},
        "elements": [{"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
            "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
            "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#side"}}},
            box([3, 12, 7], [13, 15, 9], "#blade")],
    }

    models["flux_infuser"] = {
        "textures": {"side": f"{NS}:block/infuser_side", "top": f"{NS}:block/infuser_top",
                     "crystal": f"{NS}:block/moon_glow", "particle": f"{NS}:block/infuser_side"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 8, 15], "faces": {
                "north": {"texture": "#side"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
                "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#side"}}},
            box([6, 8, 6], [10, 13, 10], "#crystal"),
        ],
    }

    def lectern(name, front, book_top):
        models[name] = {
            "textures": {"side": f"{NS}:block/lectern_side", "front": f"{NS}:block/{front}",
                         "book": f"{NS}:block/{book_top}", "particle": f"{NS}:block/lectern_side"},
            "elements": [
                box([2, 0, 2], [14, 3, 14], "#side"),
                {"from": [5, 3, 5], "to": [11, 10, 11], "faces": {
                    "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
                    "west": {"texture": "#side"}}},
                {"from": [2, 10, 2], "to": [14, 12, 14],
                 "rotation": {"origin": [8, 10, 8], "axis": "x", "angle": -22.5},
                 "faces": {"up": {"texture": "#book"}, "north": {"texture": "#side"},
                           "south": {"texture": "#side"}, "east": {"texture": "#side"},
                           "west": {"texture": "#side"}, "down": {"texture": "#side"}}},
            ],
        }
    lectern("flux_enchanter", "enchanter_front", "book_open")
    lectern("flux_disenchanter", "disenchanter_front", "book_closed")

    models["plant_grower"] = {
        "textures": {"soil": f"{NS}:block/soil_top", "sun": f"{NS}:block/sun_panel",
                     "moon": f"{NS}:block/moon_panel", "frame": f"{NS}:block/frame_copper",
                     "sprout": f"{NS}:block/sprout", "particle": f"{NS}:block/frame_copper"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 10, 16], "faces": {
                "north": {"texture": "#sun"}, "south": {"texture": "#moon"}, "east": {"texture": "#frame"},
                "west": {"texture": "#frame"}, "up": {"texture": "#soil"}, "down": {"texture": "#frame"}}},
            {"from": [8, 10, 0.5], "to": [8, 15, 15.5], "faces": {"east": {"texture": "#sprout"},
                                                                  "west": {"texture": "#sprout"}}},
            {"from": [0.5, 10, 8], "to": [15.5, 15, 8], "faces": {"north": {"texture": "#sprout"},
                                                                  "south": {"texture": "#sprout"}}},
        ],
    }

    models["chunk_loader"] = {
        "textures": {"stone": f"{NS}:block/obelisk_side", "plate": f"{NS}:block/frame_copper",
                     "ring": f"{NS}:block/loader_ring", "particle": f"{NS}:block/obelisk_side"},
        "elements": [
            box([3, 0, 3], [13, 2, 13], "#plate"),
            box([5, 2, 5], [11, 13, 11], "#stone"),
            box([2, 9, 2], [14, 11, 4], "#ring"),
            box([2, 9, 12], [14, 11, 14], "#ring"),
            box([2, 9, 4], [4, 11, 12], "#ring"),
            box([12, 9, 4], [14, 11, 12], "#ring"),
        ],
    }
    models["pipe_node"] = {"parent": "minecraft:block/cube_all",
                           "textures": {"all": f"{NS}:block/pipe_node"}}
    return models


# ---------------------------------------------------------------- item textures from the concept art
def cut_out(path):
    """Removes the flat grey backdrop of a concept picture; returns an RGBA image."""
    im = Image.open(path).convert("RGB")
    marker = (255, 0, 255)
    work = im.copy()
    w, h = work.size
    for corner in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        ImageDraw.floodfill(work, corner, marker, thresh=34)
    out = Image.new("RGBA", im.size)
    src, dst = im.load(), out.load()
    mk = work.load()
    for y in range(h):
        for x in range(w):
            if mk[x, y] != marker:
                dst[x, y] = src[x, y] + (255,)
    return out


def column_runs(im, min_gap=4):
    alpha = im.getchannel("A")
    w, h = im.size
    cols = [alpha.crop((x, 0, x + 1, h)).getbbox() is not None for x in range(w)]
    runs, start, gap = [], None, 0
    for x, on in enumerate(cols):
        if on:
            if start is None:
                start = x
            gap = 0
            end = x
        elif start is not None:
            gap += 1
            if gap >= min_gap:
                runs.append((start, end))
                start = None
    if start is not None:
        runs.append((start, end))
    return runs


ITEM_SIZE = 32  # item art keeps more of the concept detail at 32 px; blocks stay at 16


def to_item_size(im, n=ITEM_SIZE):
    box_ = im.getbbox()
    im = im.crop(box_)
    side = max(im.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    # eat the light fringe the backdrop removal leaves around the edge
    alpha = square.getchannel("A").filter(ImageFilter.MinFilter(2 * max(2, side // 96) + 1))
    square.putalpha(alpha)
    small = square.resize((n, n), Image.BOX)
    px = small.load()
    for y in range(n):
        for x in range(n):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    rgb = small.convert("RGB").quantize(colors=20, method=Image.MEDIANCUT).convert("RGB")
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    op, sp = out.load(), small.load()
    for y in range(n):
        for x in range(n):
            if sp[x, y][3]:
                op[x, y] = rgb.getpixel((x, y)) + (255,)
    return out


ITEMS = {
    # item id: (concept file, run index or None, handheld)
    "aero_crystal": ("item_aero_crystal", None, False),
    "moon_crystal": ("item_moon_crystal", None, False),
    "fluxsteel_ingot": ("item_fluxsteel_aerogold_ingots", 0, False),
    "aerogold_ingot": ("item_fluxsteel_aerogold_ingots", 1, False),
    "flux_wrench": ("tool_flux_wrench", None, True),
    "flux_configurator": ("tool_flux_configurator", None, False),
    "conduit_tool": ("tool_conduit_tool", None, True),
    "guidebook": ("item_guidebook", None, False),
    "upgrade_speed": ("item_pipe_upgrades", 0, False),
    "upgrade_capacity": ("item_pipe_upgrades", 1, False),
    "upgrade_filter": ("item_pipe_upgrades", 2, False),
    "upgrade_priority": ("item_pipe_upgrades", 3, False),
    "aero_flux_bucket": ("item_aero_flux_bucket", None, False),
    "moon_flux_bucket": ("item_moon_flux_bucket", None, False),
}


def build_items():
    cache = {}
    for item_id, (concept, run, handheld) in ITEMS.items():
        if concept not in cache:
            cache[concept] = cut_out(os.path.join(CONCEPT, concept + ".png"))
        im = cache[concept]
        if run is not None:
            runs = column_runs(im)
            x0, x1 = runs[run]
            piece = Image.new("RGBA", im.size)
            piece.paste(im.crop((x0, 0, x1 + 1, im.height)), (x0, 0))
            im = piece
        to_item_size(im).save(os.path.join(TEX_ITEM, item_id + ".png"))
        write_json(os.path.join(MODEL_ITEM, item_id + ".json"), {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{item_id}"},
        })


# ---------------------------------------------------------------- 3D item models
def shaded(base, light, dark, extra=None):
    """A 16x16 flat-shaded swatch: light top-left edge, dark bottom-right edge, optional marks."""
    base, light, dark = hexc(base), hexc(light), hexc(dark)
    im = tex()
    rect(im, 0, 0, 15, 15, base)
    rect(im, 0, 0, 15, 1, light)
    rect(im, 0, 0, 1, 15, light)
    rect(im, 0, 14, 15, 15, dark)
    rect(im, 14, 0, 15, 15, dark)
    if extra:
        extra(im)
    return im


def chip_face(drawer):
    def paint(im):
        rect(im, 2, 2, 13, 13, hexc("2A2D33"))
        drawer(ImageDraw.Draw(im))
    return shaded("B88A5A", "D8B282", "7A5A36", paint)


def glyph_speed(d):
    d.polygon([(9, 3), (5, 9), (8, 9), (6, 12), (11, 6), (8, 6)], fill=hexc("FFFFFF"))


def glyph_capacity(d):
    for x, y in ((6, 3), (4, 8), (8, 8)):
        d.rectangle((x, y, x + 3, y + 3), fill=hexc("FFFFFF"))


def glyph_filter(d):
    d.polygon([(3, 3), (12, 3), (9, 8), (9, 12), (6, 12), (6, 8)], fill=hexc("FFFFFF"))


def glyph_priority(d):
    d.polygon([(8, 3), (12, 8), (9, 8), (9, 12), (7, 12), (7, 8), (4, 8)], fill=hexc("FFFFFF"))


def cover_texture():
    def paint(im):
        d = ImageDraw.Draw(im)
        d.ellipse((4, 4, 11, 11), fill=AERO)
        d.ellipse((6, 3, 13, 10), fill=hexc("C86F4A"))
        d.ellipse((7, 4, 12, 9), fill=MOON)
    return shaded("C86F4A", "E39A6E", "8E4B32", paint)


def pages_texture():
    def paint(im):
        for y in range(3, 14, 3):
            rect(im, 0, y, 15, y, hexc("D9CFA8"))
    return shaded("EDE4C0", "F7F1D8", "BDB287", paint)


def tool_textures():
    return {
        "tool_steel": shaded("3A3F4A", "5A6070", "22262E"),
        "tool_gem_aero": shaded("FFF2A8", "FFFFFF", "E6B422"),
        "tool_gem_moon": shaded("D6E8FF", "FFFFFF", "8FB0E0"),
        "tool_screen": shaded("1C2630", "2E4A63", "0E141A",
                              lambda im: rect(im, 3, 3, 12, 12, hexc("5C8FB8"))),
        "tool_button": shaded("8A8F96", "B0B5BC", "5A5F68"),
    }


def item_textures():
    out = {
        "crystal_aero": shaded("FFF2A8", "FFFFFF", "E6B422"),
        "crystal_moon": shaded("9A6BD6", "D6E8FF", "5E2FA8",
                               lambda im: rect(im, 5, 5, 10, 10, hexc("D6E8FF"))),
        "ingot_steel": shaded("3A3F4A", "5A6070", "22262E",
                              lambda im: [im.putpixel((x, 7), AERO if x % 2 else MOON) for x in range(2, 14)]),
        "ingot_gold": shaded("E8B930", "FBE27A", "A9801A",
                             lambda im: rect(im, 7, 4, 8, 11, hexc("FFF2A8"))),
        "bucket_iron": shaded("B8BCC0", "D8DADD", "7A7E83"),
        "bucket_rim": shaded("8A8F96", "B0B5BC", "5A5F68"),
        "liquid_aero": shaded("FFF2A8", "FFFFFF", "E6D07A"),
        "liquid_moon": shaded("D6E8FF", "FFFFFF", "9FC0E8"),
        "book_cover": cover_texture(),
        "book_pages": pages_texture(),
        "chip_speed": chip_face(glyph_speed),
        "chip_capacity": chip_face(glyph_capacity),
        "chip_filter": chip_face(glyph_filter),
        "chip_priority": chip_face(glyph_priority),
        "chip_edge": shaded("7A5A36", "B88A5A", "4A3519"),
    }
    out.update(tool_textures())
    return out


def item_box(frm, to, texture, **extra):
    return box(frm, to, texture, **extra)


def item_models():
    """Chunky cuboid models for every item that is not a flat pile (flux dust stays a sprite)."""
    models = {}

    def crystal(name, tex_key):
        models[name] = {"textures": {"c": f"{NS}:item/{tex_key}", "particle": f"{NS}:item/{tex_key}"},
                        "elements": [item_box([6, 1, 6], [10, 3, 10], "#c"),
                                     item_box([4, 3, 4], [12, 11, 12], "#c"),
                                     item_box([6, 11, 6], [10, 14, 10], "#c")]}
    crystal("aero_crystal", "crystal_aero")
    crystal("moon_crystal", "crystal_moon")

    def ingot(name, tex_key):
        models[name] = {"textures": {"i": f"{NS}:item/{tex_key}", "particle": f"{NS}:item/{tex_key}"},
                        "elements": [item_box([2, 0, 5], [14, 4, 11], "#i"),
                                     item_box([3, 4, 6], [13, 6, 10], "#i")]}
    ingot("fluxsteel_ingot", "ingot_steel")
    ingot("aerogold_ingot", "ingot_gold")

    def bucket(name, liquid):
        models[name] = {
            "textures": {"b": f"{NS}:item/bucket_iron", "r": f"{NS}:item/bucket_rim",
                         "l": f"{NS}:item/{liquid}", "particle": f"{NS}:item/bucket_iron"},
            "elements": [item_box([4, 0, 4], [12, 5, 12], "#b"),
                         item_box([3, 5, 3], [13, 11, 13], "#b"),
                         item_box([3, 11, 3], [13, 12, 13], "#r"),
                         item_box([4, 11.4, 4], [12, 11.6, 12], "#l"),
                         item_box([2, 12, 7.5], [14, 13, 8.5], "#r")],
        }
    bucket("aero_flux_bucket", "liquid_aero")
    bucket("moon_flux_bucket", "liquid_moon")

    models["flux_wrench"] = {
        "textures": {"s": f"{NS}:item/tool_steel", "g": f"{NS}:item/tool_gem_aero",
                     "particle": f"{NS}:item/tool_steel"},
        "elements": [item_box([7, 0, 7], [9, 9, 9], "#s"), item_box([5, 9, 6], [11, 12, 10], "#s"),
                     item_box([5, 12, 6], [7, 16, 10], "#s"), item_box([9, 12, 6], [11, 15, 10], "#s"),
                     item_box([7, 9.5, 5.5], [9, 11.5, 6], "#g")],
    }
    models["flux_configurator"] = {
        "textures": {"s": f"{NS}:item/tool_steel", "d": f"{NS}:item/tool_screen",
                     "g": f"{NS}:item/tool_gem_moon", "b": f"{NS}:item/tool_button",
                     "particle": f"{NS}:item/tool_steel"},
        "elements": [item_box([2, 4, 3], [14, 8, 13], "#s"), item_box([4, 8, 5], [10, 8.6, 11], "#d"),
                     item_box([11, 8, 5], [13, 9, 8], "#g"), item_box([11, 8, 9], [13, 8.6, 11], "#b")],
    }
    models["conduit_tool"] = {
        "textures": {"s": f"{NS}:item/tool_steel", "b": f"{NS}:item/tool_button",
                     "particle": f"{NS}:item/tool_steel"},
        "elements": [item_box([5, 0, 6], [7, 9, 10], "#s"), item_box([9, 0, 6], [11, 9, 10], "#s"),
                     item_box([5, 9, 6], [11, 12, 10], "#s"), item_box([4, 12, 6], [7, 15, 10], "#b"),
                     item_box([9, 12, 6], [12, 15, 10], "#b")],
    }
    models["guidebook"] = {
        "textures": {"c": f"{NS}:item/book_cover", "p": f"{NS}:item/book_pages",
                     "particle": f"{NS}:item/book_cover"},
        "elements": [item_box([3, 0, 2], [13, 1, 14], "#c"), item_box([3, 1, 2.5], [12.5, 4, 13.5], "#p"),
                     item_box([3, 4, 2], [13, 5, 14], "#c"), item_box([2, 0, 2], [3, 5, 14], "#c")],
    }
    for kind in ("speed", "capacity", "filter", "priority"):
        face = f"chip_{kind}"
        models[f"upgrade_{kind}"] = {
            "textures": {"f": f"{NS}:item/{face}", "e": f"{NS}:item/chip_edge", "particle": f"{NS}:item/chip_edge"},
            "elements": [{"from": [2, 0, 2], "to": [14, 2, 14], "faces": {
                "up": {"texture": "#f"}, "down": {"texture": "#f"}, "north": {"texture": "#e"},
                "south": {"texture": "#e"}, "east": {"texture": "#e"}, "west": {"texture": "#e"}}}],
        }
    return models


def build_item_models():
    for name, im in item_textures().items():
        im.save(os.path.join(TEX_ITEM, name + ".png"))
    for name, model in item_models().items():
        model["parent"] = "minecraft:block/block"
        model["display"] = ITEM_DISPLAY
        write_json(os.path.join(MODEL_ITEM, name + ".json"), model)


ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 1], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [1, 2, 0], "scale": [0.55, 0.55, 0.55]},
}


# ---------------------------------------------------------------- driver
def facing_state(model):
    return {"variants": {
        "facing=north": {"model": model}, "facing=east": {"model": model, "y": 90},
        "facing=south": {"model": model, "y": 180}, "facing=west": {"model": model, "y": 270}}}


def main():
    for tier in TIERS:
        save_block_tex(f"frame_{tier}", frame_texture(tier))
    for name, make in (
        ("tank_glass", glass_texture), ("collector_dish", dish_texture),
        ("machine_stone_side", stone_side), ("machine_metal_side", dark_metal_side),
        ("machine_wood_side", wood_side), ("lectern_side", lectern_side), ("obelisk_side", obelisk_side),
        ("furnace_front", front_furnace), ("grinder_front", front_grinder), ("grinder_top", top_grinder),
        ("sawmill_front", front_sawmill), ("sawmill_top", top_sawmill),
        ("smelter_front", front_smelter), ("smelter_top", top_smelter),
        ("infuser_side", side_infuser), ("infuser_top", top_infuser),
        ("enchanter_front", lambda: front_enchanter(False)),
        ("disenchanter_front", lambda: front_enchanter(True)),
        ("book_open", lambda: top_book(True)), ("book_closed", lambda: top_book(False)),
        ("soil_top", soil_top), ("sprout", sprout), ("sun_panel", sun_panel), ("moon_panel", moon_panel),
        ("loader_ring", ring_texture), ("pipe_node", node_texture),
        ("moon_glow", lambda: _solid(MOON)),
    ):
        save_block_tex(name, make())
    for name in PIPES:
        save_block_tex(f"pipe_{name}", pipe_texture(name))

    for tier in TIERS:
        write_json(os.path.join(MODEL_BLOCK, f"{tier}_collector.json"), collector_model(tier))
        write_json(os.path.join(STATES, f"{tier}_collector.json"),
                   {"variants": {"": {"model": f"{NS}:block/{tier}_collector"}}})
        write_json(os.path.join(MODEL_ITEM, f"{tier}_collector.json"),
                   {"parent": f"{NS}:block/{tier}_collector"})
    for tier in TIERS[:5]:
        write_json(os.path.join(MODEL_BLOCK, f"{tier}_tank.json"), tank_model(tier))
        write_json(os.path.join(STATES, f"{tier}_tank.json"),
                   {"variants": {"": {"model": f"{NS}:block/{tier}_tank"}}})
        write_json(os.path.join(MODEL_ITEM, f"{tier}_tank.json"), {"parent": f"{NS}:block/{tier}_tank"})

    for name, model in machine_models().items():
        write_json(os.path.join(MODEL_BLOCK, name + ".json"), model)
        write_json(os.path.join(MODEL_ITEM, name + ".json"), {"parent": f"{NS}:block/{name}"})
        if name in ("pipe_node", "chunk_loader"):
            write_json(os.path.join(STATES, name + ".json"),
                       {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        else:
            write_json(os.path.join(STATES, name + ".json"), facing_state(f"{NS}:block/{name}"))

    pipe_models()
    connector_models()
    build_items()  # flat 32 px sprites, kept as inventory-icon art
    build_item_models()  # chunky 3D models; these replace the flat item models written above
    print("done")


def _solid(color):
    im = tex()
    rect(im, 0, 0, 15, 15, color)
    return im


if __name__ == "__main__":
    main()
