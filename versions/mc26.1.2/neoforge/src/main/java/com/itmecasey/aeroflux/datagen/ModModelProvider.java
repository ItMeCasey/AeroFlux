package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Generates the item and block models. */
public class ModModelProvider extends ModelProvider {
    /** Turns the model by the way the block faces, the same as the game does for its own horizontal blocks. */
    private static final PropertyDispatch<VariantMutator> FACING = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
            .select(Direction.EAST, BlockModelGenerators.Y_ROT_90)
            .select(Direction.SOUTH, BlockModelGenerators.Y_ROT_180)
            .select(Direction.WEST, BlockModelGenerators.Y_ROT_270)
            .select(Direction.NORTH, BlockModelGenerators.NOP);

    public ModModelProvider(PackOutput output) {
        super(output, AeroFlux.MODID);
    }

    // The collector, tank, pipe, wrench and first-tier machine models, block states and client items
    // are hand-made static files (the machine art ships with its own), so only the blocks and items
    // listed below are expected from the generator.
    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return Stream.concat(Stream.of(ModBlocks.AERO_FLUX, ModBlocks.MOON_FLUX), upperTierMachines());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return Stream.concat(Stream.of(ModItems.FLUX_DUST, ModItems.AERO_FLUX_BUCKET, ModItems.MOON_FLUX_BUCKET),
                upperTierMachines().map(block -> BuiltInRegistries.ITEM.wrapAsHolder(block.get().asItem())));
    }

    /** The machine blocks of every tier but the first, which face the same way and share the first tier model. */
    private static Stream<DeferredBlock<FluxMachineBlock>> upperTierMachines() {
        return ModBlocks.machines().stream().filter(block -> block.get().tier() != Tier.COPPER);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.FLUX_DUST.get(), ModelTemplates.FLAT_ITEM);

        fluidBlock(blockModels, ModBlocks.AERO_FLUX.get());
        fluidBlock(blockModels, ModBlocks.MOON_FLUX.get());

        fluidBucket(itemModels, ModItems.AERO_FLUX_BUCKET.get(), ModFluids.AERO_FLUX.get());
        fluidBucket(itemModels, ModItems.MOON_FLUX_BUCKET.get(), ModFluids.MOON_FLUX.get());

        upperTierMachines().forEach(block -> upperTierMachine(blockModels, itemModels, block.get()));
    }

    /**
     * A machine above the first tier: it faces the way the first tier does and shows the first
     * tier model, block and item alike, so the art is delivered once.
     */
    private static void upperTierMachine(BlockModelGenerators blockModels, ItemModelGenerators itemModels, FluxMachineBlock block) {
        String id = block.machine().blockId();
        blockModels.blockStateOutput.accept(MultiVariantGenerator
                .dispatch(block, BlockModelGenerators.plainVariant(Ids.mod("block/" + id)))
                .with(FACING));
        itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(Ids.mod("item/" + id)));
    }

    /**
     * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
     * texture (the break and splash particles) from the shared still texture.
     */
    private static void fluidBlock(BlockModelGenerators blockModels, Block block) {
        Identifier model = ModelTemplates.PARTICLE_ONLY.create(block,
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
