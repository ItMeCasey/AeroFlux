package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideCommand;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.machine.MachineFactory;
import com.itmecasey.aeroflux.machine.MachineMenu;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The self-test for machine upgrades and redstone control. Like {@link MachineSelfTest} it builds
 * every scenario at once, in its own layer of the forced chunk above the other machine checks, and
 * reads the timed ones at fixed tick counts. The menu, saving and breaking checks need no waiting
 * and run as soon as everything is built.
 *
 * <p>It uses a fake player of its own, so nothing it leaves in an inventory can upset the screen
 * checks that run at the same time.
 */
final class MachineUpgradeSelfTest {
    private static final int Y = 316;
    /** Tick at which the job lengths are read; every machine has started by then. */
    private static final int STARTED = 5;
    /** Tick at which the redstone machines are checked while held back, then released. */
    private static final int RELEASE = 20;
    /** Tick at which the pulse ends; two ticks is as short as a button press gets. */
    private static final int PULSE_END = RELEASE + 2;
    /** Tick at which the sped-up furnace is long done and the plain one is still working. */
    private static final int SPED_DONE = 60;
    /** Tick at which the pulsed furnace would have made three items if a pulse did not stop it. */
    private static final int LAST = 110;
    /** A vanilla smelt, the job every furnace here does. */
    private static final int SMELT = 200;
    private static final int START_FLUX = 4_000;

    private static ServerLevel level;
    private static FakePlayer player;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos plainFurnace;
    private static BlockPos spedFurnace;
    private static BlockPos alwaysFurnace;
    private static BlockPos highFurnace;
    private static BlockPos lowFurnace;
    private static BlockPos pulseFurnace;
    private static BlockPos autoFurnace;

    private MachineUpgradeSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        built = false;
        finished = false;
    }

    /** Advances the scenarios by one server tick; they are built on the first tick the chunk really ticks. */
    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            BlockPos probe = new BlockPos(2, Y, 12);
            if (level.shouldTickBlocksAt(probe) && level.isPositionEntityTicking(probe)) {
                UUID id = UUID.nameUUIDFromBytes("aeroflux-upgrades".getBytes(StandardCharsets.UTF_8));
                player = FakePlayerFactory.get(level, new GameProfile(id, "aeroflux_upgrades"));
                build();
                built = true;
            }
            return;
        }
        tick++;
        if (tick == STARTED) {
            checkJobLengths();
        }
        if (tick == RELEASE) {
            checkHeldBack();
            release();
        }
        if (tick == PULSE_END) {
            level.setBlock(pulseFurnace.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (tick == SPED_DONE) {
            checkSpeedResult();
            checkReleased();
        }
        if (tick == LAST) {
            checkPulse();
            checkAutoTransfer();
            finished = true;
        }
    }

    // --- Building ----------------------------------------------------------------------------

    private static void build() {
        // Speed: the same smelt with and without two Speed upgrades.
        plainFurnace = furnace(1, Y, 12, Tier.COPPER, START_FLUX, new ItemStack(Items.RAW_IRON));
        spedFurnace = furnace(4, Y, 12, Tier.COPPER, START_FLUX, new ItemStack(Items.RAW_IRON));
        install(machine(spedFurnace), ModItems.SPEED_UPGRADE.get(), 2);

        // Redstone: one furnace per mode, at the top tier but one so a job is short. Spaced so a
        // redstone block on one never powers the next.
        alwaysFurnace = furnace(1, Y, 14, Tier.NETHERITE, START_FLUX, new ItemStack(Items.RAW_IRON, 3));
        level.setBlock(alwaysFurnace.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        highFurnace = furnace(4, Y, 14, Tier.NETHERITE, START_FLUX, new ItemStack(Items.RAW_IRON, 3));
        MachineConfigActions.setRedstoneMode(machine(highFurnace), RedstoneMode.HIGH);
        lowFurnace = furnace(7, Y, 14, Tier.NETHERITE, START_FLUX, new ItemStack(Items.RAW_IRON, 3));
        MachineConfigActions.setRedstoneMode(machine(lowFurnace), RedstoneMode.LOW);
        level.setBlock(lowFurnace.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        pulseFurnace = furnace(10, Y, 14, Tier.NETHERITE, START_FLUX, new ItemStack(Items.RAW_IRON, 3));
        MachineConfigActions.setRedstoneMode(machine(pulseFurnace), RedstoneMode.PULSE);

        checkCapacity(furnace(7, Y, 12, Tier.COPPER, 0, ItemStack.EMPTY));
        checkMenu(furnace(10, Y, 12, Tier.COPPER, 0, ItemStack.EMPTY));
        checkNetheriteSlots(furnace(13, Y, 12, Tier.NETHERITE, 0, ItemStack.EMPTY));
        checkPersistence(furnace(1, Y, 10, Tier.COPPER, 0, ItemStack.EMPTY));
        checkDrops(furnace(4, Y, 10, Tier.COPPER, 0, new ItemStack(Items.RAW_IRON)));

        // Auto Import and Export: raw iron in a chest on top, an empty chest to the east, no pipes.
        autoFurnace = furnace(10, Y, 10, Tier.NETHERITE, START_FLUX, ItemStack.EMPTY);
        FluxMachineBlockEntity auto = machine(autoFurnace);
        MachineConfigActions.apply(auto, SideCommand.set(SideResource.ITEMS, Face.UP, ConnectionMode.IMPORT));
        MachineConfigActions.apply(auto, SideCommand.set(SideResource.ITEMS, Face.EAST, ConnectionMode.EXPORT));
        MachineConfigActions.applyButton(auto, MachineConfigActions.AUTO_IMPORT_BUTTON);
        MachineConfigActions.applyButton(auto, MachineConfigActions.AUTO_EXPORT_BUTTON);
        level.setBlock(autoFurnace.above(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(autoFurnace.east(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        chest(autoFurnace.above()).setItem(0, new ItemStack(Items.RAW_IRON, 8));
    }

    // --- Auto Import and Export --------------------------------------------------------------

    private static void checkAutoTransfer() {
        Container above = chest(autoFurnace.above());
        Container east = chest(autoFurnace.east());
        SelfTest.check("auto import: the furnace pulled the raw iron out of the chest above, with no pipe",
                above.countItem(Items.RAW_IRON) == 0);
        SelfTest.check("auto export: smelted iron went into the chest to the east, and only that",
                east.countItem(Items.IRON_INGOT) > 0 && east.countItem(Items.RAW_IRON) == 0);
        SelfTest.check("auto import/export: no iron was lost along the way",
                east.countItem(Items.IRON_INGOT) + input(autoFurnace).getCount() + output(autoFurnace).getCount() == 8);
    }

    // --- Speed -------------------------------------------------------------------------------

    private static void checkJobLengths() {
        FluxMachineBlockEntity sped = machine(spedFurnace);
        SelfTest.check("upgrades: two Speed upgrades shorten a copper smelt exactly as the math says",
                sped.totalTime() == MachineUpgrades.ticksFor(SMELT, Tier.COPPER, 2) && sped.totalTime() == 50);
        SelfTest.check("upgrades: they raise the Flux burned per tick exactly as the math says",
                sped.fluxPerTick() == MachineUpgrades.fluxPerTick(Tier.COPPER, 2) && sped.fluxPerTick() > MachineMath.fluxPerTick(Tier.COPPER));
        SelfTest.check("upgrades: a machine without upgrades keeps the tier's own numbers",
                machine(plainFurnace).totalTime() == MachineMath.ticksFor(SMELT, Tier.COPPER)
                        && machine(plainFurnace).fluxPerTick() == MachineMath.fluxPerTick(Tier.COPPER));
    }

    private static void checkSpeedResult() {
        FluxMachineBlockEntity plain = machine(plainFurnace);
        SelfTest.check("upgrades: the sped-up furnace finished while the plain one is still smelting",
                output(spedFurnace).is(Items.IRON_INGOT) && output(plainFurnace).isEmpty() && plain.status() == MachineStatus.RUNNING);
        SelfTest.check("upgrades: the sped-up smelt cost exactly its ticks times the upgraded Flux per tick",
                START_FLUX - machine(spedFurnace).fluxAmount() == MachineUpgrades.fluxForJob(SMELT, Tier.COPPER, 2));
        SelfTest.check("upgrades: the plain furnace paid the tier's cost for every tick so far",
                START_FLUX - plain.fluxAmount() == plain.progress() * MachineMath.fluxPerTick(Tier.COPPER));
    }

    // --- Capacity ----------------------------------------------------------------------------

    private static void checkCapacity(BlockPos pos) {
        FluxMachineBlockEntity furnace = machine(pos);
        install(furnace, ModItems.CAPACITY_UPGRADE.get(), 1);
        ResourceHandler<FluidResource> tank = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        FluidResource aero = FluidResource.of(ModFluids.AERO_FLUX.get());
        int taken = fill(tank, aero, 10_000, false);
        SelfTest.check("upgrades: a Capacity upgrade doubles a copper tank, exactly as the math says",
                taken == MachineUpgrades.inputCapacity(Tier.COPPER, 1)
                        && taken == 2 * MachineMath.inputCapacity(Tier.COPPER) && furnace.fluxCapacity() == taken);

        furnace.upgrades().set(0, ItemResource.EMPTY, 0);
        SelfTest.check("upgrades: taking it out shrinks the tank but keeps the Flux already in it",
                furnace.fluxCapacity() == MachineMath.inputCapacity(Tier.COPPER) && furnace.fluxAmount() == taken);
        SelfTest.check("upgrades: an overfull tank takes no more until it drops below its size", fill(tank, aero, 100, true) == 0);
    }

    // --- Slots and the menu ------------------------------------------------------------------

    private static void checkMenu(BlockPos pos) {
        FluxMachineBlockEntity furnace = machine(pos);
        player.getInventory().clearContent();
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
        MachineMenu server = new MachineMenu(3, player.getInventory(), furnace);
        int first = server.firstUpgradeSlot();
        int hotbar = server.slots.size() - 9;

        SelfTest.check("upgrade slots: a copper machine has three, after its own slots and before the inventory",
                furnace.upgrades().size() == 3 && server.strip().slotCount() == 3 && first == 2
                        && server.slots.size() == 2 + 3 + 36);
        SelfTest.check("upgrade slots: they take upgrades and nothing else",
                server.getSlot(first).mayPlace(new ItemStack(ModItems.SPEED_UPGRADE.get()))
                        && server.getSlot(first).mayPlace(new ItemStack(ModItems.CAPACITY_UPGRADE.get()))
                        && !server.getSlot(first).mayPlace(new ItemStack(Items.RAW_IRON)));
        SelfTest.check("upgrade slots: pipes and hoppers never see them",
                level.getCapability(Capabilities.Item.BLOCK, pos, null).size() == 2);

        // By hand: a click with a stack puts in one upgrade.
        server.setCarried(new ItemStack(ModItems.SPEED_UPGRADE.get(), 5));
        server.clicked(first, 0, ContainerInput.PICKUP, player);
        SelfTest.check("upgrade slots: clicking with a stack installs exactly one upgrade",
                furnace.speedUpgrades() == 1 && server.getCarried().getCount() == 4);

        // By shift-click: the rest fill the free slots, and what does not fit stays put.
        player.getInventory().setItem(0, server.getCarried().copy());
        server.setCarried(ItemStack.EMPTY);
        server.clicked(hotbar, 0, ContainerInput.QUICK_MOVE, player);
        SelfTest.check("upgrade slots: shift-click fills the free slots, one upgrade each",
                furnace.speedUpgrades() == 3 && player.getInventory().getItem(0).getCount() == 2);
        player.getInventory().setItem(1, new ItemStack(ModItems.CAPACITY_UPGRADE.get()));
        server.clicked(hotbar + 1, 0, ContainerInput.QUICK_MOVE, player);
        SelfTest.check("upgrade slots: a full machine takes no fourth upgrade",
                furnace.capacityUpgrades() == 0 && player.getInventory().getItem(1).getCount() == 1);
        player.getInventory().setItem(2, new ItemStack(Items.RAW_IRON, 4));
        server.clicked(hotbar + 2, 0, ContainerInput.QUICK_MOVE, player);
        SelfTest.check("upgrade slots: shift-clicking an input still sends it to the input slot",
                furnace.stackIn(0).is(Items.RAW_IRON) && furnace.stackIn(0).getCount() == 4 && furnace.speedUpgrades() == 3);

        // The client copy learns the counts and shows the upgrades.
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        furnace.writeOpenData(buf);
        MachineMenu client = MachineMenu.fromNetwork(3, player.getInventory(), buf);
        server.setSynchronizer(new MachineSelfTest.Mirror(client));
        server.broadcastFullState();
        SelfTest.check("upgrade slots: the client sees the upgrades and the Flux they cost",
                client.speedUpgrades() == 3 && client.fluxPerTick() == MachineUpgrades.fluxPerTick(Tier.COPPER, 3)
                        && client.getSlot(first + 2).getItem().is(ModItems.SPEED_UPGRADE.get()));
        SelfTest.check("upgrade slots: the client's slots follow the same rules",
                client.getSlot(first).mayPlace(new ItemStack(ModItems.CAPACITY_UPGRADE.get()))
                        && !client.getSlot(first).mayPlace(new ItemStack(Items.DIRT)));

        server.clicked(first, 0, ContainerInput.QUICK_MOVE, player);
        SelfTest.check("upgrade slots: shift-click takes an upgrade back out",
                furnace.speedUpgrades() == 2 && player.getInventory().countItem(ModItems.SPEED_UPGRADE.get()) == 3);

        // The redstone button travels as a menu button too.
        SelfTest.check("redstone: the button sets the mode on the machine",
                server.clickMenuButton(player, RedstoneMode.HIGH.buttonId()) && furnace.redstoneMode() == RedstoneMode.HIGH);
        FluxMachineBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        server.broadcastChanges();
        SelfTest.check("redstone: the client sees the mode and that the machine is disabled",
                client.redstoneMode() == RedstoneMode.HIGH && client.redstoneDisabled() && furnace.redstoneDisabled());
        SelfTest.check("redstone: picking the same mode again changes nothing",
                !server.clickMenuButton(player, RedstoneMode.HIGH.buttonId()));
        SelfTest.check("redstone: a number past the last mode is refused",
                !server.clickMenuButton(player, RedstoneMode.BUTTON_BASE + RedstoneMode.values().length)
                        && furnace.redstoneMode() == RedstoneMode.HIGH);
        SelfTest.check("redstone: the client copy cannot change the mode",
                !client.clickMenuButton(player, RedstoneMode.ALWAYS.buttonId()) && furnace.redstoneMode() == RedstoneMode.HIGH);
        player.getInventory().clearContent();
    }

    private static void checkNetheriteSlots(BlockPos pos) {
        FluxMachineBlockEntity furnace = machine(pos);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
        MachineMenu menu = new MachineMenu(4, player.getInventory(), furnace);
        int hotbar = menu.slots.size() - 9;
        player.getInventory().setItem(0, new ItemStack(ModItems.CAPACITY_UPGRADE.get(), 6));
        menu.clicked(hotbar, 0, ContainerInput.QUICK_MOVE, player);
        SelfTest.check("upgrade slots: a netherite machine has a fourth slot and takes four upgrades",
                furnace.upgrades().size() == MachineUpgrades.slots(Tier.NETHERITE) && furnace.capacityUpgrades() == 4
                        && player.getInventory().getItem(0).getCount() == 2
                        && furnace.fluxCapacity() == MachineUpgrades.inputCapacity(Tier.NETHERITE, 4));
        player.getInventory().clearContent();
    }

    // --- Saving and breaking -----------------------------------------------------------------

    private static void checkPersistence(BlockPos pos) {
        FluxMachineBlockEntity original = machine(pos);
        install(original, ModItems.SPEED_UPGRADE.get(), 1);
        original.upgrades().set(1, ItemResource.of(ModItems.CAPACITY_UPGRADE.get()), 1);
        MachineConfigActions.setRedstoneMode(original, RedstoneMode.LOW);
        CompoundTag saved = original.saveWithFullMetadata(level.registryAccess());
        BlockState state = level.getBlockState(pos);
        FluxMachineBlockEntity restored = MachineFactory.create(pos, state);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        SelfTest.check("persistence: upgrades survive saving and loading",
                restored.speedUpgrades() == 1 && restored.capacityUpgrades() == 1
                        && restored.upgrades().stackIn(1).is(ModItems.CAPACITY_UPGRADE.get())
                        && restored.fluxCapacity() == MachineUpgrades.inputCapacity(Tier.COPPER, 1));
        SelfTest.check("persistence: the redstone mode survives saving and loading", restored.redstoneMode() == RedstoneMode.LOW);

        CompoundTag damaged = saved.copy();
        damaged.getCompoundOrEmpty("redstone").putString("mode", "sideways");
        FluxMachineBlockEntity patched = MachineFactory.create(pos, state);
        patched.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), damaged));
        SelfTest.check("persistence: an unknown redstone mode loads as always on", patched.redstoneMode() == RedstoneMode.ALWAYS);
    }

    private static void checkDrops(BlockPos pos) {
        FluxMachineBlockEntity furnace = machine(pos);
        install(furnace, ModItems.SPEED_UPGRADE.get(), 2);
        furnace.upgrades().set(2, ItemResource.of(ModItems.CAPACITY_UPGRADE.get()), 1);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        int speed = 0;
        int capacity = 0;
        int ore = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0))) {
            ItemStack stack = entity.getItem();
            speed += stack.is(ModItems.SPEED_UPGRADE.get()) ? stack.getCount() : 0;
            capacity += stack.is(ModItems.CAPACITY_UPGRADE.get()) ? stack.getCount() : 0;
            ore += stack.is(Items.RAW_IRON) ? stack.getCount() : 0;
            entity.discard();
        }
        SelfTest.check("persistence: breaking a machine drops its upgrades along with its contents",
                speed == 2 && capacity == 1 && ore == 1);
    }

    // --- Redstone ----------------------------------------------------------------------------

    /** Each mode at tick 20, before any signal changes. */
    private static void checkHeldBack() {
        FluxMachineBlockEntity always = machine(alwaysFurnace);
        SelfTest.check("redstone: always on works with a signal, lit and running",
                always.status() == MachineStatus.RUNNING && !always.redstoneDisabled() && lit(alwaysFurnace));
        SelfTest.check("redstone: on with signal waits without one, unlit, burning nothing",
                held(highFurnace));
        SelfTest.check("redstone: off with signal waits while it has one, unlit, burning nothing",
                held(lowFurnace));
        SelfTest.check("redstone: pulse waits for its first pulse, unlit, burning nothing",
                held(pulseFurnace));
    }

    /** Gives the high furnace its signal, takes the low furnace's away, and starts the pulse. */
    private static void release() {
        level.setBlock(highFurnace.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lowFurnace.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pulseFurnace.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void checkReleased() {
        SelfTest.check("redstone: on with signal starts once it gets one",
                output(highFurnace).is(Items.IRON_INGOT) && !machine(highFurnace).redstoneDisabled());
        SelfTest.check("redstone: off with signal starts once the signal goes away",
                output(lowFurnace).is(Items.IRON_INGOT) && !machine(lowFurnace).redstoneDisabled());
    }

    private static void checkPulse() {
        FluxMachineBlockEntity pulsed = machine(pulseFurnace);
        int perJob = MachineMath.fluxForJob(SMELT, Tier.NETHERITE);
        SelfTest.check("redstone: one pulse made exactly one item",
                output(pulseFurnace).is(Items.IRON_INGOT) && output(pulseFurnace).getCount() == 1
                        && input(pulseFurnace).getCount() == 2 && START_FLUX - pulsed.fluxAmount() == perJob);
        SelfTest.check("redstone: after its job the pulsed machine waits again, unlit",
                pulsed.redstoneDisabled() && pulsed.progress() == 0 && !lit(pulseFurnace));
        SelfTest.check("redstone: always on carried on through all its work",
                output(alwaysFurnace).getCount() == 3 && input(alwaysFurnace).isEmpty());
    }

    /** Whether a furnace is held back by redstone: disabled, unlit, no progress and all its Flux. */
    private static boolean held(BlockPos pos) {
        FluxMachineBlockEntity furnace = machine(pos);
        return furnace.redstoneDisabled() && furnace.status() == MachineStatus.IDLE && !lit(pos)
                && furnace.progress() == 0 && furnace.fluxAmount() == START_FLUX && output(pos).isEmpty();
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static BlockPos furnace(int x, int y, int z, Tier tier, int flux, ItemStack input) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.machine(MachineType.FURNACE, tier).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = machine(pos);
        if (flux > 0) {
            machine.setFlux(FluidResource.of(ModFluids.AERO_FLUX.get()), flux);
        }
        machine.setStackIn(0, input);
        return pos;
    }

    /** Puts {@code count} of an upgrade into the first slots, the way a player would through the screen. */
    private static void install(FluxMachineBlockEntity machine, Item upgrade, int count) {
        for (int slot = 0; slot < count; slot++) {
            machine.upgrades().set(slot, ItemResource.of(upgrade), 1);
        }
    }

    private static Container chest(BlockPos pos) {
        return (Container) level.getBlockEntity(pos);
    }

    private static FluxMachineBlockEntity machine(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static ItemStack input(BlockPos pos) {
        return machine(pos).stackIn(0);
    }

    private static ItemStack output(BlockPos pos) {
        return machine(pos).stackIn(MachineType.FURNACE.inputSlots());
    }

    private static boolean lit(BlockPos pos) {
        return level.getBlockState(pos).getValue(FluxMachineBlock.LIT);
    }

    /** Fills a fluid handler and returns how much went in. */
    private static int fill(ResourceHandler<FluidResource> handler, FluidResource resource, int amount, boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return inserted;
        }
    }
}
