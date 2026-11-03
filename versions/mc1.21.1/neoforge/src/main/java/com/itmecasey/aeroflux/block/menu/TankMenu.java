package com.itmecasey.aeroflux.block.menu;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.RateMeter;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.SplitInt;
import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * The menu behind a tank's screen: no slots, just what the tank holds, its size, and how much went
 * in and came out during the last second. Every amount travels in two data slots ({@link SplitInt}),
 * since tanks hold far more than 16 bits count.
 */
public class TankMenu extends AbstractContainerMenu {
    private static final int DATA_AMOUNT = 0;    // two slots
    private static final int DATA_CAPACITY = 2;  // two slots
    private static final int DATA_TYPE = 4;
    private static final int DATA_IN = 5;        // two slots
    private static final int DATA_OUT = 7;       // two slots
    private static final int DATA_FLUID = 9;     // the fluid's id in the fluid registry, plus one; 0 when empty
    private static final int DATA_COUNT = 10;

    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final BlockPos pos;
    private final Tier tier;
    private final ContainerData data;
    private final @Nullable FluxTankBlockEntity entity;

    public TankMenu(int containerId, Inventory inventory, FluxTankBlockEntity entity) {
        this(containerId, entity.getBlockPos(), entity.tier(), new LiveData(entity), entity);
    }

    private TankMenu(int containerId, BlockPos pos, Tier tier, ContainerData data, @Nullable FluxTankBlockEntity entity) {
        super(ModMenus.TANK.get(), containerId);
        this.pos = pos;
        this.tier = tier;
        this.data = data;
        this.entity = entity;
        addDataSlots(data);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, Tier tier) {
        buf.writeBlockPos(pos);
        buf.writeEnum(tier);
    }

    public static TankMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new TankMenu(containerId, buf.readBlockPos(), buf.readEnum(Tier.class), new SimpleContainerData(DATA_COUNT), null);
    }

    // --- What the screen reads ---------------------------------------------------------------

    public BlockPos pos() {
        return pos;
    }

    public Tier tier() {
        return tier;
    }

    /** Flux in the tank, in mB. */
    public int amount() {
        return SplitInt.join(data.get(DATA_AMOUNT), data.get(DATA_AMOUNT + 1));
    }

    /** The tank's size, in mB. */
    public int capacity() {
        return SplitInt.join(data.get(DATA_CAPACITY), data.get(DATA_CAPACITY + 1));
    }

    /** The flux in the tank, or {@code null} if it is empty. */
    public @Nullable FluxType fluxType() {
        int stored = data.get(DATA_TYPE);
        return stored <= 0 || stored > FluxType.values().length ? null : FluxType.values()[stored - 1];
    }

    /** The fluid in the tank, whatever it is, or {@code null} if it is empty. */
    public @Nullable Fluid fluid() {
        int stored = data.get(DATA_FLUID);
        return stored <= 0 ? null : BuiltInRegistries.FLUID.byId(stored - 1);
    }

    /** mB that went in during the last second. */
    public int inLastSecond() {
        return SplitInt.join(data.get(DATA_IN), data.get(DATA_IN + 1));
    }

    /** mB that came out during the last second. */
    public int outLastSecond() {
        return SplitInt.join(data.get(DATA_OUT), data.get(DATA_OUT + 1));
    }

    /** The rate in, in mB per tick. */
    public double inPerTick() {
        return inLastSecond() / (double) RateMeter.WINDOW;
    }

    /** The rate out, in mB per tick. */
    public double outPerTick() {
        return outLastSecond() / (double) RateMeter.WINDOW;
    }

    // --- Menu plumbing -----------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity found = player.level().getBlockEntity(pos);
        if (!(found instanceof FluxTankBlockEntity) || found.isRemoved() || (entity != null && found != entity)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }

    private record LiveData(FluxTankBlockEntity tank) implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_AMOUNT -> SplitInt.low(tank.amount());
                case DATA_AMOUNT + 1 -> SplitInt.high(tank.amount());
                case DATA_CAPACITY -> SplitInt.low(tank.capacity());
                case DATA_CAPACITY + 1 -> SplitInt.high(tank.capacity());
                case DATA_TYPE -> tank.storedFlux() == null ? 0 : tank.storedFlux().ordinal() + 1;
                case DATA_FLUID -> tank.storedFluid() == null ? 0 : BuiltInRegistries.FLUID.getId(tank.storedFluid()) + 1;
                case DATA_IN -> SplitInt.low(clamp(tank.inLastWindow()));
                case DATA_IN + 1 -> SplitInt.high(clamp(tank.inLastWindow()));
                case DATA_OUT -> SplitInt.low(clamp(tank.outLastWindow()));
                case DATA_OUT + 1 -> SplitInt.high(clamp(tank.outLastWindow()));
                default -> 0;
            };
        }

        private static int clamp(long value) {
            return (int) Math.min(Integer.MAX_VALUE, value);
        }

        @Override
        public void set(int index, int value) {
            // Read only.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
