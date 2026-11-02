package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/** Generates the loot tables for AeroFlux blocks: every one drops itself when mined correctly. */
public class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        for (Block block : machineBlocks()) {
            dropSelf(block);
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
        blocks.add(ModBlocks.PIPE_NODE.get());
        for (Tier tier : Tier.values()) {
            blocks.add(ModBlocks.collector(tier).get());
            blocks.add(ModBlocks.tank(tier).get());
            for (MachineType machine : MachineType.values()) {
                blocks.add(ModBlocks.machine(machine, tier).get());
            }
        }
        return blocks;
    }
}
