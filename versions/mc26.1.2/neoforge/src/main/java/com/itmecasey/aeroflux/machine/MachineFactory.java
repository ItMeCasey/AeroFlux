package com.itmecasey.aeroflux.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the right block entity for a machine block. All machines share one block entity type, so
 * this is where the machine type picks the behavior: the furnace, the enchanters and most of the
 * newer machines have rules of their own, and everything else runs on recipes.
 */
public final class MachineFactory {
    private MachineFactory() {
    }

    public static FluxMachineBlockEntity create(BlockPos pos, BlockState state) {
        return switch (((FluxMachineBlock) state.getBlock()).machine()) {
            case FURNACE -> new FurnaceBlockEntity(pos, state);
            case ENCHANTER -> new EnchanterBlockEntity(pos, state);
            case DISENCHANTER -> new DisenchanterBlockEntity(pos, state);
            case CRAFTER -> new CrafterBlockEntity(pos, state);
            case SIFTER -> new SifterBlockEntity(pos, state);
            case BLENDER -> new BlenderBlockEntity(pos, state);
            case SKY_CONTROLLER -> new SkyControllerBlockEntity(pos, state);
            case MAGNET -> new MagnetBlockEntity(pos, state);
            case BREEDER -> new BreederBlockEntity(pos, state);
            case REPAIRER -> new RepairerBlockEntity(pos, state);
            case SPAWNER -> new SpawnerBlockEntity(pos, state);
            default -> new RecipeMachineBlockEntity(pos, state);
        };
    }
}
