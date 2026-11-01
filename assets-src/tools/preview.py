"""Draws quick isometric preview sheets of the block and item models, each from two sides.

Rough on purpose: it ignores element rotation and translucency. The north-west view shows the
north, west and top faces; the south-east view shows the south, east and top faces, so every
side of a model can be checked. Run: python preview.py
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "aeroflux")
S = 3.6  # screen pixels per model pixel
CELL = 270


def load_model(kind, name):
    with open(os.path.join(ROOT, "models", kind, name + ".json"), encoding="utf-8") as f:
        data = json.load(f)
    parent = data.get("parent")
    if parent == "minecraft:block/orientable":
        data["elements"] = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
            "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#top"}}}]
    elif parent == "minecraft:block/cube_all":
        data["textures"] = {"a": data["textures"]["all"]}
        data["elements"] = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            f: {"texture": "#a"} for f in ("north", "south", "east", "west", "up", "down")}}]
    return data


def tex_image(ref):
    return Image.open(os.path.join(ROOT, "textures", ref.split(":")[-1] + ".png")).convert("RGBA")


def make_project(view):
    def project(p):
        x, y, z = p
        if view == "nw":
            return ((z - x) * 0.866 * S + CELL / 2, CELL / 2 + 50 - (x + z) * 0.5 * S - y * S)
        return ((x - z) * 0.866 * S + CELL / 2, CELL / 2 - 40 + (x + z) * 0.5 * S - y * S)
    return project


def draw_face(canvas, project, texture, uv, corners):
    u0, v0, u1, v1 = uv
    crop = texture.crop((int(round(u0 * texture.width / 16)), int(round(v0 * texture.height / 16)),
                         int(round(u1 * texture.width / 16)), int(round(v1 * texture.height / 16))))
    if crop.width < 1 or crop.height < 1:
        return
    scale = max(1, 96 // max(crop.width, crop.height))
    crop = crop.resize((crop.width * scale, crop.height * scale), Image.NEAREST)
    tl, tr, bl = (np.array(project(c)) for c in corners)
    w, h = crop.size
    m = np.array([[(tr - tl)[0] / w, (bl - tl)[0] / h], [(tr - tl)[1] / w, (bl - tl)[1] / h]])
    if abs(np.linalg.det(m)) < 1e-9:
        return
    inv = np.linalg.inv(m)
    off = -inv @ tl
    data = (inv[0, 0], inv[0, 1], off[0], inv[1, 0], inv[1, 1], off[1])
    canvas.alpha_composite(crop.transform(canvas.size, Image.AFFINE, data, resample=Image.NEAREST))


def render(kind, name, view):
    model = load_model(kind, name)
    textures = model["textures"]

    def resolve(ref):
        while ref.startswith("#"):
            ref = textures[ref[1:]]
        return tex_image(ref)

    faces = []
    for el in model["elements"]:
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        for side, spec in el["faces"].items():
            xc, yc, zc = (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2
            if view == "nw" and side == "north":
                uv = spec.get("uv", [16 - x1, 16 - y1, 16 - x0, 16 - y0])
                corners = ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)); key = xc + z0 - yc
            elif view == "nw" and side == "west":
                uv = spec.get("uv", [z0, 16 - y1, z1, 16 - y0])
                corners = ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)); key = x0 + zc - yc
            elif view == "se" and side == "south":
                uv = spec.get("uv", [x0, 16 - y1, x1, 16 - y0])
                corners = ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)); key = -(xc + z1 + yc)
            elif view == "se" and side == "east":
                uv = spec.get("uv", [16 - z1, 16 - y1, 16 - z0, 16 - y0])
                corners = ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)); key = -(x1 + zc + yc)
            elif side == "up":
                uv = spec.get("uv", [x0, z0, x1, z1])
                corners = ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1))
                key = (xc + zc - y1) if view == "nw" else -(xc + zc + y1)
            else:
                continue
            faces.append((key, resolve(spec["texture"]), uv, corners))
    faces.sort(key=lambda f: -f[0])
    canvas = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    project = make_project(view)
    for _, texture, uv, corners in faces:
        draw_face(canvas, project, texture, uv, corners)
    return canvas


def sheet(kind, names, out_name, cols):
    rows = (len(names) + cols - 1) // cols
    img = Image.new("RGBA", (cols * CELL * 2, rows * CELL), (88, 90, 94, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    for i, name in enumerate(names):
        x, y = (i % cols) * CELL * 2, (i // cols) * CELL
        img.alpha_composite(render(kind, name, "nw"), (x, y))
        img.alpha_composite(render(kind, name, "se"), (x + CELL, y))
        d.text((x + 6, y + 6), name, fill=(255, 255, 255, 255), font=font)
    img.convert("RGB").save(os.path.join(HERE, "..", out_name))


def model_names(kind, skip=()):
    folder = os.path.join(ROOT, "models", kind)
    names = []
    for n in sorted(os.listdir(folder)):
        base = n[:-5]
        with open(os.path.join(folder, n), encoding="utf-8") as f:
            data = json.load(f)
        if "elements" not in data and data.get("parent") not in (
                "minecraft:block/orientable", "minecraft:block/cube_all"):
            continue
        if base.startswith(skip):
            continue
        names.append(base)
    return names


def main():
    blocks = model_names("block", skip=("pipe_", "connector_")) + ["pipe_node"]
    blocks = sorted(set(blocks))
    sheet("block", blocks, "preview_blocks.png", 2)
    sheet("item", model_names("item"), "preview_items_3d.png", 2)
    sheet("block", ["pipe_fluid_core", "pipe_energy_arm", "connector_1", "connector_2", "connector_4"],
          "preview_pipe_parts.png", 2)
    print("blocks", len(blocks))


if __name__ == "__main__":
    main()
