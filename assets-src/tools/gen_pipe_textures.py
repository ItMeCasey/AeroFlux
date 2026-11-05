"""Pipe textures for the conduit bundle, sized to the parts so nothing stretches and drawn so nothing rotates.

Every texture is already colored (the model stops tinting) and has its own pixel size, matching the part's
face in world pixels (1 px = 1/16 block):

  part           world face         texture size   texels per px
  arm side       2 wide x L long    16 x 80 (V)    8      L up to 10 px; crop with the UV below
                                    80 x 16 (H)
  arm end        2 x 2              16 x 16        8
  core side      3 x 3              32 x 32 (V/H)  10.7
  core end       3 x 3              32 x 32        10.7
  plug face      3 x 3              16 x 16        5.3    the glyph
  plug side      3 wide x 4 long    48 x 64 (V)    16
                                    64 x 48 (H)

Every size has a factor of 16, so none of them lowers the block atlas's mip level.

V means the pipe's length runs down the texture (v axis); H means along it (u axis). The V and H files are
the same picture turned a quarter, and the pictures are symmetric (same edge on both sides, nothing that
changes along the length), so no face ever needs a rotation or a flip: pick the file by the face.

  pipe axis | up, down | north, south | east, west
  X         | H        | H           | (end)
  Y         | (end)    | V           | V
  Z         | V        | (end)       | H

Arm UV, for an arm of length L px: V files u 0..16, v 0..16*L/10; H files u 0..16*L/10, v 0..16. Plug sides
are 4 px long: use the whole texture. Everything else uses the whole texture.

Names: pipe_<type>_<part>[_tier]; copper has no suffix; ME and Dense ME have no tiers.
Writes assets-src/aeroflux_pipes/textures/block. Run: python gen_pipe_textures.py
"""
import os
import shutil

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

import ui_glyphs
from gen_machines_v3 import TIERS, accent, mix, sfx

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "aeroflux_pipes", "textures", "block")

# the pipe's main color, as in PipeType.java, and the glyph that says what it carries
TYPES = {
    "fluid": (0x8C939A, "fluid", True), "item": (0x4B5057, "items", True), "redstone": (0xD8322C, "redstone", True),
    "energy": (0xE8731A, "energy", True), "gas": (0xE6B422, "gas", True),
    "me": (0x8B4FD8, "me", False), "dense_me": (0x5E2FA8, "dense_me", False),
}
OUTLINE = (12, 13, 16, 255)
NOISE = 0.02            # +-2% brightness in soft blobs, where the old texture flickered texel by texel
ARM_LEN = 10            # longest arm, in px


def rgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


def edge_colors(tier):
    """Outline, shadow, base, light of the tier metal; the dark metals are lifted so they show on dark pipes."""
    o, d, b, l, gl = accent(tier)
    if tier in ("netherite", "fluxium"):
        b, l, d = mix(b, 1.6), mix(l, 1.7), mix(d, 1.4)
    return o, d, b, l


def field(w, h, base, seed):
    """The type color with a very soft noise and a faint shading toward the middle, as an image."""
    rng = np.random.default_rng(seed)
    n = gaussian_filter(rng.normal(size=(h, w)), sigma=2.2 * w / 16, mode="wrap")
    n /= n.std() or 1.0
    xs = np.abs(np.linspace(-1, 1, w))[None, :]
    shade = 1.04 - 0.07 * xs                                  # a hint of roundness across the width
    arr = np.array(base[:3], dtype=float)[None, None, :] * (shade * (1 + NOISE * n))[:, :, None]
    im = Image.new("RGBA", (w, h))
    px = im.load()
    arr = np.clip(arr, 0, 255)
    for y in range(h):
        for x in range(w):
            px[x, y] = tuple(int(v) for v in arr[y, x]) + (255,)
    return im


def edged_v(w, h, base, tier, seed, edge):
    """A V texture: tier-metal edges down both long sides, identical left and right."""
    o, d, b, l = edge_colors(tier)
    im = field(w, h, base, seed)
    px = im.load()
    for y in range(h):
        for i in range(edge):
            col = OUTLINE if i == 0 else (l if i == 1 else b)
            px[i, y] = col
            px[w - 1 - i, y] = col
        px[edge, y] = mix(px[edge, y], 0.85)                  # a soft shadow where metal meets pipe
        px[w - 1 - edge, y] = mix(px[w - 1 - edge, y], 0.85)
    return im


def framed(size, base, tier, seed, edge):
    """An end face: the tier-metal frame all round, type color inside. Symmetric, so it never needs turning."""
    o, d, b, l = edge_colors(tier)
    im = field(size, size, base, seed + 50)
    px = im.load()
    for i in range(edge):
        col = OUTLINE if i == 0 else (l if i == 1 else b)
        for k in range(i, size - i):
            for x, y in ((k, i), (k, size - 1 - i), (i, k), (size - 1 - i, k)):
                px[x, y] = col
    for k in range(edge, size - edge):
        for x, y in ((k, edge), (k, size - 1 - edge), (edge, k), (size - 1 - edge, k)):
            px[x, y] = mix(px[x, y], 0.85)
    return im


def turned(im):
    """The H version of a V picture."""
    return im.rotate(90, expand=True)


def plug_face(base, glyph, seed, size=16):
    im = field(size, size, base, seed + 90)
    px = im.load()
    lum = 0.299 * base[0] + 0.587 * base[1] + 0.114 * base[2]
    ink = mix(base, 0.2) if lum > 140 else tuple(int(v + (255 - v) * 0.9) for v in base[:3]) + (255,)
    for k in range(size):
        for x, y in ((k, 0), (k, size - 1), (0, k), (size - 1, k)):
            px[x, y] = OUTLINE
    for k in range(1, size - 1):
        px[k, 1], px[1, k] = mix(px[k, 1], 1.12), mix(px[1, k], 1.12)
        px[k, size - 2], px[size - 2, k] = mix(px[k, size - 2], 0.86), mix(px[size - 2, k], 0.86)
    g = ui_glyphs.GLYPHS[glyph]()
    ox = (size - 13) // 2
    for x, y in g.on:
        px[x + ox, y + ox] = ink
    return im


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT)
    count = 0

    def save(im, name):
        nonlocal count
        im.save(os.path.join(OUT, name + ".png"))
        count += 1

    for name, (color, glyph, tiered) in TYPES.items():
        base = rgb(color)
        seed = sum(map(ord, name))
        save(plug_face(base, glyph, seed), f"pipe_{name}_plug")
        for tier in (TIERS if tiered else ["iron"]):
            s = sfx(tier) if tiered else ""
            arm = edged_v(16, 8 * ARM_LEN, base, tier, seed, 3)
            save(arm, f"pipe_{name}_arm_v{s}")
            save(turned(arm), f"pipe_{name}_arm_h{s}")
            save(framed(16, base, tier, seed, 3), f"pipe_{name}_arm_end{s}")
            core = edged_v(32, 32, base, tier, seed + 7, 4)
            save(core, f"pipe_{name}_core_v{s}")
            save(turned(core), f"pipe_{name}_core_h{s}")
            save(framed(32, base, tier, seed + 7, 4), f"pipe_{name}_core_end{s}")
            plug = edged_v(48, 64, base, tier, seed + 13, 5)
            save(plug, f"pipe_{name}_plug_v{s}")
            save(turned(plug), f"pipe_{name}_plug_h{s}")
    print("pipe textures:", count)


if __name__ == "__main__":
    main()
