package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideConfig;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.core.machine.SplitInt;
import com.itmecasey.aeroflux.core.machine.UpgradeStripLayout;
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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The menu behind every machine screen: input slots, output slots, upgrade slots, the player's
 * inventory and the numbers the screen draws (Flux, progress, status, the face settings, the
 * upgrade counts and the redstone mode).
 *
 * <p>The server's copy reads straight from the {@link FluxMachineBlockEntity}; the client's copy
 * only displays what the server sends. Menu data slots carry 16 bits each on the wire, so the
 * bigger numbers are sent in two halves (see {@link SplitInt}). Every change a player makes to the
 * face settings or the redstone mode comes back as a menu button click, and the server applies it
 * through {@link MachineConfigActions}, so a modified client cannot set anything the rules forbid.
 */
public abstract class FluxMachineMenu extends AbstractContainerMenu {
    // Indexes of the synced values.
    private static final int DATA_FLUX = 0;          // two slots
    private static final int DATA_FLUX_TYPE = 2;
    private static final int DATA_PROGRESS = 3;      // two slots
    private static final int DATA_TOTAL = 5;         // two slots
    private static final int DATA_STATUS = 7;
    private static final int DATA_SPEED_UPGRADES = 8;
    private static final int DATA_CAPACITY_UPGRADES = 9;
    private static final int DATA_REDSTONE_MODE = 10;
    private static final int DATA_REDSTONE_DISABLED = 11;
    // The face settings come last, one slot per SideResource, so a new resource only adds to the end.
    private static final int DATA_SIDES = 12;
    private static final int DATA_AUTO_INSERT = DATA_SIDES + SideResource.values().length;
    /** Bit 0 Auto Import, bit 1 Auto Export. */
    private static final int DATA_AUTO_IO = DATA_AUTO_INSERT + 1;
    /** Two slots per extra tank, for up to two extra tanks. */
    private static final int DATA_EXTRA = DATA_AUTO_IO + 1;
    private static final int MAX_EXTRA_TANKS = 2;
    /** A setting only this kind of machine has; see {@link FluxMachineBlockEntity#machineMode}. */
    private static final int DATA_MODE = DATA_EXTRA + 2 * MAX_EXTRA_TANKS;
    /** The machine-only upgrade counts, three bits each in {@link MachineUpgrades.Special} order. */
    private static final int DATA_SPECIAL_UPGRADES = DATA_MODE + 1;
    private static final int SPECIAL_BITS = 3;
    /** Two slots: the current job's whole Flux cost, worked out by the server (only it knows the job's extra Flux). */
    private static final int DATA_OPERATION_COST = DATA_SPECIAL_UPGRADES + 1;
    /** Two slots: what the next tick of the job takes. */
    private static final int DATA_NEXT_TICK_COST = DATA_OPERATION_COST + 2;
    private static final int DATA_COUNT = DATA_NEXT_TICK_COST + 2;

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
    private final UpgradeStripLayout strip;
    private final ContainerData data;
    /** The server's machine; {@code null} on the client, which has nothing to check against. */
    private final @Nullable FluxMachineBlockEntity entity;
    private final ItemStackHandler items;
    private final MachineUpgradeSlots upgrades;
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
        this.strip = new UpgradeStripLayout(MachineUpgrades.slots(tier));
        this.items = entity == null
                ? new ItemStackHandler(machine.inputSlots() + machine.outputSlots()) : entity.inventory();
        this.upgrades = entity == null ? new MachineUpgradeSlots(strip.slotCount(), machine) : entity.upgrades();
        this.data = entity == null ? new SimpleContainerData(DATA_COUNT) : new MachineData(entity);
        addDataSlots(data);

        for (int i = 0; i < layout.inputCount(); i++) {
            addSlot(new InputSlot(items, i, layout.inputSlot(i).x(), layout.inputSlot(i).y()));
        }
        for (int i = 0; i < layout.outputCount(); i++) {
            addSlot(new OutputSlot(items, layout.inputCount() + i, layout.outputSlot(i).x(), layout.outputSlot(i).y()));
        }
        // The upgrade slots come right after the machine's own, before the player's inventory.
        for (int i = 0; i < strip.slotCount(); i++) {
            addSlot(new SlotItemHandler(upgrades, i, strip.slot(i).x(), strip.slot(i).y()));
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

    /** The tank's size in mB; it follows from the tier and the Capacity upgrades. */
    public int fluxCapacity() {
        return StorageSizes.machine(tier, capacityUpgrades());
    }

    /** Where the redstone button and the upgrade slots sit. */
    public UpgradeStripLayout strip() {
        return strip;
    }

    /** The index of the first upgrade slot in {@link #slots}. */
    public int firstUpgradeSlot() {
        return layout.inputCount() + layout.outputCount();
    }

    /** Speed upgrades installed, as last synced. */
    public int speedUpgrades() {
        return data.get(DATA_SPEED_UPGRADES);
    }

    /** Capacity upgrades installed, as last synced. */
    public int capacityUpgrades() {
        return data.get(DATA_CAPACITY_UPGRADES);
    }

    /** The work speed against the tier's own, in percent; see {@link MachineUpgrades#speedPercent}. */
    public int speedPercent() {
        return MachineUpgrades.speedPercent(speedUpgrades());
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byIndex(data.get(DATA_REDSTONE_MODE));
    }

    /** Whether a plant grower refills its input from its outputs. */
    public boolean autoInsert() {
        return data.get(DATA_AUTO_INSERT) != 0;
    }

    /** Whether the machine pulls from its neighbors through its import faces. */
    public boolean autoImport() {
        return (data.get(DATA_AUTO_IO) & 1) != 0;
    }

    /** Whether the machine pushes its products out through its export faces. */
    public boolean autoExport() {
        return (data.get(DATA_AUTO_IO) & 2) != 0;
    }

    /** mB in an extra tank, as last synced. */
    public int extraAmount(int index) {
        return SplitInt.join(data.get(DATA_EXTRA + 2 * index), data.get(DATA_EXTRA + 2 * index + 1));
    }

    /** A setting only this kind of machine has, such as the Magnet's whitelist, as last synced. */
    public int machineMode() {
        return data.get(DATA_MODE);
    }

    /** Whether the redstone mode is holding the machine back, as last synced. */
    public boolean redstoneDisabled() {
        return data.get(DATA_REDSTONE_DISABLED) != 0;
    }

    /** The flux type in the tank, or {@code null} if it is empty. */
    public @Nullable FluxType fluxType() {
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

    /** Flux burned per tick while working, in mB, with Speed and Efficiency upgrades counted. */
    public int fluxPerTick() {
        return MachineUpgrades.fluxPerTick(tier, speedUpgrades(), upgrades(MachineUpgrades.Special.EFFICIENCY));
    }

    /** Flux the current job takes from start to finish, in mB, as last synced; 0 with no job. */
    public int fluxPerOperation() {
        return SplitInt.join(data.get(DATA_OPERATION_COST), data.get(DATA_OPERATION_COST + 1));
    }

    /** Flux the next tick of the job takes, in mB, as last synced; 0 with no job. */
    public int nextTickCost() {
        return SplitInt.join(data.get(DATA_NEXT_TICK_COST), data.get(DATA_NEXT_TICK_COST + 1));
    }

    /** How many machine-only upgrades of one kind are installed, as last synced. */
    public int upgrades(MachineUpgrades.Special kind) {
        return (data.get(DATA_SPECIAL_UPGRADES) >> (SPECIAL_BITS * kind.ordinal())) & ((1 << SPECIAL_BITS) - 1);
    }

    /** The mode of a face for a resource, as last synced. */
    public ConnectionMode mode(SideResource resource, Face face) {
        return SideConfig.unpack(data.get(DATA_SIDES + resource.ordinal())).mode(face);
    }

    // --- What the server does ----------------------------------------------------------------

    /** A click on the side-config window or the redstone button; the id says what to do. Returns whether anything changed. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        return entity != null && stillValid(player) && MachineConfigActions.applyButton(entity, id);
    }

    /**
     * Shift-click: upgrades go to the upgrade slots and other items to the input slots if they fit
     * there, otherwise between the inventory and the hotbar. Anything in the machine goes back to
     * the player.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int upgradeStart = firstUpgradeSlot();
        int machineSlots = upgradeStart + strip.slotCount();
        int mainEnd = machineSlots + 27;

        boolean moved;
        if (index < machineSlots) {
            moved = moveItemStackTo(stack, machineSlots, slots.size(), true);
        } else if (MachineUpgradeSlots.accepts(machine, stack.getItem())) {
            // An upgrade never counts as an input, so it only looks for a free upgrade slot.
            moved = moveItemStackTo(stack, upgradeStart, machineSlots, false);
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

        InputSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
            this.inputIndex = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && acceptsInput(inputIndex, stack);
        }

        @Override
        public int getMaxStackSize() {
            return machine == MachineType.SIFTER && inputIndex == SieveMesh.MESH_SLOT ? 1 : super.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), super.getMaxStackSize(stack));
        }
    }

    /** An output slot: the player can take products out but never put anything in. */
    private static final class OutputSlot extends SlotItemHandler {
        OutputSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** The synced numbers, read from the machine each time the server checks for changes. */
    private record MachineData(FluxMachineBlockEntity machine) implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FLUX -> SplitInt.low(machine.fluxAmount());
                case DATA_FLUX + 1 -> SplitInt.high(machine.fluxAmount());
                case DATA_FLUX_TYPE -> machine.storedFlux() == null ? 0 : machine.storedFlux().ordinal() + 1;
                case DATA_PROGRESS -> SplitInt.low(machine.progress());
                case DATA_PROGRESS + 1 -> SplitInt.high(machine.progress());
                case DATA_TOTAL -> SplitInt.low(machine.totalTime());
                case DATA_TOTAL + 1 -> SplitInt.high(machine.totalTime());
                case DATA_STATUS -> machine.status().ordinal();
                case DATA_SPEED_UPGRADES -> machine.speedUpgrades();
                case DATA_CAPACITY_UPGRADES -> machine.capacityUpgrades();
                case DATA_REDSTONE_MODE -> machine.redstoneMode().ordinal();
                case DATA_REDSTONE_DISABLED -> machine.redstoneDisabled() ? 1 : 0;
                default -> {
                    if (index == DATA_AUTO_INSERT) {
                        yield machine.autoInsert() ? 1 : 0;
                    }
                    if (index == DATA_AUTO_IO) {
                        yield (machine.autoImport() ? 1 : 0) | (machine.autoExport() ? 2 : 0);
                    }
                    if (index == DATA_MODE) {
                        yield machine.machineMode();
                    }
                    if (index == DATA_SPECIAL_UPGRADES) {
                        int packed = 0;
                        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
                            int count = Math.min(machine.upgrades(kind), (1 << SPECIAL_BITS) - 1);
                            packed |= count << (SPECIAL_BITS * kind.ordinal());
                        }
                        yield packed;
                    }
                    if (index == DATA_OPERATION_COST || index == DATA_OPERATION_COST + 1) {
                        int cost = machine.fluxPerOperation();
                        yield index == DATA_OPERATION_COST ? SplitInt.low(cost) : SplitInt.high(cost);
                    }
                    if (index == DATA_NEXT_TICK_COST || index == DATA_NEXT_TICK_COST + 1) {
                        int cost = machine.nextTickCost();
                        yield index == DATA_NEXT_TICK_COST ? SplitInt.low(cost) : SplitInt.high(cost);
                    }
                    if (index >= DATA_EXTRA && index < DATA_MODE) {
                        int tank = (index - DATA_EXTRA) / 2;
                        if (tank >= machine.extraTankCount()) {
                            yield 0;
                        }
                        int amount = machine.extraAmount(tank);
                        yield (index - DATA_EXTRA) % 2 == 0 ? SplitInt.low(amount) : SplitInt.high(amount);
                    }
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
