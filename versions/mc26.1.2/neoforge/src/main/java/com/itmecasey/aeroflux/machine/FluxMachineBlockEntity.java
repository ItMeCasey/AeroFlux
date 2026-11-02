package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.FaceRules;
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
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The shared body of every Flux-powered machine (Furnace, Grinder, Sawmill and the rest).
 *
 * <p>A machine has a Flux input tank that takes only the flux types its {@link MachineType} is
 * built for, a row of input slots followed by a row of output slots, a progress counter, and a
 * face setting for each kind of resource it uses. Each tick on the server it asks its subclass
 * what job the inputs make ({@link #findJob()}), and while the job can run it burns
 * {@link MachineMath#fluxPerTick Flux} from the tank and advances the progress; when the progress
 * is full the job hands over its product. A subclass only has to say which job it has and what
 * finishing it does.
 *
 * <p>The outside world sees the machine through item and fluid capabilities that follow the face
 * settings (see {@link SidedItemHandler} and {@link SidedFluidHandler}); the player sees it through
 * its menu. Tier decides the speed, the Flux cost and the tank size, all through {@link MachineMath};
 * Speed and Capacity upgrades in the upgrade slots change those numbers through
 * {@link MachineUpgrades}. A {@link RedstoneControl} can hold the machine back: while it does, the
 * machine keeps its progress, burns nothing and stays unlit.
 */
public abstract class FluxMachineBlockEntity extends BlockEntity implements MenuProvider {
    /** Version of the saved layout, so a later change can still read today's worlds. */
    private static final int SCHEMA_VERSION = 1;
    private static final String TAG_SCHEMA = "schema_version";
    private static final String TAG_FLUX = "flux";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_SHOWN_CROP = "shown_crop";
    private static final String TAG_TOTAL = "total";
    private static final String TAG_SIDES = "sides";
    private static final String TAG_UPGRADES = "upgrades";
    private static final String TAG_REDSTONE = "redstone";
    private static final String TAG_AUTO_INSERT = "auto_insert";
    private static final String TAG_AUTO_IMPORT = "auto_import";
    private static final String TAG_AUTO_EXPORT = "auto_export";
    private static final String TAG_REDSTONE_MODE = "mode";
    private static final String TAG_REDSTONE_POWERED = "powered";
    private static final String TAG_REDSTONE_PULSE = "pulse";

    /** An idle machine looks for new work this often instead of every tick. */
    private static final int IDLE_CHECK_INTERVAL = 10;

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
        default @Nullable FluxType requiredFlux() {
            return null;
        }
    }

    private final Tier tier;
    private final MachineType machine;
    private final FluidStacksResourceHandler fluxTank;
    private final MachineInventory inventory;
    private final SideConfigs sides;
    private final ProcessTimer timer = new ProcessTimer();
    private final SidedItemHandler[] itemViews = new SidedItemHandler[7];
    private final SidedFluidHandler[] fluidViews = new SidedFluidHandler[7];
    private final MachineUpgradeSlots upgrades;
    private final RedstoneControl redstone = new RedstoneControl();

    private MachineStatus status = MachineStatus.IDLE;
    /** True while the redstone mode holds the machine back; the screen then says "Disabled". */
    private boolean redstoneDisabled;
    /** False until the machine has looked at its redstone signal since it was placed or loaded. */
    private boolean signalRead;
    private @Nullable Object activeKey;
    /** True while there was nothing to do, so the machine only checks now and then. */
    private boolean sleeping;
    /** What a plant grower shows in its chamber; see {@link CropDisplay}. Always null for other machines. */
    private @Nullable BlockState shownCrop;

    protected FluxMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
        FluxMachineBlock block = (FluxMachineBlock) state.getBlock();
        this.tier = block.tier();
        this.machine = block.machine();
        this.sides = new SideConfigs(machine.resources());
        // A single slot, so the tank can only ever hold one kind of flux.
        this.fluxTank = new FluidStacksResourceHandler(1, MachineMath.inputCapacity(tier)) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update, or a Capacity upgrade came out); it still takes nothing more, and probes such as
             * Jade, which refuse a fill above 100%, see a full tank instead of crashing.
             */
            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return Math.max(super.getCapacityAsLong(index, resource), getAmountAsLong(index));
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return acceptsFlux(resource);
            }

            @Override
            protected int getCapacity(int index, FluidResource resource) {
                // Capacity upgrades can change this at any time, so it is never cached.
                return fluxCapacity();
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
        this.inventory = new MachineInventory(machine.inputSlots() + machine.outputSlots());
        this.upgrades = new MachineUpgradeSlots(MachineUpgrades.slots(tier), this::upgradesChanged);
    }

    /** The slots: input slots take what the machine accepts, output slots take nothing from outside. */
    private final class MachineInventory extends ItemStacksResourceHandler {
        MachineInventory(int size) {
            super(size);
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return isInputSlot(slot) && isInputValid(slot, resource.toStack(1));
        }

        @Override
        protected void onContentsChanged(int slot, ItemStack previousContents) {
            setChanged();
            sleeping = false;
        }

        @Override
        public void deserialize(ValueInput input) {
            super.deserialize(input);
            int wanted = machine.inputSlots() + machine.outputSlots();
            if (size() != wanted) {
                // A save from a build with a different slot count: keep the size this machine needs.
                NonNullList<ItemStack> resized = NonNullList.withSize(wanted, ItemStack.EMPTY);
                for (int i = 0; i < Math.min(wanted, size()); i++) {
                    resized.set(i, stacks.get(i));
                }
                setStacks(resized);
            }
        }
    }

    // --- What a subclass provides ------------------------------------------------------------

    /** The job the current inputs make, or {@code null} if there is nothing to work on. Runs every tick the machine is awake. */
    protected abstract @Nullable Job findJob();

    /** Whether a stack may be put into an input slot, such as "has a smelting recipe". Called for players and for pipes alike. */
    protected abstract boolean isInputValid(int slot, ItemStack stack);

    // --- Parts -------------------------------------------------------------------------------

    public Tier tier() {
        return tier;
    }

    public MachineType machine() {
        return machine;
    }

    /** The Flux input tank. Menus and the machine itself use it directly; the outside world gets {@link #fluidHandler}. */
    public FluidStacksResourceHandler fluxTank() {
        return fluxTank;
    }

    /** All slots, inputs first. Menus use it directly; the outside world gets {@link #itemHandler}. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    /** Whether a plant grower puts seeds from its outputs back into its input; see {@link #refillFromOutputs}. */
    private boolean autoInsert;

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

    /** Whether the machine pulls from its neighbors through its import faces; see {@link #autoTransfer}. */
    private boolean autoImport;
    /** Whether the machine pushes its products out through its export faces; see {@link #autoTransfer}. */
    private boolean autoExport;

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
     * every face set to Export (or Both) pushes products into it. The machine moves things through
     * its own sided views, so the face rules hold exactly as they do for a pipe: nothing goes into
     * an output slot, and nothing comes out of an input. Flux only ever comes in; a machine has no
     * use for pushing its own fuel away.
     */
    private void autoTransfer(Level level) {
        for (Direction side : Direction.values()) {
            BlockPos next = worldPosition.relative(side);
            Direction back = side.getOpposite();
            if (autoImport && FaceRules.canInsert(PortRole.INPUT, mode(SideResource.ITEMS, side))) {
                ResourceHandler<ItemResource> from = level.getCapability(Capabilities.Item.BLOCK, next, back);
                ResourceHandler<ItemResource> into = itemHandler(side);
                if (from != null && into != null) {
                    ResourceHandlerUtil.move(from, into, resource -> true, MachineMath.autoItems(tier), null);
                }
            }
            if (autoImport && FaceRules.canInsert(PortRole.BUFFER, mode(SideResource.FLUID, side))) {
                ResourceHandler<FluidResource> from = level.getCapability(Capabilities.Fluid.BLOCK, next, back);
                ResourceHandler<FluidResource> into = fluidHandler(side);
                if (from != null && into != null) {
                    ResourceHandlerUtil.move(from, into, resource -> true, MachineMath.autoFlux(tier), null);
                }
            }
            if (autoExport && FaceRules.canExtract(PortRole.OUTPUT, mode(SideResource.ITEMS, side))) {
                ResourceHandler<ItemResource> to = level.getCapability(Capabilities.Item.BLOCK, next, back);
                ResourceHandler<ItemResource> out = itemHandler(side);
                if (to != null && out != null) {
                    ResourceHandlerUtil.move(out, to, resource -> true, MachineMath.autoItems(tier), null);
                }
            }
        }
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
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }

    /** Puts a stack in a slot, whatever the slot would normally accept. */
    public void setStackIn(int slot, ItemStack stack) {
        inventory.set(slot, ItemResource.of(stack), stack.getCount());
    }

    /** The Flux in the tank, in mB. */
    public int fluxAmount() {
        return fluxTank.getAmountAsInt(0);
    }

    /** Replaces the contents of the tank, bypassing the usual checks. */
    public void setFlux(FluidResource resource, int amount) {
        fluxTank.set(0, resource, amount);
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

    /** Flux burned per tick while working, in mB, with Speed upgrades counted. */
    public int fluxPerTick() {
        return MachineUpgrades.fluxPerTick(tier, speedUpgrades());
    }

    /** The size of the Flux tank in mB, with Capacity upgrades counted. */
    public int fluxCapacity() {
        return MachineUpgrades.inputCapacity(tier, capacityUpgrades());
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
        return slot >= machine.inputSlots() && slot < inventory.size();
    }

    /** Whether the tank takes this fluid: one of the flux types this machine is built for. */
    public boolean acceptsFlux(FluidResource resource) {
        FluxType type = ModFluids.fluxTypeOf(resource);
        return type != null && machine.acceptedFlux().contains(type);
    }

    /** The flux type in the tank, or {@code null} if it is empty. */
    public @Nullable FluxType storedFlux() {
        FluidResource held = fluxTank.getResource(0);
        return held.isEmpty() ? null : ModFluids.fluxTypeOf(held);
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
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
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

    /** The item capability for a side, or {@code null} if the face is blocked. */
    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        if (!FaceRules.exposed(mode(SideResource.ITEMS, side))) {
            return null;
        }
        int index = side == null ? 6 : side.get3DDataValue();
        if (itemViews[index] == null) {
            itemViews[index] = new SidedItemHandler(this, side);
        }
        return itemViews[index];
    }

    /** The fluid capability for a side, or {@code null} if the face is blocked. */
    public @Nullable ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        if (!FaceRules.exposed(mode(SideResource.FLUID, side))) {
            return null;
        }
        int index = side == null ? 6 : side.get3DDataValue();
        if (fluidViews[index] == null) {
            fluidViews[index] = new SidedFluidHandler(this, side);
        }
        return fluidViews[index];
    }

    /**
     * Called after the face settings changed. Saves them, drops the capabilities other blocks
     * have cached (a blocked face must vanish for them), and pokes the neighbors so pipes next to
     * the machine attach or detach.
     */
    void sidesChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(worldPosition);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
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
        ItemResource held = inventory.getResource(inputSlot);
        if (held.isEmpty()) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            inventory.extract(inputSlot, held, count, transaction);
            transaction.commit();
        }
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
            tag.store(TAG_SHOWN_CROP, BlockState.CODEC, shownCrop);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return machine == MachineType.PLANT_GROWER ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        readShownCrop(input);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readShownCrop(input);
    }

    /** Takes the crop from a sync and asks for a redraw when it changed. */
    private void readShownCrop(ValueInput input) {
        BlockState crop = input.read(TAG_SHOWN_CROP, BlockState.CODEC).orElse(null);
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
        return shownCrop == null ? ModelData.EMPTY : ModelData.of(CropDisplay.SHOWN_CROP, shownCrop);
    }

    /**
     * Switches the block's {@link FluxMachineBlock#LIT} state to match whether the machine is
     * working. Only a real change touches the world, so a steady machine costs no block updates.
     */
    private void showWorking(Level level, boolean working) {
        BlockState state = getBlockState();
        if (state.hasProperty(FluxMachineBlock.LIT) && state.getValue(FluxMachineBlock.LIT) != working) {
            level.setBlock(worldPosition, state.setValue(FluxMachineBlock.LIT, working), Block.UPDATE_ALL);
        }
    }

    /**
     * Takes Flux out of the tank.
     *
     * @param simulate true to only find out how much would come out
     * @return how much was (or would be) taken
     */
    private int drainFlux(int amount, boolean simulate) {
        FluidResource held = fluxTank.getResource(0);
        if (held.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = fluxTank.extract(0, held, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return taken;
        }
    }

    /** Throws the contents and the upgrades on the ground when the machine is broken. The Flux in the tank is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackIn(slot));
        }
        for (int slot = 0; slot < upgrades.size(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.stackIn(slot));
        }
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_SCHEMA, SCHEMA_VERSION);
        fluxTank.serialize(output.child(TAG_FLUX));
        inventory.serialize(output.child(TAG_ITEMS));
        output.putInt(TAG_PROGRESS, timer.progress());
        output.putInt(TAG_TOTAL, timer.total());
        ValueOutput sideOutput = output.child(TAG_SIDES);
        for (SideResource resource : sides.used()) {
            sideOutput.putInt(resource.id(), sides.packed(resource));
        }
        upgrades.serialize(output.child(TAG_UPGRADES));
        ValueOutput redstoneOutput = output.child(TAG_REDSTONE);
        redstoneOutput.putString(TAG_REDSTONE_MODE, redstone.mode().id());
        redstoneOutput.putBoolean(TAG_REDSTONE_POWERED, redstone.powered());
        redstoneOutput.putBoolean(TAG_REDSTONE_PULSE, redstone.pulseArmed());
        output.putBoolean(TAG_AUTO_INSERT, autoInsert);
        output.putBoolean(TAG_AUTO_IMPORT, autoImport);
        output.putBoolean(TAG_AUTO_EXPORT, autoExport);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; later versions will branch on TAG_SCHEMA here.
        // The upgrades and the redstone settings came later in version 1 and are simply missing
        // from older saves, which leaves an unupgraded machine that ignores redstone.
        input.child(TAG_FLUX).ifPresent(fluxTank::deserialize);
        input.child(TAG_UPGRADES).ifPresent(upgrades::deserialize);
        input.child(TAG_REDSTONE).ifPresent(redstoneInput -> redstone.restore(
                RedstoneMode.byId(redstoneInput.getStringOr(TAG_REDSTONE_MODE, RedstoneMode.ALWAYS.id())),
                redstoneInput.getBooleanOr(TAG_REDSTONE_POWERED, false),
                redstoneInput.getBooleanOr(TAG_REDSTONE_PULSE, false)));
        signalRead = false;
        autoInsert = input.getBooleanOr(TAG_AUTO_INSERT, false);
        autoImport = input.getBooleanOr(TAG_AUTO_IMPORT, false);
        autoExport = input.getBooleanOr(TAG_AUTO_EXPORT, false);
        input.child(TAG_ITEMS).ifPresent(inventory::deserialize);
        timer.restore(input.getIntOr(TAG_PROGRESS, 0), input.getIntOr(TAG_TOTAL, 0));
        input.child(TAG_SIDES).ifPresent(sideInput -> {
            for (SideResource resource : sides.used()) {
                sideInput.getInt(resource.id()).ifPresent(packed -> sides.restore(resource, packed));
            }
        });
        sleeping = false;
    }
}
