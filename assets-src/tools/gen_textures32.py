"""Detailed 32x32 block textures. Output goes to assets-src/aeroflux32/textures/block so the
16x16 set stays untouched until the new look is approved. UV coordinates in the models are in
0-16 space, so the same models work with these textures."""
import os
import numpy as np
from PIL import Image, ImageDraw

import gen_assets as g

S = 32
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "aeroflux32", "textures", "block")
os.makedirs(OUT, exist_ok=True)


# ------------------------------------------------------------------ toolkit
def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def vnoise(seed, scale):
    rng = np.random.default_rng(seed)
    n = S // scale + 2
    grid = (rng.random((n, n)) * 255).astype("uint8")
    im = Image.fromarray(grid).resize((n * scale, n * scale), Image.BICUBIC)
    return np.asarray(im, float)[:S, :S] / 255


def fbm(seed):
    return 0.5 * vnoise(seed, 8) + 0.3 * vnoise(seed + 1, 4) + 0.2 * vnoise(seed + 2, 2)


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"), "RGB").convert("RGBA")


def arr_of(im):
    return np.asarray(im.convert("RGB"), float).copy()


def shade(c, f):
    return tuple(int(max(0, min(255, v * f))) for v in c[:3])


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def bevel(arr, x0, y0, x1, y1, w=1, lift=0.22, drop=0.3):
    """Lights the top and left edges and darkens the bottom and right edges of a box (inclusive)."""
    arr[y0:y0 + w, x0:x1 + 1] *= 1 + lift
    arr[y0:y1 + 1, x0:x0 + w] *= 1 + lift
    arr[y1 - w + 1:y1 + 1, x0:x1 + 1] *= 1 - drop
    arr[y0:y1 + 1, x1 - w + 1:x1 + 1] *= 1 - drop


def rivet(arr, x, y, base):
    arr[y, x] = np.array(shade(base, 1.45))
    arr[y, x + 1] = np.array(shade(base, 1.0))
    arr[y + 1, x] = np.array(shade(base, 0.8))
    arr[y + 1, x + 1] = np.array(shade(base, 0.55))


def mask_rect(box):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).rectangle(box, fill=255)
    return np.asarray(m) > 0


def mask_ellipse(box):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse(box, fill=255)
    return np.asarray(m) > 0


def radial(arr, mask, cx, cy, rad, c_in, c_out, power=1.0):
    ys, xs = np.mgrid[0:S, 0:S]
    d = np.clip(np.hypot(xs - cx, ys - cy) / rad, 0, 1) ** power
    for i in range(3):
        arr[..., i] = np.where(mask, c_in[i] + (c_out[i] - c_in[i]) * d, arr[..., i])


def vgrad(arr, mask, y0, y1, top, bottom):
    ys = np.arange(S)[:, None] * np.ones((1, S))
    t = np.clip((ys - y0) / max(1, (y1 - y0)), 0, 1)
    for i in range(3):
        arr[..., i] = np.where(mask, top[i] + (bottom[i] - top[i]) * t, arr[..., i])


def cavity(arr, box, floor=(24, 26, 30)):
    """A recessed opening: dark floor, shadow on the top and left, lit lip on the bottom and right."""
    x0, y0, x1, y1 = box
    arr[y0:y1 + 1, x0:x1 + 1] = np.array(floor)
    arr[y0:y0 + 2, x0:x1 + 1] = np.array(shade(floor, 0.5))
    arr[y0:y1 + 1, x0:x0 + 2] = np.array(shade(floor, 0.6))
    if y1 + 1 < S:
        arr[y1 + 1, x0:x1 + 1] = arr[y1 + 1, x0:x1 + 1] * 1.35
    if x1 + 1 < S:
        arr[y0:y1 + 1, x1 + 1] = arr[y0:y1 + 1, x1 + 1] * 1.25


# ------------------------------------------------------------------ materials
def stone_base(tier, seed=11):
    rng = np.random.default_rng(seed)
    base = rgb("55595F")
    arr = np.zeros((S, S, 3))
    for r in range(4):
        y0 = r * 8
        off = 0 if r % 2 == 0 else 8
        for xs in range(off - 16, S, 16):
            x0, x1 = max(xs, 0), min(xs + 16, S) - 1
            arr[y0:y0 + 8, x0:x1 + 1] = base * (0.88 + 0.24 * rng.random())
            bevel(arr, x0, y0, x1, y0 + 7, 1, 0.16, 0.0)
            arr[y0 + 7, x0:x1 + 1] = base * 0.5       # mortar below
            arr[y0:y0 + 8, x1] = base * 0.5            # mortar to the right
    arr *= (0.84 + 0.32 * fbm(seed))[..., None]
    for _ in range(40):
        x, y = rng.integers(0, S, 2)
        arr[y, x] *= 0.8 + 0.4 * rng.random()
    corner_plates(arr, tier)
    return arr


def metal_base(tier, seed=21):
    rng = np.random.default_rng(seed)
    base = rgb("3E4249")
    streak = rng.random(S)[:, None] * 0.10 + 0.95
    arr = np.ones((S, S, 3)) * base * streak[..., None] * (0.92 + 0.16 * vnoise(seed, 4))[..., None]
    for (x0, y0) in ((0, 0), (16, 0), (0, 16), (16, 16)):
        bevel(arr, x0, y0, x0 + 15, y0 + 15, 1, 0.20, 0.30)
        for rx, ry in ((x0 + 3, y0 + 3), (x0 + 11, y0 + 3), (x0 + 3, y0 + 11), (x0 + 11, y0 + 11)):
            rivet(arr, rx, ry, (90, 95, 104))
    corner_plates(arr, tier)
    return arr


def wood_base(tier, seed=31):
    rng = np.random.default_rng(seed)
    arr = np.zeros((S, S, 3))
    base = rgb("6B4A2B")
    xs = np.arange(S)[None, :]
    for r in range(4):
        y0 = r * 8
        tint = 0.86 + 0.28 * rng.random()
        warp = vnoise(seed + r, 8)[y0:y0 + 8]
        grain = 0.5 + 0.5 * np.sin(xs * 0.28 + warp * 9 + r * 2.1)
        for y in range(8):
            arr[y0 + y] = base * tint * (0.82 + 0.28 * grain[y % 8 if grain.shape[0] > 1 else 0])[:, None]
        for y in range(8):
            row = 0.5 + 0.5 * np.sin(y * 1.9 + r)
            arr[y0 + y] *= 0.96 + 0.08 * row
        arr[y0] *= 1.18
        arr[y0 + 7] *= 0.55
        kx = int(rng.integers(4, 28))
        arr[y0 + 3:y0 + 5, kx:kx + 2] = base * 0.5     # knot
        arr[y0 + 3, kx] = base * 0.7
        for nx in (2, 29):
            arr[y0 + 3, nx] = np.array((150, 150, 158))
            arr[y0 + 4, nx] = np.array((60, 60, 66))
    corner_plates(arr, tier)
    return arr


def corner_plates(arr, tier):
    base, light, dark = (rgb(c) for c in g.MATERIALS[tier])
    gloss = {"netherite": 1.5, "fluxium": 1.4}.get(tier, 1.0)
    for x0, y0 in ((0, 0), (25, 0), (0, 25), (25, 25)):
        arr[y0:y0 + 7, x0:x0 + 7] = np.tile(base * gloss, (7, 7, 1))[:7, :7]
        bevel(arr, x0, y0, x0 + 6, y0 + 6, 1, 0.35, 0.35)
        arr[y0 + 2:y0 + 5, x0 + 2:x0 + 5] = light * (0.8 if gloss > 1 else 1.0) * 0.9
        cx, cy = x0 + 3, y0 + 3
        arr[cy, cx] = (np.array((154, 107, 214)) if tier == "gold"
                       else np.array((255, 242, 168)) if tier == "fluxium" and x0 == y0
                       else np.array((214, 232, 255)) if tier == "fluxium"
                       else dark * 0.8)


# ------------------------------------------------------------------ shared drawing
AERO = (255, 242, 168)
MOON = (214, 232, 255)


def gear(d, cx, cy, r_out, r_in, teeth, body, edge, hub):
    import math
    pts = []
    for i in range(teeth * 4):
        ang = math.tau * i / (teeth * 4)
        r = r_out if (i % 4) in (0, 1) else r_in
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    d.polygon(pts, fill=body, outline=edge)
    d.ellipse((cx - r_in + 3, cy - r_in + 3, cx + r_in - 3, cy + r_in - 3), outline=shade(body, 0.7))
    d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=hub, outline=shade(hub, 0.5))
    d.point((cx - 1, cy - 1), fill=shade(hub, 1.6))


# ------------------------------------------------------------------ machine fronts and tops
def front_furnace(tier):
    arr = stone_base(tier)
    cavity(arr, (7, 13, 24, 25))
    m = mask_rect((8, 15, 23, 24))
    radial(arr, m, 15.5, 24, 12, (255, 236, 150), (190, 70, 10), 0.8)
    rng = np.random.default_rng(5)
    for x in range(9, 23):
        h = int(rng.integers(3, 8))
        for k in range(h):
            t = k / h
            arr[24 - k, x] = np.array(lerp((255, 245, 190), (240, 150, 30), t))
    for x in range(8, 24, 3):                    # grate bars across the opening
        arr[15:25, x] = np.array((34, 36, 40))
    arr[12, 7:25] = np.array((120, 124, 132))    # iron lintel
    arr[11, 7:25] = np.array((80, 84, 92))
    for x in range(9, 23, 4):                    # vent slits above
        arr[6:8, x:x + 2] = np.array((26, 28, 32))
    return to_img(arr)


def front_grinder(tier):
    arr = metal_base(tier)
    cavity(arr, (6, 6, 25, 25), (20, 22, 26))
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    gear(d, 15.5, 15.5, 8.5, 6.5, 8, (255, 200, 87), (150, 100, 20), (140, 90, 30))
    d.point((9, 9), fill=(255, 235, 170))
    return im


def top_grinder(tier):
    arr = metal_base(tier)
    cavity(arr, (6, 6, 25, 25), (20, 22, 26))
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    for x0 in (8, 16):
        d.rectangle((x0, 8, x0 + 6, 23), fill=(255, 200, 87), outline=(150, 100, 20))
        for y in range(9, 23, 3):
            d.line((x0 + 1, y, x0 + 5, y), fill=(150, 100, 20))
        d.line((x0 + 1, 9, x0 + 1, 22), fill=(255, 236, 170))
    return im


def front_sawmill(tier):
    arr = wood_base(tier)
    cavity(arr, (5, 10, 26, 22), (22, 20, 18))
    m = mask_rect((6, 12, 25, 21))
    vgrad(arr, m, 12, 21, (255, 240, 170), (210, 160, 60))
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    gear(d, 15.5, 20, 7.5, 6.5, 12, (170, 176, 186), (60, 64, 72), (96, 100, 110))
    return im


def top_sawmill(tier):
    arr = wood_base(tier)
    cavity(arr, (3, 13, 28, 18), (22, 20, 18))
    arr[15:17, 4:28] = np.array((210, 214, 222))
    arr[15, 4:28] = np.array((255, 255, 255))
    arr[16, 4:28] = np.array((130, 136, 148))
    return to_img(arr)


def front_smelter(tier):
    arr = metal_base(tier)
    cavity(arr, (6, 7, 25, 26), (20, 22, 26))
    m = mask_ellipse((8, 9, 23, 24))
    radial(arr, m, 15.5, 16.5, 8.5, (255, 244, 170), (200, 100, 20), 1.0)
    base = rgb(g.MATERIALS[tier][0])
    for y in (13, 14, 29):
        arr[y, 0:32] = base
        arr[y + 1 if y < 29 else y - 1, 0:32] = base * 0.6
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    d.ellipse((8, 9, 23, 24), outline=(60, 62, 68))
    d.arc((10, 11, 21, 22), 200, 300, fill=(255, 255, 230))
    return im


def top_smelter(tier):
    arr = metal_base(tier)
    base = rgb(g.MATERIALS[tier][0])
    for x in (3, 12, 21):
        cavity(arr, (x, 11, x + 7, 20), (18, 20, 24))
        arr[12:20, x + 1:x + 7] = base * 0.45
        arr[11:13, x:x + 8] = base * 1.1
        arr[13:19, x + 2:x + 6] = np.array((255, 214, 120))
    return to_img(arr)


def side_infuser(tier):
    arr = stone_base(tier)
    cavity(arr, (9, 9, 22, 22), (18, 22, 34))
    m = mask_ellipse((11, 11, 20, 20))
    radial(arr, m, 15.5, 15.5, 6, MOON, (60, 100, 190), 1.1)
    m2 = mask_ellipse((13, 10, 22, 19))
    radial(arr, m2, 17, 14, 6, (18, 22, 34), (18, 22, 34))
    return to_img(arr)


def top_infuser(tier):
    arr = stone_base(tier)
    m = mask_ellipse((4, 4, 27, 27))
    radial(arr, m, 15.5, 15.5, 12, (26, 28, 34), (26, 28, 34))
    m2 = mask_ellipse((7, 7, 24, 24))
    radial(arr, m2, 15.5, 15.5, 9, (255, 255, 255), (70, 120, 210), 0.8)
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    d.ellipse((4, 4, 27, 27), outline=(100, 105, 114))
    d.ellipse((6, 6, 25, 25), outline=(20, 22, 28))
    for ang_pts in ((15, 3), (15, 28), (3, 15), (28, 15)):
        d.point(ang_pts, fill=MOON)
    return im


def front_enchanter(tier, chained):
    arr = lectern_base(tier=tier)
    cavity(arr, (8, 8, 23, 23), (18, 16, 30))
    m = mask_rect((10, 10, 21, 21))
    if chained:
        radial(arr, m, 15.5, 15.5, 8, (70, 72, 80), (28, 30, 36))
    else:
        radial(arr, m, 15.5, 15.5, 8, (150, 190, 255), (40, 70, 200))
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    if chained:
        for i in range(-1, 4):
            x = 4 + i * 8
            d.rectangle((x, 14, x + 5, 17), outline=(150, 154, 164))
        d.line((8, 8, 23, 23), fill=(120, 124, 134))
        d.line((23, 8, 8, 23), fill=(120, 124, 134))
    else:
        d.polygon([(15, 12), (19, 15), (15, 19), (11, 15)], fill=(235, 245, 255), outline=(90, 130, 255))
        d.point((14, 14), fill=(255, 255, 255))
    return im


def lectern_base(seed=41, tier="copper"):
    arr = wood_base(tier, seed)
    arr *= np.array([0.75, 0.72, 0.9])
    if tier == "copper":
        for x0, y0 in ((0, 0), (25, 0), (0, 25), (25, 25)):
            arr[y0:y0 + 7, x0:x0 + 7] = np.array((154, 107, 214))
            bevel(arr, x0, y0, x0 + 6, y0 + 6, 1, 0.35, 0.4)
            arr[y0 + 3, x0 + 3] = np.array((235, 215, 255))
    return arr


def lectern_side(tier="copper"):
    return to_img(lectern_base(tier=tier))


def top_book(open_book):
    arr = lectern_base()
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    if open_book:
        d.rectangle((3, 7, 28, 25), fill=(232, 221, 181), outline=(120, 90, 60))
        d.line((15, 7, 15, 25), fill=(107, 74, 43))
        d.line((16, 7, 16, 25), fill=(160, 140, 100))
        for y in range(10, 23, 3):
            d.line((5, y, 13, y), fill=(154, 107, 214))
            d.line((18, y, 26, y), fill=(154, 107, 214))
    else:
        d.rectangle((3, 7, 28, 25), fill=(38, 40, 46), outline=(20, 22, 26))
        d.rectangle((4, 8, 27, 24), outline=(107, 111, 118))
        d.line((15, 7, 15, 25), fill=(107, 111, 118))
        d.rectangle((13, 14, 18, 17), fill=(154, 107, 214))
    return im


def obelisk_side(tier="copper"):
    rng = np.random.default_rng(51)
    base = rgb("3A3D42")
    arr = np.ones((S, S, 3)) * base * (0.82 + 0.34 * fbm(51))[..., None]
    for x0 in (0, 16):
        bevel(arr, x0, 0, x0 + 15, 31, 1, 0.2, 0.3)
    for y in range(4, 30, 8):
        arr[y, 6:10] = np.array((26, 28, 32))
        arr[y + 1, 6:10] = np.array((84, 88, 96))
        arr[y, 22:26] = np.array((26, 28, 32))
        arr[y + 1, 22:26] = np.array((84, 88, 96))
    arr[:, 15] *= 0.6
    for _ in range(40):
        x, y = rng.integers(0, S, 2)
        arr[y, x] *= 0.8 + 0.4 * rng.random()
    return to_img(arr)


# ------------------------------------------------------------------ frames, glass, panels
def frame_texture(tier):
    base, light, dark = (rgb(c) for c in g.MATERIALS[tier])
    streak = np.random.default_rng(61).random(S)[:, None] * 0.08 + 0.96
    arr = np.ones((S, S, 3)) * base * streak[..., None] * (0.92 + 0.16 * vnoise(61, 4))[..., None]
    arr *= 0.82
    bevel(arr, 0, 0, 31, 31, 2, 0.2, 0.3)
    bevel(arr, 4, 4, 27, 27, 1, 0.08, 0.16)
    for x, y in ((2, 2), (28, 2), (2, 28), (28, 28)):
        rivet(arr, x, y, tuple(light))
    if tier == "gold":
        for x, y in ((2, 2), (28, 28)):
            arr[y:y + 2, x:x + 2] = np.array((154, 107, 214))
    if tier == "fluxium":
        for x in range(7, 25):
            arr[16, x] = np.array(AERO if x % 2 else MOON)
            arr[15, x] = np.array(AERO) * 0.35 + base * 0.5
            arr[17, x] = np.array(AERO) * 0.35 + base * 0.5
    if tier == "diamond":
        arr[10:12, 9:11] = np.array((220, 255, 250))
        arr[20:22, 20:22] = np.array((220, 255, 250))
    return to_img(arr)


def tank_glass():
    arr = np.zeros((S, S, 4))
    arr[..., :3] = (200, 230, 255)
    arr[..., 3] = 55
    for t, a in ((0, 170), (1, 90)):
        arr[t, :, 3] = a
        arr[:, t, 3] = a
        arr[31 - t, :, 3] = a
        arr[:, 31 - t, 3] = a
    for i in range(5, 14):
        arr[i, i, :3] = 255
        arr[i, i, 3] = 150
        arr[i + 1, i, 3] = 80
    for i in range(18, 24):
        arr[i, i - 6, :3] = 255
        arr[i, i - 6, 3] = 110
    return Image.fromarray(arr.astype("uint8"), "RGBA")


def collector_dish():
    arr = np.zeros((S, S, 3))
    ys, xs = np.mgrid[0:S, 0:S]
    d = np.hypot(xs - 15.5, ys - 15.5)
    left = xs < 16
    c_a, c_m = np.array(AERO), np.array(MOON)
    base = np.where(left[..., None], c_a, c_m)
    ring = 0.86 + 0.14 * (np.sin(d * 0.9) * 0.5 + 0.5)
    arr = base * (ring * (1.05 - 0.4 * np.clip(d / 22, 0, 1)))[..., None]
    arr[:, 15:17] = np.array((90, 60, 40))
    im = to_img(arr)
    dr = ImageDraw.Draw(im)
    dr.rectangle((0, 0, 31, 31), outline=(142, 75, 50))
    dr.rectangle((1, 1, 30, 30), outline=(200, 111, 74))
    for p in ((6, 6), (7, 6), (6, 7), (22, 7), (24, 9)):
        dr.point(p, fill=(255, 255, 255))
    return im


def sun_panel():
    arr = np.zeros((S, S, 3))
    for cx in range(4):
        for cy in range(4):
            x0, y0 = cx * 8, cy * 8
            arr[y0:y0 + 8, x0:x0 + 8] = np.array((255, 240, 160))
            vgrad(arr, mask_rect((x0 + 1, y0 + 1, x0 + 6, y0 + 6)), y0 + 1, y0 + 6, (255, 247, 190), (236, 190, 60))
            arr[y0, x0:x0 + 8] = np.array((200, 150, 40))
            arr[y0:y0 + 8, x0] = np.array((200, 150, 40))
    im = to_img(arr)
    ImageDraw.Draw(im).rectangle((0, 0, 31, 31), outline=(200, 111, 74))
    return im


def moon_panel():
    arr = np.zeros((S, S, 3))
    for cx in range(4):
        for cy in range(4):
            x0, y0 = cx * 8, cy * 8
            arr[y0:y0 + 8, x0:x0 + 8] = np.array((60, 90, 150))
            vgrad(arr, mask_rect((x0 + 1, y0 + 1, x0 + 6, y0 + 6)), y0 + 1, y0 + 6, (226, 238, 255), (150, 180, 235))
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    d.ellipse((9, 8, 23, 22), fill=(120, 150, 215))
    d.ellipse((13, 6, 27, 20), fill=(214, 232, 255))
    d.rectangle((0, 0, 31, 31), outline=(200, 111, 74))
    return im


def moon_glow():
    arr = np.ones((S, S, 3)) * np.array(MOON) * (0.92 + 0.12 * fbm(71))[..., None]
    return to_img(arr)


def soil_top():
    arr = np.ones((S, S, 3)) * np.array((91, 58, 30)) * (0.75 + 0.5 * fbm(81))[..., None]
    for y in range(4, 28, 6):
        arr[y:y + 2] *= 0.65
        arr[y + 2] *= 1.18
    rng = np.random.default_rng(81)
    for _ in range(50):
        x, y = rng.integers(2, 30, 2)
        arr[y, x] *= 0.6 + 0.9 * rng.random()
    bevel(arr, 0, 0, 31, 31, 2, 0.0, 0.0)
    im = to_img(arr)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 31, 31), outline=(200, 111, 74))
    d.rectangle((1, 1, 30, 30), outline=(142, 75, 50))
    return im


def sprout():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.line((15, 28, 15, 12), fill=(63, 143, 47), width=2)
    d.line((16, 28, 16, 14), fill=(46, 110, 36))
    d.polygon([(15, 18), (6, 13), (4, 9), (11, 10), (15, 14)], fill=(93, 187, 63), outline=(46, 110, 36))
    d.polygon([(16, 14), (24, 9), (28, 5), (28, 11), (17, 18)], fill=(118, 208, 80), outline=(46, 110, 36))
    d.line((14, 16, 7, 11), fill=(150, 225, 110))
    d.line((17, 15, 26, 8), fill=(160, 235, 120))
    return im


def loader_ring():
    arr = np.zeros((S, S, 3))
    ys = np.arange(S)[:, None] * np.ones((1, S))
    for x in range(S):
        c = np.array(AERO if x < 16 else MOON)
        arr[:, x] = c
    arr *= (0.86 + 0.14 * np.sin(ys * 0.9))[..., None]
    arr[0:2] = 255
    arr[30:32] = np.array((184, 184, 160))
    arr[:, 15:17] *= 0.7
    return to_img(arr)


def node_texture():
    arr = np.ones((S, S, 3)) * rgb("3A3D42") * (0.94 + 0.12 * vnoise(91, 4))[..., None]
    bevel(arr, 0, 0, 31, 31, 1, 0.12, 0.18)
    return to_img(arr)


def pipe_texture(name):
    main_h, glyph, gcol = g.PIPES[name]
    main, gc = rgb(main_h), tuple(int(v) for v in rgb(gcol))
    arr = np.zeros((S, S, 3))
    # long-side strip: columns 0-7, rows 0-31
    for x in range(8):
        t = x / 7
        f = 1.28 - 0.62 * t - 0.22 * (1 - abs(2 * t - 1)) * 0 + 0.10 * np.sin(t * np.pi)
        arr[0:32, x] = main * f
    arr[:, 0] = main * 1.4
    arr[:, 7] = main * 0.55
    for y in range(32):
        if y % 8 == 0:
            arr[y, 0:8] *= 0.78
    for oy in (4, 20):
        for dx, dy in glyph:
            arr[oy + dy * 2:oy + dy * 2 + 2, 2 * dx + 0:2 * dx + 2] = np.array(gc)
            arr[oy + dy * 2 + 1, 2 * dx + 0:2 * dx + 2] = np.array(gc) * 0.75
    # end cap: columns 8-15, rows 0-7
    arr[0:8, 8:16] = main * 0.6
    arr[2:6, 10:14] = main * 1.05
    arr[2, 10:14] = main * 1.4
    arr[5, 10:14] = main * 0.7
    return to_img(arr)


# ------------------------------------------------------------------ driver
def save(name, im):
    im.save(os.path.join(OUT, name + ".png"))


def main():
    for tier in g.TIERS:
        save(f"frame_{tier}", frame_texture(tier))
    for name, make in (
        ("tank_glass", tank_glass), ("collector_dish", collector_dish),
        ("obelisk_side", obelisk_side), ("book_open", lambda: top_book(True)),
        ("book_closed", lambda: top_book(False)), ("soil_top", soil_top), ("sprout", sprout),
        ("sun_panel", sun_panel), ("moon_panel", moon_panel), ("loader_ring", loader_ring),
        ("pipe_node", node_texture), ("moon_glow", moon_glow),
    ):
        save(name, make())
    for name in g.PIPES:
        save(f"pipe_{name}", pipe_texture(name))
    machine = {
        "machine_stone_side": lambda t: to_img(stone_base(t)),
        "machine_metal_side": lambda t: to_img(metal_base(t)),
        "machine_wood_side": lambda t: to_img(wood_base(t)),
        "furnace_front": front_furnace, "grinder_front": front_grinder, "grinder_top": top_grinder,
        "sawmill_front": front_sawmill, "sawmill_top": top_sawmill,
        "smelter_front": front_smelter, "smelter_top": top_smelter,
        "infuser_side": side_infuser, "infuser_top": top_infuser,
        "lectern_side": lectern_side,
        "enchanter_front": lambda t: front_enchanter(t, False),
        "disenchanter_front": lambda t: front_enchanter(t, True),
    }
    for name, make in machine.items():
        for tier in g.TIERS:
            save(name if tier == "copper" else f"{name}_{tier}", make(tier))


if __name__ == "__main__":
    main()
