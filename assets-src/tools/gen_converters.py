"""The three converter machines, in the same Mekanism-style casing family as gen_machines_v3:
  flux_energy_converter  Flux in, Forge Energy out (flux tanks in the window, copper terminals)
  flux_ae_converter      Flux in, Applied Energistics 2 power out (fluix crystal, cable-style port)
  flux_stress_converter  Flux in, Create rotation out (a 4x4 px open shaft hole centered on the front)

Reuses the casing grammar, model writer, tiers and item/blockstate layout from gen_machines_v3 and
writes to assets-src/aeroflux_v3. Run: python gen_converters.py
"""
import math
import os
import shutil

from PIL import Image

import gen_assets as g
import gen_machines_v3 as b
from gen_machines_v3 import STEEL, Canvas, accent, hsh, hx, mix, plate, recess, sfx

OUT = os.path.join(b.HERE, "..", "aeroflux_v3")
b.OUT = OUT
b.TEX = os.path.join(OUT, "textures", "block")
b.MOD = os.path.join(OUT, "models", "block")
b.SHP = os.path.join(OUT, "shapes")

NEW = {
    "flux_energy_converter": ("sun", (6, 5, 25, 16)),
    "flux_ae_converter": ("moon", (9, 4, 22, 17)),
    "flux_stress_converter": ("sun", (12, 12, 19, 19)),      # 8x8 texture px = a 4x4 px model hole
}
for _m, (_theme, _win) in NEW.items():
    b.THEME[_m] = _theme
    b.WIN[_m] = _win
b.MACHINES = list(NEW)

COPPER = [hx(c) for c in ("5A2E1A", "8E4B32", "C86F4A", "E39A6E", "FFD2A8")]
FLUIX = [hx(c) for c in ("2A1A4A", "5B3A9C", "8F63DB", "C3A0FF", "F0E6FF")]
QUARTZ = hx("E8E4F0")
SPARK = hx("FFF6B0")


# ------------------------------------------------------------------ energy converter
def energy_window(cv, box, phase, active):
    x0, y0, x1, y1 = box
    cv.rect(x0, y0, x1, y1, hx("0A0C0E"))
    levels = (0.55 + 0.35 * math.sin(phase * 1.6), 0.55 + 0.35 * math.sin(phase * 1.6 + 1.6)) if active else (0.5, 0.5)
    for (tx0, tx1), col, lv in (((x0 + 2, x0 + 7), (hx("F2B72A"), hx("FFF0A0")), levels[0]),
                                ((x1 - 7, x1 - 2), (hx("7FB4FF"), hx("E6F2FF")), levels[1])):
        cv.rect(tx0, y0 + 1, tx1, y1 - 1, hx("16181C"))                 # glass
        h = int((y1 - y0 - 3) * lv)
        cv.rect(tx0 + 1, y1 - 1 - h, tx1 - 1, y1 - 2, col[0])          # liquid
        cv.hline(tx0 + 1, tx1 - 1, y1 - 1 - h, col[1])                  # surface
        cv.vline(tx0, y0 + 1, y1 - 1, hx("4A4F57"))                    # glass edge
        cv.px(tx0 + 1, y0 + 2, hx("FFFFFF") if active else hx("8A8F98"))
    cx = (x0 + x1) // 2
    cv.rect(cx - 1, y0 + 1, cx, y1 - 1, COPPER[2])                      # copper bus between the tanks
    cv.vline(cx - 1, y0 + 1, y1 - 1, COPPER[3])
    if active:
        for i, y in enumerate(range(y0 + 2, y1 - 1, 2)):
            if (phase + i) % 2 == 0:
                cv.px(cx - 2 + (i % 2) * 3, y, SPARK)
                cv.px(cx - 3 + (i % 2) * 5, y + 1, hx("FFD040"))


def energy_terminals(cv, theme, active, phase):
    P = STEEL[theme]
    for i in range(4):
        x = 7 + i * 5
        cv.rect(x, 23, x + 3, 27, P[0])
        cv.rect(x + 1, 24, x + 2, 26, COPPER[2])
        cv.px(x + 1, 24, COPPER[4])
        if active and (phase + i) % 2 == 0:
            cv.rect(x + 1, 21, x + 2, 22, SPARK)


def energy_side(tier):
    cv = Canvas()
    theme = "sun"
    plate(cv, theme, tier, seed=21)
    P = STEEL[theme]
    recess(cv, theme, (7, 6, 24, 16), hx("0C0E10"))
    for y in range(7, 16):                                              # copper winding
        c = COPPER[3] if y % 3 == 0 else COPPER[2] if y % 3 == 1 else COPPER[1]
        cv.hline(8, 23, y, c)
    cv.vline(8, 7, 15, COPPER[0])
    cv.vline(23, 7, 15, COPPER[0])
    cv.rect(12, 22, 19, 27, P[0])
    cv.rect(13, 23, 18, 26, accent(tier)[2])
    cv.rect(14, 24, 17, 25, hx("0A0B0D"))
    return cv


def energy_back(tier):
    cv = Canvas()
    plate(cv, "sun", tier, seed=22)
    P = STEEL["sun"]
    for i in range(3):                                                  # three output plugs
        x = 6 + i * 8
        cv.rect(x, 8, x + 5, 18, P[0])
        cv.rect(x + 1, 9, x + 4, 17, COPPER[2])
        cv.rect(x + 1, 9, x + 1, 17, COPPER[3])
        cv.rect(x + 2, 11, x + 3, 15, hx("0A0B0D"))
    cv.rect(8, 22, 23, 27, P[1])
    cv.hline(8, 23, 22, P[4])
    return cv


def energy_top(tier):
    cv = b.top("flux_energy_converter", tier)
    cx = cy = 15.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if 3.5 <= d <= 6.5:
                cv.px(x, y, COPPER[2] if int(d * 2) % 2 else COPPER[1])
    cv.rect(14, 13, 17, 18, hx("F2B72A"))                               # bolt
    cv.px(15, 13, hx("FFF0A0"))
    cv.rect(12, 16, 17, 17, hx("F2B72A"))
    return cv


# ------------------------------------------------------------------ AE converter
def gem(cv, cx, cy, r, active, phase):
    """A faceted fluix crystal, lit from the upper left."""
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r, cx + r + 1):
            dx, dy = x - cx, y - cy
            if abs(dx) / r + abs(dy) / (r + 1) <= 1.0:
                if dx < -dy * 0.2 and dy < 0:
                    c = FLUIX[3]
                elif dx >= 0 and dy < 0:
                    c = FLUIX[2]
                elif dx < 0:
                    c = FLUIX[2]
                else:
                    c = FLUIX[1]
                cv.px(x, y, c)
    cv.px(cx - 1, cy - 2, FLUIX[4])
    cv.px(cx - 2, cy - 1, FLUIX[4])
    cv.vline(cx, cy - r, cy + r, FLUIX[1])
    if active:
        s = (phase % 2)
        cv.pts([(cx - r - 2, cy - 2 + s), (cx + r + 2, cy + 1 - s), (cx + 1, cy - r - 2), (cx - 2, cy + r + 2 - s)], FLUIX[4])


def ae_window(cv, box, phase, active):
    x0, y0, x1, y1 = box
    cv.rect(x0, y0, x1, y1, hx("0B0914"))
    if active:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                d = math.hypot(x - 15.5, y - 10.5)
                if d < 6.5:
                    cv.px(x, y, mix(FLUIX[1], 0.9 - d * 0.08))
    gem(cv, 15, 10, 4, active, phase)


def ae_port(cv, active, phase):
    """A cable-style connector: a quartz-white square sleeve with a fluix core."""
    cv.rect(10, 22, 21, 28, hx("14121C"))
    cv.rect(11, 23, 20, 27, QUARTZ)
    cv.hline(11, 20, 23, hx("FFFFFF"))
    cv.hline(11, 20, 27, hx("A9A4B8"))
    cv.rect(13, 24, 18, 26, FLUIX[0])
    cv.rect(14, 25, 17, 25, FLUIX[3] if (active and phase % 2 == 0) else FLUIX[2])
    cv.pts([(8, 25), (9, 25), (22, 25), (23, 25)], FLUIX[2] if active else FLUIX[1])


def ae_side(tier):
    cv = Canvas()
    plate(cv, "moon", tier, seed=31)
    P = STEEL["moon"]
    recess(cv, "moon", (11, 6, 20, 16), hx("0B0914"))
    for i, y in enumerate((8, 11, 14)):
        cv.rect(13, y, 18, y + 1, FLUIX[2 + (i % 2)])
    cv.rect(12, 22, 19, 27, P[0])
    cv.rect(13, 23, 18, 26, QUARTZ)
    cv.rect(14, 24, 17, 25, FLUIX[0])
    return cv


def ae_back(tier):
    cv = Canvas()
    plate(cv, "moon", tier, seed=32)
    P = STEEL["moon"]
    cv.rect(9, 7, 22, 20, P[0])
    cv.rect(10, 8, 21, 19, QUARTZ)
    cv.hline(10, 21, 8, hx("FFFFFF"))
    cv.rect(12, 10, 19, 17, hx("14121C"))
    cv.rect(14, 12, 17, 15, FLUIX[2])
    cv.rect(15, 13, 16, 14, FLUIX[4])
    for x, y in ((11, 9), (20, 9), (11, 18), (20, 18)):
        cv.px(x, y, hx("A9A4B8"))
    cv.rect(8, 22, 23, 27, P[1])
    cv.hline(8, 23, 22, P[4])
    return cv


def ae_top(tier):
    cv = b.top("flux_ae_converter", tier)
    gem(cv, 15, 15, 5, False, 0)
    return cv


# ------------------------------------------------------------------ stress converter
def cog(cv, tier, phase, active):
    """A brass cog around the 8x8 shaft hole: solid body, dark outline, lit top-left edge."""
    o, d, bb, l, gl = accent(tier)
    cx = cy = 15.5
    teeth = 8
    rot = phase * math.pi / (teeth * 4) if active else 0.0
    body = set()
    for y in range(32):
        for x in range(32):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            tooth = math.cos((math.atan2(dy, dx) + rot) * teeth) > 0.25
            if 5.6 <= dist <= (11.6 if tooth else 9.4):
                body.add((x, y))
    for x, y in body:
        cv.px(x, y, bb)
    for x, y in body:
        up, left = (x, y - 1) not in body, (x - 1, y) not in body
        down, right = (x, y + 1) not in body, (x + 1, y) not in body
        if up or left:
            cv.px(x, y, l)
        if down or right:
            cv.px(x, y, d)
    for x, y in body:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in body and not (5.6 > math.hypot(x + dx - cx, y + dy - cy)):
                cv.px(x + dx, y + dy, o)
    cv.px(10, 7, gl)


def stress_front(tier, phase=0, active=False):
    cv = Canvas()
    plate(cv, "sun", tier, seed=41)
    cog(cv, tier, phase, active)
    b.recess(cv, "sun", b.WIN["flux_stress_converter"], hx("050607"))
    return cv


def stress_side(tier):
    cv = Canvas()
    plate(cv, "sun", tier, seed=42)
    o, d, bb, l, gl = accent(tier)
    P = STEEL["sun"]
    recess(cv, "sun", (7, 6, 24, 16), hx("0C0E10"))
    for cx, r in ((12, 3), (19, 4)):                                    # two meshing gears
        for y in range(6, 17):
            for x in range(7, 25):
                dd = math.hypot(x - cx, y - 11)
                if dd <= r + (0.7 if (x + y) % 2 else 0):
                    cv.px(x, y, bb if dd > 1.2 else hx("0C0E10"))
    cv.rect(12, 22, 19, 27, P[0])
    cv.rect(13, 23, 18, 26, accent(tier)[2])
    cv.rect(14, 24, 17, 25, hx("0A0B0D"))
    return cv


def stress_back(tier):
    cv = b.back("flux_stress_converter", tier)
    return cv


def stress_top(tier):
    cv = b.top("flux_stress_converter", tier)
    o, d, bb, l, gl = accent(tier)
    for y in range(32):
        for x in range(32):
            dd = math.hypot(x - 15.5, y - 15.5)
            if 3 <= dd <= 5.2:
                cv.px(x, y, bb)
    return cv


# ------------------------------------------------------------------ assemble
def front(m, tier, phase=0, active=False):
    if m == "flux_stress_converter":
        return stress_front(tier, phase, active)
    cv = Canvas()
    theme = b.THEME[m]
    plate(cv, theme, tier, seed=hash(m) % 17)
    box = b.WIN[m]
    recess(cv, theme, box, hx("0C0E10"))
    if m == "flux_energy_converter":
        energy_window(cv, box, phase, active)
        energy_terminals(cv, theme, active, phase)
    else:
        ae_window(cv, box, phase, active)
        ae_port(cv, active, phase)
    return cv


FACES = {
    "flux_energy_converter": (energy_side, energy_back, energy_top),
    "flux_ae_converter": (ae_side, ae_back, ae_top),
    "flux_stress_converter": (stress_side, stress_back, stress_top),
}


def main():
    for d in (b.TEX, b.MOD, b.SHP):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    for m in NEW:
        side, back, top = FACES[m]
        for tier in b.TIERS:
            s = sfx(tier)
            front(m, tier).save(f"{m}_front{s}")
            side(tier).save(f"{m}_side{s}")
            back(tier).save(f"{m}_back{s}")
            top(tier).save(f"{m}_top{s}")
            b.bottom(m, tier).save(f"{m}_bottom{s}")
            strip = Image.new("RGBA", (32, 32 * b.FRAMES))
            for p in range(b.FRAMES):
                strip.paste(front(m, tier, p, True).im, (0, p * 32))
            strip.save(os.path.join(b.TEX, f"{m}_front_active{s}.png"))
            b.write_json(os.path.join(b.TEX, f"{m}_front_active{s}.png.mcmeta"), {"animation": {"frametime": 4}})
    b.write_models()
    b.write_items_and_states()
    b.write_shapes()
    print("converters:", len(os.listdir(b.TEX)), "texture files,", len(os.listdir(b.MOD)), "models")


if __name__ == "__main__":
    main()
