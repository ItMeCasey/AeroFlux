package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Generates block states and block models. */
public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AeroFlux.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        fluidBlock(ModBlocks.AERO_FLUX.get(), "aero_flux");
        fluidBlock(ModBlocks.MOON_FLUX.get(), "moon_flux");
    }

    /**
     * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
     * texture (the break and splash particles) from the shared still texture.
     */
    private void fluidBlock(Block block, String name) {
        ModelFile model = models().getBuilder("block/" + name)
                .texture("particle", ResourceLocation.fromNamespaceAndPath(AeroFlux.MODID, "block/flux_still"));
        simpleBlock(block, model);
    }
}
