package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.FaceRules;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.ProcessTimer;
import com.itmecasey.aeroflux.core.machine.SideConfigs;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * its menu. Tier decides the speed, the Flux cost and the tank size, all through {@link MachineMath}.
 */
public abstract class FluxMachineBlockEntity extends BlockEntity implements MenuProvider {
    /** Version of the saved layout, so a later change can still read today's worlds. */
    private static final int SCHEMA_VERSION = 1;
    private static final String TAG_SCHEMA = "schema_version";
    private static final String TAG_FLUX = "flux";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_TOTAL = "total";
    private static final String TAG_SIDES = "sides";

    /** An idle machine looks for new work this often instead of every tick. */
    private static final int IDLE_CHECK_INTERVAL = 10;

    /** Index of the view handed to a query without a side; the six sides use their direction's index. */
    private static final int NO_SIDE = 6;

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
    private final MachineInventory inventory;
    private final SideConfigs sides;
    private final ProcessTimer timer = new ProcessTimer();
    /** What each face is shown, indexed like {@link #NO_SIDE}; the face settings decide per query whether it is shown at all. */
    private final List<LazyOptional<IItemHandler>> itemViews = new ArrayList<>();
    private final List<LazyOptional<IFluidHandler>> fluidViews = new ArrayList<>();

    private MachineStatus status = MachineStatus.IDLE;
    private Object activeKey;
    /** True while there was nothing to do, so the machine only checks now and then. */
    private boolean sleeping;

    protected FluxMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
        FluxMachineBlock block = (FluxMachineBlock) state.getBlock();
        this.tier = block.tier();
        this.machine = block.machine();
        this.sides = new SideConfigs(machine.resources());
        this.fluxTank = new FluidTank(MachineMath.inputCapacity(tier), this::acceptsFlux) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        this.inventory = new MachineInventory(machine.inputSlots() + machine.outputSlots());
        for (int index = 0; index <= NO_SIDE; index++) {
            Direction side = index == NO_SIDE ? null : Direction.from3DDataValue(index);
            SidedItemHandler items = new SidedItemHandler(this, side);
            SidedFluidHandler fluids = new SidedFluidHandler(this, side);
            itemViews.add(LazyOptional.of(() -> items));
            fluidViews.add(LazyOptional.of(() -> fluids));
        }
    }

    // --- What a subclass provides ------------------------------------------------------------

    /** The job the current inputs make, or {@code null} if there is nothing to work on. Runs every tick the machine is awake. */
    @Nullable
    protected abstract Job findJob();

    /** Whether a stack may be put into an input slot, such as "has a smelting recipe". Called for players and for pipes alike. */
    protected abstract boolean isInputValid(int slot, ItemStack stack);

    // --- Parts -------------------------------------------------------------------------------

    public Tier tier() {
        return tier;
    }

    public MachineType machine() {
        return machine;
    }

    /** The Flux input tank. Menus and the machine itself use it directly; the outside world gets the sided fluid view. */
    public FluidTank fluxTank() {
        return fluxTank;
    }

    /** All slots, inputs first. Menus use it directly; the outside world gets the sided item view. */
    public ItemStackHandler inventory() {
        return inventory;
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

    /** Ticks the current job needs with tier speed applied; 0 when there is no job. */
    public int totalTime() {
        return timer.total();
    }

    /** Flux burned per tick while working, in mB. */
    public int fluxPerTick() {
        return MachineMath.fluxPerTick(tier);
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

    /**
     * Shows the item and fluid views the face settings allow. The settings are read on every
     * query, so a blocked face disappears for pipes and hoppers at once, and a blocked face shows
     * nothing at all rather than a handler that refuses everything.
     */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        int index = side == null ? NO_SIDE : side.get3DDataValue();
        if (capability == ForgeCapabilities.ITEM_HANDLER && FaceRules.exposed(mode(SideResource.ITEMS, side))) {
            return itemViews.get(index).cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER && FaceRules.exposed(mode(SideResource.FLUID, side))) {
            return fluidViews.get(index).cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemViews.forEach(LazyOptional::invalidate);
        fluidViews.forEach(LazyOptional::invalidate);
    }

    /**
     * Called after the face settings changed. Saves them and pokes the neighbors so pipes next
     * to the machine attach or detach.
     */
    void sidesChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock(), null);
        }
    }

    // --- Helpers for jobs --------------------------------------------------------------------

    /** The stacks in the input slots, in order. These are the real stacks, so jobs must not change them directly. */
    protected List<ItemStack> inputStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < machine.inputSlots(); slot++) {
            stacks.add(inventory.getStackInSlot(slot));
        }
        return stacks;
    }

    /** Takes items out of an input slot as part of a finished job. */
    protected void consumeInput(int inputSlot, int count) {
        inventory.extractItem(inputSlot, count, false);
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

    /** Puts every product in the output slots. Check {@link #canFitOutputs} first; what does not fit is dropped. */
    protected void putOutputs(List<ItemStack> products) {
        List<ItemStack> slots = outputCopies();
        for (ItemStack product : products) {
            if (!product.isEmpty() && !mergeInto(slots, product.copy()) && level != null) {
                Block.popResource(level, worldPosition, product.copy());
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            inventory.setStackInSlot(machine.inputSlots() + i, slots.get(i));
        }
    }

    private List<ItemStack> outputCopies() {
        List<ItemStack> copies = new ArrayList<>();
        for (int i = 0; i < machine.outputSlots(); i++) {
            copies.add(inventory.getStackInSlot(machine.inputSlots() + i).copy());
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
        if (sleeping && level.getGameTime() % IDLE_CHECK_INTERVAL != 0) {
            return;
        }

        Job job = findJob();
        if (job == null) {
            // Nothing to do: forget any half-finished work and sleep until the inventory changes.
            timer.reset();
            activeKey = null;
            sleeping = true;
            status = MachineStatus.IDLE;
            return;
        }
        sleeping = false;

        if (!job.key().equals(activeKey)) {
            activeKey = job.key();
            timer.reset();
        }
        timer.setTotal(MachineMath.ticksFor(job.baseTicks(), tier));

        // The last tick of a job also pays the job's extra Flux.
        boolean lastTick = timer.progress() + 1 >= timer.total();
        int cost = MachineMath.fluxPerTick(tier) + (lastTick ? job.extraFlux() : 0);
        boolean outputFits = job.outputFits();
        FluxType required = job.requiredFlux();
        boolean rightFlux = required == null || required == storedFlux();
        boolean paid = outputFits && rightFlux && fluxTank.drain(cost, IFluidHandler.FluidAction.SIMULATE).getAmount() >= cost;
        status = MachineStatus.decide(true, outputFits, paid);

        if (status == MachineStatus.RUNNING) {
            fluxTank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
            if (timer.advance()) {
                job.complete();
                timer.reset();
                activeKey = null;
            }
            setChanged();
        }
    }

    /** Throws the contents on the ground when the machine is broken. The Flux in the tank is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(slot));
        }
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_SCHEMA, SCHEMA_VERSION);
        fluxTank.writeTo(output.child(TAG_FLUX));
        inventory.save(output.child(TAG_ITEMS));
        output.putInt(TAG_PROGRESS, timer.progress());
        output.putInt(TAG_TOTAL, timer.total());
        ValueOutput sideOutput = output.child(TAG_SIDES);
        for (SideResource resource : sides.used()) {
            sideOutput.putInt(resource.id(), sides.packed(resource));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; later versions will branch on TAG_SCHEMA here.
        fluxTank.readFrom(input.childOrEmpty(TAG_FLUX));
        inventory.load(input.childOrEmpty(TAG_ITEMS));
        timer.restore(input.getIntOr(TAG_PROGRESS, 0), input.getIntOr(TAG_TOTAL, 0));
        ValueInput sideInput = input.childOrEmpty(TAG_SIDES);
        for (SideResource resource : sides.used()) {
            sideInput.getInt(resource.id()).ifPresent(packed -> sides.restore(resource, packed));
        }
        sleeping = false;
    }

    /** The slots, which wake the machine and save the block when their contents change, and which turn away what the machine cannot use. */
    private final class MachineInventory extends ItemStackHandler {
        MachineInventory(int size) {
            super(size);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sleeping = false;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isInputSlot(slot) && isInputValid(slot, stack);
        }

        void save(ValueOutput output) {
            ContainerHelper.saveAllItems(output, stacks);
        }

        /** Starts from empty slots at the size this machine needs, so a save from a build with another slot count still loads. */
        void load(ValueInput input) {
            setSize(machine.inputSlots() + machine.outputSlots());
            ContainerHelper.loadAllItems(input, stacks);
        }
    }
}
