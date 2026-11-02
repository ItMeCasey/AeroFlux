package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Generates block tags: which tool mines AeroFlux blocks and how strong it must be. */
public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, AeroFlux.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.CONDUIT_BUNDLE.get());

        for (Tier tier : Tier.values()) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE)
                    .add(ModBlocks.collector(tier).get())
                    .add(ModBlocks.tank(tier).get());
            tag(BlockTags.NEEDS_STONE_TOOL)
                    .add(ModBlocks.collector(tier).get())
                    .add(ModBlocks.tank(tier).get());
            for (MachineType machine : MachineType.values()) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.machine(machine, tier).get());
                tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.machine(machine, tier).get());
            }
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.chunkLoader(tier).get());
            tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.chunkLoader(tier).get());
            for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.converter(type, tier).get());
                tag(BlockTags.NEEDS_STONE_TOOL).add(ModBlocks.converter(type, tier).get());
            }
            for (LampKind kind : LampKind.values()) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.lamp(kind, tier).get());
            }
        }
        // What each lamp helps grow; packs can add their own plants to these.
        tag(ModTags.SUN_LAMP_PLANTS)
                .addTag(BlockTags.CROPS)
                .addTag(BlockTags.SAPLINGS)
                .add(Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO, Blocks.BAMBOO_SAPLING, Blocks.SWEET_BERRY_BUSH,
                        Blocks.COCOA, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM, Blocks.KELP, Blocks.VINE);
        tag(ModTags.MOON_LAMP_PLANTS)
                .add(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.NETHER_WART, Blocks.CAVE_VINES,
                        Blocks.CAVE_VINES_PLANT, Blocks.CHORUS_FLOWER, Blocks.TWISTING_VINES, Blocks.WEEPING_VINES);
    }
}
