package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.block.menu.CollectorMenu;
import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.core.CollectionMath;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.FluxCost;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineMenu;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * The server side of the collector and tank screens and of the machine screens' cost bar: the
 * blocks open their screens on an empty hand (and still take buckets), and every number a screen
 * draws arrives on a client copy of the menu exactly as the block entity has it, cut to 16 bit
 * data slots the way the network sends them. Builds in a layer of its own at y {@value #Y}.
 */
final class FluxScreenSelfTest {
    private static final int Y = 206;
    private static final int Z = 13;
    /** The tank rates count whole seconds, so the tank is read exactly one window after it was filled. */
    private static final int TANK_CHECK = 20;
    /** Long enough for the collectors' sky check (every 20 ticks) and for the furnaces to start. */
    private static final int REST_CHECK = 45;
    /** A vanilla smelt's cooking time, before the tier's speed. */
    private static final int SMELT_TICKS = 200;

    private static ServerLevel level;
    private static FakePlayer player;
    private static boolean finished;
    private static long started = -1;

    private static BlockPos coveredCollector;
    private static BlockPos otherCollector;
    private static BlockPos bigTank;
    private static BlockPos runningFurnace;
    private static BlockPos starvedFurnace;
    private static int bigTankStart;

    private FluxScreenSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        finished = false;
        started = -1;
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (started < 0) {
            if (!level.shouldTickBlocksAt(new BlockPos(1, Y, Z))) {
                return;
            }
            started = level.getGameTime();
            player = FakePlayerFactory.getMinecraft(level);
            build();
            checkOpening();
            return;
        }
        long elapsed = level.getGameTime() - started;
        if (elapsed == TANK_CHECK) {
            checkTankSync();
        } else if (elapsed == REST_CHECK) {
            checkCollectorSync();
            checkCostSync();
            finished = true;
        }
    }

    private static void build() {
        // A collector under a stone roof two blocks up, and one with whatever sky this spot has.
        coveredCollector = new BlockPos(1, Y, Z);
        level.setBlock(coveredCollector, ModBlocks.collector(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(coveredCollector.above(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        otherCollector = new BlockPos(3, Y, Z);
        level.setBlock(otherCollector, ModBlocks.collector(Tier.GOLD).get().defaultBlockState(), Block.UPDATE_ALL);

        // A netherite tank, so the amounts need both halves of their data slots.
        bigTank = new BlockPos(5, Y, Z);
        level.setBlock(bigTank, ModBlocks.tank(Tier.NETHERITE).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxTankBlockEntity tank = tank(bigTank);
        bigTankStart = Math.min(tank.capacity(), 70_000);
        move(tank, bigTankStart);
        move(tank, -2_000);

        // A furnace with Flux for most of a smelt but not all of it, and one with none at all.
        int smelt = FluxCost.perOperation(MachineUpgrades.fluxPerTick(Tier.COPPER, 0),
                MachineUpgrades.ticksFor(SMELT_TICKS, Tier.COPPER, 0), 0);
        runningFurnace = furnace(7, smelt - 1);
        starvedFurnace = furnace(9, 0);
    }

    private static void checkOpening() {
        BlockPos copperTank = new BlockPos(11, Y, Z);
        level.setBlock(copperTank, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        BlockHitResult onTank = topHit(copperTank);
        BlockHitResult onCollector = topHit(coveredCollector);
        player.setPos(copperTank.getX() + 0.5, Y, copperTank.getZ() - 1.5);

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        SelfTest.check("tank screen: an empty hand opens it",
                level.getBlockState(copperTank).useWithoutItem(level, player, onTank).consumesAction());
        SelfTest.check("collector screen: an empty hand opens it",
                level.getBlockState(coveredCollector).useWithoutItem(level, player, onCollector).consumesAction());

        ItemStack bucket = new ItemStack(ModItems.AERO_FLUX_BUCKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        ItemInteractionResult filled = level.getBlockState(copperTank).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND, onTank);
        SelfTest.check("tank screen: a bucket of Flux still fills the tank instead",
                filled.consumesAction() && tank(copperTank).amount() == 1_000);

        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        SelfTest.check("tank and collector screens: another item in hand keeps its own use, so blocks can be stacked",
                level.getBlockState(copperTank).useItemOn(stone, level, player, InteractionHand.MAIN_HAND, onTank) == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                        && level.getBlockState(coveredCollector).useItemOn(stone, level, player, InteractionHand.MAIN_HAND, onCollector)
                        == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void checkTankSync() {
        FluxTankBlockEntity tank = tank(bigTank);
        TankMenu server = new TankMenu(1, player.getInventory(), tank);
        RegistryFriendlyByteBuf buf = buffer();
        tank.writeOpenData(buf);
        TankMenu client = TankMenu.fromNetwork(1, player.getInventory(), buf);
        server.setSynchronizer(new MachineSelfTest.Mirror(client));
        server.broadcastFullState();

        SelfTest.check("tank screen: the client menu is for the same tank and tier",
                client.pos().equals(bigTank) && client.tier() == Tier.NETHERITE);
        SelfTest.check("tank screen: amount and capacity arrive whole, past 16 bits",
                client.amount() == tank.amount() && client.capacity() == tank.capacity()
                        && client.amount() == bigTankStart - 2_000 && client.capacity() > 0xFFFF);
        SelfTest.check("tank screen: the contents arrive", client.fluxType() == FluxType.AERO);
        SelfTest.check("tank screen: what went in and came out in the last second arrives",
                client.inLastSecond() == bigTankStart && client.outLastSecond() == 2_000
                        && client.inPerTick() == bigTankStart / 20.0);
    }

    private static void checkCollectorSync() {
        FluxCollectorBlockEntity covered = collector(coveredCollector);
        CollectorMenu client = syncedCollector(covered);
        SelfTest.check("collector screen: a roofed collector reports the sky blocked",
                !covered.skyVisible() && !client.skyVisible() && client.needsSky());
        SelfTest.check("collector screen: it names the stone two blocks up as the blocker",
                client.blocker() == Blocks.STONE && client.blockerPos().equals(coveredCollector.above(2)));
        SelfTest.check("collector screen: a roofed collector makes nothing and will not fill",
                client.ratePerTick() == 0.0 && client.ticksToFull() == -1);

        FluxCollectorBlockEntity other = collector(otherCollector);
        CollectorMenu otherClient = syncedCollector(other);
        SelfTest.check("collector screen: buffer, capacity and contents arrive as the collector has them",
                otherClient.amount() == other.amount() && otherClient.capacity() == other.capacity()
                        && otherClient.storedFlux() == other.storedFlux());
        SelfTest.check("collector screen: the sky check and blocker agree with the collector",
                otherClient.skyVisible() == other.skyVisible() && (other.blocker() == null) == (otherClient.blocker() == null));
        SelfTest.check("collector screen: the rate arrives to a thousandth of a mB",
                Math.abs(otherClient.ratePerTick() - other.lastRate()) < 0.001);

        DimensionRules rules = FluxDimensions.rulesFor(level);
        long clock = level.getDayTime();
        long overworldClock = level.getServer().overworld().getDayTime();
        FluxType expected = CollectionMath.isActive(FluxType.AERO, rules, clock, overworldClock) ? FluxType.AERO
                : CollectionMath.isActive(FluxType.MOON, rules, clock, overworldClock) ? FluxType.MOON : null;
        SelfTest.check("collector screen: what the sky offers matches the collection rules",
                otherClient.offered() == expected && other.offered() == expected);
        SelfTest.check("collector screen: the clock, weather and moon arrive as the level has them",
                otherClient.timeOfDay() == (int) Math.floorMod(clock, CollectionMath.DAY_LENGTH)
                        && otherClient.timeSource() == rules.timeSource()
                        && otherClient.raining() == level.isRaining() && otherClient.thundering() == level.isThundering()
                        && otherClient.moonPhase() == level.getMoonPhase());
    }

    private static void checkCostSync() {
        FluxMachineBlockEntity running = machine(runningFurnace);
        MachineMenu client = syncedMachine(running);
        int perOperation = running.fluxPerTick() * running.totalTime();
        SelfTest.check("cost bar: a smelt costs its ticks times the Flux per tick",
                running.status() == MachineStatus.RUNNING && running.totalTime() > 0
                        && running.fluxPerOperation() == perOperation && running.nextTickCost() == running.fluxPerTick());
        SelfTest.check("cost bar: the client gets the server's job cost and next-tick cost",
                client.fluxPerOperation() == running.fluxPerOperation() && client.nextTickCost() == running.nextTickCost()
                        && client.fluxAmount() == running.fluxAmount());
        SelfTest.check("cost bar: a tank with less than one smelt covers only part of it",
                FluxCost.coverFraction(client.fluxAmount(), client.fluxPerOperation()) < 1.0
                        && FluxCost.coversNextTick(client.fluxAmount(), client.nextTickCost()));

        FluxMachineBlockEntity starved = machine(starvedFurnace);
        MachineMenu starvedClient = syncedMachine(starved);
        SelfTest.check("cost bar: an empty machine with a job knows the job's cost and cannot pay the next tick",
                starved.status() == MachineStatus.NO_FLUX && starvedClient.fluxPerOperation() == perOperation
                        && !FluxCost.coversNextTick(starvedClient.fluxAmount(), starvedClient.nextTickCost()));
        SelfTest.check("flux badge: the client knows which flux the furnace takes and what it holds",
                starvedClient.machine().acceptedFlux().contains(FluxType.AERO) && starvedClient.fluxType() == null
                        && client.fluxType() == FluxType.AERO);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static CollectorMenu syncedCollector(FluxCollectorBlockEntity collector) {
        CollectorMenu server = new CollectorMenu(1, player.getInventory(), collector);
        RegistryFriendlyByteBuf buf = buffer();
        collector.writeOpenData(buf);
        CollectorMenu client = CollectorMenu.fromNetwork(1, player.getInventory(), buf);
        server.setSynchronizer(new MachineSelfTest.Mirror(client));
        server.broadcastFullState();
        return client;
    }

    private static MachineMenu syncedMachine(FluxMachineBlockEntity machine) {
        MachineMenu server = new MachineMenu(1, player.getInventory(), machine);
        RegistryFriendlyByteBuf buf = buffer();
        machine.writeOpenData(buf);
        MachineMenu client = MachineMenu.fromNetwork(1, player.getInventory(), buf);
        server.setSynchronizer(new MachineSelfTest.Mirror(client));
        server.broadcastFullState();
        return client;
    }

    private static BlockPos furnace(int x, int flux) {
        BlockPos pos = new BlockPos(x, Y, Z);
        level.setBlock(pos, ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = machine(pos);
        if (flux > 0) {
            machine.setFlux(new FluidStack(ModFluids.AERO_FLUX.get(), flux));
        }
        machine.setStackIn(0, new ItemStack(Items.RAW_IRON, 4));
        return pos;
    }

    /** Fills (positive) or drains (negative) a tank by an amount of Aero Flux, as a pipe would. */
    private static void move(FluxTankBlockEntity tank, int amount) {
        if (amount > 0) {
            tank.tank().fill(new FluidStack(ModFluids.AERO_FLUX.get(), amount), IFluidHandler.FluidAction.EXECUTE);
        } else {
            tank.tank().drain(-amount, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
    }

    private static BlockHitResult topHit(BlockPos pos) {
        return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false);
    }

    private static FluxTankBlockEntity tank(BlockPos pos) {
        return (FluxTankBlockEntity) level.getBlockEntity(pos);
    }

    private static FluxCollectorBlockEntity collector(BlockPos pos) {
        return (FluxCollectorBlockEntity) level.getBlockEntity(pos);
    }

    private static FluxMachineBlockEntity machine(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }
}
