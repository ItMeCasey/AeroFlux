package com.itmecasey.aeroflux.converter;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.SplitInt;
import com.itmecasey.aeroflux.registry.ModMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The menu behind a converter's screen. Like the chunk loader's it has no slots, only the numbers
 * the screen draws: the Flux and its type, what the buffer holds and how big it is, the output,
 * and last tick's burn. Every converter kind shows through {@link ConverterView}.
 */
public class ConverterMenu extends AbstractContainerMenu {
    private static final int DATA_FLUX = 0;      // two slots
    private static final int DATA_FLUX_TYPE = 2;
    private static final int DATA_STORED = 3;    // two slots
    private static final int DATA_BURN = 5;
    private static final int DATA_OUTPUTS = 6;
    private static final int DATA_CAPACITY = 7;  // two slots
    private static final int DATA_OUTPUT = 9;    // two slots
    private static final int DATA_COUNT = 11;

    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final BlockPos pos;
    private final ConverterType type;
    private final Tier tier;
    private final Direction facing;
    private final ContainerData data;
    private final @Nullable ConverterView entity;

    public ConverterMenu(int containerId, Inventory inventory, ConverterView entity) {
        this(containerId, entity.getBlockPos(), entity.type(), entity.tier(), entity.facing(), new LiveData(entity), entity);
    }

    private ConverterMenu(int containerId, BlockPos pos, ConverterType type, Tier tier, Direction facing, ContainerData data,
                          @Nullable ConverterView entity) {
        super(ModMenus.CONVERTER.get(), containerId);
        this.pos = pos;
        this.type = type;
        this.tier = tier;
        this.facing = facing;
        this.data = data;
        this.entity = entity;
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, ConverterType type, Tier tier, Direction facing) {
        buf.writeBlockPos(pos);
        buf.writeEnum(type);
        buf.writeEnum(tier);
        buf.writeEnum(facing);
    }

    public static ConverterMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new ConverterMenu(containerId, buf.readBlockPos(), buf.readEnum(ConverterType.class), buf.readEnum(Tier.class),
                buf.readEnum(Direction.class), new SimpleContainerData(DATA_COUNT), null);
    }

    // --- What the screen reads ---------------------------------------------------------------

    public ConverterType type() {
        return type;
    }

    public Tier tier() {
        return tier;
    }

    public int fluxAmount() {
        return SplitInt.join(data.get(DATA_FLUX), data.get(DATA_FLUX + 1));
    }

    public int fluxCapacity() {
        return StorageSizes.converterTank(tier);
    }

    public @Nullable FluxType fluxType() {
        int stored = data.get(DATA_FLUX_TYPE);
        return stored <= 0 || stored > FluxType.values().length ? null : FluxType.values()[stored - 1];
    }

    /** What the output buffer holds, in FE or AE, or for the Stress converter the stress in use, in SU. */
    public int stored() {
        return SplitInt.join(data.get(DATA_STORED), data.get(DATA_STORED + 1));
    }

    /** The buffer's size, or the Stress converter's capacity; the server's own number, config included. */
    public int bufferCapacity() {
        return SplitInt.join(data.get(DATA_CAPACITY), data.get(DATA_CAPACITY + 1));
    }

    /** Which way the converter's front points, so the screen can name its faces. */
    public Direction facing() {
        return facing;
    }

    /** Whether power goes out through the face with this label (front, top and so on). */
    public boolean outputsOn(FaceLabel label) {
        Face face = label.faceFor(Face.byIndex(facing.get3DDataValue()));
        return (data.get(DATA_OUTPUTS) & (1 << face.index())) != 0;
    }

    /** Flux burned last tick, in mB. */
    public int burn() {
        return data.get(DATA_BURN);
    }

    /** Output made per tick at last tick's burn, in FE or AE, or the stress capacity supplied, in SU. */
    public int outputPerTick() {
        return SplitInt.join(data.get(DATA_OUTPUT), data.get(DATA_OUTPUT + 1));
    }

    // --- Menu plumbing -----------------------------------------------------------------------

    /** Button {@code n} switches the face with {@link FaceLabel} ordinal {@code n} on or off. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || !stillValid(player) || id < 0 || id >= FaceLabel.values().length) {
            return false;
        }
        entity.toggleOutput(FaceLabel.values()[id].faceFor(Face.byIndex(facing.get3DDataValue())));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity found = player.level().getBlockEntity(pos);
        if (!(found instanceof ConverterView) || found.isRemoved() || (entity != null && found != entity)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }

    private record LiveData(ConverterView converter) implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FLUX -> SplitInt.low(converter.fluxAmount());
                case DATA_FLUX + 1 -> SplitInt.high(converter.fluxAmount());
                case DATA_FLUX_TYPE -> converter.storedFlux() == null ? 0 : converter.storedFlux().ordinal() + 1;
                case DATA_STORED -> SplitInt.low(converter.stored());
                case DATA_STORED + 1 -> SplitInt.high(converter.stored());
                case DATA_BURN -> converter.lastBurn();
                case DATA_OUTPUTS -> converter.outputMask();
                case DATA_CAPACITY -> SplitInt.low(converter.bufferCapacity());
                case DATA_CAPACITY + 1 -> SplitInt.high(converter.bufferCapacity());
                case DATA_OUTPUT -> SplitInt.low(converter.output());
                case DATA_OUTPUT + 1 -> SplitInt.high(converter.output());
                default -> 0;
            };
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
