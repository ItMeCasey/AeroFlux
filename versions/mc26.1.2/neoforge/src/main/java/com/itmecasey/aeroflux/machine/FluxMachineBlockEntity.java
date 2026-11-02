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
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
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

    private MachineStatus status = MachineStatus.IDLE;
    private @Nullable Object activeKey;
    /** True while there was nothing to do, so the machine only checks now and then. */
    private boolean sleeping;

    protected FluxMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
        FluxMachineBlock block = (FluxMachineBlock) state.getBlock();
        this.tier = block.tier();
        this.machine = block.machine();
        this.sides = new SideConfigs(machine.resources());
        // A single slot, so the tank can only ever hold one kind of flux.
        this.fluxTank = new FluidStacksResourceHandler(1, MachineMath.inputCapacity(tier)) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return acceptsFlux(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
        this.inventory = new MachineInventory(machine.inputSlots() + machine.outputSlots());
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

    /** Puts every product in the output slots. Check {@link #canFitOutputs} first; what does not fit is dropped. */
    protected void putOutputs(List<ItemStack> products) {
        List<ItemStack> slots = outputCopies();
        for (ItemStack product : products) {
            if (!product.isEmpty() && !mergeInto(slots, product.copy()) && level != null) {
                Block.popResource(level, worldPosition, product.copy());
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
        boolean paid = outputFits && rightFlux && drainFlux(cost, true) >= cost;
        status = MachineStatus.decide(true, outputFits, paid);

        if (status == MachineStatus.RUNNING) {
            drainFlux(cost, false);
            if (timer.advance()) {
                job.complete();
                timer.reset();
                activeKey = null;
            }
            setChanged();
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

    /** Throws the contents on the ground when the machine is broken. The Flux in the tank is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackIn(slot));
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; later versions will branch on TAG_SCHEMA here.
        input.child(TAG_FLUX).ifPresent(fluxTank::deserialize);
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
