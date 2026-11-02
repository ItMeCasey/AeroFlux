package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * Generates the item and block models. Forge's model provider covers vanilla content by
 * default, so each hook below narrows it down to AeroFlux's own items and blocks.
 *
 * <p>The collector, tank, pipe, wrench and machine models, block states and client items are
 * hand-made static files (the machine art ships every tier with its own), and so are the three
 * buckets, which use the loader's fluid container model. Only the blocks and items listed below
 * come from here.
 */
public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output);
    }

    @Override
    protected Stream<Block> getKnownBlocks() {
        return Stream.of(ModBlocks.AERO_FLUX.get(), ModBlocks.MOON_FLUX.get(), ModBlocks.TWILIGHT_FLUX.get());
    }

    @Override
    protected Stream<Item> getKnownItems() {
        return Stream.of(ModItems.FLUX_DUST.get(), ModItems.SAWDUST.get());
    }

    @Override
    protected BlockModelGenerators getBlockModelGenerators(BlockStateGeneratorCollector blocks,
                                                           ItemInfoCollector items, SimpleModelCollector models) {
        return new BlockModelGenerators(blocks, items, models) {
            @Override
            public void run() {
                fluidBlock(ModBlocks.AERO_FLUX.get());
                fluidBlock(ModBlocks.MOON_FLUX.get());
                fluidBlock(ModBlocks.TWILIGHT_FLUX.get());
            }

            /**
             * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
             * texture (the break and splash particles) from the shared still texture.
             */
            private void fluidBlock(Block block) {
                Identifier model = ModelTemplates.PARTICLE_ONLY.create(block,
                        TextureMapping.particle(new Material(Ids.mod("block/flux_still"))), modelOutput);
                blockStateOutput.accept(createSimpleBlock(block, plainVariant(model)));
            }
        };
    }

    @Override
    protected ItemModelGenerators getItemModelGenerators(ItemInfoCollector items, SimpleModelCollector models) {
        return new ItemModelGenerators(items, models) {
            @Override
            public void run() {
                generateFlatItem(ModItems.FLUX_DUST.get(), ModelTemplates.FLAT_ITEM);
                generateFlatItem(ModItems.SAWDUST.get(), ModelTemplates.FLAT_ITEM);
            }
        };
    }
}
