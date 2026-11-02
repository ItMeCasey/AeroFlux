"""Isometric preview sheets for the v2 machine models (two views each). Own renderer, so treat
it as a layout check, not as how the game will look. Run: python preview_machines.py"""
import json
import os
import sys

import preview as p

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "aeroflux_v2")
p.ROOT = ROOT
p.S = 10
p.CELL = 640
MOD = os.path.join(ROOT, "models", "block")


def load(kind, name):
    child = json.load(open(os.path.join(MOD, name + ".json"), encoding="utf-8"))
    parent = json.load(open(os.path.join(MOD, child["parent"].split("/")[-1] + ".json"), encoding="utf-8"))
    return {"textures": child["textures"], "elements": parent["elements"]}


p.load_model = load

MACHINES = ["flux_furnace", "flux_grinder", "alloy_smelter", "flux_sawmill",
            "flux_infuser", "flux_enchanter", "flux_disenchanter", "plant_grower"]


def tiers_sheet():
    names = [f"{m}{s}" for m in ("flux_furnace", "flux_enchanter") for s in ("", "_gold", "_diamond", "_netherite", "_fluxium")]
    p.sheet("block", names, "preview_machines_tiers.png", 2)


if __name__ == "__main__":
    p.sheet("block", MACHINES, "preview_machines.png", 2)
    tiers_sheet()
    print("ok")


def tight_sheet(names, out_name, per_row=2, view_pair=("nw", "se"), pad=10):
    """Each machine rendered from two sides, cropped to its content, laid out in rows."""
    from PIL import Image, ImageDraw
    tiles = []
    for n in names:
        views = []
        for v in view_pair:
            im = p.render("block", n, v)
            views.append(im.crop(im.getbbox()))
        w = sum(v.width for v in views) + pad * (len(views) + 1)
        h = max(v.height for v in views) + 26
        tile = Image.new("RGBA", (w, h), (88, 90, 94, 255))
        x = pad
        for v in views:
            tile.alpha_composite(v, (x, 22))
            x += v.width + pad
        ImageDraw.Draw(tile).text((6, 4), n, fill=(255, 255, 255, 255))
        tiles.append(tile)
    rows = [tiles[i:i + per_row] for i in range(0, len(tiles), per_row)]
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
    sheet.convert("RGB").save(os.path.join(HERE, "..", out_name))
    return sheet.size


if __name__ == "__main__":
    print(tight_sheet(MACHINES[:4], "preview_machines_a.png"))
    print(tight_sheet(MACHINES[4:], "preview_machines_b.png"))
    print(tight_sheet([f"flux_furnace{s}" for s in ("", "_iron", "_gold", "_diamond", "_netherite", "_fluxium")],
                      "preview_tiers_furnace.png", 3, ("nw",)))
