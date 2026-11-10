package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModTags;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Generates block tags: which tool mines AeroFlux blocks and how strong it must be. */
public class ModBlockTagProvider extends TagsProvider<Block> {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               ExistingFileHelper existingFileHelper) {
        super(output, Registries.BLOCK, lookupProvider, AeroFlux.MODID, existingFileHelper);
    }

    /** Tag entries are registry keys since 26.2. */
    private static net.minecraft.resources.ResourceKey<Block> key(Block block) {
        return block.builtInRegistryHolder().key();
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(key(ModBlocks.CONDUIT_BUNDLE.get()))
                .add(key(ModBlocks.CREATIVE_TANK.get()));

        for (Tier tier : Tier.values()) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE)
                    .add(key(ModBlocks.collector(tier).get()))
                    .add(key(ModBlocks.tank(tier).get()));
            tag(BlockTags.NEEDS_STONE_TOOL)
                    .add(key(ModBlocks.collector(tier).get()))
                    .add(key(ModBlocks.tank(tier).get()));
            for (MachineType machine : MachineType.values()) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key(ModBlocks.machine(machine, tier).get()));
                tag(BlockTags.NEEDS_STONE_TOOL).add(key(ModBlocks.machine(machine, tier).get()));
            }
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key(ModBlocks.chunkLoader(tier).get()));
            tag(BlockTags.NEEDS_STONE_TOOL).add(key(ModBlocks.chunkLoader(tier).get()));
            for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key(ModBlocks.converter(type, tier).get()));
                tag(BlockTags.NEEDS_STONE_TOOL).add(key(ModBlocks.converter(type, tier).get()));
            }
            for (LampKind kind : LampKind.values()) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(key(ModBlocks.lamp(kind, tier).get()));
            }
        }
        // The soils the Plant Grower accepts: vanilla's, plus other mods' farmland where they are known.
        // Vanilla lets saplings and bushes stand on farmland as well as on dirt. On Forge 26.1 the dirt
        // tag leaves out the grassy and mossy kinds, so those are named here.
        tag(ModTags.SOIL_DIRT).addTag(BlockTags.DIRT).add(key(Blocks.FARMLAND), key(Blocks.GRASS_BLOCK), key(Blocks.PODZOL), key(Blocks.MYCELIUM),
                key(Blocks.MOSS_BLOCK), key(Blocks.MUD), key(Blocks.MUDDY_MANGROVE_ROOTS));
        tag(ModTags.SOIL_FARMLAND).add(key(Blocks.FARMLAND));
        // Other mods' farmland may be missing, so it goes in as optional entries.
        for (String farmland : List.of("farmersdelight:rich_soil_farmland", "mysticalagriculture:inferium_farmland",
                "mysticalagriculture:prudentium_farmland", "mysticalagriculture:tertium_farmland",
                "mysticalagriculture:imperium_farmland", "mysticalagriculture:supremium_farmland",
                "mysticalagriculture:awakened_supremium_farmland")) {
            getOrCreateRawBuilder(ModTags.SOIL_FARMLAND).addOptionalElement(Identifier.parse(farmland));
        }
        // What each lamp helps grow; packs can add their own plants to these.
        tag(ModTags.SUN_LAMP_PLANTS)
                .addTag(BlockTags.CROPS)
                .addTag(net.minecraft.tags.BlockItemTags.SAPLINGS.block())
                .add(key(Blocks.SUGAR_CANE), key(Blocks.CACTUS), key(Blocks.BAMBOO), key(Blocks.BAMBOO_SAPLING), key(Blocks.SWEET_BERRY_BUSH),
                        key(Blocks.COCOA), key(Blocks.MELON_STEM), key(Blocks.PUMPKIN_STEM), key(Blocks.KELP), key(Blocks.VINE));
        tag(ModTags.MOON_LAMP_PLANTS)
                .add(key(Blocks.BROWN_MUSHROOM), key(Blocks.RED_MUSHROOM), key(Blocks.NETHER_WART), key(Blocks.CAVE_VINES),
                        key(Blocks.CAVE_VINES_PLANT), key(Blocks.CHORUS_FLOWER), key(Blocks.TWISTING_VINES), key(Blocks.WEEPING_VINES));
    }
}
