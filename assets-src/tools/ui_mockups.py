"""Mockups of the reworked machine side-config panel and the pipe (node connector) screen, with
glyphs instead of words. Draws at GUI scale 1x in the vanilla gray style the mod already uses and
upscales with nearest neighbor. Also writes the glyphs as 16x16 white-on-transparent PNGs
(assets-src/ui/glyphs) so the game can tint them.

Run: python ui_mockups.py   Output: assets-src/ui/*.png
"""
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "ui")
GLY = os.path.join(OUT, "glyphs")
os.makedirs(GLY, exist_ok=True)

# the mod's current panel colors (PipeConfigScreen / Panel)
PANEL, LIGHT, SHADOW, EDGE = (0xC6,) * 3, (255, 255, 255), (0x55,) * 3, (0, 0, 0)
SLOT_FILL, SLOT_SHADOW = (0x8B,) * 3, (0x37,) * 3
TEXT = (0x40, 0x40, 0x40)


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


MODE_COLOR = {"import": hx("4A7FD8"), "export": hx("E8913A"), "open": hx("55B85A"),
              "off": hx("8A8A8A"), "blocked": hx("C04040")}
PIPE_COLOR = {"fluid": hx("C9CED3"), "item": hx("4B5057"), "redstone": hx("D8322C"), "energy": hx("E8731A"),
              "gas": hx("E6B422"), "me": hx("8B4FD8"), "dense_me": hx("5E2FA8")}
TIER_COLOR = {"copper": hx("C86F4A"), "iron": hx("B8BCC0"), "gold": hx("E8B930"), "diamond": hx("4DD9D0"),
              "netherite": hx("4A4046"), "fluxium": hx("3B404B")}


from ui_glyphs import GLYPHS  # noqa: E402


def save_glyphs():
    for name, fn in GLYPHS.items():
        g = fn()
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for x, y in g.on:
            im.putpixel((x + 1, y + 1), (255, 255, 255, 255))
        im.save(os.path.join(GLY, name + ".png"))


# ------------------------------------------------------------------ gui drawing
class Gui:
    def __init__(self, w, h):
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def fill(self, x0, y0, x1, y1, c):
        self.d.rectangle((x0, y0, x1 - 1, y1 - 1), fill=c + (255,) if len(c) == 3 else c)

    def raised(self, x, y, w, h, face=PANEL):
        r, b = x + w, y + h
        self.fill(x, y, r, b, EDGE)
        self.fill(x + 1, y + 1, r - 1, b - 1, LIGHT)
        self.fill(x + 2, y + 2, r - 1, b - 1, SHADOW)
        self.fill(x + 2, y + 2, r - 2, b - 2, face)

    def sunken(self, x, y, w, h, face=SLOT_FILL):
        self.fill(x, y, x + w, y + h, SLOT_SHADOW)
        self.fill(x + 1, y + 1, x + w, y + h, LIGHT)
        self.fill(x + 1, y + 1, x + w - 1, y + h - 1, face)

    def outline(self, x, y, w, h, c):
        self.d.rectangle((x, y, x + w - 1, y + h - 1), outline=c + (255,))

    def glyph(self, name, cx, cy, color):
        g = GLYPHS[name]()
        ox, oy = cx - g.n // 2, cy - g.n // 2
        for x, y in g.on:
            self.im.putpixel((ox + x, oy + y), color + (255,))

    def digits(self, text, x, y, color=TEXT):
        for ch in text:
            if ch == " ":
                x += 3
                continue
            for r, row in enumerate(DIG[ch]):
                for c, v in enumerate(row):
                    if v == "#":
                        self.im.putpixel((x + c, y + r), color + (255,))
            x += 4


DIG = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", "###", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", "..#", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    "/": ["..#", "..#", ".#.", "#..", "#.."], ".": ["...", "...", "...", "...", ".#."],
}


def scaled(gui, k, bg=(52, 54, 60, 255), pad=12):
    im = gui.im.resize((gui.im.width * k, gui.im.height * k), Image.NEAREST)
    out = Image.new("RGBA", (im.width + pad * 2, im.height + pad * 2), bg)
    out.alpha_composite(im, (pad, pad))
    return out


# ------------------------------------------------------------------ side config window
NET = {"top": (1, 0), "left": (0, 1), "front": (1, 1), "right": (2, 1), "bottom": (1, 2), "back": (2, 2)}


def side_window(resources, active, modes, color_of):
    """The 96 px wide window. modes: face name -> mode name."""
    W, H = 96, 126
    g = Gui(W, H)
    g.raised(0, 0, W, H)
    n = len(resources)
    t = (W - 8) // n                 # square tabs
    x0 = (W - n * t) // 2
    for i, res in enumerate(resources):
        x, y = x0 + i * t, 4
        if i == active:
            g.raised(x, y, t, t)
            g.fill(x + 3, y + t - 4, x + t - 2, y + t - 2, color_of[res])
            g.glyph(res, x + t // 2, y + t // 2 - 1, TEXT)
        else:
            g.sunken(x, y + 1, t - 1, t - 1)
            g.glyph(res, x + t // 2, y + t // 2 + 1, (0x30, 0x30, 0x30))
    cell, ox, oy = 22, (W - 66) // 2, 4 + t + 6
    for face, (c, r) in NET.items():
        x, y = ox + c * cell, oy + r * cell
        base = MODE_COLOR[modes[face]]
        g.fill(x, y, x + cell - 1, y + cell - 1, EDGE)
        g.fill(x + 1, y + 1, x + cell - 2, y + cell - 2, tuple(min(255, int(v * 1.25)) for v in base))
        g.fill(x + 2, y + 2, x + cell - 2, y + cell - 2, tuple(int(v * 0.72) for v in base))
        g.fill(x + 2, y + 2, x + cell - 3, y + cell - 3, base)
        if modes[face] == "blocked":
            g.glyph("mode_blocked", x + cell // 2, y + cell // 2, (255, 255, 255))
        if face == "front":
            g.fill(x + 3, y + 3, x + 6, y + 6, (255, 255, 255))   # marks the front
    by = oy + 3 * cell + 5
    for i, name in enumerate(("action_all_off", "action_copy", "action_reset")):
        x = (W - 74) // 2 + i * 26
        g.raised(x, by, 22, 22)
        g.glyph(name, x + 11, by + 11, TEXT)
    return g


# ------------------------------------------------------------------ pipe (node connector) screen
def mini_net(g, x, y, face, cell=6):
    for name, (c, r) in NET.items():
        cx, cy = x + c * cell, y + r * cell
        g.fill(cx, cy, cx + cell - 1, cy + cell - 1, (0x37, 0x37, 0x37))
        g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, (255, 255, 255) if name == face else (0x8B, 0x8B, 0x8B))


def slot(g, x, y, glyph=None):
    g.sunken(x, y, 18, 18)
    if glyph:
        g.glyph(glyph, x + 9, y + 9, (0x6E, 0x6E, 0x6E))


def pipe_screen(ptype, tier, face, mode, dist, rate, unit_glyph, upgrades):
    W = 176
    has_dist = dist is not None
    H = 178 + (32 if has_dist else 0) + (26 if upgrades else 0)
    g = Gui(W, H)
    g.raised(0, 0, W, H)
    accent = PIPE_COLOR[ptype]
    g.fill(2, 2, W - 2, 5, accent)
    # header: the pipe's glyph, its tier gem, and which face of the machine this is
    gl = {"fluid": "fluid", "item": "items", "redstone": "redstone", "energy": "energy", "gas": "gas",
          "me": "me", "dense_me": "dense_me"}[ptype]
    g.sunken(8, 8, 22, 22)
    g.glyph(gl, 19, 19, accent if ptype != "item" else (0x4B, 0x50, 0x57))
    tc = TIER_COLOR[tier]
    g.fill(36, 14, 45, 23, EDGE)
    g.fill(37, 15, 44, 22, tc)
    g.fill(37, 15, 44, 16, tuple(min(255, int(v * 1.3)) for v in tc))
    mini_net(g, W - 8 - 18, 10, face)
    y = 38
    # face mode: five square colored buttons
    for i, m in enumerate(("open", "import", "export", "off", "blocked")):
        x = 8 + i * 27
        c = MODE_COLOR[m]
        g.fill(x, y, x + 22, y + 22, EDGE)
        g.fill(x + 1, y + 1, x + 21, y + 21, tuple(min(255, int(v * 1.2)) for v in c))
        g.fill(x + 2, y + 2, x + 21, y + 21, tuple(int(v * 0.72) for v in c))
        g.fill(x + 2, y + 2, x + 20, y + 20, c)
        g.glyph("mode_" + m, x + 11, y + 11, (255, 255, 255))
        if m == mode:
            g.outline(x - 1, y - 1, 24, 24, (255, 255, 255))
            g.outline(x - 2, y - 2, 26, 26, EDGE)
    y += 32
    if has_dist:
        for i, d in enumerate(("round_robin", "first_to_last", "last_to_first", "random")):
            x = 8 + i * 27
            g.raised(x, y, 22, 22)
            g.glyph("dist_" + d, x + 11, y + 11, TEXT)
            if d == dist:
                g.outline(x - 1, y - 1, 24, 24, accent)
                g.outline(x - 2, y - 2, 26, 26, EDGE)
        y += 32
    # throughput: glyphs and numbers, a tier-length bar
    g.glyph(unit_glyph, 14, y + 6, accent if ptype != "item" else (0x4B, 0x50, 0x57))
    g.digits(rate[0], 22, y + 3)
    x = 22 + 4 * len(rate[0])
    if rate[1]:
        g.digits("/", x, y + 3)
        g.glyph("ticks", x + 11, y + 6, TEXT)
        g.digits(rate[1], x + 18, y + 3)
    seg = {"copper": 1, "iron": 2, "gold": 3, "diamond": 4, "netherite": 5, "fluxium": 6}[tier]
    for i in range(6):
        g.sunken(100 + i * 11, y + 1, 10, 8, tc if i < seg else (0x6B, 0x6B, 0x6B))
    y += 22
    if upgrades:
        g.glyph("upgrade_speed", 14, y + 9, TEXT)
        for i in range(2):
            slot(g, 24 + i * 19, y)
        g.glyph("upgrade_capacity", 96, y + 9, TEXT)
        for i in range(2):
            slot(g, 106 + i * 19, y)
        y += 26
    for r in range(3):
        for c in range(9):
            slot(g, 8 + c * 18, y + r * 18)
    for c in range(9):
        slot(g, 8 + c * 18, y + 58)
    return g


def main():
    save_glyphs()
    # glyph sheet
    names = list(GLYPHS)
    sheet = Gui(len(names) * 16 + 8, 40)
    sheet.fill(0, 0, sheet.im.width, 40, (0xC6,) * 3)
    for i, n in enumerate(names):
        sheet.glyph(n, 12 + i * 16, 12, TEXT)
        sheet.glyph(n, 12 + i * 16, 28, (0xD8, 0x32, 0x2C) if n == "redstone" else (0x30, 0x60, 0xB0))
    scaled(sheet, 3).save(os.path.join(OUT, "glyph_sheet.png"))

    res = ["items", "fluid", "energy", "gas", "redstone"]
    col = {"items": PIPE_COLOR["item"], "fluid": PIPE_COLOR["fluid"], "energy": PIPE_COLOR["energy"],
           "gas": PIPE_COLOR["gas"], "redstone": PIPE_COLOR["redstone"]}
    m1 = {"top": "off", "left": "import", "front": "open", "right": "export", "bottom": "blocked", "back": "off"}
    m2 = {"top": "open", "left": "export", "front": "export", "right": "off", "bottom": "import", "back": "blocked"}
    a = scaled(side_window(res, 0, m1, col), 5)
    b = scaled(side_window(res, 1, m2, col), 5)
    both = Image.new("RGBA", (a.width + b.width, a.height), (52, 54, 60, 255))
    both.alpha_composite(a, (0, 0))
    both.alpha_composite(b, (a.width, 0))
    both.save(os.path.join(OUT, "side_config_new.png"))

    p1 = scaled(pipe_screen("item", "gold", "front", "import", "round_robin", ("4", "20"), "items", True), 3)
    p2 = scaled(pipe_screen("fluid", "diamond", "top", "open", "first_to_last", ("800", ""), "fluid", True), 3)
    p3 = scaled(pipe_screen("redstone", "iron", "right", "export", None, ("16", ""), "redstone", False), 3)
    h = max(p1.height, p2.height, p3.height)
    sheet2 = Image.new("RGBA", (p1.width + p2.width + p3.width, h), (52, 54, 60, 255))
    x = 0
    for p in (p1, p2, p3):
        sheet2.alpha_composite(p, (x, 0))
        x += p.width
    sheet2.save(os.path.join(OUT, "pipe_screen_new.png"))
    print("ok")


if __name__ == "__main__":
    main()
