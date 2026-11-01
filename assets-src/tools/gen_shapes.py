"""Derives each block's hit/collision bounds from its model boxes.

Writes ../shapes/shapes.json (block id -> boxes in 0-16 space, north-facing) and
../shapes/ModShapes.java (ready to drop into the 1.21.1 registry package). Run: python gen_shapes.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS = os.path.join(HERE, "..", "aeroflux", "models", "block")
OUT = os.path.join(HERE, "..", "shapes")
os.makedirs(OUT, exist_ok=True)

FULL_CUBES = {"flux_furnace", "flux_grinder", "alloy_smelter", "pipe_node"}
# Tanks are open frames in the model, but players expect a solid block to hit and stand against.
FULL_CUBE_PREFIX = ("copper_tank", "iron_tank", "gold_tank", "diamond_tank", "netherite_tank")
FACING = {"flux_sawmill", "flux_infuser", "flux_enchanter", "flux_disenchanter", "plant_grower"}
TIERS = ("copper", "iron", "gold", "diamond", "netherite", "fluxium")


def boxes_for(name):
    if name in FULL_CUBES or name.startswith(FULL_CUBE_PREFIX):
        return [[0, 0, 0, 16, 16, 16]]
    with open(os.path.join(MODELS, name + ".json"), encoding="utf-8") as f:
        model = json.load(f)
    out = []
    for el in model["elements"]:
        a, b = el["from"], el["to"]
        if any(x == y for x, y in zip(a, b)):
            continue  # flat planes such as sprouts are not solid
        out.append([a[0], a[1], a[2], b[0], b[1], b[2]])
    return out


def fmt(v):
    return str(int(v)) if float(v).is_integer() else str(v)


def main():
    # every tier shares one silhouette, so collectors and tanks each get a single shape
    names = ["flux_furnace", "flux_grinder", "flux_sawmill", "alloy_smelter", "flux_infuser",
             "flux_enchanter", "flux_disenchanter", "plant_grower", "chunk_loader", "pipe_node"]
    shapes = {"collector": boxes_for("copper_collector"), "tank": [[0, 0, 0, 16, 16, 16]]}
    shapes.update({n: boxes_for(n) for n in names})
    shapes["collector"] = [b for b in shapes["collector"] if b[4] - b[1] > 0.5]  # drop the thin dish plate
    with open(os.path.join(OUT, "shapes.json"), "w", encoding="utf-8") as f:
        json.dump({"facing_rotated": sorted(FACING), "boxes": shapes}, f, indent=2)
        f.write("\n")

    lines = [
        "package com.itmecasey.aeroflux.registry;",
        "",
        "import net.minecraft.core.Direction;",
        "import net.minecraft.world.level.block.Block;",
        "import net.minecraft.world.phys.shapes.Shapes;",
        "import net.minecraft.world.phys.shapes.VoxelShape;",
        "",
        "/**",
        " * Hit and collision bounds for the blocks that are not plain cubes, taken from the block models",
        " * in assets-src. Boxes are in 0-16 space and written for a block facing north; use",
        " * {@link #rotated(VoxelShape, Direction)} for blocks with a facing.",
        " */",
        "public final class ModShapes {",
    ]
    for name, boxes in shapes.items():
        const = name.upper()
        parts = ",\n            ".join(
            f"Block.box({', '.join(fmt(v) for v in b)})" for b in boxes)
        if len(boxes) == 1:
            lines.append(f"    public static final VoxelShape {const} = {parts};")
        else:
            lines.append(f"    public static final VoxelShape {const} = Shapes.or(\n            {parts});")
        lines.append("")
    lines += [
        "    private ModShapes() {",
        "    }",
        "",
        "    /** Turns a north-facing shape to face the given horizontal direction. */",
        "    public static VoxelShape rotated(VoxelShape north, Direction facing) {",
        "        int quarterTurns = switch (facing) {",
        "            case EAST -> 1;",
        "            case SOUTH -> 2;",
        "            case WEST -> 3;",
        "            default -> 0;",
        "        };",
        "        VoxelShape result = north;",
        "        for (int i = 0; i < quarterTurns; i++) {",
        "            VoxelShape turned = Shapes.empty();",
        "            for (var box : result.toAabbs()) {",
        "                turned = Shapes.or(turned, Shapes.box(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX));",
        "            }",
        "            result = turned;",
        "        }",
        "        return result;",
        "    }",
        "}",
        "",
    ]
    with open(os.path.join(OUT, "ModShapes.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("shapes", len(shapes))


if __name__ == "__main__":
    main()
