"""Tier variants of the shared machine textures, plus the sawdust item.

Each machine texture is rebuilt with the copper trim swapped for the tier's frame color,
so a machine reads as its tier at a glance. Copper stays the original art."""
import os
from PIL import Image

import gen_assets as g

TRIM_SWAPS = {
    # texture -> the corner/stripe color to replace with the tier color
    "machine_stone_side": "C86F4A", "machine_metal_side": "C86F4A", "machine_wood_side": "3A3D42",
    "furnace_front": "C86F4A", "grinder_front": "C86F4A", "grinder_top": "C86F4A",
    "sawmill_front": "3A3D42", "sawmill_top": "3A3D42",
    "smelter_front": "C86F4A", "smelter_top": "C86F4A",
    "infuser_side": "C86F4A", "infuser_top": "C86F4A",
    "lectern_side": "9A6BD6", "enchanter_front": "9A6BD6", "disenchanter_front": "9A6BD6",
}
MAKERS = {
    "machine_stone_side": g.stone_side, "machine_metal_side": g.dark_metal_side, "machine_wood_side": g.wood_side,
    "furnace_front": g.front_furnace, "grinder_front": g.front_grinder, "grinder_top": g.top_grinder,
    "sawmill_front": g.front_sawmill, "sawmill_top": g.top_sawmill,
    "smelter_front": g.front_smelter, "smelter_top": g.top_smelter,
    "infuser_side": g.side_infuser, "infuser_top": g.top_infuser,
    "lectern_side": g.lectern_side, "enchanter_front": lambda: g.front_enchanter(False),
    "disenchanter_front": lambda: g.front_enchanter(True),
}
CORNERS = ((0, 0), (13, 0), (0, 13), (13, 13))


def recolor(im, old, new):
    old = g.hexc(old)
    for x in range(16):
        for y in range(16):
            if im.getpixel((x, y)) == old:
                im.putpixel((x, y), new)


def tier_variant(name, tier):
    base, light, dark = (g.hexc(c) for c in g.MATERIALS[tier])
    im = MAKERS[name]()
    recolor(im, TRIM_SWAPS[name], base)
    # a bright corner block and a glint make dark tiers (netherite, fluxium) still read
    for x0, y0 in CORNERS:
        if tier in ("netherite", "fluxium"):
            g.rect(im, x0, y0, x0 + 2, y0 + 2, light)
            if tier == "fluxium":
                im.putpixel((x0 + 1, y0 + 1), g.AERO if (x0 + y0) % 26 == 0 else g.MOON)
        im.putpixel((x0, y0), light)
        if tier == "gold":
            im.putpixel((x0 + 1, y0 + 1), g.hexc("9A6BD6"))
    return im


SAWDUST_MAP = (
    "................",
    "................",
    "................",
    "................",
    "................",
    "......h.........",
    ".....hlm..h.....",
    "....hlhlm.lm....",
    "...hllmlhmlm....",
    ".h.lmlhlmmdm.l..",
    "..hlmmlmdmmdmd..",
    ".lhmlmmdmmdxmdm.",
    ".mlmmdmdmdmxdxd.",
    "..mdmdxdmdxxdx..",
    "...dxdxxdxx.xd..",
    "................",
)
SAWDUST_COLORS = {"h": "F3E2B0", "l": "DDBF84", "m": "C4A066", "d": "9A7B48", "x": "74582F"}


def sawdust():
    """A loose heap in the style of vanilla dusts (sugar, redstone): lumpy, no outline."""
    im = g.tex()
    for y, row in enumerate(SAWDUST_MAP):
        assert len(row) == 16
        for x, c in enumerate(row):
            if c in SAWDUST_COLORS:
                im.putpixel((x, y), g.hexc(SAWDUST_COLORS[c]))
    return im


def main():
    for tier in g.TIERS[1:]:
        for name in TRIM_SWAPS:
            if name in MAKERS:
                g.save_block_tex(f"{name}_{tier}", tier_variant(name, tier))
    sawdust().save(os.path.join(g.TEX_ITEM, "sawdust.png"))


if __name__ == "__main__":
    main()
