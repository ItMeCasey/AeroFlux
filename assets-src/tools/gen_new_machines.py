"""The next machine wave, in the same Mekanism-style casing family as gen_machines_v3:
  flux_crafter     auto crafting table (3x3 grid window, scans while it works)
  flux_purifier    ore washer: spray nozzles over an ore basket, water pool
  moon_spawner     barred mob chamber with watching eyes
  sky_controller   day/night + weather: a split sun/moon sky that cycles through clouds and rain
  flux_blender     Aero + Moon in, Twilight Flux out: two tanks into a mixing bowl with a paddle
  animal_breeder   hay pen with a trough and a heart
  flux_repairer    anvil, hammer and an XP orb
  flux_sifter      tiered sieve: open top tray with a real mesh, grains falling in the window
  flux_magnet      item vacuum: horseshoe magnet lying in an open bowl
  sun_lamp / moon_lamp   caged crystal lamps (not full cubes, lit state only)

All six tiers each. Writes assets-src/aeroflux_v4. Run: python gen_new_machines.py
"""
import json
import math
import os
import shutil

from PIL import Image

import gen_assets as g
import gen_machines_v3 as b
from gen_machines_v3 import STEEL, Canvas, accent, hx, mix, plate, recess, sfx

OUT = os.path.join(b.HERE, "..", "aeroflux_v4")
b.OUT = OUT
b.TEX = os.path.join(OUT, "textures", "block")
b.MOD = os.path.join(OUT, "models", "block")
b.SHP = os.path.join(OUT, "shapes")
b.hash = lambda s: sum((i + 1) * ord(c) for i, c in enumerate(s))      # same textures every run

NS = b.NS
MACHINE_ORDER = ["flux_crafter", "flux_purifier", "moon_spawner", "sky_controller", "flux_blender",
                 "animal_breeder", "flux_repairer", "flux_sifter", "flux_magnet"]
THEME = {"flux_crafter": "sun", "flux_purifier": "sun", "moon_spawner": "moon", "sky_controller": "sun",
         "flux_blender": "moon", "animal_breeder": "sun", "flux_repairer": "sun", "flux_sifter": "sun",
         "flux_magnet": "moon"}
WIN = {"flux_crafter": (6, 4, 27, 17), "flux_purifier": (6, 5, 25, 16), "moon_spawner": (9, 4, 22, 17),
       "sky_controller": (6, 4, 25, 17), "flux_blender": (7, 4, 24, 17), "animal_breeder": (6, 5, 25, 16),
       "flux_repairer": (8, 4, 23, 17), "flux_sifter": (6, 5, 25, 16), "flux_magnet": (9, 9, 22, 17)}
b.THEME.update(THEME)
b.WIN.update(WIN)
b.MACHINES = list(MACHINE_ORDER)
SEED = {m: 3 * i + 1 for i, m in enumerate(MACHINE_ORDER)}
# pit machines: the top is open down to this height, and the floor shows a texture of its own
PIT = {"flux_sifter": 14, "flux_magnet": 12}

AQUA = [hx(c) for c in ("0E2A55", "1F5FA8", "3A8FE0", "8CD0FF", "EAF6FF")]
TWILIGHT = [hx(c) for c in ("3B1B6B", "7B3FD0", "C77DFF", "FFB3F0", "FFF0FF")]
GOLD_TANK, BLUE_TANK = (hx("F2B72A"), hx("FFF0A0")), (hx("7FB4FF"), hx("E6F2FF"))
WOOD = [hx(c) for c in ("3E2410", "6B4423", "8F5E31", "B27F47")]
HAY = [hx(c) for c in ("8C6A1E", "B8902E", "D9B44A", "F0D675")]
WHEAT = hx("E8C547")
XP = [hx(c) for c in ("1E6B0E", "49C21F", "9BF050", "E4FFB0")]
EYE = hx("FF4D6A")
DARK = hx("0C0E10")
# mesh color per sifter tier: string, flint, iron, diamond, netherite, then a glowing flux weave
MESH = {"copper": hx("D8CBA0"), "iron": hx("5A5A64"), "gold": hx("C9CDD2"), "diamond": hx("4EE4D6"),
        "netherite": hx("4A3A44"), "fluxium": hx("FFE680")}
GRAINS = [hx("D8CBA0"), hx("D8AF93"), hx("F2B72A"), hx("8A8F98"), hx("5A4632")]


def ring_port(cv, tier, box=(11, 22, 20, 27), core=hx("0A0B0D")):
    """Tier-ringed square outlet in the lower front, same family as the side ports."""
    x0, y0, x1, y1 = box
    a = accent(tier)
    cv.rect(x0 - 1, y0, x1 + 1, y1, STEEL["sun"][0])
    cv.rect(x0, y0 + 1, x1, y1 - 1, a[2])
    cv.hline(x0, x1, y0 + 1, a[3])
    cv.rect(x0 + 1, y0 + 2, x1 - 1, y1 - 2, core)


def runes(cv, P, active, phase, col=b.MOON, y=23):
    for i in range(5):
        x = 7 + i * 4
        lit = active and ((phase + i) % 2 == 0)
        cv.rect(x, y, x + 2, y + 2, col if lit else mix(col, 0.2))
        cv.hline(x, x + 2, y + 3, P[1])


# ------------------------------------------------------------------ crafter
def crafter_art(cv, box, tier, phase, active):
    P = STEEL["sun"]
    a = accent(tier)
    ingot, stick = hx("B7BBC2"), WOOD[3]
    pattern = {0: ingot, 1: ingot, 2: ingot, 4: stick, 7: stick}       # a pickaxe
    scan = (phase * 2) % 9 if active else -1
    for i in range(9):
        cx, cy = 8 + (i % 3) * 4, 5 + (i // 3) * 4
        hot = active and i in (scan, (scan + 1) % 9)
        cv.rect(cx, cy, cx + 2, cy + 2, hx("1C1F24"))
        cv.px(cx, cy, P[0])
        if hot:
            cv.rect(cx, cy, cx + 2, cy + 2, mix(b.AERO, 0.55))
        if i in pattern:
            cv.rect(cx, cy, cx + 2, cy + 2, pattern[i] if not hot else mix(pattern[i], 1.2))
            cv.px(cx, cy, mix(pattern[i], 1.25))
    cv.hline(19, 22, 10, P[5] if active else P[4])
    cv.pts([(21, 9), (21, 11)], P[5] if active else P[4])
    glow = active and phase == 3
    cv.rect(23, 7, 26, 13, P[0])
    cv.rect(24, 8, 25, 12, mix(b.AERO, 0.8) if glow else hx("16181C"))
    cv.rect(24, 9, 25, 11, a[2])
    cv.px(24, 9, a[4])


def crafter_lower(cv, theme, tier, phase, active):
    P = STEEL[theme]
    cv.rect(9, 22, 22, 27, P[0])
    cv.rect(10, 23, 21, 26, hx("0A0B0D"))
    cv.hline(10, 21, 23, P[4])
    cv.hline(9, 22, 27, accent(tier)[2])
    for x in (12, 19):
        cv.px(x, 25, b.AERO if active else mix(b.AERO, 0.25))


# ------------------------------------------------------------------ purifier
def purifier_art(cv, box, tier, phase, active):
    P = STEEL["sun"]
    cv.rect(8, 6, 23, 7, P[2])
    cv.hline(8, 23, 6, P[4])
    nozzles = (9, 13, 17, 21)
    for i, x in enumerate(nozzles):
        cv.px(x, 8, P[0])
        cv.px(x + 1, 8, P[0])
        if active:
            for k in range(2):
                y = 9 + ((phase * 2 + k * 2 + i) % 5)
                cv.px(x, y, AQUA[3])
                cv.px(x, y + 1, AQUA[2])
        else:
            cv.px(x, 9, AQUA[1])
    ores = ((9, hx("F2B72A")), (14, hx("D8AF93")), (19, hx("4EE4D6")))
    for x, col in ores:
        cv.rect(x, 12, x + 3, 13, hx("6E7178"))
        cv.hline(x, x + 3, 12, hx("8E9198"))
        cv.px(x + 1, 13, col)
        cv.px(x + 3, 12, col)
    cv.hline(8, 23, 14, P[2])
    cv.rect(8, 15, 23, 16, AQUA[1] if active else AQUA[0])
    if active:
        for x in range(8 + (phase % 2), 24, 3):
            cv.px(x, 15, AQUA[3])


def purifier_lower(cv, theme, tier, phase, active):
    b.vents(cv, theme, 7, 24, 22, 27, 4, AQUA if active else None, phase)


# ------------------------------------------------------------------ moon spawner
def spawner_art(cv, box, tier, phase, active):
    P = STEEL["moon"]
    cv.rect(9, 4, 22, 17, hx("08060E"))
    cv.rect(11, 7, 19, 13, hx("17121F"))                                # the shape behind the bars
    cv.rect(12, 6, 18, 7, hx("17121F"))
    closed = active and phase == 3
    for x in (12, 17):
        if closed:
            cv.hline(x, x + 1, 10, mix(EYE, 0.5))
        else:
            c = EYE if active else mix(EYE, 0.22)
            cv.rect(x, 9, x + 1, 10, c)
            if active:
                cv.px(x - 1, 9, mix(EYE, 0.4))
                cv.px(x + 2, 9, mix(EYE, 0.4))
    for x in (11, 14, 17, 20):                                          # iron bars over the chamber
        cv.vline(x, 4, 17, hx("5C6070"))
        cv.vline(x + 1, 4, 17, hx("2C2F3A"))
        cv.px(x, 4, hx("8A90A4"))
    cv.hline(9, 22, 4, P[0])
    cv.hline(9, 22, 17, P[0])


def spawner_lower(cv, theme, tier, phase, active):
    runes(cv, STEEL["moon"], active, phase, b.VOID)


# ------------------------------------------------------------------ sky controller
def cloud(cv, x, y, col):
    cv.rect(x + 1, y + 1, x + 7, y + 2, col)
    cv.rect(x + 2, y, x + 5, y, col)
    cv.rect(x, y + 2, x + 8, y + 2, mix(col, 0.82))


def sky_art(cv, box, tier, phase, active):
    P = STEEL["sun"]
    for y in range(4, 18):                                              # day sky left, night sky right
        t = (y - 4) / 13
        cv.hline(6, 15, y, tuple(int(c0 + (c1 - c0) * t) for c0, c1 in zip((0x4A, 0x9C, 0xF0), (0xA6, 0xD8, 0xFF))) + (255,))
        cv.hline(16, 25, y, tuple(int(c0 + (c1 - c0) * t) for c0, c1 in zip((0x0E, 0x14, 0x34), (0x2A, 0x2F, 0x66))) + (255,))
    rays = active and phase in (0, 2)
    sx, sy = 10, 9
    for y in range(sy - 3, sy + 4):
        for x in range(sx - 3, sx + 4):
            d = math.hypot(x - sx, y - sy)
            if d <= 2.6:
                cv.px(x, y, hx("FFD23C") if d > 1.3 else hx("FFF3A0"))
    if rays:
        cv.pts([(sx, sy - 4), (sx, sy + 4), (sx - 4, sy), (sx + 4, sy), (sx - 3, sy - 3), (sx + 3, sy + 3), (sx - 3, sy + 3), (sx + 3, sy - 3)], hx("FFE680"))
    mx, my = 21, 9
    for y in range(my - 4, my + 5):                                     # crescent: disc minus an offset disc
        for x in range(mx - 4, mx + 5):
            if math.hypot(x - mx, y - my) <= 3.2 and math.hypot(x - mx - 2, y - my + 1) > 2.8:
                cv.px(x, y, hx("EAF2FF") if active and phase == 3 else hx("C9D6EE"))
    for x, y in ((18, 6), (24, 6), (19, 14), (23, 12), (17, 11)):
        if not active or (x + y + phase) % 3:
            cv.px(x, y, hx("FFFFFF"))
    if active and phase in (1, 2):
        cloud(cv, 7 + phase, 6, hx("DDE3EC") if phase == 1 else hx("8A93A3"))
    if active and phase == 2:                                           # rain across both halves
        for x in range(7, 25, 3):
            for k in range(2):
                y = 9 + ((x + k * 4) % 7)
                cv.px(x, y, AQUA[3])
                cv.px(x - 1, y + 1, AQUA[2])
    cv.vline(15, 4, 17, P[0])
    cv.vline(16, 4, 17, P[0])


def sky_lower(cv, theme, tier, phase, active):
    P = STEEL[theme]
    lit = [0, 1, 1, 2][phase] if active else -1
    for i, (x, col) in enumerate(((9, hx("FFD23C")), (15, hx("9FB0C8")), (21, hx("8F7CFF")))):
        cv.rect(x, 22, x + 4, 26, P[0])
        cv.rect(x + 1, 23, x + 3, 25, col if i == lit else mix(col, 0.25))
        if i == lit:
            cv.px(x + 1, 23, hx("FFFFFF"))
        cv.hline(x, x + 4, 27, P[1])


# ------------------------------------------------------------------ blender
def blender_art(cv, box, tier, phase, active):
    P = STEEL["moon"]
    for (x0, x1), col in (((8, 10), GOLD_TANK), ((21, 23), BLUE_TANK)):    # the two source tanks
        cv.rect(x0, 5, x1, 16, hx("16181C"))
        cv.rect(x0, 9, x1, 16, col[0])
        cv.hline(x0, x1, 9, col[1])
        cv.vline(x0, 5, 16, hx("4A4F57"))
    cv.hline(11, 13, 7, GOLD_TANK[0])                                   # feed pipes into the bowl
    cv.hline(18, 20, 7, BLUE_TANK[0])
    cv.vline(13, 7, 10, GOLD_TANK[0])
    cv.vline(18, 7, 10, BLUE_TANK[0])
    cv.rect(12, 11, 19, 16, P[0])                                       # bowl
    cv.rect(13, 11, 18, 15, TWILIGHT[1])
    cv.hline(13, 18, 11, TWILIGHT[3])
    cv.vline(15, 5, 12, P[5])                                           # paddle shaft
    blades = [[(13, 13), (14, 13), (16, 13), (17, 13)], [(13, 14), (14, 13), (16, 13), (17, 12)],
              [(14, 13), (16, 13)], [(13, 12), (14, 13), (16, 13), (17, 14)]][phase if active else 0]
    cv.pts(blades, TWILIGHT[4])
    if active:
        for i, (x, y) in enumerate(((14, 14), (17, 14), (16, 12))):
            if (phase + i) % 2 == 0:
                cv.px(x, y, TWILIGHT[3])


def blender_lower(cv, theme, tier, phase, active):
    ring_port(cv, tier, (12, 22, 19, 27), TWILIGHT[1] if active else TWILIGHT[0])


# ------------------------------------------------------------------ animal breeder
HEART = ["..##.##..", ".#######.", ".#######.", "..#####..", "...###...", "....#...."]


def breeder_art(cv, box, tier, phase, active):
    cv.rect(6, 5, 25, 16, HAY[1])
    for y in range(5, 17):
        for x in range(6, 26):
            r = b.hsh(x, y, 9) % 7
            if r == 0:
                cv.px(x, y, HAY[0])
            elif r == 1:
                cv.px(x, y, HAY[2])
    cv.hline(6, 25, 16, HAY[0])
    cv.rect(10, 13, 21, 15, WOOD[0])                                    # trough
    cv.hline(10, 21, 13, WOOD[3])
    cv.rect(11, 14, 20, 14, WOOD[1])
    for x in range(11, 21, 2):                                          # wheat heaped in it
        cv.px(x, 12, WHEAT)
        cv.px(x + 1, 12, mix(WHEAT, 0.8))
    hot = active and phase % 2 == 0
    for r, row in enumerate(HEART):
        for c, ch in enumerate(row):
            if ch == "#":
                col = hx("F2455C") if (active or True) else hx("F2455C")
                cv.px(11 + c, 6 + r, col if active else mix(col, 0.45))
    if hot:
        cv.pts([(12, 7), (13, 7)], hx("FF9CAA"))
        cv.pts([(9, 6), (21, 6), (10, 9)], hx("FFE0E6"))
    cv.rect(7, 10, 9, 12, hx("E8A0B0"))                                 # a pig on the left
    cv.pts([(7, 9), (9, 9)], hx("C97088"))
    cv.px(8, 11, hx("B05870"))
    cv.rect(22, 10, 24, 12, hx("F2F2F2"))                               # a cow on the right
    cv.pts([(22, 9), (24, 9)], hx("2A2A2A"))
    cv.px(23, 10, hx("2A2A2A"))
    cv.px(24, 12, hx("2A2A2A"))


def breeder_lower(cv, theme, tier, phase, active):
    P = STEEL[theme]
    cv.rect(11, 22, 20, 27, P[0])
    cv.rect(12, 23, 19, 26, HAY[1])
    for x in range(12, 20, 2):
        cv.px(x, 24, WHEAT)
        cv.px(x + 1, 25, HAY[3])
    cv.hline(11, 20, 27, accent(tier)[2])


# ------------------------------------------------------------------ repairer
def repairer_art(cv, box, tier, phase, active):
    P = STEEL["sun"]
    iron, light = hx("5A5F68"), hx("A0A6B0")
    cv.rect(12, 14, 19, 16, iron)
    cv.hline(12, 19, 14, P[3])
    cv.rect(14, 12, 17, 13, iron)
    cv.rect(10, 10, 21, 11, iron)
    cv.hline(10, 21, 10, light)
    cv.pts([(9, 10), (8, 10)], light)
    cv.pts([(22, 11), (22, 10)], mix(iron, 0.8))
    struck = active and phase == 2
    if active:
        hx0, hy0 = (15, 7) if struck else (17, 4)
    else:
        hx0, hy0 = 18, 5
    cv.rect(hx0, hy0, hx0 + 3, hy0 + 2, hx("8A8F98"))
    cv.hline(hx0, hx0 + 3, hy0, hx("C9CDD2"))
    for k in range(5):                                                  # handle running off to the upper right
        cv.px(hx0 + 4 + k, hy0 + 1 - min(k, 2) // 1, WOOD[2])
    if struck:
        cv.pts([(13, 9), (18, 9), (12, 8), (19, 8), (16, 8)], hx("FFF6B0"))
    bob = 0 if (not active or phase % 2) else 1
    ox, oy = 11, 6 + bob
    for y in range(oy - 2, oy + 3):
        for x in range(ox - 2, ox + 3):
            d = math.hypot(x - ox, y - oy)
            if d <= 1.9:
                cv.px(x, y, (XP[2] if d > 0.9 else XP[3]) if active else mix(XP[1], 0.5))


def repairer_lower(cv, theme, tier, phase, active):
    P = STEEL[theme]
    for x in (8, 18):
        cv.rect(x, 22, x + 5, 27, P[0])
        cv.rect(x + 1, 23, x + 4, 26, hx("0A0B0D"))
        cv.hline(x + 1, x + 4, 23, P[4])
        cv.hline(x, x + 5, 27, accent(tier)[2])
    cv.px(15, 25, XP[2] if active else mix(XP[1], 0.4))
    cv.px(16, 25, XP[2] if active else mix(XP[1], 0.4))


# ------------------------------------------------------------------ sifter
def sifter_art(cv, box, tier, phase, active):
    mesh = MESH[tier]
    shake = (phase % 2) if active else 0
    for x in range(8, 24):                                              # mound of material on the mesh
        h = max(0, 3 - abs(x - 15) // 2)
        for k in range(h):
            cv.px(x + shake, 8 - 1 - k, hx("7A7570") if (x + k) % 3 else hx("5E5A56"))
    cv.hline(7, 24, 8, mesh)
    for x in range(7 + shake, 25, 2):
        cv.px(x, 9, mesh)
    cv.hline(7, 24, 8, mesh)
    cv.rect(6, 8, 6, 10, STEEL["sun"][4])                               # frame ends
    cv.rect(25, 8, 25, 10, STEEL["sun"][4])
    cols = (9, 12, 15, 18, 21)
    for i, x in enumerate(cols):
        if active:
            y = 10 + ((phase * 2 + i * 3) % 5)
            cv.px(x, y, GRAINS[i % len(GRAINS)])
            cv.px(x + 1, (y + 2 - 10) % 5 + 10, GRAINS[(i + 2) % len(GRAINS)])
    for x in range(8, 24):
        cv.px(x, 16, GRAINS[0] if x % 3 else GRAINS[(x // 3) % len(GRAINS)])
        if x % 2:
            cv.px(x, 15, GRAINS[0])


def sifter_lower(cv, theme, tier, phase, active):
    P = STEEL[theme]
    cv.rect(10, 22, 21, 27, P[0])
    cv.rect(11, 23, 20, 26, hx("0A0B0D"))
    cv.hline(11, 20, 23, P[4])
    for x in (13, 16, 18):
        cv.px(x, 26, GRAINS[x % len(GRAINS)])
    cv.hline(10, 21, 27, accent(tier)[2])


# ------------------------------------------------------------------ magnet
def magnet_art(cv, box, tier, phase, active):
    P = STEEL["moon"]
    cv.rect(9, 9, 22, 17, hx("0A0912"))
    for r, col in ((7, hx("2A3050")), (5, hx("3E4778")), (3, hx("5662A8"))):
        for ang in range(200, 341, 3):
            x = 15.5 + r * math.cos(math.radians(ang)) * 1.5
            y = 17 + r * math.sin(math.radians(ang)) * 0.9
            if 9 <= x <= 22 and 9 <= y <= 17:
                cv.px(int(round(x)), int(round(y)), col if not active or (ang // 3 + phase) % 4 else b.MOON)
    cv.rect(13, 14, 14, 16, hx("D83A3A"))
    cv.rect(17, 14, 18, 16, hx("3A6AD8"))
    cv.hline(13, 18, 13, hx("B7BBC2"))
    cv.hline(13, 18, 17, hx("B7BBC2"))
    for i, ang in enumerate((200, 235, 305, 340)):                      # items drifting in
        r = 7.0 - ((phase + i) % 4) * 1.6 if active else 7.0 - i * 0.4
        x = 15.5 + r * math.cos(math.radians(ang)) * 1.5
        y = 17 + r * math.sin(math.radians(ang)) * 0.9
        col = GRAINS[i % len(GRAINS)]
        cv.px(int(round(x)), int(round(y)), col)
        cv.px(int(round(x)) + 1, int(round(y)), mix(col, 0.8))


def magnet_lower(cv, theme, tier, phase, active):
    ring_port(cv, tier, (12, 22, 19, 27))


ART = {"flux_crafter": (crafter_art, crafter_lower), "flux_purifier": (purifier_art, purifier_lower),
       "moon_spawner": (spawner_art, spawner_lower), "sky_controller": (sky_art, sky_lower),
       "flux_blender": (blender_art, blender_lower), "animal_breeder": (breeder_art, breeder_lower),
       "flux_repairer": (repairer_art, repairer_lower), "flux_sifter": (sifter_art, sifter_lower),
       "flux_magnet": (magnet_art, magnet_lower)}


def front(m, tier, phase=0, active=False):
    theme = THEME[m]
    cv = Canvas()
    plate(cv, theme, tier, seed=SEED[m])
    box = WIN[m]
    recess(cv, theme, box, DARK)
    art, lower = ART[m]
    art(cv, box, tier, phase, active)
    lower(cv, theme, tier, phase, active)
    return cv


# ------------------------------------------------------------------ sides, backs, tops
def slats(m, tier):
    cv = Canvas()
    plate(cv, THEME[m], tier, seed=SEED[m] + 1)
    P = STEEL[THEME[m]]
    for y in (6, 9, 12, 15):
        cv.rect(7, y, 24, y + 1, P[0])
        cv.hline(7, 24, y + 2, P[4])
    b_port(cv, tier, THEME[m])
    return cv


def b_port(cv, tier, theme):
    P = STEEL[theme]
    a = accent(tier)
    cv.rect(12, 22, 19, 27, P[0])
    cv.rect(13, 23, 18, 26, a[2])
    cv.rect(14, 24, 17, 25, hx("0A0B0D"))
    cv.hline(13, 18, 23, a[3])


def pipe_side(m, tier):
    cv = Canvas()
    plate(cv, THEME[m], tier, seed=SEED[m] + 1)
    P = STEEL[THEME[m]]
    cv.rect(13, 5, 18, 17, P[0])
    cv.rect(14, 5, 17, 17, AQUA[1])
    cv.vline(14, 5, 17, AQUA[3])
    cv.vline(17, 5, 17, AQUA[0])
    for y in (7, 12, 16):
        cv.rect(12, y, 19, y + 1, P[4])
        cv.hline(12, 19, y, P[5])
    b_port(cv, tier, THEME[m])
    return cv


def plank_side(m, tier):
    cv = Canvas()
    plate(cv, THEME[m], tier, seed=SEED[m] + 1)
    recess(cv, THEME[m], (8, 5, 23, 16), WOOD[1])
    for y in (8, 11, 14):
        cv.hline(8, 23, y, WOOD[0])
    for y in range(5, 17):
        cv.px(8 + (y * 5) % 16, y, WOOD[2])
    b_port(cv, tier, THEME[m])
    return cv


SIDES = {"flux_crafter": slats, "flux_purifier": pipe_side, "animal_breeder": plank_side,
         "flux_repairer": slats, "flux_sifter": slats}


def side(m, tier):
    if m in SIDES:
        return SIDES[m](m, tier)
    return b.side(m, tier)


def top(m, tier):
    cv = b.top(m, tier)
    P = STEEL[THEME[m]]
    a = accent(tier)
    if m == "flux_crafter":
        for i in range(9):
            x, y = 12 + (i % 3) * 3, 12 + (i // 3) * 3
            cv.rect(x, y, x + 1, y + 1, a[2] if i == 4 else P[3])
    elif m == "flux_purifier":
        cv.rect(13, 13, 18, 18, AQUA[1])
        cv.rect(14, 14, 17, 17, AQUA[3])
        cv.px(14, 14, AQUA[4])
        for x, y in ((12, 12), (19, 12), (12, 19), (19, 19)):
            cv.px(x, y, AQUA[2])
    elif m == "moon_spawner":
        cv.rect(12, 14, 19, 17, hx("17121F"))
        cv.rect(13, 14, 18, 17, EYE)
        cv.rect(15, 15, 16, 16, hx("0A0810"))
        cv.px(13, 14, hx("FFB0BC"))
    elif m == "sky_controller":
        cv.rect(11, 11, 15, 20, hx("FFD23C"))
        cv.rect(16, 11, 20, 20, hx("5662A8"))
        cv.rect(12, 12, 13, 13, hx("FFF3A0"))
        cv.px(18, 13, hx("EAF2FF"))
    elif m == "flux_blender":
        cv.rect(12, 12, 19, 19, TWILIGHT[1])
        cv.rect(13, 13, 18, 18, TWILIGHT[2])
        cv.hline(13, 18, 15, TWILIGHT[4])
        cv.vline(15, 13, 18, TWILIGHT[4])
        cv.pts([(13, 13), (18, 18)], TWILIGHT[3])
    elif m == "animal_breeder":
        cv.rect(11, 11, 20, 20, HAY[1])
        for r, row in enumerate(HEART):
            for c, ch in enumerate(row):
                if ch == "#":
                    cv.px(11 + c, 12 + r, hx("F2455C"))
    elif m == "flux_repairer":
        cv.rect(11, 11, 20, 20, hx("0C0D10"))
        cv.rect(13, 13, 18, 18, XP[1])
        cv.rect(14, 14, 17, 17, XP[2])
        cv.px(14, 14, XP[3])
    return cv


def bottom(m, tier):
    return b.bottom(m, tier)


def back(m, tier):
    return b.back(m, tier)


def tray_texture(tier):
    """The sifter's tray floor, seen from above: a woven mesh in the tier's mesh color over a dark bin."""
    cv = Canvas()
    P = STEEL["sun"]
    cv.rect(0, 0, 31, 31, P[0])
    cv.rect(1, 1, 30, 30, P[3])
    cv.rect(2, 2, 29, 29, hx("0A0B0D"))
    mesh = MESH[tier]
    for y in range(2, 30):
        for x in range(2, 30):
            if y % 3 == 0 and x % 3 != 1:
                cv.px(x, y, mesh)
            elif x % 3 == 0 and y % 3 != 1:
                cv.px(x, y, mix(mesh, 0.72))
    return cv


def magnet_floor(tier):
    cv = Canvas()
    P = STEEL["moon"]
    a = accent(tier)
    cv.rect(0, 0, 31, 31, P[0])
    cv.rect(1, 1, 30, 30, P[2])
    cv.rect(2, 2, 29, 29, hx("0A0912"))
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if abs(d - 6) < 0.6 or abs(d - 10) < 0.6:
                cv.px(x, y, a[1] if int(d) == 6 else mix(a[2], 0.8))
    return cv


def solid(base, hi, lo, stripe=None):
    cv = Canvas()
    cv.rect(0, 0, 31, 31, base)
    cv.hline(0, 31, 0, hi)
    cv.vline(0, 0, 31, hi)
    cv.hline(0, 31, 31, lo)
    cv.vline(31, 0, 31, lo)
    for y in range(32):
        for x in range(32):
            if b.hsh(x, y, 4) % 11 == 0:
                cv.px(x, y, mix(base, 0.88))
    if stripe:
        cv.rect(0, 12, 31, 19, stripe)
    return cv


# ------------------------------------------------------------------ lamps
LAMP = {"sun_lamp": [hx(c) for c in ("6B3D00", "C47A00", "FFB52E", "FFE27A", "FFFBE0")],
        "moon_lamp": [hx(c) for c in ("1B1B5A", "3D3FB0", "7C84FF", "BCC8FF", "F2F5FF")]}


def lamp_crystal(name, active, phase=0):
    cv = Canvas()
    C = LAMP[name]
    cv.rect(0, 0, 31, 31, C[0] if not active else C[1])
    for x in range(1, 31):                                              # vertical facets with a diagonal sheen
        band = (x // 6) % 3
        col = (C[2], C[3], C[2])[band] if active else (C[1], C[2], C[1])[band]
        cv.vline(x, 1, 30, col)
    for y in range(1, 31):
        for x in range(1, 31):
            if (x + y + phase * 4) % 16 in (0, 1):
                cv.px(x, y, C[4] if active else C[3])
    cv.rect(0, 0, 31, 0, C[0])
    cv.rect(0, 31, 31, 31, C[0])
    cv.rect(0, 0, 0, 31, C[0])
    cv.rect(31, 0, 31, 31, C[0])
    return cv


def lamp_base(name, tier, top_face=False):
    theme = "sun" if name == "sun_lamp" else "moon"
    cv = Canvas()
    plate(cv, theme, tier, seed=5 if name == "sun_lamp" else 6, band=False)
    a = accent(tier)
    P = STEEL[theme]
    if not top_face:
        cv.hline(2, 29, 22, a[2])
        cv.hline(2, 29, 23, a[1])
    else:
        cv.rect(10, 10, 21, 21, P[0])
        cv.rect(11, 11, 20, 20, a[2])
        cv.rect(13, 13, 18, 18, P[1])
    return cv


def lamp_cap(name, tier):
    theme = "sun" if name == "sun_lamp" else "moon"
    cv = Canvas()
    plate(cv, theme, tier, seed=7, band=False)
    a = accent(tier)
    C = LAMP[name]
    cv.rect(9, 9, 22, 22, STEEL[theme][0])
    cv.rect(10, 10, 21, 21, a[2])
    cv.rect(12, 12, 19, 19, C[2])
    cv.rect(14, 14, 17, 17, C[4])
    return cv


def lamp_elements():
    def p(frm, to, **faces):
        return b.pbox(frm, to, faces)
    allf = lambda t: dict(n=t, s=t, e=t, w=t, u=t, d=t)
    els = [p((0, 0, 0), (16, 3, 16), n="base", s="base", e="base", w="base", u="basetop", d="base"),
           p((5, 3, 5), (11, 6, 11), **allf("post")),
           p((4.5, 6, 4.5), (11.5, 14, 11.5), **allf("crystal"))]
    for x, z in ((3, 3), (11.5, 3), (3, 11.5), (11.5, 11.5)):
        els.append(p((x, 6, z), (x + 1.5, 14, z + 1.5), **allf("post")))
    els.append(p((2, 14, 2), (14, 16, 14), n="cap", s="cap", e="cap", w="cap", u="captop", d="cap"))
    return els


def write_lamps():
    for name in LAMP:
        base = {"parent": "minecraft:block/block", "elements": lamp_elements()}
        b.write_json(os.path.join(b.MOD, f"{name}_base.json"), base)
        for tier in b.TIERS:
            s = sfx(tier)
            for lit in (False, True):
                tex = {"base": f"{NS}:block/{name}_base{s}", "basetop": f"{NS}:block/{name}_basetop{s}",
                       "cap": f"{NS}:block/{name}_cap{s}", "captop": f"{NS}:block/{name}_captop{s}",
                       "post": f"{NS}:block/{name}_post",
                       "crystal": f"{NS}:block/{name}_crystal{'_active' if lit else ''}"}
                tex["particle"] = tex["base"]
                b.write_json(os.path.join(b.MOD, f"{name}{'_active' if lit else ''}{s}.json"),
                             {"parent": f"{NS}:block/{name}_base", "textures": tex})
            nm = f"{name}{s}"
            b.write_json(os.path.join(OUT, "models", "item", nm + ".json"), {"parent": f"{NS}:block/{nm}"})
            b.write_json(os.path.join(OUT, "items", nm + ".json"),
                         {"model": {"type": "minecraft:model", "model": f"{NS}:item/{nm}"}})
            b.write_json(os.path.join(OUT, "blockstates", nm + ".json"),
                         {"variants": {"lit=false": {"model": f"{NS}:block/{nm}"},
                                       "lit=true": {"model": f"{NS}:block/{name}_active{s}"}}})


# ------------------------------------------------------------------ machine models
def pit_elements(m):
    floor = PIT[m]
    ox0, ox1, oy0, oy1 = b.window_model_box(m)
    d, rim = 1.5, 1.5
    els = [b.pbox((0, 0, d), (16, floor, 16), dict(n="front", s="back", e="side", w="side", u="tray", d="bottom")),
           b.std((0, 0, 0), (ox0, 16, d)), b.std((ox1, 0, 0), (16, 16, d)),
           b.std((ox0, 0, 0), (ox1, oy0, d)), b.std((ox0, oy1, 0), (ox1, 16, d))]
    side_all = dict(n="side", s="side", e="side", w="side", u="top", d="bottom")
    els += [b.pbox((0, floor, d), (rim, 16, 16), side_all), b.pbox((16 - rim, floor, d), (16, 16, 16), side_all),
            b.pbox((rim, floor, 16 - rim), (16 - rim, 16, 16), side_all),
            b.pbox((rim, floor, d), (16 - rim, 16, d + rim), side_all)]
    if m == "flux_magnet":                                              # horseshoe lying in the bowl, poles toward the front
        y0, y1 = floor, floor + 2.5
        els += [b.pbox((3, y0, 5.5), (6, y1, 13), dict(n="steel", s="steel", e="steel", w="steel", u="steel", d="steel")),
                b.pbox((10, y0, 5.5), (13, y1, 13), dict(n="steel", s="steel", e="steel", w="steel", u="steel", d="steel")),
                b.pbox((6, y0, 10), (10, y1, 13), dict(n="steel", s="steel", e="steel", w="steel", u="steel", d="steel")),
                b.pbox((3, y0, 3.5), (6, y1, 5.5), dict(n="red", s="red", e="red", w="red", u="red", d="red")),
                b.pbox((10, y0, 3.5), (13, y1, 5.5), dict(n="blue", s="blue", e="blue", w="blue", u="blue", d="blue"))]
    return els


def elements(m):
    if m in PIT:
        return pit_elements(m)
    return b.elements(m) if m not in THEME else _std_elements(m)


def _std_elements(m):
    ox0, ox1, oy0, oy1 = b.window_model_box(m)
    d = 1.5
    return [b.std((0, 0, d), (16, 16, 16)), b.std((0, 0, 0), (ox0, 16, d)), b.std((ox1, 0, 0), (16, 16, d)),
            b.std((ox0, 0, 0), (ox1, oy0, d)), b.std((ox0, oy1, 0), (ox1, 16, d))]


def write_models():
    for m in MACHINE_ORDER:
        b.write_json(os.path.join(b.MOD, f"{m}_base.json"), {"parent": "minecraft:block/block", "elements": elements(m)})
        for tier in b.TIERS:
            s = sfx(tier)
            for active in (False, True):
                tex = {k: f"{NS}:block/{m}_{k}{s}" for k in ("side", "back", "top", "bottom")}
                tex["front"] = f"{NS}:block/{m}_front{'_active' if active else ''}{s}"
                tex["particle"] = tex["side"]
                if m in PIT:
                    tex["tray"] = f"{NS}:block/{m}_tray{s}"
                if m == "flux_magnet":
                    tex.update({"steel": f"{NS}:block/flux_magnet_steel", "red": f"{NS}:block/flux_magnet_red",
                                "blue": f"{NS}:block/flux_magnet_blue"})
                b.write_json(os.path.join(b.MOD, f"{m}{'_active' if active else ''}{s}.json"),
                             {"parent": f"{NS}:block/{m}_base", "textures": tex})


# ------------------------------------------------------------------ assemble
def main():
    for d in (b.TEX, b.MOD, b.SHP):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    for m in MACHINE_ORDER:
        for tier in b.TIERS:
            s = sfx(tier)
            front(m, tier).save(f"{m}_front{s}")
            side(m, tier).save(f"{m}_side{s}")
            back(m, tier).save(f"{m}_back{s}")
            top(m, tier).save(f"{m}_top{s}")
            bottom(m, tier).save(f"{m}_bottom{s}")
            strip = Image.new("RGBA", (32, 32 * b.FRAMES))
            for p in range(b.FRAMES):
                strip.paste(front(m, tier, p, True).im, (0, p * 32))
            strip.save(os.path.join(b.TEX, f"{m}_front_active{s}.png"))
            b.write_json(os.path.join(b.TEX, f"{m}_front_active{s}.png.mcmeta"), {"animation": {"frametime": 4}})
            if m == "flux_sifter":
                tray_texture(tier).save("flux_sifter_tray" + s)
            if m == "flux_magnet":
                magnet_floor(tier).save("flux_magnet_tray" + s)
    solid(hx("8A8F98"), hx("C9CDD2"), hx("4A4F57")).save("flux_magnet_steel")
    solid(hx("C93030"), hx("FF7A7A"), hx("7A1818")).save("flux_magnet_red")
    solid(hx("2F5FC9"), hx("7AA8FF"), hx("183A7A")).save("flux_magnet_blue")
    for name in LAMP:
        theme = "sun" if name == "sun_lamp" else "moon"
        P = STEEL[theme]
        solid(P[2], P[4], P[0]).save(f"{name}_post")
        lamp_crystal(name, False).save(f"{name}_crystal")
        strip = Image.new("RGBA", (32, 32 * b.FRAMES))
        for p in range(b.FRAMES):
            strip.paste(lamp_crystal(name, True, p).im, (0, p * 32))
        strip.save(os.path.join(b.TEX, f"{name}_crystal_active.png"))
        b.write_json(os.path.join(b.TEX, f"{name}_crystal_active.png.mcmeta"), {"animation": {"frametime": 6}})
        for tier in b.TIERS:
            s = sfx(tier)
            lamp_base(name, tier).save(f"{name}_base{s}")
            lamp_base(name, tier, True).save(f"{name}_basetop{s}")
            lamp_cap(name, tier).save(f"{name}_cap{s}")
            lamp_cap(name, tier).save(f"{name}_captop{s}")
    write_models()
    b.write_items_and_states()                                          # rebuilds items/blockstates for the machines
    write_lamps()
    shapes = {m: [[[0, 0, 0], [16, 16, 16]]] for m in MACHINE_ORDER}
    shapes.update({n: [[[0, 0, 0], [16, 16, 16]]] for n in LAMP})
    b.write_json(os.path.join(b.SHP, "machine_shapes_v4.json"), shapes)
    print("new machines:", len(os.listdir(b.TEX)), "texture files,", len(os.listdir(b.MOD)), "models")


if __name__ == "__main__":
    main()
