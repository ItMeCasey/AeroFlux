"""AeroFlux machines in the style of Mekanism's: a steel casing cube, one framed face per side,
a recessed window on the front that shows the working part, a vent band along the bottom, ports and
slats on the sides, a concentric inset on top, and an animated lit front.

Studied from Mekanism's enrichment chamber, crusher, energized smelter and sawmill textures. The
construction is imitated (panel frames, bevels, window-over-vents layout, lit indicators); the art,
palette and machine details are our own. Textures are 32x32, drawn pixel by pixel from a small palette.
Each machine fits one 16x16x16 block; the only geometry is the casing and its recessed window.

Output: assets-src/aeroflux_v2/{models/block, textures/block, shapes}
  <machine>_{front,side,back,top,bottom}[_<tier>].png   <machine>_front_active[_<tier>].png (+ .mcmeta)
  <machine>_base.json, <machine>[_active][_<tier>].json
"""
import json
import math
import os
import shutil

from PIL import Image

import gen_assets as g

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "aeroflux_v2")
TEX = os.path.join(OUT, "textures", "block")
MOD = os.path.join(OUT, "models", "block")
SHP = os.path.join(OUT, "shapes")
NS = "aeroflux"
TIERS = g.TIERS
FRAMES = 4


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def mix(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


# steel, six steps from outline to highlight; the sun machines run warm, the moon machines cool
STEEL = {
    "sun": [hx(c) for c in ("131416", "26272b", "3a3c41", "505359", "686c73", "8a8e96")],
    "moon": [hx(c) for c in ("11141a", "232733", "363c4c", "4b5367", "646d86", "8791ae")],
}
HEAT = [hx(c) for c in ("4A1204", "A32F08", "E8650F", "FFAE2B", "FFEFA0")]
AERO, MOON = hx("FFE680"), hx("CFE6FF")
VOID = hx("B07CFF")


def accent(tier):
    """Tier metal: outline, shadow, base, light, glint."""
    base, light, dark = (g.hexc(c) for c in g.MATERIALS[tier])
    glint = tuple(min(255, int(v * 0.4 + 255 * 0.6)) for v in light[:3]) + (255,)
    gloss = 1.35 if tier in ("netherite", "fluxium") else 1.0
    return mix(dark, 0.5), mix(dark, 0.95), mix(base, gloss), mix(light, gloss), glint


class Canvas:
    def __init__(self, w=32, h=32):
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.p = self.im.load()
        self.w, self.h = w, h

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def pts(self, pts, c):
        for x, y in pts:
            self.px(x, y, c)

    def save(self, name):
        self.im.save(os.path.join(TEX, name + ".png"))


def hsh(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


# ------------------------------------------------------------------ casing grammar
def plate(cv, theme, tier, seed=0, band=True):
    """The framed steel face: dark outline, lit inner edge, stippled plate, a base band below."""
    P = STEEL[theme]
    a = accent(tier)
    cv.rect(0, 0, 31, 31, P[0])
    cv.rect(2, 2, 29, 29, P[3])
    for y in range(2, 30):
        for x in range(2, 30):
            r = hsh(x, y, seed) % 12
            if r == 0:
                cv.px(x, y, P[2])
            elif r == 1:
                cv.px(x, y, P[4])
    cv.hline(2, 29, 2, P[4])
    cv.vline(2, 2, 29, P[4])
    cv.hline(2, 29, 29, P[1])
    cv.vline(29, 2, 29, P[1])
    cv.hline(3, 28, 3, P[5])
    cv.vline(3, 3, 28, P[5]) if False else None
    if band:
        cv.rect(3, 20, 28, 28, P[2])
        for y in range(20, 29):
            for x in range(3, 29):
                if hsh(x, y, seed + 5) % 9 == 0:
                    cv.px(x, y, P[3])
        cv.hline(2, 29, 19, P[0])
        cv.hline(2, 29, 20, a[2])             # tier-colored trim line above the base band
        cv.hline(3, 28, 21, a[1])
        cv.hline(3, 28, 28, P[1])
    # tier studs in the corners
    for x, y in ((4, 4), (27, 4), (4, 27 if not band else 17), (27, 27 if not band else 17)):
        cv.px(x, y, a[3])
        cv.px(x + 1, y, a[2])
        cv.px(x, y + 1, a[2])
        cv.px(x + 1, y + 1, a[1])


def recess(cv, theme, box, floor):
    """A window cut into the plate: lit bevel on the near edge, dark floor, shadow under the top lip."""
    P = STEEL[theme]
    x0, y0, x1, y1 = box
    cv.rect(x0 - 2, y0 - 2, x1 + 2, y1 + 2, P[0])
    cv.rect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P[4])
    cv.hline(x0 - 1, x1 + 1, y0 - 1, P[1])
    cv.vline(x0 - 1, y0 - 1, y1 + 1, P[1])
    cv.rect(x0, y0, x1, y1, floor)
    cv.hline(x0, x1, y0, mix(floor, 0.55))
    cv.vline(x0, y0, y1, mix(floor, 0.7))


def vents(cv, theme, x0, x1, y0, y1, n, glow=None, phase=0):
    P = STEEL[theme]
    span = x1 - x0 + 1
    gap = span // n
    for i in range(n):
        x = x0 + i * gap + 1
        cv.rect(x, y0, x + 1, y1, P[0])
        cv.vline(x + 2, y0, y1, P[4])
        if glow:
            top = y1 - 1 - ((phase + i) % 3)
            cv.rect(x, top, x + 1, y1, glow[2])
            cv.hline(x, x + 1, y1, glow[3])


# ------------------------------------------------------------------ window interiors
WIN = {
    "flux_furnace": (6, 5, 25, 16), "flux_grinder": (8, 4, 23, 17), "alloy_smelter": (5, 5, 19, 16),
    "flux_sawmill": (6, 5, 25, 16), "flux_infuser": (9, 4, 22, 17), "flux_enchanter": (9, 4, 22, 17),
    "flux_disenchanter": (9, 4, 22, 17), "plant_grower": (5, 4, 26, 21),
}
THEME = {"flux_furnace": "sun", "flux_grinder": "sun", "alloy_smelter": "sun", "flux_sawmill": "sun",
         "flux_infuser": "moon", "flux_enchanter": "moon", "flux_disenchanter": "moon", "plant_grower": "sun"}


def tongue_fire(cv, box, phase, active):
    x0, y0, x1, y1 = box
    cv.rect(x0, y0, x1, y1, HEAT[0])
    if not active:
        cv.pts([(x0 + 2, y1), (x0 + 3, y1), (x0 + 7, y1), (x0 + 12, y1), (x0 + 13, y1), (x0 + 5, y1 - 1)], HEAT[1])
        cv.pts([(x0 + 3, y1), (x0 + 12, y1)], HEAT[2])
        return
    rng = [4, 7, 5, 8, 6, 9, 5, 7, 4, 6]
    for i, x in enumerate(range(x0 + 1, x1, 2)):
        h = max(2, min(y1 - y0 - 1, rng[i % len(rng)] + ((phase + i) % 3) - 1))
        for k in range(h):
            c = HEAT[4] if k < 1 else HEAT[3] if k < h * 0.45 else HEAT[2] if k < h * 0.8 else HEAT[1]
            cv.px(x, y1 - k, c)
            cv.px(x + 1, y1 - k, c if k < h - 1 else HEAT[1])
    cv.hline(x0, x1, y1, HEAT[4])
    cv.hline(x0, x1, y1 - 1, HEAT[3])


def gear_art(cv, cx, cy, r, phase, active, theme):
    P = STEEL[theme]
    body = hx("F2B72A") if active else P[4]
    edge = hx("8A5F0E") if active else P[1]
    teeth = 6
    for yy in range(cy - r - 2, cy + r + 3):
        for xx in range(cx - r - 2, cx + r + 3):
            dx, dy = xx - cx, yy - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx) + phase * math.pi / (teeth * 2)
            tooth = (math.sin(ang * teeth) > 0.2)
            rr = r + (1.6 if tooth else 0)
            if d <= rr and d > 1.5:
                cv.px(xx, yy, body if d < rr - 1 else edge)
    cv.px(cx, cy, edge)
    cv.px(cx - 1, cy - 1, hx("FFF0A8") if active else P[5])


def window_art(m, cv, theme, box, phase, active, tier):
    x0, y0, x1, y1 = box
    P = STEEL[theme]
    a = accent(tier)
    if m == "flux_furnace":
        tongue_fire(cv, box, phase, active)
    elif m == "flux_grinder":
        cv.rect(x0, y0, x1, y1, hx("0E0F11"))
        gear_art(cv, (x0 + x1) // 2, (y0 + y1) // 2 + 1, 4, phase, active, theme)
        if active:
            cv.pts([(x0 + 1 + (phase * 3) % 10, y1), (x1 - 2 - (phase * 2) % 8, y1 - 1)], HEAT[3])
    elif m == "alloy_smelter":
        glow = 0.65 + 0.35 * math.sin(phase * 1.6)
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                d = math.hypot((xx - (x0 + x1) / 2) / 7, (yy - y1) / 8)
                c = HEAT[min(4, max(0, int(4 - d * 4 * (1.0 if active else 0.4)) if d < 1.2 else 0))]
                cv.px(xx, yy, c if active else mix(c, 0.5) if d < 1.2 else HEAT[0])
        cv.hline(x0, x1, y1, HEAT[3] if active else HEAT[1])
        if active:
            cv.pts([(x0 + 3 + phase, y0 + 2), (x1 - 3 - phase, y0 + 3)], HEAT[4])
    elif m == "flux_sawmill":
        cv.rect(x0, y0, x1, y1, hx("0E0F11"))
        cx, cy, r = (x0 + x1) // 2, y1 + 3, 9
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                d = math.hypot(xx - cx, yy - cy)
                ang = math.atan2(yy - cy, xx - cx) + (phase * 0.35 if active else 0)
                tooth = math.sin(ang * 14) > 0.4
                if d <= r + (1 if tooth else 0):
                    cv.px(xx, yy, hx("D4D8DE") if d > 3 else hx("7B808A"))
                if d <= r - 1 and (xx + yy) % 5 == 0:
                    cv.px(xx, yy, hx("AEB3BC"))
        if active:
            cv.pts([(x0 + 2 + (phase * 5) % 14, y1 - 1), (x1 - 3 - (phase * 3) % 11, y1 - 2)], hx("F0D49A"))
    elif m in ("flux_infuser", "flux_enchanter", "flux_disenchanter"):
        col = VOID if m == "flux_disenchanter" else MOON
        cv.rect(x0, y0, x1, y1, hx("0C0E14"))
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        r = 4.8 + (0.6 * math.sin(phase * 1.6) if active else 0)
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                d = math.hypot(xx - cx - 0.5, yy - cy - 0.5)
                if m == "flux_infuser":
                    if d < r:
                        t = d / r
                        cv.px(xx, yy, mix(col, 1.0 - 0.55 * t) if active else mix(col, 0.35 - 0.1 * t))
                else:
                    ring = abs(d - 4.8) < 0.8
                    if ring:
                        cv.px(xx, yy, col if active else mix(col, 0.4))
        if m != "flux_infuser":
            cv.vline(cx, cy - 2, cy + 2, col if active else mix(col, 0.4))
            cv.hline(cx - 2, cx + 2, cy, col if active else mix(col, 0.4))
            cv.pts([(cx - 3, cy - 3), (cx + 3, cy + 3)], col if active else mix(col, 0.4))
        if active:
            cv.pts([(x0 + 1 + (phase * 4) % 11, y1 - 1), (x1 - 2 - (phase * 3) % 9, y0 + 1)], hx("FFFFFF"))
    elif m == "plant_grower":
        cv.rect(x0, y0, x1, y1, hx("0F1A12") if not active else hx("1B3A1F"))
        for x in range(x0 + 1, x1, 4):
            cv.vline(x + 1, y1 - 5, y1, hx("3F8F2F"))
            cv.pts([(x, y1 - 4), (x + 2, y1 - 6), (x - 1, y1 - 3)], hx("5DBB3F"))
        cv.hline(x0, x1, y1, hx("4B2F18"))
        if active:
            cv.hline(x0 + 1, x1 - 1, y0 + 1, AERO)


def lamp_panel(cv, theme, tier, m, box, active, phase):
    """The small status window beside the main one, lit when active."""
    x0, y0, x1, y1 = box
    recess(cv, theme, box, hx("0C0E10"))
    a = accent(tier)
    col = MOON if THEME[m] == "moon" else AERO
    if active:
        cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, col if (phase % 2 == 0) else mix(col, 0.8))
        cv.px(x0 + 1, y0 + 1, hx("FFFFFF"))
    else:
        cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, mix(col, 0.22))


def front(m, tier, phase=0, active=False):
    theme = THEME[m]
    cv = Canvas()
    plate(cv, theme, tier, seed=hash(m) % 17, band=(m != "plant_grower"))
    P = STEEL[theme]
    a = accent(tier)
    box = WIN[m]
    recess(cv, theme, box, hx("0C0E10"))
    if m == "plant_grower":
        return grower_front(cv, theme, tier, phase, active)
    window_art(m, cv, theme, box, phase, active, tier)
    if m == "alloy_smelter":
        lamp_panel(cv, theme, tier, m, (22, 6, 26, 12), active, phase)
        lamp_panel(cv, theme, tier, m, (22, 14, 26, 16), False, phase)
    if m in ("flux_furnace", "flux_grinder", "alloy_smelter", "flux_sawmill"):
        vents(cv, theme, 7, 24, 22, 27, 4, HEAT if active else None, phase)
    elif m == "plant_grower":
        for i in range(3):
            x = 8 + i * 6
            cv.rect(x, 22, x + 3, 27, hx("0E1A10"))
            if active:
                cv.rect(x, 22, x + 3, 27, mix(AERO, 0.9 if (phase + i) % 2 else 0.7))
            cv.hline(x, x + 3, 27, P[1])
    else:
        # moon machines: a row of small rune lights instead of a vent grille
        for i in range(5):
            x = 7 + i * 4
            lit = active and ((phase + i) % 2 == 0)
            cv.rect(x, 23, x + 2, 25, MOON if lit else (VOID if m == "flux_disenchanter" and active else mix(MOON, 0.2)))
            cv.hline(x, x + 2, 26, P[1])
    return cv


def grower_front(cv, theme, tier, phase, active):
    """The window is a real glass pane over a hollow chamber (see grower_elements); only the frame is drawn here."""
    P = STEEL[theme]
    a = accent(tier)
    cv.hline(2, 29, 24, P[0])
    cv.hline(2, 29, 25, a[2])
    cv.rect(3, 26, 28, 28, P[2])
    for i in range(3):
        x = 7 + i * 7
        cv.rect(x, 26, x + 4, 28, P[0])
        if active:
            lit = mix(AERO if i != 1 else MOON, 1.0 if (phase + i) % 2 == 0 else 0.8)
        else:
            lit = hx("1B2A1D")
        cv.rect(x + 1, 26, x + 3, 27, lit)
        cv.hline(x, x + 4, 28, P[1])
    return cv


def side(m, tier):
    theme = THEME[m]
    cv = Canvas()
    plate(cv, theme, tier, seed=hash(m + "s") % 17)
    P = STEEL[theme]
    a = accent(tier)
    if m in ("flux_furnace", "alloy_smelter"):
        for i, y in enumerate((6, 9, 12, 15)):
            cv.rect(7, y, 24, y + 1, P[0])
            cv.hline(7, 24, y + 2, P[4])
    elif m in ("flux_grinder", "flux_sawmill"):
        cv.rect(8, 6, 23, 15, P[0])
        cv.rect(9, 7, 22, 14, P[4])
        cv.rect(10, 8, 21, 13, P[0])
        for x in range(11, 21, 3):
            cv.rect(x, 9, x + 1, 12, hx("B7BBC2") if m == "flux_sawmill" else hx("D9A21B"))
    elif m == "plant_grower":
        # window frame only; the opening is real glass over the chamber (see grower_elements)
        recess(cv, theme, (8, 5, 23, 18), hx("0C0E10"))
    else:
        recess(cv, theme, (11, 6, 20, 15), hx("0C0E14"))
        col = VOID if m == "flux_disenchanter" else MOON
        cv.rect(14, 9, 17, 12, mix(col, 0.8))
        cv.px(14, 9, hx("FFFFFF"))
    # port: tier-ringed square at the base
    cv.rect(12, 22, 19, 27, P[0])
    cv.rect(13, 23, 18, 26, a[2])
    cv.rect(14, 24, 17, 25, hx("0A0B0D"))
    cv.hline(13, 18, 23, a[3])
    return cv


def back(m, tier):
    theme = THEME[m]
    cv = Canvas()
    plate(cv, theme, tier, seed=hash(m + "b") % 17)
    P = STEEL[theme]
    a = accent(tier)
    for i in range(5):
        cv.rect(8 + i * 4, 5, 9 + i * 4, 10, P[0])
        cv.vline(10 + i * 4, 5, 10, P[4])
    cv.rect(13, 13, 18, 17, P[0])
    cv.rect(14, 14, 17, 16, a[2])
    cv.rect(15, 15, 16, 15, hx("0A0B0D"))
    cv.rect(10, 22, 21, 26, P[0])
    cv.rect(11, 23, 20, 25, P[1])
    return cv


def top(m, tier):
    theme = THEME[m]
    cv = Canvas()
    plate(cv, theme, tier, seed=hash(m + "t") % 17, band=False)
    P = STEEL[theme]
    a = accent(tier)
    cv.rect(7, 7, 24, 24, P[0])
    cv.rect(8, 8, 23, 23, a[2])
    cv.hline(8, 23, 8, a[3])
    cv.vline(8, 8, 23, a[3])
    cv.hline(8, 23, 23, a[1])
    cv.vline(23, 8, 23, a[1])
    cv.rect(10, 10, 21, 21, P[1])
    cv.rect(11, 11, 20, 20, hx("0C0D10"))
    if m == "flux_furnace":
        for x in range(12, 20, 2):
            cv.vline(x, 12, 19, P[2])
    elif m == "flux_grinder":
        cv.rect(12, 12, 19, 19, hx("20180A"))
        for y in (13, 16):
            cv.hline(12, 19, y, hx("D9A21B"))
    elif m == "alloy_smelter":
        cv.rect(12, 12, 19, 19, HEAT[1])
        cv.rect(13, 13, 18, 18, HEAT[2])
        cv.rect(14, 14, 17, 17, HEAT[3])
    elif m == "flux_sawmill":
        cv.rect(11, 15, 20, 16, hx("D4D8DE"))
        cv.hline(11, 20, 17, P[2])
    elif m in ("flux_infuser", "flux_enchanter", "flux_disenchanter"):
        col = VOID if m == "flux_disenchanter" else MOON
        cv.rect(13, 13, 18, 18, mix(col, 0.7))
        cv.rect(14, 14, 17, 17, col)
        cv.px(14, 14, hx("FFFFFF"))
    elif m == "plant_grower":
        for i in range(3):
            for j in range(3):
                c = AERO if (i + j) % 2 == 0 else MOON
                cv.rect(12 + i * 3, 12 + j * 3, 13 + i * 3, 13 + j * 3, mix(c, 0.9))
    return cv


def bottom(m, tier):
    theme = THEME[m]
    cv = Canvas()
    plate(cv, theme, tier, seed=3, band=False)
    P = STEEL[theme]
    cv.rect(8, 8, 23, 23, P[0])
    cv.rect(9, 9, 22, 22, P[2])
    cv.rect(11, 11, 20, 20, P[1])
    return cv


# ------------------------------------------------------------------ grower chamber textures
def grower_inner():
    """Greenhouse wall inside the chamber: dark teal steel with a window grid and bolts."""
    cv = Canvas()
    base = [hx(c) for c in ("0F1C1B", "1A2E2C", "26403D", "35564F", "4B7168")]
    cv.rect(0, 0, 31, 31, base[1])
    for y in range(32):
        for x in range(32):
            if hsh(x, y, 9) % 10 == 0:
                cv.px(x, y, base[2])
    for k in (0, 10, 21, 31):
        cv.hline(0, 31, k, base[0])
        cv.vline(k, 0, 31, base[0])
        if k < 31:
            cv.hline(0, 31, k + 1, base[3])
            cv.vline(k + 1, 0, 31, base[3])
    for x in (3, 14, 25):
        for y in (3, 14, 25):
            cv.px(x, y, base[4])
            cv.px(x + 1, y + 1, base[0])
    return cv


def grower_soil():
    cv = Canvas()
    soil = [hx(c) for c in ("2A1A0E", "3D2614", "54361C", "6B4726", "855D33")]
    cv.rect(0, 0, 31, 31, soil[2])
    for y in range(32):
        for x in range(32):
            r = hsh(x, y, 4) % 9
            if r == 0:
                cv.px(x, y, soil[1])
            elif r == 1:
                cv.px(x, y, soil[3])
    for y in range(3, 32, 6):
        cv.hline(0, 31, y, soil[0])
        cv.hline(0, 31, y + 1, soil[1])
    for x, y in ((5, 6), (13, 12), (24, 9), (9, 22), (21, 26), (28, 17)):
        cv.rect(x, y, x + 1, y + 1, soil[4])
    return cv


def grower_lamp(active):
    """The grow lamp under the chamber roof: a sun strip and a moon strip."""
    cv = Canvas()
    P = STEEL["sun"]
    cv.rect(0, 0, 31, 31, P[1])
    for y in range(32):
        for x in range(32):
            if hsh(x, y, 6) % 11 == 0:
                cv.px(x, y, P[2])
    for x0, col in ((3, AERO), (17, MOON)):
        cv.rect(x0, 4, x0 + 11, 27, P[0])
        cv.rect(x0 + 1, 5, x0 + 10, 26, mix(col, 0.95 if active else 0.28))
        if active:
            for y in range(7, 26, 4):
                cv.hline(x0 + 2, x0 + 9, y, mix(col, 1.1))
            cv.hline(x0 + 1, x0 + 10, 5, hx("FFFFFF"))
    return cv


# ------------------------------------------------------------------ models
FACE_NAMES = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}


def box(frm, to, **faces):
    return {"from": list(frm), "to": list(to),
            "faces": {FACE_NAMES[k]: {"texture": "#" + v} for k, v in faces.items()}}


def std(frm, to):
    return box(frm, to, n="front", s="back", e="side", w="side", u="top", d="bottom")


def window_model_box(m):
    x0, y0, x1, y1 = WIN[m]
    return x0 / 2, (x1 + 1) / 2, 16 - (y1 + 1) / 2, 16 - y0 / 2


def pbox(frm, to, faces):
    return {"from": list(frm), "to": list(to),
            "faces": {FACE_NAMES[k]: {"texture": "#" + v} for k, v in faces.items()}}


def grower_elements():
    """A casing with a hollow glass-fronted chamber: soil floor and grow lamp roof. The crop itself is drawn
    by the block entity renderer (the actual planted crop at its real growth stage), not by this model."""
    ox0, ox1, oy0, oy1 = window_model_box("plant_grower")
    d = 1.5
    els = [std((0, 0, 0), (ox0, 16, d)), std((ox1, 0, 0), (16, 16, d)),
           std((ox0, 0, 0), (ox1, oy0, d)), std((ox0, oy1, 0), (ox1, 16, d)),
           pbox((0, 0, d), (16, oy0, 16), dict(n="front", s="back", e="side", w="side", u="soil", d="bottom")),
           pbox((0, oy1, d), (16, 16, 16), dict(n="front", s="back", e="side", w="side", u="top", d="lamp")),
           pbox((ox0, oy0, 14), (ox1, oy1, 16), dict(n="inner", s="back", e="inner", w="inner", u="top", d="bottom"))]
    # side walls with a real window each: four pieces around the opening, outer faces show the side texture
    wy0, wy1, wz0, wz1 = oy0 + 1.5, oy1 - 0.5, 4, 12
    for x0, x1, outer, inner in ((0, ox0, "w", "e"), (ox1, 16, "e", "w")):
        for frm, to in (((x0, oy0, d), (x1, oy1, wz0)), ((x0, oy0, wz1), (x1, oy1, 16)),
                        ((x0, oy0, wz0), (x1, wy0, wz1)), ((x0, wy1, wz0), (x1, oy1, wz1))):
            faces = dict(n="side", s="side", u="top", d="bottom")
            faces[outer] = "side"
            faces[inner] = "inner"
            els.append(pbox(frm, to, faces))
        gx = 0.5 if outer == "w" else 15.2
        els.append(pbox((gx, wy0, wz0), (gx + 0.3, wy1, wz1), dict(n="glass", s="glass", e="glass", w="glass", u="glass", d="glass")))
    # the glass pane sits just behind the front plate
    els.append(pbox((ox0, oy0, 0.6), (ox1, oy1, 0.9), dict(n="glass", s="glass", e="glass", w="glass", u="glass", d="glass")))
    return els


def elements(m):
    if m == "plant_grower":
        return grower_elements()
    ox0, ox1, oy0, oy1 = window_model_box(m)
    depth = 1.5
    els = [std((0, 0, depth), (16, 16, 16)),
           std((0, 0, 0), (ox0, 16, depth)), std((ox1, 0, 0), (16, 16, depth)),
           std((ox0, 0, 0), (ox1, oy0, depth)), std((ox0, oy1, 0), (ox1, 16, depth))]
    return els


MACHINES = list(WIN)


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def sfx(tier):
    return "" if tier == "copper" else "_" + tier


def write_models():
    for m in MACHINES:
        base = {"parent": "minecraft:block/block", "elements": elements(m)}
        if m == "plant_grower":
            base["render_type"] = "minecraft:translucent"
        write_json(os.path.join(MOD, f"{m}_base.json"), base)
        for tier in TIERS:
            for active in (False, True):
                tex = {k: f"{NS}:block/{m}_{k}{sfx(tier)}" for k in ("side", "back", "top", "bottom")}
                tex["front"] = f"{NS}:block/{m}_front{'_active' if active else ''}{sfx(tier)}"
                tex["particle"] = tex["side"]
                if m == "plant_grower":
                    lit = "_active" if active else ""
                    tex.update({"inner": f"{NS}:block/plant_grower_inner", "soil": f"{NS}:block/plant_grower_soil",
                                "lamp": f"{NS}:block/plant_grower_lamp{lit}",
                                "glass": f"{NS}:block/plant_grower_glass"})
                write_json(os.path.join(MOD, f"{m}{'_active' if active else ''}{sfx(tier)}.json"),
                           {"parent": f"{NS}:block/{m}_base", "textures": tex})


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def write_items_and_states():
    """Block items (3D, vanilla block display transforms), blockstates with facing and lit, and the
    1.21.4+/26.1 item definitions. <machine>.json is the copper tier; <machine>_<tier>.json for the rest."""
    for sub in ("models/item", "blockstates", "items"):
        shutil.rmtree(os.path.join(OUT, sub), ignore_errors=True)
        os.makedirs(os.path.join(OUT, sub))
    for m in MACHINES:
        for tier in TIERS:
            name = f"{m}{sfx(tier)}"
            write_json(os.path.join(OUT, "models", "item", name + ".json"), {"parent": f"{NS}:block/{name}"})
            write_json(os.path.join(OUT, "items", name + ".json"),
                       {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
            variants = {}
            for facing, y in FACINGS:
                for lit in (False, True):
                    entry = {"model": f"{NS}:block/{name.replace(m, m + ('_active' if lit else ''), 1)}"}
                    if y:
                        entry["y"] = y
                    variants[f"facing={facing},lit={'true' if lit else 'false'}"] = entry
            write_json(os.path.join(OUT, "blockstates", name + ".json"), {"variants": variants})


def write_shapes():
    """Every machine is a full cube; the window recess is visual only."""
    write_json(os.path.join(SHP, "machine_shapes_v2.json"), {m: [[[0, 0, 0], [16, 16, 16]]] for m in MACHINES})


def main():
    for d in (TEX, MOD, SHP):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    for m in MACHINES:
        for tier in TIERS:
            s = sfx(tier)
            front(m, tier).save(f"{m}_front{s}")
            side(m, tier).save(f"{m}_side{s}")
            back(m, tier).save(f"{m}_back{s}")
            top(m, tier).save(f"{m}_top{s}")
            bottom(m, tier).save(f"{m}_bottom{s}")
            strip = Image.new("RGBA", (32, 32 * FRAMES))
            for p in range(FRAMES):
                strip.paste(front(m, tier, p, True).im, (0, p * 32))
            strip.save(os.path.join(TEX, f"{m}_front_active{s}.png"))
            write_json(os.path.join(TEX, f"{m}_front_active{s}.png.mcmeta"), {"animation": {"frametime": 4}})
    grower_inner().save("plant_grower_inner")
    grower_soil().save("plant_grower_soil")
    grower_lamp(False).save("plant_grower_lamp")
    grower_lamp(True).save("plant_grower_lamp_active")
    shutil.copy(os.path.join(HERE, "..", "aeroflux32", "textures", "block", "tank_glass.png"),
                os.path.join(TEX, "plant_grower_glass.png"))
    write_models()
    write_items_and_states()
    write_shapes()
    print("done", len(os.listdir(TEX)), "files in textures,", len(os.listdir(MOD)), "models")


if __name__ == "__main__":
    main()
