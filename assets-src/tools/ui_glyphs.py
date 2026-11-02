"""The glyph set for the AeroFlux screens: 13x13 pixel pictures, one lit/unlit bit per pixel, so the
game can tint them any color. Saved as white-on-transparent 16x16 PNGs by ui_mockups.py."""
import math


class Grid:
    def __init__(self, n=13):
        self.n = n
        self.on = set()

    def dot(self, x, y):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.on.add((x, y))

    def ring(self, cx, cy, r, skip=None, thick=0.55):
        for y in range(self.n):
            for x in range(self.n):
                if abs(math.hypot(x - cx, y - cy) - r) <= thick:
                    ang = math.degrees(math.atan2(-(y - cy), x - cx)) % 360
                    if skip and skip[0] <= ang <= skip[1]:
                        continue
                    self.dot(x, y)

    def mirror(self):
        g = Grid(self.n)
        g.on = {(self.n - 1 - x, y) for x, y in self.on}
        return g


def bitmap(rows):
    """A glyph from rows of 13 characters; '#' is lit. Short pictures are centered vertically."""
    for r in rows:
        assert len(r) == 13, (len(r), r)
    pad = 13 - len(rows)
    rows = ["." * 13] * (pad // 2) + list(rows) + ["." * 13] * (pad - pad // 2)
    g = Grid(13)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                g.dot(x, y)
    return g


BITMAPS = {
    # what a pipe or machine side carries
    "items": [
        ".###########.",
        ".#.........#.",
        ".#.........#.",
        ".###########.",
        ".#....#....#.",
        ".#...###...#.",
        ".#...###...#.",
        ".#.........#.",
        ".###########.",
    ],
    "fluid": [
        "......#......",
        "......#......",
        ".....###.....",
        ".....###.....",
        "....#####....",
        "...#######...",
        "...#######...",
        "..#########..",
        "..#########..",
        "..#########..",
        "...#######...",
        "....#####....",
    ],
    "energy": [
        "........###..",
        ".......###...",
        "......###....",
        ".....###.....",
        "....#######..",
        "....#######..",
        ".......###...",
        "......###....",
        ".....###.....",
        "....###......",
        "...###.......",
        "..###........",
    ],
    "gas": [
        "..#...#...#..",
        ".#...#...#...",
        ".#...#...#...",
        "..#...#...#..",
        "...#...#...#.",
        "...#...#...#.",
        "..#...#...#..",
        ".#...#...#...",
        ".#...#...#...",
        "..#...#...#..",
        "...#...#...#.",
        "...#...#...#.",
    ],
    "redstone": [
        "......#......",
        "..#...#...#..",
        "...#..#..#...",
        "....#.#.#....",
        ".....###.....",
        ".###########.",
        ".....###.....",
        "....#.#.#....",
        "...#..#..#...",
        "..#...#...#..",
        "......#......",
    ],
    "me": [
        "....#####....",
        "...#.....#...",
        "..#.......#..",
        ".#.........#.",
        ".#....#....#.",
        ".#...###...#.",
        ".#....#....#.",
        ".#.........#.",
        "..#.......#..",
        "...#.....#...",
        "....#####....",
    ],
    "dense_me": [
        "....#####....",
        "...#.....#...",
        "..#...#...#..",
        ".#...###...#.",
        ".#....#....#.",
        ".#.#.....#.#.",
        ".#...###...#.",
        ".#....#....#.",
        "..#.......#..",
        "...#.....#...",
        "....#####....",
    ],
    # what a face does
    "mode_open": [
        "....#........",
        "...##........",
        "..#########..",
        "...##........",
        "....#........",
        ".............",
        "........#....",
        "........##...",
        "..#########..",
        "........##...",
        "........#....",
    ],
    "mode_import": [
        "..........##.",
        "..........##.",
        ".....#....##.",
        "......#...##.",
        ".......#..##.",
        "#########.##.",
        ".......#..##.",
        "......#...##.",
        ".....#....##.",
        "..........##.",
        "..........##.",
    ],
    "mode_export": [
        ".##..........",
        ".##..........",
        ".##......#...",
        ".##.......#..",
        ".##........#.",
        ".##.#########",
        ".##........#.",
        ".##.......#..",
        ".##......#...",
        ".##..........",
        ".##..........",
    ],
    "mode_off": [
        "....#####....",
        "..##.....##..",
        ".#.........#.",
        ".#.........#.",
        "#...........#",
        "#...........#",
        "#...........#",
        ".#.........#.",
        ".#.........#.",
        "..##.....##..",
        "....#####....",
    ],
    "mode_blocked": [
        "##.........##",
        "###.......###",
        ".###.....###.",
        "..###...###..",
        "...###.###...",
        "....#####....",
        ".....###.....",
        "....#####....",
        "...###.###...",
        "..###...###..",
        ".###.....###.",
        "###.......###",
        "##.........##",
    ],
    # how a pipe picks where to deliver
    "dist_first_to_last": [
        "..#....#####.",
        "..#....#####.",
        "..#....#####.",
        "..#..........",
        "..#....#####.",
        "..#....#...#.",
        "..#....#####.",
        "..#..........",
        "#.#.#..#####.",
        ".###...#...#.",
        "..#....#####.",
    ],
    "dist_random": [
        ".###########.",
        ".#.........#.",
        ".#.##...##.#.",
        ".#.##...##.#.",
        ".#.........#.",
        ".#....##...#.",
        ".#....##...#.",
        ".#.........#.",
        ".#.##...##.#.",
        ".#.##...##.#.",
        ".#.........#.",
        ".###########.",
    ],
    # buttons and small labels
    "action_all_off": [
        ".....###.....",
        ".....###.....",
        ".#...###...#.",
        "##...###...##",
        "#....###....#",
        "#....###....#",
        "#...........#",
        "##.........##",
        ".##.......##.",
        "..###...###..",
        "....#####....",
    ],
    "action_copy": [
        ".....###.....",
        "......#......",
        "......#......",
        "......#......",
        ".............",
        "#...#####...#",
        "###.#####.###",
        "#...#####...#",
        ".............",
        "......#......",
        "......#......",
        "......#......",
        ".....###.....",
    ],
    "upgrade_speed": [
        ".#....#......",
        ".##...##.....",
        "..##...##....",
        "...##...##...",
        "....##...##..",
        ".....##...##.",
        "....##...##..",
        "...##...##...",
        "..##...##....",
        ".##...##.....",
        ".#....#......",
    ],
    "upgrade_capacity": [
        ".###########.",
        ".#.........#.",
        ".###########.",
        ".............",
        ".###########.",
        ".#.........#.",
        ".###########.",
        ".............",
        ".###########.",
        ".#.........#.",
        ".###########.",
    ],
    "ticks": [
        "....#####....",
        "..##..#..##..",
        ".#....#....#.",
        ".#....#....#.",
        "#.....#.....#",
        "#.....###...#",
        "#...........#",
        ".#.........#.",
        ".#.........#.",
        "..##.....##..",
        "....#####....",
    ],
}


def _cycle(clockwise=True):
    """A circular arrow: a ring open at the upper right with an arrowhead where it ends."""
    g = Grid(13)
    g.ring(6, 6, 4.6, skip=(22, 68), thick=0.55)
    for x, y in ((8, 4), (9, 4), (10, 4), (11, 4), (12, 4), (9, 5), (10, 5), (11, 5), (10, 6)):
        g.dot(x, y)
    return g if clockwise else g.mirror()


GLYPHS = {name: (lambda rows=rows: bitmap(rows)) for name, rows in BITMAPS.items()}
GLYPHS["dist_last_to_first"] = lambda: bitmap(BITMAPS["dist_first_to_last"][::-1])
GLYPHS["dist_round_robin"] = lambda: _cycle(True)
GLYPHS["action_reset"] = lambda: _cycle(False)


def _ring_glyph(radius, extra=()):
    g = Grid(13)
    g.ring(6, 6, radius, thick=0.55)
    for p in extra:
        g.dot(*p)
    return g


def _disk(g, cx, cy, r):
    for y in range(13):
        for x in range(13):
            if math.hypot(x - cx, y - cy) <= r:
                g.dot(x, y)


def _sun(core=2.6, keep=lambda x: True):
    """A sun: a round core and eight short rays. ``keep`` picks the columns to draw."""
    g = Grid(13)
    _disk(g, 6, 6, core)
    for i in range(8):
        a = math.radians(i * 45)
        for d in (core + 1.8, core + 2.8):
            g.dot(round(6 + d * math.cos(a)), round(6 - d * math.sin(a)))
    g.on = {(x, y) for x, y in g.on if keep(x)}
    return g


def _moon():
    """A crescent moon, horns to the right: a disk with a smaller disk cut out of its upper right."""
    g = Grid(13)
    _disk(g, 6, 6, 5.2)
    cut = Grid(13)
    _disk(cut, 8.6, 4.6, 4.2)
    g.on -= cut.on
    return g


def _moon_half():
    """A crescent in the right half, horns to the left, for the badge of a machine that takes either flux."""
    g = Grid(13)
    _disk(g, 6, 6, 5.6)
    cut = Grid(13)
    _disk(cut, 4.2, 6, 4.6)
    g.on = {(x, y) for x, y in g.on - cut.on if x >= 7}
    return g


GLYPHS["flux_sun"] = lambda: _sun()
GLYPHS["flux_moon"] = _moon
# the two halves of the "either flux" badge, drawn in separate colors with a gap column between
GLYPHS["flux_sun_half"] = lambda: _sun(3.4, lambda x: x <= 5)
GLYPHS["flux_moon_half"] = _moon_half

BITMAPS_SKY = {
    # open sky: a check mark
    "sky_clear": [
        "...........##",
        "..........##.",
        ".........##..",
        "........##...",
        "##.....##....",
        ".##...##.....",
        "..##.##......",
        "...###.......",
        "....#........",
    ],
    # weather: a cloud with rain under it
    "weather": [
        ".....###.....",
        "...##...##...",
        "..#.......#..",
        ".#.........#.",
        "#...........#",
        ".###########.",
        ".............",
        "..#...#...#..",
        ".#...#...#...",
        "#...#...#....",
    ],
}
for _name, _rows in BITMAPS_SKY.items():
    GLYPHS[_name] = lambda rows=_rows: bitmap(rows)

# round, not oval: generated rather than typed
GLYPHS["mode_off"] = lambda: _ring_glyph(5.0)
GLYPHS["ticks"] = lambda: _ring_glyph(5.2, [(6, 3), (6, 4), (6, 5), (6, 6), (7, 6), (8, 7), (9, 8)])
