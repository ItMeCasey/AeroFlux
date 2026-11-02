package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Generates the loot tables for AeroFlux blocks: every one drops itself when mined correctly, and
 * a Flux Tank keeps its flux on the item.
 */
public class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        for (Block block : machineBlocks()) {
            if (block instanceof FluxTankBlock) {
                // A tank keeps its flux when broken, carried on the item it drops.
                add(block, createSingleItemTable(block).apply(
                        CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                                .include(ModDataComponents.TANK_FLUID.get())));
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
        return blocks;
    }
}
