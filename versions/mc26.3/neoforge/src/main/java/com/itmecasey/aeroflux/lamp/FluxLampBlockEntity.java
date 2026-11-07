package com.itmecasey.aeroflux.lamp;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineReach;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModTags;
import com.itmecasey.aeroflux.save.SaveVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * What a lamp does once a second: burns a little Flux, and while that works it stays lit, keeps its
 * place in {@link LampField} (which turns monster spawns away) and gives a few random plants in
 * its light an extra growth tick. The Sun Lamp's plants are in {@link ModTags#SUN_LAMP_PLANTS}, the
 * Moon Lamp's in {@link ModTags#MOON_LAMP_PLANTS}, so packs can add their own.
 */
public class FluxLampBlockEntity extends BlockEntity {
    private static final String TAG_TANK = "tank";
    /** The lamp's tank, four buckets, unless the config says otherwise (see {@link StorageSizes#lamp}). */
    public static final int CAPACITY = 4_000;
    /** How often a lamp works, in ticks. */
    private static final int INTERVAL = 20;
    /** How far above and below the lamp it helps plants, in blocks. */
    private static final int GROW_HEIGHT = 2;

    private final FluidStacksResourceHandler tank;

    public FluxLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAMP.get(), pos, state);
        LampKind kind = ((FluxLampBlock) state.getBlock()).kind();
        this.tank = new FluidStacksResourceHandler(1, StorageSizes.lamp()) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (the config made lamps
             * smaller); it still takes nothing more, and probes such as Jade, which refuse a fill
             * above 100%, see a full tank instead of crashing.
             */
            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return Math.max(super.getCapacityAsLong(index, resource), getAmountAsLong(index));
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return ModFluids.fluxTypeOf(resource) == kind.flux();
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
    }

    public LampKind kind() {
        return ((FluxLampBlock) getBlockState().getBlock()).kind();
    }

    public Tier tier() {
        return ((FluxLampBlock) getBlockState().getBlock()).tier();
    }

    /** The lamp's tank, open on every side. */
    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    public int fluxAmount() {
        return tank.getAmountAsInt(0);
    }

    /** Flux burned per second: one mB, and one more for each tier above copper, as the light reaches further. */
    public int burnPerSecond() {
        return tier().ordinal() + 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxLampBlockEntity lamp) {
        if (level.getGameTime() % INTERVAL == 0 && level instanceof ServerLevel server) {
            lamp.work(server, pos, state);
        }
    }

    private void work(ServerLevel level, BlockPos pos, BlockState state) {
        boolean lit = burn();
        if (state.getValue(FluxLampBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(FluxLampBlock.LIT, lit), Block.UPDATE_ALL);
        }
        int radius = MachineReach.lampRadius(tier());
        if (lit) {
            LampField.light(level, pos, radius);
            grow(level, pos, radius);
        } else {
            LampField.darken(level, pos);
        }
    }

    private boolean burn() {
        FluidResource held = tank.getResource(0);
        int cost = burnPerSecond();
        if (held.isEmpty() || tank.getAmountAsInt(0) < cost) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            tank.extract(0, held, cost, transaction);
            transaction.commit();
        }
        return true;
    }

    /** Gives a few random spots in the light a growth tick if a plant of the lamp's kind is there. */
    private void grow(ServerLevel level, BlockPos pos, int radius) {
        TagKey<Block> plants = kind() == LampKind.SUN ? ModTags.SUN_LAMP_PLANTS : ModTags.MOON_LAMP_PLANTS;
        RandomSource random = level.getRandom();
        for (int i = 0; i < MachineReach.lampGrowthPerSecond(tier()); i++) {
            BlockPos spot = pos.offset(random.nextIntBetweenInclusive(-radius, radius),
                    random.nextIntBetweenInclusive(-GROW_HEIGHT, GROW_HEIGHT),
                    random.nextIntBetweenInclusive(-radius, radius));
            BlockState plant = level.getBlockState(spot);
            if (plant.is(plants) && plant.isRandomlyTicking()) {
                plant.randomTick(level, spot, random);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            LampField.darken(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        SaveVersion.stamp(output, SaveSchema.Kind.LAMP);
        tank.serialize(output.child(TAG_TANK));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; reading it reports a save from a newer build.
        SaveVersion.read(SaveSchema.Kind.LAMP, input);
        input.child(TAG_TANK).ifPresent(tank::deserialize);
    }
}
