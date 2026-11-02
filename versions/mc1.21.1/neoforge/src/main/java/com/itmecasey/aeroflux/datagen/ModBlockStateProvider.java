package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Generates the block states and models of the fluid blocks. Every other block's art, all six
 * tiers of it, is delivered by hand in {@code src/main/resources}.
 */
public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AeroFlux.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        fluidBlock(ModBlocks.AERO_FLUX.get(), "aero_flux");
        fluidBlock(ModBlocks.MOON_FLUX.get(), "moon_flux");
        fluidBlock(ModBlocks.TWILIGHT_FLUX.get(), "twilight_flux");
    }

    private void fluidBlock(Block block, String name) {
        ModelFile model = models().getBuilder("block/" + name)
                .texture("particle", ResourceLocation.fromNamespaceAndPath(AeroFlux.MODID, "block/flux_still"));
        simpleBlock(block, model);
    }
}
