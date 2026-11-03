package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The Blender: Aero Flux from its main tank and Moon Flux from its second tank, one to one,
 * into Twilight Flux in its third. The blend takes a small running cost in Aero Flux on top. An
 * empty bucket in its slot is filled from the Twilight tank once a full bucket is there.
 */
public class BlenderBlockEntity extends FluxMachineBlockEntity {
    /** mB of each flux one blend uses, and of Twilight Flux it makes. */
    public static final int BATCH = 100;
    private static final int BLEND_TICKS = 40;
    private static final int BUCKET = 1_000;
    private static final String KEY = "blend";

    public BlenderBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    private int moonTank() {
        return extraTankIndex(ExtraTank.MOON_FLUX);
    }

    private int twilightTank() {
        return extraTankIndex(ExtraTank.TWILIGHT_FLUX);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return stack.is(Items.BUCKET);
    }

    @Override
    protected @Nullable Job findJob() {
        if (extraAmount(moonTank()) < BATCH) {
            return null;
        }
        return new Job() {
            @Override
            public Object key() {
                return KEY;
            }

            @Override
            public int baseTicks() {
                return BLEND_TICKS;
            }

            @Override
            public boolean outputFits() {
                return fillExtra(twilightTank(), FluidResource.of(ModFluids.TWILIGHT_FLUX.get()), BATCH, true) >= BATCH;
            }

            @Override
            public int extraFlux() {
                return BATCH;
            }

            @Override
            public FluxType requiredFlux() {
                return FluxType.AERO;
            }

            @Override
            public void complete() {
                drainExtra(moonTank(), BATCH, false);
                fillExtra(twilightTank(), FluidResource.of(ModFluids.TWILIGHT_FLUX.get()), BATCH, false);
            }
        };
    }

    /** Fills an empty bucket from the Twilight tank whenever a whole bucket is there and the output slot is free. */
    @Override
    protected void tickAlways(Level level) {
        if (!stackIn(0).is(Items.BUCKET) || extraAmount(twilightTank()) < BUCKET) {
            return;
        }
        ItemStack filled = new ItemStack(ModItems.TWILIGHT_FLUX_BUCKET.get());
        if (!canFitOutputs(List.of(filled))) {
            return;
        }
        drainExtra(twilightTank(), BUCKET, false);
        consumeInput(0, 1);
        putOutputs(List.of(filled));
    }
}
