package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SideConfig;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.core.machine.SplitInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The menu behind every machine screen: input slots, output slots, the player's inventory and the
 * numbers the screen draws (Flux, progress, status and the face settings).
 *
 * <p>The server's copy reads straight from the {@link FluxMachineBlockEntity}; the client's copy
 * only displays what the server sends. Menu data slots carry 16 bits each, so the bigger numbers
 * are sent in two halves (see {@link SplitInt}). Every change a player makes to the face settings
 * comes back as a menu button click, and the server applies it through
 * {@link MachineConfigActions}, so a modified client cannot set anything the rules forbid.
 */
public abstract class FluxMachineMenu extends AbstractContainerMenu {
    // Indexes of the synced values.
    private static final int DATA_FLUX = 0;          // two slots
    private static final int DATA_FLUX_TYPE = 2;
    private static final int DATA_PROGRESS = 3;      // two slots
    private static final int DATA_TOTAL = 5;         // two slots
    private static final int DATA_STATUS = 7;
    private static final int DATA_SIDES = 8;         // one slot per SideResource
    private static final int DATA_COUNT = DATA_SIDES + SideResource.values().length;

    /** How far from the machine a player may be and keep the screen open, like a chest. */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    /** What the client needs to build its copy of a menu. */
    public record OpenData(BlockPos pos, MachineType machine, Tier tier, Face facing) {
    }

    private final BlockPos pos;
    private final MachineType machine;
    private final Tier tier;
    private final Face facing;
    private final MachineLayout layout;
    private final ContainerData data;
    /** The server's machine; {@code null} on the client, which has nothing to check against. */
    @Nullable
    private final FluxMachineBlockEntity entity;
    private final IItemHandler items;
    private final Level level;

    protected FluxMachineMenu(MenuType<?> type, int containerId, Inventory inventory, OpenData open,
                              @Nullable FluxMachineBlockEntity entity) {
        super(type, containerId);
        this.pos = open.pos();
        this.machine = open.machine();
        this.tier = open.tier();
        this.facing = open.facing();
        this.entity = entity;
        this.level = inventory.player.level();
        this.layout = MachineLayout.of(machine.inputSlots(), machine.outputSlots());
        this.items = entity == null ? new ItemStackHandler(machine.inputSlots() + machine.outputSlots()) : entity.inventory();
        this.data = entity == null ? new SimpleContainerData(DATA_COUNT) : new MachineData(entity);
        addDataSlots(data);

        for (int i = 0; i < layout.inputCount(); i++) {
            addSlot(new InputSlot(items, i, layout.inputSlot(i).x(), layout.inputSlot(i).y()));
        }
        for (int i = 0; i < layout.outputCount(); i++) {
            addSlot(new OutputSlot(items, layout.inputCount() + i, layout.outputSlot(i).x(), layout.outputSlot(i).y()));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col,
                        layout.inventoryX() + col * MachineLayout.SLOT, layout.inventoryY() + row * MachineLayout.SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, layout.inventoryX() + col * MachineLayout.SLOT, layout.hotbarY()));
        }
    }

    /** Whether a stack may be put into an input slot. The client asks too, so the answer must not need the block entity. */
    protected abstract boolean acceptsInput(int slot, ItemStack stack);

    /** What {@link #readOpenData} reads back, in the same order. */
    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, MachineType machine, Tier tier, Face facing) {
        buf.writeBlockPos(pos);
        buf.writeEnum(machine);
        buf.writeEnum(tier);
        buf.writeEnum(facing);
    }

    /** Reads what {@link FluxMachineBlockEntity#writeOpenData} wrote. */
    public static OpenData readOpenData(FriendlyByteBuf buf) {
        return new OpenData(buf.readBlockPos(), buf.readEnum(MachineType.class), buf.readEnum(Tier.class), buf.readEnum(Face.class));
    }

    // --- What the screen reads ---------------------------------------------------------------

    public BlockPos pos() {
        return pos;
    }

    public MachineType machine() {
        return machine;
    }

    public Tier tier() {
        return tier;
    }

    /** The machine's front, as the face it points to. */
    public Face facing() {
        return facing;
    }

    /** The level the menu was opened in; used to judge items against recipes. */
    protected Level level() {
        return level;
    }

    public MachineLayout layout() {
        return layout;
    }

    /** The kinds of resource that have face settings, in the order the tabs show them. */
    public Set<SideResource> resources() {
        return machine.resources();
    }

    /** The Flux in the tank, in mB, as last synced. */
    public int fluxAmount() {
        return SplitInt.join(data.get(DATA_FLUX), data.get(DATA_FLUX + 1));
    }

    /** The tank's size in mB; it follows from the tier. */
    public int fluxCapacity() {
        return MachineMath.inputCapacity(tier);
    }

    /** The flux type in the tank, or {@code null} if it is empty. */
    @Nullable
    public FluxType fluxType() {
        int stored = data.get(DATA_FLUX_TYPE);
        return stored <= 0 || stored > FluxType.values().length ? null : FluxType.values()[stored - 1];
    }

    public int progress() {
        return SplitInt.join(data.get(DATA_PROGRESS), data.get(DATA_PROGRESS + 1));
    }

    public int totalTime() {
        return SplitInt.join(data.get(DATA_TOTAL), data.get(DATA_TOTAL + 1));
    }

    public MachineStatus status() {
        return MachineStatus.byIndex(data.get(DATA_STATUS));
    }

    /** Flux burned per tick while working, in mB. */
    public int fluxPerTick() {
        return MachineMath.fluxPerTick(tier);
    }

    /** The mode of a face for a resource, as last synced. */
    public ConnectionMode mode(SideResource resource, Face face) {
        return SideConfig.unpack(data.get(DATA_SIDES + resource.ordinal())).mode(face);
    }

    // --- What the server does ----------------------------------------------------------------

    /** A click on the side-config window; the id says what to do. Returns whether anything changed. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        return entity != null && stillValid(player) && MachineConfigActions.applyButton(entity, id);
    }

    /** Shift-click: items go to the input slots if they fit there, otherwise between the inventory and the hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = layout.inputCount() + layout.outputCount();
        int mainEnd = machineSlots + 27;

        boolean moved;
        if (index < machineSlots) {
            moved = moveItemStackTo(stack, machineSlots, slots.size(), true);
        } else {
            moved = moveItemStackTo(stack, 0, layout.inputCount(), false)
                    || (index < mainEnd
                    ? moveItemStackTo(stack, mainEnd, slots.size(), false)
                    : moveItemStackTo(stack, machineSlots, mainEnd, false));
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Valid while the player is near and the machine is still the one the screen was opened for. */
    @Override
    public boolean stillValid(Player player) {
        BlockEntity found = player.level().getBlockEntity(pos);
        if (!(found instanceof FluxMachineBlockEntity) || found.isRemoved() || (entity != null && found != entity)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }

    // --- Pieces ------------------------------------------------------------------------------

    /** An input slot: takes what the machine accepts. */
    private final class InputSlot extends SlotItemHandler {
        private final int inputIndex;

        InputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
            this.inputIndex = index;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return acceptsInput(inputIndex, stack);
        }
    }

    /** An output slot: the player can take products out but never put anything in. */
    private static final class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }

    /** The synced numbers, read from the machine each time the server checks for changes. */
    private record MachineData(FluxMachineBlockEntity machine) implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FLUX -> SplitInt.low(machine.fluxTank().getFluidAmount());
                case DATA_FLUX + 1 -> SplitInt.high(machine.fluxTank().getFluidAmount());
                case DATA_FLUX_TYPE -> machine.storedFlux() == null ? 0 : machine.storedFlux().ordinal() + 1;
                case DATA_PROGRESS -> SplitInt.low(machine.progress());
                case DATA_PROGRESS + 1 -> SplitInt.high(machine.progress());
                case DATA_TOTAL -> SplitInt.low(machine.totalTime());
                case DATA_TOTAL + 1 -> SplitInt.high(machine.totalTime());
                case DATA_STATUS -> machine.status().ordinal();
                default -> {
                    int resource = index - DATA_SIDES;
                    yield resource >= 0 && resource < SideResource.values().length
                            ? machine.sides().packed(SideResource.values()[resource]) : 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // Read only: every change goes through MachineConfigActions.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
