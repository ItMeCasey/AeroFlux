package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;

/**
 * Generates the loot tables for AeroFlux blocks: every one drops itself when mined correctly, and
 * a Flux Tank keeps its flux on the item.
 */
public class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        for (Block block : machineBlocks()) {
            if (block instanceof FluxTankBlock) {
                // A tank keeps its flux when broken: the saved fluid goes into the item's
                // BlockEntityTag, which vanilla loads into the tank it places. An empty tank saves
                // no fluid, so nothing is copied and it drops a plain tank.
                add(block, createSingleItemTable(block).apply(
                        CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                                .copy("FluidName", "BlockEntityTag.FluidName")
                                .copy("Amount", "BlockEntityTag.Amount")));
            } else {
                dropSelf(block);
            }
        }
    }

    /** Only the blocks listed here get tables; the fluid blocks have none by design. */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        return machineBlocks();
    }

    private static List<Block> machineBlocks() {
        // The bundle has no table: it drops the pipes it holds (see ConduitBundleBlock#getDrops).
        List<Block> blocks = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            blocks.add(ModBlocks.collector(tier).get());
            blocks.add(ModBlocks.tank(tier).get());
            for (MachineType machine : MachineType.values()) {
                blocks.add(ModBlocks.machine(machine, tier).get());
            }
            blocks.add(ModBlocks.chunkLoader(tier).get());
            for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
                blocks.add(ModBlocks.converter(type, tier).get());
            }
            for (LampKind kind : LampKind.values()) {
                blocks.add(ModBlocks.lamp(kind, tier).get());
            }
        }
        blocks.add(ModBlocks.CREATIVE_TANK.get());
        return blocks;
    }
}
