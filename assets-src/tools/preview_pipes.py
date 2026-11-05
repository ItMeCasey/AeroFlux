"""Previews for the aeroflux_pipes textures: a flat texture sheet and 3D scenes that use the face table from
gen_pipe_textures.py (own renderer, no Minecraft lighting). Run: python preview_pipes.py"""
import json
import os

from PIL import Image, ImageDraw

import preview as p
from gen_machines_v3 import TIERS, sfx

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "aeroflux_pipes")
TEX = os.path.join(ROOT, "textures", "block")
NODE = os.path.join(HERE, "..", "..", "versions", "mc26.1.2", "neoforge", "src", "main", "resources", "assets",
                    "aeroflux", "textures", "block", "pipe_node.png")
p.S = 22
p.CELL = 560
SCENES = {}


def load(kind, name):
    return SCENES[name]


def tex_image(ref):
    if ref == "node":
        return Image.open(NODE).convert("RGBA")
    return Image.open(os.path.join(TEX, ref + ".png")).convert("RGBA")


p.load_model = load
p.tex_image = tex_image
FULL = [0, 0, 16, 16]


def face(tex, uv=FULL):
    return {"texture": "#" + tex, "uv": uv}


def box(frm, to, **faces):
    return {"from": frm, "to": to, "faces": faces}


def pipe_z(cx, cy, z0, z1, key, tier_s, textures, length=10):
    """A pipe along Z: V on up/down, H on east/west, end on the south face."""
    t = f"{key}_arm"
    for suffix, tname in (("v", "av"), ("h", "ah"), ("end", "ae")):
        textures[f"{key}{tname}"] = f"pipe_{key}_arm_{suffix}{tier_s}"
    return box([cx - 1, cy - 1, z0], [cx + 1, cy + 1, z1], up=face(f"{key}av"), down=face(f"{key}av"),
               east=face(f"{key}ah"), west=face(f"{key}ah"), south=face(f"{key}ae"), north=face(f"{key}ae"))


def plug_z(cx, cy, key, tier_s, textures):
    textures[f"{key}pf"] = f"pipe_{key}_plug"
    textures[f"{key}pv"] = f"pipe_{key}_plug_v{tier_s}"
    textures[f"{key}ph"] = f"pipe_{key}_plug_h{tier_s}"
    return box([cx - 1.5, cy - 1.5, 0], [cx + 1.5, cy + 1.5, 4], up=face(f"{key}pv"), down=face(f"{key}pv"),
               east=face(f"{key}ph"), west=face(f"{key}ph"), south=face(f"{key}pf"), north=face(f"{key}pf"))


def machine_face(tier):
    ts = sfx(tier)
    textures = {"node": "node"}
    els = [box([2.5, 2.5, 0], [13.5, 13.5, 0.5], **{k: face("node") for k in ("north", "south", "east", "west", "up", "down")}),
           box([4.5, 4.5, 0.5], [11.5, 11.5, 1], **{k: face("node") for k in ("north", "south", "east", "west", "up", "down")})]
    keys = (("fluid", 5, 9.5), ("item", 8, 9.5), ("redstone", 11, 9.5),
            ("energy", 5, 6.5), ("gas", 8, 6.5), ("me", 11, 6.5))
    for key, cx, cy in keys:
        s = "" if key == "me" else ts
        els.append(plug_z(cx, cy, key, s, textures))
        els.append(pipe_z(cx, cy, 4, 14, key, s, textures))
    SCENES[f"machine_{tier}"] = {"textures": textures, "elements": els}
    return f"machine_{tier}"


def axes_scene():
    """One pipe along each axis: the faces must read the same way along the length on all three."""
    textures = {}
    els = []
    for key, tier_s in (("fluid", ""), ("energy", ""), ("item", "")):
        for suffix, tname in (("v", "av"), ("h", "ah"), ("end", "ae")):
            textures[f"{key}{tname}"] = f"pipe_{key}_arm_{suffix}{tier_s}"
    els.append(box([0, 12, 6], [10, 14, 8], up=face("fluidah"), down=face("fluidah"), north=face("fluidah"),
                   south=face("fluidah"), east=face("fluidae"), west=face("fluidae")))               # along X
    els.append(box([12, 0, 6], [14, 10, 8], north=face("energyav"), south=face("energyav"), east=face("energyav"),
                   west=face("energyav"), up=face("energyae"), down=face("energyae")))               # along Y
    els.append(box([3, 0, 0], [5, 2, 10], up=face("itemav"), down=face("itemav"), east=face("itemah"),
                   west=face("itemah"), south=face("itemae"), north=face("itemae")))                 # along Z
    SCENES["axes"] = {"textures": textures, "elements": els}
    return "axes"


def scene_sheet(names, out):
    tiles = []
    for n in names:
        views = [p.render("block", n, v) for v in ("nw", "se")]
        views = [v.crop(v.getbbox()) for v in views]
        w = sum(v.width for v in views) + 30
        h = max(v.height for v in views) + 30
        tile = Image.new("RGBA", (w, h), (88, 90, 94, 255))
        x = 10
        for v in views:
            tile.alpha_composite(v, (x, 24))
            x += v.width + 10
        ImageDraw.Draw(tile).text((8, 6), n, fill=(255, 255, 255, 255))
        tiles.append(tile)
    cols = 2
    rows = [tiles[i:i + cols] for i in range(0, len(tiles), cols)]
    W = max(sum(t.width for t in r) for r in rows)
    H = sum(max(t.height for t in r) for r in rows)
    sheet = Image.new("RGBA", (W, H), (88, 90, 94, 255))
    y = 0
    for r in rows:
        x = 0
        for t in r:
            sheet.alpha_composite(t, (x, y))
            x += t.width
        y += max(t.height for t in r)
    sheet.convert("RGB").save(os.path.join(HERE, "..", out))


def texture_sheet(out):
    types = ["fluid", "item", "redstone", "energy", "gas", "me", "dense_me"]
    parts = ["arm_v", "arm_end", "core_v", "core_end", "plug", "plug_v"]
    scale = {"arm_v": 3, "arm_end": 8, "core_v": 5, "core_end": 5, "plug": 6, "plug_v": 5}
    colw = {"arm_v": 52, "arm_end": 134, "core_v": 126, "core_end": 126, "plug": 112, "plug_v": 96}
    W = sum(colw.values()) + 12
    sheet = Image.new("RGBA", (W, 7 * 258 + 20), (88, 90, 94, 255))
    d = ImageDraw.Draw(sheet)
    for r, t in enumerate(types):
        x = 8
        for part in parts:
            name = f"pipe_{t}_{part}"
            im = Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")
            im = im.resize((im.width * scale[part], im.height * scale[part]), Image.NEAREST)
            sheet.alpha_composite(im, (x, 10 + r * 258 + 10))
            x += colw[part]
        d.text((8, 10 + r * 258), t, fill=(255, 255, 255, 255))
    sheet.convert("RGB").save(os.path.join(HERE, "..", out))


def tier_sheet(out):
    types = ["fluid", "item", "energy"]
    sheet = Image.new("RGBA", (6 * 360 + 10, 3 * 250 + 10), (88, 90, 94, 255))
    for r, t in enumerate(types):
        for c, tier in enumerate(TIERS):
            s = sfx(tier)
            x, y = 10 + c * 360, 10 + r * 250
            arm = Image.open(os.path.join(TEX, f"pipe_{t}_arm_v{s}.png")).convert("RGBA")
            sheet.alpha_composite(arm.resize((arm.width * 3, arm.height * 3), Image.NEAREST), (x, y))
            end = Image.open(os.path.join(TEX, f"pipe_{t}_arm_end{s}.png")).convert("RGBA")
            sheet.alpha_composite(end.resize((end.width * 6, end.height * 6), Image.NEAREST), (x + 60, y))
            core = Image.open(os.path.join(TEX, f"pipe_{t}_core_end{s}.png")).convert("RGBA")
            sheet.alpha_composite(core.resize((core.width * 5, core.height * 5), Image.NEAREST), (x + 170, y))
            ImageDraw.Draw(sheet).text((x + 60, y + 105), f"{t} {tier}", fill=(255, 255, 255, 255))
    sheet.convert("RGB").save(os.path.join(HERE, "..", out))


if __name__ == "__main__":
    names = [machine_face(t) for t in ("copper", "diamond", "fluxium")] + [axes_scene()]
    scene_sheet(names, "preview_pipes_scenes.png")
    texture_sheet("preview_pipes_textures.png")
    tier_sheet("preview_pipes_tiers.png")
    print("ok")
