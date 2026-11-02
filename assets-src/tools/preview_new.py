"""Preview sheets for the aeroflux_v4 machines (own renderer: a layout check, not Minecraft lighting)."""
import os

from PIL import Image

import preview_machines as pm

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "aeroflux_v4")
pm.p.ROOT = ROOT
pm.MOD = os.path.join(ROOT, "models", "block")
TEX = os.path.join(ROOT, "textures", "block")
MACH = ["flux_crafter", "flux_purifier", "moon_spawner", "sky_controller", "flux_blender", "animal_breeder",
        "flux_repairer", "flux_sifter", "flux_magnet", "sun_lamp_active", "moon_lamp_active"]

if __name__ == "__main__":
    pm.tight_sheet(MACH, "preview_new_machines.png", per_row=2)
    pm.tight_sheet([f"flux_sifter{s}" for s in ("", "_iron", "_gold", "_diamond", "_netherite", "_fluxium")],
                   "preview_sifter_tiers.png", per_row=2, view_pair=("nw",))
    names = [m for m in MACH if not m.endswith("_active")]
    rows = ["flux_crafter", "flux_purifier", "moon_spawner", "sky_controller", "flux_blender", "animal_breeder",
            "flux_repairer", "flux_sifter", "flux_magnet"]
    sheet = Image.new("RGBA", (len(rows) * 4 * 100 + 10, 2 * 100 + 10 + 4 * 100), (58, 60, 66, 255))
    for i, m in enumerate(rows):
        strip = Image.open(os.path.join(TEX, f"{m}_front_active.png"))
        sheet.alpha_composite(Image.open(os.path.join(TEX, f"{m}_front.png")).resize((96, 96), Image.NEAREST), (5 + i * 100, 5))
        for f in range(4):
            sheet.alpha_composite(strip.crop((0, f * 32, 32, f * 32 + 32)).resize((96, 96), Image.NEAREST), (5 + i * 100, 105 + f * 100)) if False else None
    # front textures: idle on top, then the four active frames underneath
    W = len(rows) * 100 + 10
    sheet = Image.new("RGBA", (W, 5 * 100 + 10), (58, 60, 66, 255))
    for i, m in enumerate(rows):
        sheet.alpha_composite(Image.open(os.path.join(TEX, f"{m}_front.png")).resize((96, 96), Image.NEAREST), (5 + i * 100, 5))
        strip = Image.open(os.path.join(TEX, f"{m}_front_active.png"))
        for f in range(4):
            sheet.alpha_composite(strip.crop((0, f * 32, 32, f * 32 + 32)).resize((96, 96), Image.NEAREST), (5 + i * 100, 105 + f * 100))
    sheet.save(os.path.join(HERE, "..", "v4_fronts.png"))
    print("ok")
