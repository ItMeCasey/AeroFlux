package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel;

/** Generates the item and block models. */
public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, AeroFlux.MODID);
    }

    // The collector, tank, pipe, wrench and machine models, block states and client items are
    // hand-made static files (the machine art ships every tier with its own), so only the blocks
    // and items listed below are expected from the generator.
    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return Stream.of(ModBlocks.AERO_FLUX, ModBlocks.MOON_FLUX);
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return Stream.of(ModItems.FLUX_DUST, ModItems.SAWDUST, ModItems.AERO_FLUX_BUCKET, ModItems.MOON_FLUX_BUCKET);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.FLUX_DUST.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SAWDUST.get(), ModelTemplates.FLAT_ITEM);

        fluidBlock(blockModels, ModBlocks.AERO_FLUX.get());
        fluidBlock(blockModels, ModBlocks.MOON_FLUX.get());

        fluidBucket(itemModels, ModItems.AERO_FLUX_BUCKET.get(), ModFluids.AERO_FLUX.get());
        fluidBucket(itemModels, ModItems.MOON_FLUX_BUCKET.get(), ModFluids.MOON_FLUX.get());
    }

    /**
     * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
     * texture (the break and splash particles) from the shared still texture.
     */
    private static void fluidBlock(BlockModelGenerators blockModels, Block block) {
        var model = ModelTemplates.PARTICLE_ONLY.create(block,
                TextureMapping.particle(new Material(Ids.mod("block/flux_still"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
    }

    /**
     * A bucket that draws the fluid it holds, tinted, through NeoForge's dynamic container model.
     * The base and the two masks are the same ones NeoForge uses for its own buckets.
     */
    private static void fluidBucket(ItemModelGenerators itemModels, Item bucket, Fluid fluid) {
        Material base = new Material(Ids.of("minecraft", "item/bucket"));
        DynamicFluidContainerModel.Textures textures = new DynamicFluidContainerModel.Textures(
                Optional.of(base),
                Optional.of(base),
                Optional.of(new Material(Ids.of("neoforge", "item/mask/bucket_fluid"))),
                Optional.of(new Material(Ids.of("neoforge", "item/mask/bucket_fluid_cover"))));
        itemModels.itemModelOutput.accept(bucket,
                new DynamicFluidContainerModel.Unbaked(textures, fluid, false, true, true));
    }
}
