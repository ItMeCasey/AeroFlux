package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.FluidTagsProvider;

/**
 * Generates the fluid tags: {@code c:aero_flux}, {@code c:moon_flux} and {@code c:twilight_flux},
 * each with the still and the flowing fluid, so other mods' tanks, pipes and filters can name Flux
 * without knowing AeroFlux.
 */
public class ModFluidTagProvider extends FluidTagsProvider {
    public ModFluidTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, AeroFlux.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(ModTags.AERO_FLUX).add(ModFluids.AERO_FLUX.get(), ModFluids.FLOWING_AERO_FLUX.get());
        tag(ModTags.MOON_FLUX).add(ModFluids.MOON_FLUX.get(), ModFluids.FLOWING_MOON_FLUX.get());
        tag(ModTags.TWILIGHT_FLUX).add(ModFluids.TWILIGHT_FLUX.get(), ModFluids.FLOWING_TWILIGHT_FLUX.get());
    }
}
