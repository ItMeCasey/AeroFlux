"""Tier upgrade cards: a circuit card with the target tier's metal around it, a chip, traces and an
up arrow. One per tier above copper (iron, gold, diamond, netherite, fluxium). 32x32 item textures,
hand-placed pixels, same steel as the machine casings.

Output: assets-src/aeroflux_v2/{textures/item, models/item, items}/upgrade_card_<tier>
"""
import json
import os

from PIL import Image

import gen_assets as g
from gen_machines_v3 import STEEL, Canvas, accent, hx, mix

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "aeroflux_v2")
TEX = os.path.join(OUT, "textures", "item")
MOD = os.path.join(OUT, "models", "item")
ITM = os.path.join(OUT, "items")
for d in (TEX, MOD, ITM):
    os.makedirs(d, exist_ok=True)

TARGETS = [t for t in g.TIERS if t != "copper"]

# card body spans x 3..28, y 6..25 (26 x 20)
X0, Y0, X1, Y1 = 3, 6, 28, 25


def card(tier):
    P = STEEL["sun"]
    o, d, b, l, gl = accent(tier)
    if tier in ("netherite", "fluxium"):      # the dark metals need a lift to read on a dark card
        b, l = mix(b, 1.7), mix(l, 1.7)
        d = mix(d, 1.5)
    cv = Canvas()
    # outline with the corners nicked, then the tier-metal border and the dark plate
    cv.rect(X0, Y0, X1, Y1, P[0])
    for x, y in ((X0, Y0), (X1, Y0), (X0, Y1), (X1, Y1)):
        cv.px(x, y, (0, 0, 0, 0))
    cv.rect(X0 + 1, Y0 + 1, X1 - 1, Y1 - 1, b)
    for x, y in ((X0 + 1, Y0 + 1), (X1 - 1, Y0 + 1), (X0 + 1, Y1 - 1), (X1 - 1, Y1 - 1)):
        cv.px(x, y, P[0])
    cv.hline(X0 + 2, X1 - 2, Y0 + 1, l)
    cv.vline(X0 + 1, Y0 + 3, Y1 - 3, l)
    cv.hline(X0 + 2, X1 - 2, Y1 - 1, d)
    cv.vline(X1 - 1, Y0 + 3, Y1 - 3, d)
    cv.rect(X0 + 3, Y0 + 3, X1 - 3, Y1 - 3, P[1])
    for y in range(Y0 + 3, Y1 - 2):
        for x in range(X0 + 3, X1 - 2):
            if (x * 7 + y * 13) % 11 == 0:
                cv.px(x, y, P[2])
    cv.hline(X0 + 3, X1 - 3, Y0 + 3, P[2])

    # the chip, left: a dark package with pins and a glowing tier-colored die
    cv.rect(7, 11, 14, 20, P[0])
    cv.rect(8, 12, 13, 19, P[2])
    cv.rect(9, 13, 12, 18, o)
    cv.rect(10, 14, 11, 17, l)
    cv.px(10, 14, gl)
    for y in (12, 15, 18):
        cv.px(6, y, d)
        cv.px(15, y, d)

    # traces from the chip to the arrow: dark groove with a tier-colored core
    for pts in (((16, 12), (18, 12), (18, 14)), ((16, 15), (19, 15)), ((16, 18), (18, 18), (18, 16))):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            cv.rect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), d)
    cv.pts([(18, 14), (18, 15), (18, 16), (19, 15)], b)

    # the up arrow, right: tier light with a dark edge
    arrow = [(24, 9)] + [(x, 10) for x in (23, 24, 25)] + [(x, 11) for x in (22, 23, 24, 25, 26)] + \
            [(x, 12) for x in (21, 22, 23, 24, 25, 26, 27)] + [(x, y) for y in range(13, 21) for x in (23, 24, 25)]
    for x, y in arrow:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if (x + dx, y + dy) not in arrow:
                cv.px(x + dx, y + dy, P[0])
    for x, y in arrow:
        cv.px(x, y, l)
    for x, y in arrow:
        if (x + 1, y) not in arrow or (x, y + 1) not in arrow:
            cv.px(x, y, b)
    cv.px(24, 9, gl)
    cv.px(23, 11, gl)
    return cv


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    sheet = Image.new("RGBA", (len(TARGETS) * 196 + 8, 196), (58, 60, 66, 255))
    for i, tier in enumerate(TARGETS):
        name = f"upgrade_card_{tier}"
        cv = card(tier)
        cv.im.save(os.path.join(TEX, name + ".png"))
        write_json(os.path.join(MOD, name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"aeroflux:item/{name}"}})
        write_json(os.path.join(ITM, name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"aeroflux:item/{name}"}})
        sheet.alpha_composite(cv.im.resize((180, 180), Image.NEAREST), (8 + i * 196, 8))
    sheet.save(os.path.join(HERE, "..", "upgrade_cards_preview.png"))
    print("cards:", len(TARGETS))


if __name__ == "__main__":
    main()
