package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.Ids;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/** The tags AeroFlux defines, so packs can change what its blocks act on. */
public final class ModTags {
    /** Plants a lit Sun Lamp helps grow. */
    public static final TagKey<Block> SUN_LAMP_PLANTS = TagKey.create(Registries.BLOCK, Ids.mod("sun_lamp_plants"));
    /** Plants a lit Moon Lamp helps grow: the ones that like the dark. */
    public static final TagKey<Block> MOON_LAMP_PLANTS = TagKey.create(Registries.BLOCK, Ids.mod("moon_lamp_plants"));

    /** The common fluid tags other mods filter Flux by, as PLAN.md promises them. */
    public static final TagKey<Fluid> AERO_FLUX = TagKey.create(Registries.FLUID, Ids.of("c", "aero_flux"));
    public static final TagKey<Fluid> MOON_FLUX = TagKey.create(Registries.FLUID, Ids.of("c", "moon_flux"));
    public static final TagKey<Fluid> TWILIGHT_FLUX = TagKey.create(Registries.FLUID, Ids.of("c", "twilight_flux"));

    private ModTags() {
    }
}
