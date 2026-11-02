package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Generates the item and block models. Forge's model provider covers vanilla content by
 * default, so each hook below narrows it down to AeroFlux's own items and blocks.
 *
 * <p>The two buckets are not generated here: they use the loader's fluid container model, which
 * is a hand-written model file under {@code src/main/resources}.
 */
public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected Stream<Block> getKnownBlocks() {
        return Stream.concat(Stream.of(ModBlocks.AERO_FLUX.get(), ModBlocks.MOON_FLUX.get()), sharedModelMachines().map(Block.class::cast));
    }

    @Override
    protected Stream<Item> getKnownItems() {
        return Stream.concat(Stream.of(ModItems.FLUX_DUST.get()), sharedModelMachines().map(Block::asItem));
    }

    /**
     * The machine blocks of the higher tiers. The first tier's blockstate and models are delivered
     * with the art (assets-src); the others face the same way and share the first tier's model, so
     * only their blockstates and item definitions are generated.
     */
    private static Stream<Block> sharedModelMachines() {
        return Stream.of(MachineType.values())
                .flatMap(machine -> Stream.of(Tier.values())
                        .filter(tier -> tier != Tier.COPPER)
                        .map(tier -> (Block) ModBlocks.machine(machine, tier).get()));
    }

    @Override
    protected BlockModelGenerators getBlockModelGenerators(BlockStateGeneratorCollector blocks,
                                                           ItemInfoCollector items, SimpleModelCollector models) {
        return new BlockModelGenerators(blocks, items, models) {
            @Override
            public void run() {
                fluidBlock(ModBlocks.AERO_FLUX.get());
                fluidBlock(ModBlocks.MOON_FLUX.get());
                for (MachineType machine : MachineType.values()) {
                    MultiVariant model = plainVariant(Ids.mod("block/" + machine.blockId()));
                    for (Tier tier : Tier.values()) {
                        if (tier != Tier.COPPER) {
                            blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.machine(machine, tier).get(), model)
                                    .with(facing()));
                        }
                    }
                }
            }

            /** Turns the model the way the machine faces; the delivered model looks north. */
            private PropertyDispatch<VariantMutator> facing() {
                return PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
                        .select(Direction.NORTH, NOP)
                        .select(Direction.EAST, Y_ROT_90)
                        .select(Direction.SOUTH, Y_ROT_180)
                        .select(Direction.WEST, Y_ROT_270);
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
                // The block item model ships with the art under the first tier's name.
                for (MachineType machine : MachineType.values()) {
                    for (Tier tier : Tier.values()) {
                        if (tier != Tier.COPPER) {
                            itemModelOutput.accept(ModBlocks.machine(machine, tier).get().asItem(),
                                    ItemModelUtils.plainModel(Ids.mod("item/" + machine.blockId())));
                        }
                    }
                }
            }
        };
    }
}
