package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.compat.modjs.AeroFluxScripts;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.FluxCost;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.PortRole;
import com.itmecasey.aeroflux.core.machine.ProcessTimer;
import com.itmecasey.aeroflux.core.machine.RedstoneControl;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideConfigs;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.core.protect.ProtectedMachines;
import com.itmecasey.aeroflux.core.protect.ProtectionCache;
import com.itmecasey.aeroflux.core.save.MachineSaveMigrations;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.protect.Protection;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModSounds;
import com.itmecasey.aeroflux.save.SaveVersion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The shared body of every Flux-powered machine (Furnace, Grinder, Sawmill and the rest).
 *
 * <p>A machine has a Flux input tank that takes only the flux types its {@link MachineType} is
 * built for, any extra tanks its type lists (water, Moon Flux, Twilight Flux), a row of input
 * slots followed by a row of output slots, a progress counter, and a face setting for each kind
 * of resource it uses. Each tick on the server it asks its subclass what job the inputs make
 * ({@link #findJob()}), and while the job can run it burns {@link MachineMath#fluxPerTick Flux}
 * from the tank and advances the progress; when the progress is full the job hands over its
 * product. A subclass only has to say which job it has and what finishing it does.
 *
 * <p>The outside world sees the machine through item and fluid capabilities that follow the face
 * settings (see {@link SidedItemHandler} and {@link SidedFluidHandler}); the player sees it through
 * its menu. Tier decides the speed and the Flux cost through {@link MachineMath}, and the tank size
 * through the config ({@link StorageSizes#machine}); Speed and Capacity upgrades in the upgrade
 * slots change those numbers through {@link MachineUpgrades}. Forge hands capabilities out through {@code getCapability}, so the
 * machine keeps one {@link LazyOptional} per side and drops them all whenever a face setting changes. A {@link RedstoneControl} can hold the machine back: while it does, the
 * machine keeps its progress, burns nothing and stays unlit.
 */
public abstract class FluxMachineBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_OWNER = "owner";
    private static final String TAG_FLUX = "flux";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_SHOWN_CROP = "shown_crop";
    private static final String TAG_TOTAL = "total";
    private static final String TAG_SIDES = "sides";
    private static final String TAG_UPGRADES = "upgrades";
    private static final String TAG_REDSTONE = "redstone";
    private static final String TAG_AUTO_INSERT = "auto_insert";
    private static final String TAG_EXTRA_TANKS = "extra_tanks";
    private static final String TAG_AUTO_IMPORT = "auto_import";
    private static final String TAG_AUTO_EXPORT = "auto_export";
    private static final String TAG_REDSTONE_MODE = "mode";
    private static final String TAG_REDSTONE_POWERED = "powered";
    private static final String TAG_REDSTONE_PULSE = "pulse";

    /** How long a claim answer is trusted, in ticks; a claim changes rarely and this keeps the check cheap. */
    private static final int PROTECTION_TTL = 40;
    /** Set in the cache key of an entity check, so it never collides with the machine's own spot. */
    private static final long ENTITY_KEY_FLAG = 1L << 62;
    /** An idle machine looks for new work this often instead of every tick. */
    private static final int IDLE_CHECK_INTERVAL = 10;
    /** The start, stop and done sounds are cues, kept a little under full volume. */
    private static final float ONE_SHOT_VOLUME = 0.5F;

    /**
     * One piece of work: the thing the inputs are being turned into right now. A subclass makes a
     * new one each tick it is asked, so it must be cheap to build.
     */
    protected interface Job {
        /** Names the work. Progress starts over when this changes, such as when the input is swapped for a different recipe. */
        Object key();

        /** How long the job takes before tier speed is applied, in ticks. */
        int baseTicks();

        /** Whether the finished product has room in the output slots. */
        boolean outputFits();

        /** Uses up the inputs and puts the product in the outputs. Only called when {@link #outputFits()} is true. */
        void complete();

        /** Flux, in mB, taken on the last tick on top of the running cost; the job does not finish without it. */
        default int extraFlux() {
            return 0;
        }

        /** The flux type the job needs in the tank, or {@code null} if either will do. */
        @Nullable
        default FluxType requiredFlux() {
            return null;
        }
    }

    private final Tier tier;
    private final MachineType machine;
    private final FluidTank fluxTank;
    /** The tanks besides the Flux tank, one per {@link MachineType#extraTanks()}, in that order. */
    private final List<FluidTank> extraTanks = new ArrayList<>();
    private final ItemStackHandler inventory;
    private final SideConfigs sides;
    private final ProcessTimer timer = new ProcessTimer();
    /** The six sides plus the query that names no side. */
    private static final int SIDE_VIEWS = 7;
    /** One entry per side plus one for "no side"; empty until the side is first asked for. */
    private final List<LazyOptional<IItemHandler>> itemViews = new ArrayList<>(Collections.nCopies(SIDE_VIEWS, null));
    private final List<LazyOptional<IFluidHandler>> fluidViews = new ArrayList<>(Collections.nCopies(SIDE_VIEWS, null));
    private final MachineUpgradeSlots upgrades;
    private final RedstoneControl redstone = new RedstoneControl();
    private final ProtectedMachines.Reach reach;
    private final ProtectionCache protection = new ProtectionCache(PROTECTION_TTL);

    /** The player who placed the machine, if one did; the claim checks are made as this player. */
    @Nullable
    private UUID owner;
    private MachineStatus status = MachineStatus.IDLE;
    /** True while the redstone mode holds the machine back; the screen then says "Disabled". */
    private boolean redstoneDisabled;
    /** False until the machine has looked at its redstone signal since it was placed or loaded. */
    private boolean signalRead;
    @Nullable
    private Object activeKey;
    /** The current job's {@link Job#extraFlux}, for the cost the screen shows; 0 with no job. */
    private int jobExtraFlux;
    /** True while there was nothing to do, so the machine only checks now and then. */
    private boolean sleeping;
    /** What a plant grower shows in its chamber; see {@link CropDisplay}. Always null for other machines. */
    @Nullable
    private BlockState shownCrop;
    /** Whether a plant grower puts seeds from its outputs back into its input; see {@link #refillFromOutputs}. */
    private boolean autoInsert;
    /** Whether the machine pulls from its neighbors through its import faces; see {@link #autoTransfer}. */
    private boolean autoImport;
    /** Whether the machine pushes its products out through its export faces; see {@link #autoTransfer}. */
    private boolean autoExport;

    protected FluxMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
        FluxMachineBlock block = (FluxMachineBlock) state.getBlock();
        this.tier = block.tier();
        this.machine = block.machine();
        this.sides = new SideConfigs(machine.resources());
        this.reach = ProtectedMachines.reachOf(machine);
        // A single tank, so it can only ever hold one kind of flux.
        this.fluxTank = new FluxFluidTank(StorageSizes.machine(tier), this::acceptsFlux) {
            /**
             * Capacity upgrades can change this at any time, so it is never cached. It is never
             * less than what the tank holds: a tank can end up overfull (a Capacity upgrade came
             * out, or its tier's size shrank in an update), and it then takes nothing more while
             * probes such as Jade see a full tank instead of more than 100%.
             */
            @Override
            public int getCapacity() {
                return Math.max(fluxCapacity(), getFluidAmount());
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        for (ExtraTank spec : machine.extraTanks()) {
            extraTanks.add(new FluxFluidTank(StorageSizes.machine(tier), stack -> ExtraTanks.matches(spec, stack)) {
                @Override
                public int getCapacity() {
                    return Math.max(fluxCapacity(), getFluidAmount());
                }

                @Override
                protected void onContentsChanged() {
                    setChanged();
                    sleeping = false;
                }
            });
        }
        this.inventory = new ItemStackHandler(machine.inputSlots() + machine.outputSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sleeping = false;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return isInputSlot(slot) && isInputValid(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return isInputSlot(slot) ? Math.min(super.getSlotLimit(slot), inputLimit(slot)) : super.getSlotLimit(slot);
            }
        };
        this.upgrades = new MachineUpgradeSlots(MachineUpgrades.slots(tier), machine, this::upgradesChanged);
    }

    // --- What a subclass provides ------------------------------------------------------------

    /** The job the current inputs make, or {@code null} if there is nothing to work on. Runs every tick the machine is awake. */
    @Nullable
    protected abstract Job findJob();

    /** Whether a stack may be put into an input slot, such as "has a smelting recipe". Called for players and for pipes alike. */
    protected abstract boolean isInputValid(int slot, ItemStack stack);

    /** The most an input slot holds; a full stack unless a machine says less, like the Sifter's single mesh. */
    protected int inputLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    /**
     * Whether a pipe or a neighbor may put this stack into an input slot. The menu does not ask, so
     * a player can always place items by hand. The Crafter only lets pipes top up its grid, and the
     * Magnet's filter slot is set by hand only.
     */
    public boolean acceptsFromOutside(int slot, ItemStack stack) {
        return true;
    }

    /**
     * Runs every tick before the machine looks for work, for machines that do something besides
     * their jobs (the Blender fills buckets, the Sky Controller counts down). Does nothing by default.
     */
    protected void tickAlways(Level level) {
    }

    /** A button only this kind of machine has (see {@link MachineConfigActions}); false if it means nothing here. */
    public boolean machineButton(int buttonId) {
        return false;
    }

    /** A setting only this kind of machine has, synced to its screen, such as the Magnet's whitelist. */
    public int machineMode() {
        return 0;
    }

    // --- Parts -------------------------------------------------------------------------------

    public Tier tier() {
        return tier;
    }

    /** The player who placed this machine, if one did. */
    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    /** Called by the block when a player places the machine; {@code null} for anything else. */
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        protection.clear();
        setChanged();
    }

    /**
     * Whether the owner's claims let this machine act on a block position. An unowned machine is
     * never asked; answers are kept for a short time.
     */
    protected final boolean mayActAt(BlockPos target) {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return true;
        }
        return protection.allowed(target.asLong(), server.getGameTime(),
                () -> Protection.mayAct(server, owner, target, ProtectedMachines.actionOf(reach)));
    }

    /** Whether the owner's claims let this machine act on an entity. Answers are kept per chunk. */
    protected final boolean mayTouch(Entity entity) {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return true;
        }
        BlockPos at = entity.blockPosition();
        long chunk = ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4);
        return protection.allowed(chunk | ENTITY_KEY_FLAG, server.getGameTime(),
                () -> Protection.mayTouch(server, owner, entity));
    }

    public MachineType machine() {
        return machine;
    }

    /** The Flux input tank. Menus and the machine itself use it directly; the outside world gets {@link #fluidHandler}. */
    public FluidTank fluxTank() {
        return fluxTank;
    }

    /** The extra tank at an index of {@link MachineType#extraTanks()}. */
    public FluidTank extraTank(int index) {
        return extraTanks.get(index);
    }

    public int extraTankCount() {
        return extraTanks.size();
    }

    /** The index of the extra tank of this kind, or -1 when the machine has none. */
    public int extraTankIndex(ExtraTank spec) {
        return machine.extraTanks().indexOf(spec);
    }

    /** mB in an extra tank. */
    public int extraAmount(int index) {
        return extraTanks.get(index).getFluidAmount();
    }

    /**
     * Takes fluid out of an extra tank.
     *
     * @param simulate true to only find out how much would come out
     * @return how much was (or would be) taken
     */
    protected int drainExtra(int index, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        return extraTanks.get(index).drain(amount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE)
                .getAmount();
    }

    /**
     * Puts fluid into an extra tank, whatever the face settings say.
     *
     * @return how much went (or would go) in
     */
    protected int fillExtra(int index, FluidStack stack, boolean simulate) {
        return extraTanks.get(index).fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    /** All slots, inputs first. Menus use it directly; the outside world gets {@link #itemHandler}. */
    public ItemStackHandler inventory() {
        return inventory;
    }

    /**
     * The comparator signal, read from the item slots the way a furnace's is: 0 when they are all
     * empty, 15 when every slot is full. Upgrades are not counted.
     */
    public int comparatorSignal() {
        return ItemHandlerHelper.calcRedstoneFromInventory(inventory);
    }

    public boolean autoInsert() {
        return autoInsert;
    }

    /** Switches the plant grower's seed refill on or off. Other machines have no such setting. */
    public void toggleAutoInsert() {
        if (machine == MachineType.PLANT_GROWER) {
            autoInsert = !autoInsert;
            setChanged();
        }
    }

    public boolean autoImport() {
        return autoImport;
    }

    public boolean autoExport() {
        return autoExport;
    }

    public void toggleAutoImport() {
        autoImport = !autoImport;
        setChanged();
    }

    public void toggleAutoExport() {
        autoExport = !autoExport;
        setChanged();
    }

    /**
     * Auto Import and Auto Export: the machine does a pipe's job for the blocks right next to it.
     * Every face set to Import (or Both) pulls items and Flux from the neighbor on that side, and
     * every face set to Export (or Both) pushes products, and the fluid of any output tank, into
     * it. The machine moves things through its own sided views, so the face rules hold exactly as
     * they do for a pipe: nothing goes into an output slot, and nothing comes out of an input.
     * Flux only ever comes in; a machine has no use for pushing its own fuel away.
     */
    private void autoTransfer(Level level) {
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            Direction back = side.getOpposite();
            if (autoImport && FaceRules.canInsert(PortRole.INPUT, mode(SideResource.ITEMS, side))) {
                IItemHandler from = neighbor(level, next, back, ForgeCapabilities.ITEM_HANDLER);
                IItemHandler into = itemHandler(side);
                if (from != null && into != null) {
                    moveItems(from, into, MachineMath.autoItems(tier));
                }
            }
            if (autoImport && FaceRules.canInsert(PortRole.BUFFER, mode(SideResource.FLUID, side))) {
                IFluidHandler from = neighbor(level, next, back, ForgeCapabilities.FLUID_HANDLER);
                IFluidHandler into = fluidHandler(side);
                if (from != null && into != null) {
                    FluidUtil.tryFluidTransfer(into, from, MachineMath.autoFlux(tier), true);
                }
            }
            if (autoExport && FaceRules.canExtract(PortRole.OUTPUT, mode(SideResource.FLUID, side))) {
                IFluidHandler to = neighbor(level, next, back, ForgeCapabilities.FLUID_HANDLER);
                for (int i = 0; i < extraTanks.size(); i++) {
                    if (machine.extraTanks().get(i).output() && to != null && !extraTanks.get(i).isEmpty()) {
                        FluidUtil.tryFluidTransfer(to, extraTanks.get(i), MachineMath.autoFlux(tier), true);
                    }
                }
            }
            if (autoExport && FaceRules.canExtract(PortRole.OUTPUT, mode(SideResource.ITEMS, side))) {
                IItemHandler to = neighbor(level, next, back, ForgeCapabilities.ITEM_HANDLER);
                IItemHandler out = itemHandler(side);
                if (to != null && out != null) {
                    moveItems(out, to, MachineMath.autoItems(tier));
                }
            }
        }
    }

    /** Moves up to {@code limit} items from one handler to another, slot by slot. */
    private static void moveItems(IItemHandler from, IItemHandler to, int limit) {
        int left = limit;
        for (int slot = 0; slot < from.getSlots() && left > 0; slot++) {
            ItemStack offered = from.extractItem(slot, left, true);
            if (offered.isEmpty()) {
                continue;
            }
            ItemStack rest = insertAnywhere(to, offered, true);
            int moved = offered.getCount() - rest.getCount();
            if (moved <= 0) {
                continue;
            }
            ItemStack taken = from.extractItem(slot, moved, false);
            ItemStack leftover = insertAnywhere(to, taken, false);
            if (!leftover.isEmpty()) {
                // Should not happen after the simulation; put it back rather than lose it.
                from.insertItem(slot, leftover, false);
            }
            left -= moved - leftover.getCount();
        }
    }

    private static ItemStack insertAnywhere(IItemHandler to, ItemStack stack, boolean simulate) {
        ItemStack rest = stack;
        for (int slot = 0; slot < to.getSlots() && !rest.isEmpty(); slot++) {
            rest = to.insertItem(slot, rest, simulate);
        }
        return rest;
    }

    /**
     * Moves anything the input slot takes from the output slots back into the input, topping up
     * what is there or filling an empty slot. This is how a plant grower replants its own seeds,
     * so it keeps growing without a hopper loop.
     */
    private void refillFromOutputs() {
        for (int out = machine.inputSlots(); out < machine.inputSlots() + machine.outputSlots(); out++) {
            ItemStack produce = stackIn(out);
            if (produce.isEmpty() || !isInputValid(0, produce)) {
                continue;
            }
            ItemStack input = stackIn(0);
            if (!input.isEmpty() && !ItemStack.isSameItemSameComponents(input, produce)) {
                continue;
            }
            int room = produce.getMaxStackSize() - input.getCount();
            int moved = Math.min(room, produce.getCount());
            if (moved <= 0) {
                return;
            }
            setStackIn(0, produce.copyWithCount(input.getCount() + moved));
            setStackIn(out, produce.copyWithCount(produce.getCount() - moved));
            sleeping = false;
            setChanged();
        }
    }

    /** What a slot holds, as a stack of its own that can be changed freely. */
    public ItemStack stackIn(int slot) {
        return inventory.getStackInSlot(slot).copy();
    }

    /** Puts a stack in a slot, whatever the slot would normally accept. */
    public void setStackIn(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }

    /** The Flux in the tank, in mB. */
    public int fluxAmount() {
        return fluxTank.getFluidAmount();
    }

    /** Replaces the contents of the tank, bypassing the usual checks. */
    public void setFlux(FluidStack stack) {
        fluxTank.setFluid(stack);
    }

    public SideConfigs sides() {
        return sides;
    }

    /** What the machine is doing, as of its last tick. */
    public MachineStatus status() {
        return status;
    }

    public int progress() {
        return timer.progress();
    }

    /** Ticks the current job needs with tier and upgrade speed applied; 0 when there is no job. */
    public int totalTime() {
        return timer.total();
    }

    /** Flux burned per tick while working, in mB, with Speed and Efficiency upgrades counted. */
    public int fluxPerTick() {
        return MachineUpgrades.fluxPerTick(tier, speedUpgrades(), upgrades(MachineUpgrades.Special.EFFICIENCY));
    }

    /** Flux the current job takes from start to finish, in mB; 0 with no job. See {@link FluxCost#perOperation}. */
    public int fluxPerOperation() {
        return FluxCost.perOperation(fluxPerTick(), totalTime(), jobExtraFlux);
    }

    /** Flux the next tick of the current job takes, in mB; 0 with no job. See {@link FluxCost#nextTick}. */
    public int nextTickCost() {
        return FluxCost.nextTick(fluxPerTick(), progress(), totalTime(), jobExtraFlux);
    }

    /** The size of the Flux tank in mB, with Capacity upgrades counted. */
    public int fluxCapacity() {
        return StorageSizes.machine(tier, capacityUpgrades());
    }

    // --- Upgrades and redstone ---------------------------------------------------------------

    /** The upgrade slots. Only menus use them; the outside world never sees them. */
    public MachineUpgradeSlots upgrades() {
        return upgrades;
    }

    public int speedUpgrades() {
        return upgrades.count(PipeUpgrades.Kind.SPEED);
    }

    public int capacityUpgrades() {
        return upgrades.count(PipeUpgrades.Kind.CAPACITY);
    }

    /** How many machine-only upgrades of one kind are installed. */
    public int upgrades(MachineUpgrades.Special kind) {
        return upgrades.count(kind);
    }

    /**
     * Called after an upgrade went in or came out. A job under way keeps its progress; the next
     * tick works out its new length. A tank left holding more than a smaller capacity keeps the
     * extra Flux, it just takes no more until it drops below the new size.
     */
    private void upgradesChanged() {
        setChanged();
        sleeping = false;
    }

    public RedstoneMode redstoneMode() {
        return redstone.mode();
    }

    /** Whether the redstone mode held the machine back on its last tick. */
    public boolean redstoneDisabled() {
        return redstoneDisabled;
    }

    /**
     * Picks a redstone mode and saves it.
     *
     * @return true if the mode changed
     */
    public boolean setRedstoneMode(RedstoneMode mode) {
        if (!redstone.setMode(mode)) {
            return false;
        }
        setChanged();
        sleeping = false;
        return true;
    }

    /**
     * Takes the redstone signal at the machine's position, called by the block when a neighbor
     * changes. A change wakes the machine so a pulse is never slept through.
     */
    void readSignal(Level level) {
        signalRead = true;
        if (redstone.signal(level.hasNeighborSignal(worldPosition))) {
            setChanged();
            sleeping = false;
        }
    }

    /** Whether a slot is one of the input slots. */
    public boolean isInputSlot(int slot) {
        return slot >= 0 && slot < machine.inputSlots();
    }

    /** Whether a slot is one of the output slots. */
    public boolean isOutputSlot(int slot) {
        return slot >= machine.inputSlots() && slot < inventory.getSlots();
    }

    /** Whether the tank takes this fluid: one of the flux types this machine is built for. */
    public boolean acceptsFlux(FluidStack stack) {
        FluxType type = ModFluids.fluxTypeOf(stack.getFluid());
        return type != null && machine.acceptedFlux().contains(type);
    }

    /** The flux type in the tank, or {@code null} if it is empty. */
    @Nullable
    public FluxType storedFlux() {
        return fluxTank.isEmpty() ? null : ModFluids.fluxTypeOf(fluxTank.getFluid().getFluid());
    }

    /** The machine's facing, as the face its front points to. */
    public Face facing() {
        return Face.byIndex(getBlockState().getValue(FluxMachineBlock.FACING).get3DDataValue());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MachineMenu(containerId, playerInventory, this);
    }

    /** What the client needs to build its copy of the menu; {@link FluxMachineMenu#readOpenData} reads it back. */
    public void writeOpenData(FriendlyByteBuf buf) {
        FluxMachineMenu.writeOpenData(buf, worldPosition, machine, tier, facing());
    }

    // --- Faces and capabilities --------------------------------------------------------------

    /**
     * The mode at a side for a resource. A query with no side (a player's bucket, another mod
     * that does not care about faces) is treated as an open face.
     */
    public ConnectionMode mode(SideResource resource, @Nullable Direction side) {
        return side == null ? ConnectionMode.OPEN : sides.mode(resource, Face.byIndex(side.get3DDataValue()));
    }

    /** The item view for a side, or {@code null} if the face is blocked. */
    @Nullable
    public IItemHandler itemHandler(@Nullable Direction side) {
        return itemView(side).orElse(null);
    }

    /** The fluid view for a side, or {@code null} if the face is blocked. */
    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        return fluidView(side).orElse(null);
    }

    private LazyOptional<IItemHandler> itemView(@Nullable Direction side) {
        if (!FaceRules.exposed(mode(SideResource.ITEMS, side))) {
            return LazyOptional.empty();
        }
        int index = viewIndex(side);
        LazyOptional<IItemHandler> view = itemViews.get(index);
        if (view == null) {
            SidedItemHandler handler = new SidedItemHandler(this, side);
            view = LazyOptional.of(() -> handler);
            itemViews.set(index, view);
        }
        return view;
    }

    private LazyOptional<IFluidHandler> fluidView(@Nullable Direction side) {
        if (!FaceRules.exposed(mode(SideResource.FLUID, side))) {
            return LazyOptional.empty();
        }
        int index = viewIndex(side);
        LazyOptional<IFluidHandler> view = fluidViews.get(index);
        if (view == null) {
            SidedFluidHandler handler = new SidedFluidHandler(this, side);
            view = LazyOptional.of(() -> handler);
            fluidViews.set(index, view);
        }
        return view;
    }

    private static int viewIndex(@Nullable Direction side) {
        return side == null ? SIDE_VIEWS - 1 : side.get3DDataValue();
    }

    /**
     * What the machine offers other blocks: items and fluid, each following the face settings. A
     * blocked face offers nothing at all, so pipes and hoppers do not even see the machine there.
     */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemView(side).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidView(side).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        dropViews();
    }

    /** Tells everyone holding a view that it is gone and forgets them, so the next query builds fresh ones. */
    private void dropViews() {
        for (int i = 0; i < SIDE_VIEWS; i++) {
            if (itemViews.get(i) != null) {
                itemViews.get(i).invalidate();
                itemViews.set(i, null);
            }
            if (fluidViews.get(i) != null) {
                fluidViews.get(i).invalidate();
                fluidViews.set(i, null);
            }
        }
    }

    /**
     * Called after the face settings changed. Saves them, drops the capabilities other blocks
     * have cached (a blocked face must vanish for them), and pokes the neighbors so pipes next to
     * the machine attach or detach.
     */
    void sidesChanged() {
        setChanged();
        dropViews();
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** A capability of the block on one side, as Forge hands it out, or {@code null}. */
    @Nullable
    private static <T> T neighbor(Level level, BlockPos pos, Direction side, Capability<T> capability) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(capability, side).orElse(null);
    }

    // --- Helpers for jobs --------------------------------------------------------------------

    /** The stacks in the input slots, in order. These are copies, so a job may change them freely. */
    protected List<ItemStack> inputStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < machine.inputSlots(); slot++) {
            stacks.add(stackIn(slot));
        }
        return stacks;
    }

    /** Takes items out of an input slot as part of a finished job. */
    protected void consumeInput(int inputSlot, int count) {
        ItemStack held = stackIn(inputSlot);
        held.shrink(count);
        setStackIn(inputSlot, held.isEmpty() ? ItemStack.EMPTY : held);
    }

    /** Whether every product has room in the output slots at once, merging with what is already there. */
    protected boolean canFitOutputs(List<ItemStack> products) {
        List<ItemStack> slots = outputCopies();
        for (ItemStack product : products) {
            if (!product.isEmpty() && !mergeInto(slots, product.copy())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Puts the products in the output slots, in order. Check {@link #canFitOutputs} for the
     * products that must fit first; a byproduct with no room left is lost rather than spilled
     * on the floor next to the machine.
     */
    protected void putOutputs(List<ItemStack> products) {
        List<ItemStack> slots = outputCopies();
        for (ItemStack product : products) {
            if (!product.isEmpty()) {
                mergeInto(slots, product.copy());
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            setStackIn(machine.inputSlots() + i, slots.get(i));
        }
    }

    private List<ItemStack> outputCopies() {
        List<ItemStack> copies = new ArrayList<>();
        for (int i = 0; i < machine.outputSlots(); i++) {
            copies.add(stackIn(machine.inputSlots() + i));
        }
        return copies;
    }

    /** Adds a stack to the first slot that can take it, topping up matching stacks before using an empty slot. */
    private static boolean mergeInto(List<ItemStack> slots, ItemStack stack) {
        for (ItemStack slot : slots) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int moved = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
                slot.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, stack);
                return true;
            }
        }
        return false;
    }

    // --- Ticking -----------------------------------------------------------------------------

    /** Ticker entry point; see {@link FluxMachineBlock#getTicker}. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxMachineBlockEntity machine) {
        machine.tick(level);
    }

    private void tick(Level level) {
        if (autoInsert && machine == MachineType.PLANT_GROWER) {
            refillFromOutputs();
        }
        if ((autoImport || autoExport) && level.getGameTime() % MachineMath.AUTO_INTERVAL == 0) {
            autoTransfer(level);
        }
        tickAlways(level);
        if (!signalRead) {
            // First tick since placing or loading: the block only reports changes, so look once.
            readSignal(level);
        }
        if (sleeping && level.getGameTime() % IDLE_CHECK_INTERVAL != 0) {
            return;
        }

        redstoneDisabled = !redstone.mayRun();
        if (redstoneDisabled) {
            // Held back by redstone: keep the progress, burn nothing, and sleep until the signal
            // or the mode changes (both wake the machine).
            sleeping = true;
            status = MachineStatus.IDLE;
            showWorking(level, false);
            showCrop(level);
            return;
        }

        Job job = findJob();
        if (job == null) {
            // Nothing to do: forget any half-finished work and sleep until the inventory changes.
            timer.reset();
            activeKey = null;
            jobExtraFlux = 0;
            sleeping = true;
            status = MachineStatus.IDLE;
            showWorking(level, false);
            showCrop(level);
            return;
        }
        if (reach == ProtectedMachines.Reach.OWN_SPOT && !mayActAt(worldPosition)) {
            // The owner's claims no longer allow this machine to work here: it keeps its inputs and
            // burns nothing, and looks again in a moment.
            timer.reset();
            activeKey = null;
            jobExtraFlux = 0;
            sleeping = true;
            status = MachineStatus.IDLE;
            showWorking(level, false);
            showCrop(level);
            return;
        }
        sleeping = false;

        if (!job.key().equals(activeKey)) {
            activeKey = job.key();
            timer.reset();
        }
        timer.setTotal(MachineUpgrades.ticksFor(job.baseTicks(), tier, speedUpgrades()));
        jobExtraFlux = job.extraFlux();

        // The last tick of a job also pays the job's extra Flux.
        boolean lastTick = timer.progress() + 1 >= timer.total();
        int cost = fluxPerTick() + (lastTick ? job.extraFlux() : 0);
        boolean outputFits = job.outputFits();
        FluxType required = job.requiredFlux();
        boolean rightFlux = required == null || required == storedFlux();
        boolean paid = outputFits && rightFlux && drainFlux(cost, true) >= cost;
        status = MachineStatus.decide(true, outputFits, paid);
        showWorking(level, status == MachineStatus.RUNNING);

        if (status == MachineStatus.RUNNING) {
            drainFlux(cost, false);
            if (timer.advance()) {
                job.complete();
                AeroFluxScripts.machineFinished(level, worldPosition, this);
                playSound(level, ModSounds.MACHINE_DONE.get());
                timer.reset();
                activeKey = null;
                redstone.jobFinished();
            }
            setChanged();
        }
        showCrop(level);
    }

    /**
     * A plant grower tells clients what its chamber should show. The crop only changes when it
     * grows a stage or the seed changes, so most ticks send nothing.
     */
    private void showCrop(Level level) {
        if (machine != MachineType.PLANT_GROWER) {
            return;
        }
        BlockState crop = CropDisplay.stateFor(stackIn(0), timer.progress(), timer.total());
        if (!Objects.equals(crop, shownCrop)) {
            shownCrop = crop;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Client sync --------------------------------------------------------------------------
    // Clients only need the crop a plant grower shows; everything else reaches the screen through the menu.

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (shownCrop != null) {
            tag.put(TAG_SHOWN_CROP, NbtUtils.writeBlockState(shownCrop));
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return machine == MachineType.PLANT_GROWER ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readShownCrop(tag, registries);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readShownCrop(packet.getTag(), registries);
    }

    /** Takes the crop from a sync and asks for a redraw when it changed. */
    private void readShownCrop(CompoundTag tag, HolderLookup.Provider registries) {
        BlockState crop = tag.contains(TAG_SHOWN_CROP)
                ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound(TAG_SHOWN_CROP))
                : null;
        if (Objects.equals(crop, shownCrop)) {
            return;
        }
        shownCrop = crop;
        requestModelDataUpdate();
        if (level != null) {
            // The chunk mesh only reads model data when it is rebuilt, so ask for that too.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public ModelData getModelData() {
        return shownCrop == null ? ModelData.EMPTY : ModelData.builder().with(CropDisplay.SHOWN_CROP, shownCrop).build();
    }

    /**
     * Switches the block's {@link FluxMachineBlock#LIT} state to match whether the machine is
     * working. Only a real change touches the world, so a steady machine costs no block updates.
     * The change is also when the start or stop sound plays; the running loop is the client's
     * business (it follows the same property).
     */
    private void showWorking(Level level, boolean working) {
        BlockState state = getBlockState();
        if (state.hasProperty(FluxMachineBlock.LIT) && state.getValue(FluxMachineBlock.LIT) != working) {
            level.setBlock(worldPosition, state.setValue(FluxMachineBlock.LIT, working), Block.UPDATE_ALL);
            playSound(level, working ? ModSounds.MACHINE_START.get() : ModSounds.MACHINE_STOP.get());
        }
    }

    /** Plays one of the shared one-shot machine sounds at the machine, for everyone nearby. */
    private void playSound(Level level, SoundEvent sound) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, ONE_SHOT_VOLUME, 1.0F);
    }

    /**
     * Takes Flux out of the tank.
     *
     * @param simulate true to only find out how much would come out
     * @return how much was (or would be) taken
     */
    protected int drainFlux(int amount, boolean simulate) {
        if (fluxTank.isEmpty()) {
            return 0;
        }
        return fluxTank.drain(amount, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    /** Throws the contents and the upgrades on the ground when the machine is broken. The Flux in the tank is lost. */
    void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackIn(slot));
        }
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getStackInSlot(slot).copy());
        }
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        SaveVersion.stamp(tag, SaveSchema.Kind.MACHINE);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        tag.put(TAG_FLUX, fluxTank.writeToNBT(new CompoundTag()));
        if (!extraTanks.isEmpty()) {
            CompoundTag tanksTag = new CompoundTag();
            for (int i = 0; i < extraTanks.size(); i++) {
                tanksTag.put("tank" + i, extraTanks.get(i).writeToNBT(new CompoundTag()));
            }
            tag.put(TAG_EXTRA_TANKS, tanksTag);
        }
        tag.put(TAG_ITEMS, inventory.serializeNBT(registries));
        tag.putInt(TAG_PROGRESS, timer.progress());
        tag.putInt(TAG_TOTAL, timer.total());
        CompoundTag sideTag = new CompoundTag();
        for (SideResource resource : sides.used()) {
            sideTag.putInt(resource.id(), sides.packed(resource));
        }
        tag.put(TAG_SIDES, sideTag);
        tag.put(TAG_UPGRADES, upgrades.serializeNBT(registries));
        CompoundTag redstoneTag = new CompoundTag();
        redstoneTag.putString(TAG_REDSTONE_MODE, redstone.mode().id());
        redstoneTag.putBoolean(TAG_REDSTONE_POWERED, redstone.powered());
        redstoneTag.putBoolean(TAG_REDSTONE_PULSE, redstone.pulseArmed());
        tag.put(TAG_REDSTONE, redstoneTag);
        tag.putBoolean(TAG_AUTO_INSERT, autoInsert);
        tag.putBoolean(TAG_AUTO_IMPORT, autoImport);
        tag.putBoolean(TAG_AUTO_EXPORT, autoExport);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // The version decides how the inventory is read (see fitInventory). The upgrades and the
        // redstone settings came later in version 1 and are simply missing from older saves, which
        // leaves an unupgraded machine that ignores redstone; the owner (version 3) is missing the
        // same way, which leaves a machine nobody's claim is asked about.
        int version = SaveVersion.read(SaveSchema.Kind.MACHINE, tag);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        protection.clear();
        fluxTank.readFromNBT(tag.getCompound(TAG_FLUX));
        CompoundTag tanksTag = tag.getCompound(TAG_EXTRA_TANKS);
        for (int i = 0; i < extraTanks.size(); i++) {
            extraTanks.get(i).readFromNBT(tanksTag.getCompound("tank" + i));
        }
        if (tag.contains(TAG_UPGRADES)) {
            upgrades.deserializeNBT(registries, tag.getCompound(TAG_UPGRADES));
        }
        if (tag.contains(TAG_REDSTONE)) {
            CompoundTag redstoneTag = tag.getCompound(TAG_REDSTONE);
            String mode = redstoneTag.getString(TAG_REDSTONE_MODE);
            redstone.restore(RedstoneMode.byId(mode.isEmpty() ? RedstoneMode.ALWAYS.id() : mode),
                    redstoneTag.getBoolean(TAG_REDSTONE_POWERED), redstoneTag.getBoolean(TAG_REDSTONE_PULSE));
        }
        signalRead = false;
        autoInsert = tag.getBoolean(TAG_AUTO_INSERT);
        autoImport = tag.getBoolean(TAG_AUTO_IMPORT);
        autoExport = tag.getBoolean(TAG_AUTO_EXPORT);
        inventory.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
        fitInventory(version);
        timer.restore(tag.getInt(TAG_PROGRESS), tag.getInt(TAG_TOTAL));
        CompoundTag sideTag = tag.getCompound(TAG_SIDES);
        for (SideResource resource : sides.used()) {
            if (sideTag.contains(resource.id())) {
                sides.restore(resource, sideTag.getInt(resource.id()));
            }
        }
        sleeping = false;
    }

    /**
     * Brings the loaded slots to the layout this machine has now while keeping what they hold; the
     * steps are shared-core's {@link MachineSaveMigrations}. Forge's item handler does not save its
     * size, so the slots always load at today's count; a Sifter saved before its mesh slot (version 1
     * or none) is recognised by its last slot being empty, and its slots are migrated as the shorter
     * list they were saved as, which moves every output one slot further along.
     */
    private void fitInventory(int version) {
        int wanted = machine.inputSlots() + machine.outputSlots();
        List<ItemStack> loaded = new ArrayList<>(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            loaded.add(inventory.getStackInSlot(slot));
        }
        if (loaded.size() == wanted) {
            boolean oldSifter = version < 2 && machine == MachineType.SIFTER && loaded.get(wanted - 1).isEmpty();
            if (!oldSifter) {
                return;
            }
            // The slots were saved without the mesh slot, so the layout they were saved in is one shorter.
            loaded = new ArrayList<>(loaded.subList(0, wanted - 1));
        }
        MachineSaveMigrations.Inventory<ItemStack> saved = new MachineSaveMigrations.Inventory<>(
                loaded, machine.inputSlots(), machine.outputSlots(), ItemStack.EMPTY);
        List<ItemStack> migrated = MachineSaveMigrations.migrate(saved, version).stacks();
        inventory.setSize(migrated.size());
        for (int slot = 0; slot < migrated.size(); slot++) {
            inventory.setStackInSlot(slot, migrated.get(slot));
        }
    }
}
