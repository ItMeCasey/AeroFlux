package com.itmecasey.aeroflux.machine;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

/**
 * Works out what a plant grower shows in its glass chamber: the block its seed plants, grown as
 * far as the current job has got. The crop's own model draws it, so modded crops and resource
 * packs look right without any art of ours.
 */
public final class CropDisplay {
    /** The crop state the grower's model draws in the chamber, handed over as model data. */
    public static final ModelProperty<BlockState> SHOWN_CROP = new ModelProperty<>();

    private CropDisplay() {
    }

    /**
     * The crop to show for a seed.
     *
     * @param seed     what sits in the grower's input slot
     * @param progress how far the job has got, in ticks
     * @param total    how long the job takes, in ticks; 0 when nothing is running
     * @return the planted block at the matching growth stage, or {@code null} when the seed plants
     *         no block (an empty slot or an item that is not a block)
     */
    public static @Nullable BlockState stateFor(ItemStack seed, int progress, int total) {
        if (seed.isEmpty() || !(seed.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        IntegerProperty age = ageOf(state);
        if (age == null) {
            return state;
        }
        // The age climbs evenly through the job and reaches full growth on its last tick.
        int min = age.getPossibleValues().getFirst();
        int max = age.getPossibleValues().getLast();
        double done = total > 0 ? Math.min(1.0, progress / (double) total) : 0.0;
        return state.setValue(age, min + (int) Math.round(done * (max - min)));
    }

    /** The block's growth property: an integer property named {@code age}, as crops and bushes use. */
    private static @Nullable IntegerProperty ageOf(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && integer.getName().equals("age")) {
                return integer;
            }
        }
        return null;
    }
}
