package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.conduit.config.PipeConfigLayout;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import com.itmecasey.aeroflux.conduit.transfer.LaneNetwork;
import com.itmecasey.aeroflux.conduit.transfer.TransferNetworks;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeGeometry;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * The pipe screen part of the self-test. The menu is opened and clicked on the server with a
 * fake player, the way the game does it, and a second menu plays the client by receiving only
 * what the server would send. Nothing here draws anything, so the screen's looks are not
 * covered; its layout numbers are. Scenarios sit in the forced chunk, above the other tests.
 */
final class ScreenSelfTest {
    private static final int X = 12;
    private static final int Z = 12;

    private static ServerLevel level;
    private static FakePlayer player;
    private static boolean finished;
    private static boolean started;
    private static int nextY = 262;

    private ScreenSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        finished = false;
        started = false;
    }

    /** Runs all checks once the chunk is ticking. */
    static void tick() {
        if (level == null || finished || started || !level.shouldTickBlocksAt(new BlockPos(X, 260, Z))) {
            return;
        }
        started = true;
        player = FakePlayerFactory.getMinecraft(level);
        checkTargeting();
        checkOpeningByClick();
        checkHeldItems();
        checkModes();
        checkDistribution();
        checkUpgradeSlots();
        checkShiftClick();
        checkNetheriteAndUntiered();
        checkValidity();
        checkSyncToClient();
        checkRates();
        checkLayouts();
        finished = true;
    }

    // --- Checks ------------------------------------------------------------------------------

    private static void checkTargeting() {
        BundleState state = new BundleState();
        state.addPipe(PipeType.FLUID);
        state.addPipe(PipeType.ITEM);
        state.setConnection(PipeType.FLUID.lane(), Face.SOUTH, true, true);
        state.setConnection(PipeType.ITEM.lane(), Face.SOUTH, true, true);
        List<PipeGeometry.Slot> slots = PipeGeometry.layout(2);

        Box fluidArm = PipeGeometry.arm(slots.get(0), Face.SOUTH, true);
        Box itemArm = PipeGeometry.arm(slots.get(1), Face.SOUTH, true);
        SelfTest.check("screen target: a click on the fluid arm picks fluid at that face",
                new PipeConfigActions.Target(PipeType.FLUID, Face.SOUTH).equals(at(state, fluidArm)));
        SelfTest.check("screen target: a click on the item arm picks item at that face",
                new PipeConfigActions.Target(PipeType.ITEM, Face.SOUTH).equals(at(state, itemArm)));
        Box connector = PipeGeometry.connector(2, Face.SOUTH);
        PipeConfigActions.Target onConnector = at(state, connector);
        SelfTest.check("screen target: a click on the machine connector finds the face",
                onConnector != null && onConnector.face() == Face.SOUTH);
        SelfTest.check("screen target: a click on a pipe core is not a connector click",
                at(state, PipeGeometry.core(slots.get(0))) == null);
        SelfTest.check("screen target: a click on empty bundle space is not a connector click",
                PipeConfigActions.resolveTarget(state, 1, 1, 1) == null);
        SelfTest.check("screen target: an empty bundle has no target",
                PipeConfigActions.resolveTarget(new BundleState(), 8, 8, 8) == null);
    }

    private static PipeConfigActions.Target at(BundleState state, Box box) {
        return PipeConfigActions.resolveTarget(state, (box.minX() + box.maxX()) / 2, (box.minY() + box.maxY()) / 2,
                (box.minZ() + box.maxZ()) / 2);
    }

    private static void checkOpeningByClick() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = placeWithTank(pos, PipeType.FLUID, Tier.IRON);
        prepare(pos);
        Box arm = PipeGeometry.arm(PipeGeometry.layout(1).get(0), Face.SOUTH, true);
        BlockHitResult onArm = hit(pos, (arm.minX() + arm.maxX()) / 2, arm.maxY(), (arm.minZ() + arm.maxZ()) / 2);

        InteractionResult result = level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, onArm);
        // The fake player has no real connection, so what it was sent is not looked at here; the
        // block's answer is checked, and the menu is built the way the server and client build it.
        SelfTest.check("screen open: an empty hand on a connector is handled by the block", result.consumesAction());
        PipeConfigMenu server = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.FLUID, Face.SOUTH);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PipeConfigMenu.writeOpenData(buf, pos, PipeType.FLUID, Face.SOUTH, entity.bundle().tier(PipeType.FLUID.lane()));
        PipeConfigMenu client = PipeConfigMenu.fromNetwork(2, player.getInventory(), buf);
        SelfTest.check("screen open: the server menu is for that lane, face and tier",
                server.pipe() == PipeType.FLUID && server.face() == Face.SOUTH && server.tier() == Tier.IRON);
        SelfTest.check("screen open: the client menu built from the open data matches",
                client.pipe() == server.pipe() && client.face() == server.face() && client.tier() == server.tier()
                        && client.slots.size() == server.slots.size());
        player.closeContainer();

        BlockHitResult onCore = hit(pos, 8, 8, 8);
        SelfTest.check("screen open: a click on the core does not open anything",
                level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, onCore) == InteractionResult.PASS
                        && player.containerMenu == player.inventoryMenu);
        SelfTest.check("screen open: the entity is the one in the world", level.getBlockEntity(pos) == entity);
    }

    private static void checkHeldItems() {
        BlockPos pos = next();
        placeWithTank(pos, PipeType.FLUID, Tier.COPPER);
        prepare(pos);
        Box arm = PipeGeometry.arm(PipeGeometry.layout(1).get(0), Face.SOUTH, true);
        BlockHitResult onArm = hit(pos, 8, arm.maxY(), 12);

        List<ItemStack> keepers = List.of(new ItemStack(ModItems.FLUX_WRENCH.get()), new ItemStack(ModItems.CONDUIT_TOOL.get()),
                new ItemStack(ModItems.pipe(PipeType.ITEM, Tier.GOLD).get()), new ItemStack(ModItems.SPEED_UPGRADE.get()));
        boolean allPass = true;
        boolean offHandPass = true;
        for (ItemStack keeper : keepers) {
            player.setItemInHand(InteractionHand.MAIN_HAND, keeper.copy());
            allPass &= level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, onArm) == InteractionResult.PASS;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, keeper.copy());
            offHandPass &= level.getBlockState(pos).use(level, player, InteractionHand.OFF_HAND, onArm) == InteractionResult.PASS;
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        SelfTest.check("screen open: wrench, tool, pipe and upgrade in hand keep their own click", allPass);
        SelfTest.check("screen open: the same items in the off hand also keep their click", offHandPass);
        SelfTest.check("screen open: nothing was opened while they were held", player.containerMenu == player.inventoryMenu);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        SelfTest.check("screen open: any other item is handled by the block too",
                level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, onArm).consumesAction());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void checkModes() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = placeWithTank(pos, PipeType.FLUID, Tier.COPPER);
        prepare(pos);
        int lane = PipeType.FLUID.lane();
        PipeConfigMenu menu = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.FLUID, Face.SOUTH);
        LaneNetwork before = TransferNetworks.networkFor(level, pos, PipeType.FLUID);
        SelfTest.check("screen mode: the menu starts at the pipe's mode", menu.mode() == ConnectionMode.OPEN);

        SelfTest.check("screen mode: a mode button sets that mode",
                menu.clickMenuButton(player, PipeConfigMenu.modeButton(ConnectionMode.EXPORT))
                        && entity.bundle().mode(lane, Face.SOUTH) == ConnectionMode.EXPORT);
        SelfTest.check("screen mode: the menu reports the new mode", menu.mode() == ConnectionMode.EXPORT);
        SelfTest.check("screen mode: other faces are untouched", entity.bundle().mode(lane, Face.NORTH) == ConnectionMode.OPEN);
        SelfTest.check("screen mode: the change calls changed() (networks are marked stale)",
                before != TransferNetworks.networkFor(level, pos, PipeType.FLUID));
        SelfTest.check("screen mode: an unknown button id does nothing", !menu.clickMenuButton(player, 99));

        PipeConfigActions.cycleMode(entity, PipeType.FLUID, Face.SOUTH);
        SelfTest.check("screen mode: cycling goes on to the next mode", entity.bundle().mode(lane, Face.SOUTH) == ConnectionMode.OFF);
        PipeConfigActions.cycleMode(entity, PipeType.FLUID, Face.SOUTH);
        PipeConfigActions.cycleMode(entity, PipeType.FLUID, Face.SOUTH);
        SelfTest.check("screen mode: cycling wraps around after five modes", entity.bundle().mode(lane, Face.SOUTH) == ConnectionMode.OPEN
                && PipeConfigActions.supportedModes(PipeType.FLUID).size() == 5);

        menu.clickMenuButton(player, PipeConfigMenu.modeButton(ConnectionMode.BLOCKED));
        SelfTest.check("screen mode: blocking a face drops its connection", !entity.bundle().isConnected(lane, Face.SOUTH));

        BlockPos mePos = next();
        ConduitBundleBlockEntity me = place(mePos, PipeType.ME, Tier.COPPER);
        SelfTest.check("screen mode: ME pipes offer open, off and blocked only",
                PipeConfigActions.supportedModes(PipeType.ME).equals(List.of(ConnectionMode.OPEN, ConnectionMode.OFF, ConnectionMode.BLOCKED))
                        && PipeConfigActions.supportedModes(PipeType.DENSE_ME).size() == 3);
        SelfTest.check("screen mode: an ME pipe refuses import", !PipeConfigActions.setMode(me, PipeType.ME, Face.NORTH, ConnectionMode.IMPORT)
                && me.bundle().mode(PipeType.ME.lane(), Face.NORTH) == ConnectionMode.OPEN);
        me.bundle().setMode(PipeType.ME.lane(), Face.NORTH, ConnectionMode.IMPORT);
        PipeConfigActions.cycleMode(me, PipeType.ME, Face.NORTH);
        SelfTest.check("screen mode: cycling an ME pipe with an unsupported mode goes to the first supported one",
                me.bundle().mode(PipeType.ME.lane(), Face.NORTH) == ConnectionMode.OPEN);
        SelfTest.check("screen mode: a pipe not in the bundle is refused",
                !PipeConfigActions.setMode(entity, PipeType.GAS, Face.NORTH, ConnectionMode.OFF));
    }

    private static void checkDistribution() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = place(pos, PipeType.ITEM, Tier.GOLD);
        entity.addPipe(PipeType.REDSTONE, Tier.GOLD);
        entity.addPipe(PipeType.ME, Tier.COPPER);
        prepare(pos);
        PipeConfigMenu menu = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.ITEM, Face.EAST);
        SelfTest.check("screen distribution: the menu starts at round robin", menu.distribution() == DistributionMode.ROUND_ROBIN);
        SelfTest.check("screen distribution: a button sets the mode",
                menu.clickMenuButton(player, PipeConfigMenu.distributionButton(DistributionMode.RANDOM))
                        && entity.bundle().distribution(PipeType.ITEM.lane()) == DistributionMode.RANDOM
                        && menu.distribution() == DistributionMode.RANDOM);
        PipeConfigActions.cycleDistribution(entity, PipeType.ITEM);
        SelfTest.check("screen distribution: cycling wraps from random to round robin",
                entity.bundle().distribution(PipeType.ITEM.lane()) == DistributionMode.ROUND_ROBIN);

        boolean hidden = true;
        boolean refused = true;
        for (PipeType type : PipeType.values()) {
            boolean uses = type.usesDistribution();
            hidden &= PipeConfigLayout.of(type, Tier.COPPER).hasDistribution() == uses
                    && PipeConfigLayout.of(type, Tier.COPPER).distributionButtons().isEmpty() != uses;
            if (!uses && entity.bundle().set().contains(type)) {
                PipeConfigMenu other = new PipeConfigMenu(2, player.getInventory(), entity, type, Face.EAST);
                refused &= !other.clickMenuButton(player, PipeConfigMenu.distributionButton(DistributionMode.RANDOM))
                        && entity.bundle().distribution(type.lane()) == DistributionMode.ROUND_ROBIN;
            }
        }
        SelfTest.check("screen distribution: the selector is hidden for ME, Dense ME and redstone only", hidden);
        SelfTest.check("screen distribution: redstone and ME menus refuse a distribution click", refused);
    }

    private static void checkUpgradeSlots() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.COPPER);
        prepare(pos);
        int lane = PipeType.FLUID.lane();
        PipeConfigMenu menu = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.FLUID, Face.NORTH);
        SelfTest.check("screen upgrades: a copper pipe has three upgrade slots plus the inventory", menu.slots.size() == 3 + 36);
        LaneNetwork before = TransferNetworks.networkFor(level, pos, PipeType.FLUID);

        menu.setCarried(new ItemStack(ModItems.SPEED_UPGRADE.get(), 5));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        SelfTest.check("screen upgrades: clicking an empty slot installs one upgrade",
                entity.bundle().speedUpgrades(lane) == 1 && menu.getCarried().getCount() == 4);
        SelfTest.check("screen upgrades: installing calls changed()", before != TransferNetworks.networkFor(level, pos, PipeType.FLUID));
        SelfTest.check("screen upgrades: the slot shows the upgrade", menu.getSlot(0).getItem().is(ModItems.SPEED_UPGRADE.get()));
        SelfTest.check("screen upgrades: only the first free slot takes a new upgrade",
                menu.getSlot(1).mayPlace(new ItemStack(ModItems.SPEED_UPGRADE.get())) && !menu.getSlot(2).mayPlace(new ItemStack(ModItems.SPEED_UPGRADE.get())));
        SelfTest.check("screen upgrades: other items are not accepted", !menu.getSlot(1).mayPlace(new ItemStack(Items.STONE)));

        menu.clicked(1, 0, ClickType.PICKUP, player);
        menu.clicked(2, 0, ClickType.PICKUP, player);
        SelfTest.check("screen upgrades: three upgrades fill a copper pipe", entity.bundle().speedUpgrades(lane) == 3
                && entity.bundle().upgradeCount(lane) == PipeUpgrades.slots(Tier.COPPER));
        SelfTest.check("screen upgrades: a full pipe takes no more", !PipeConfigActions.addUpgrade(entity, PipeType.FLUID, PipeUpgrades.Kind.CAPACITY));

        // Swap a speed upgrade for a capacity upgrade by clicking a filled slot with one.
        menu.setCarried(new ItemStack(ModItems.CAPACITY_UPGRADE.get()));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        SelfTest.check("screen upgrades: swapping replaces one kind with the other",
                entity.bundle().speedUpgrades(lane) == 2 && entity.bundle().capacityUpgrades(lane) == 1
                        && menu.getCarried().is(ModItems.SPEED_UPGRADE.get()));

        // Take them out again.
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        SelfTest.check("screen upgrades: clicking a filled slot takes the upgrade out",
                entity.bundle().upgradeCount(lane) == 2 && !menu.getCarried().isEmpty());
        SelfTest.check("screen upgrades: slots stay filled from the start", menu.getSlot(2).getItem().isEmpty()
                && !menu.getSlot(1).getItem().isEmpty());

        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(2, 0, ClickType.PICKUP, player);
        SelfTest.check("screen upgrades: a stone click on a slot installs nothing", entity.bundle().upgradeCount(lane) == 2);
        menu.setCarried(ItemStack.EMPTY);

        CompoundTag saved = entity.saveWithFullMetadata();
        ConduitBundleBlockEntity restored = new ConduitBundleBlockEntity(pos, level.getBlockState(pos));
        restored.load(saved);
        SelfTest.check("screen upgrades: the upgrades survive saving and loading", restored.bundle().equals(entity.bundle()));
    }

    private static void checkShiftClick() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = place(pos, PipeType.ENERGY, Tier.COPPER);
        prepare(pos);
        int lane = PipeType.ENERGY.lane();
        PipeConfigMenu menu = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.ENERGY, Face.UP);
        int hotbar = menu.slots.size() - 9;
        player.getInventory().setItem(0, new ItemStack(ModItems.CAPACITY_UPGRADE.get(), 5));
        menu.clicked(hotbar, 0, ClickType.QUICK_MOVE, player);
        SelfTest.check("screen shift-click: upgrades from the inventory fill the free slots",
                entity.bundle().capacityUpgrades(lane) == 3);
        SelfTest.check("screen shift-click: the rest stays in the inventory", player.getInventory().getItem(0).getCount() == 2);

        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        SelfTest.check("screen shift-click: upgrades from a slot go to the inventory", entity.bundle().upgradeCount(lane) == 0
                && player.getInventory().countItem(ModItems.CAPACITY_UPGRADE.get()) == 5);

        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
        menu.clicked(hotbar, 0, ClickType.QUICK_MOVE, player);
        SelfTest.check("screen shift-click: other items stay put", entity.bundle().upgradeCount(lane) == 0
                && player.getInventory().getItem(0).getCount() == 3);
        player.getInventory().clearContent();
    }

    private static void checkNetheriteAndUntiered() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.NETHERITE);
        prepare(pos);
        PipeConfigMenu menu = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.FLUID, Face.UP);
        SelfTest.check("screen upgrades: a netherite pipe has four slots", menu.slots.size() == 4 + 36);
        player.getInventory().setItem(0, new ItemStack(ModItems.SPEED_UPGRADE.get(), 6));
        menu.clicked(menu.slots.size() - 9, 0, ClickType.QUICK_MOVE, player);
        SelfTest.check("screen upgrades: four fit and the fifth does not", entity.bundle().speedUpgrades(PipeType.FLUID.lane()) == 4
                && player.getInventory().getItem(0).getCount() == 2);
        player.getInventory().clearContent();

        ConduitBundleBlockEntity me = place(next(), PipeType.ME, Tier.COPPER);
        PipeConfigMenu meMenu = new PipeConfigMenu(2, player.getInventory(), me, PipeType.ME, Face.UP);
        SelfTest.check("screen upgrades: an ME pipe has no upgrade slots", meMenu.slots.size() == 36
                && !PipeConfigActions.addUpgrade(me, PipeType.ME, PipeUpgrades.Kind.SPEED));
        SelfTest.check("screen upgrades: the ME title has no tier",
                "screen.aeroflux.pipe_config.title_untiered".equals(contentKey(PipeConfigMenu.title(PipeType.ME, Tier.COPPER)))
                        && "screen.aeroflux.pipe_config.title".equals(contentKey(PipeConfigMenu.title(PipeType.FLUID, Tier.GOLD))));
    }

    private static String contentKey(net.minecraft.network.chat.Component component) {
        return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                ? translatable.getKey() : "";
    }

    private static void checkValidity() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.IRON);
        entity.addPipe(PipeType.ITEM, Tier.IRON);
        prepare(pos);
        PipeConfigMenu menu = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.FLUID, Face.UP);
        SelfTest.check("screen valid: a fresh menu is valid next to the bundle", menu.stillValid(player));

        player.setPos(pos.getX() + 30.5, pos.getY(), pos.getZ() + 0.5);
        SelfTest.check("screen valid: too far away is invalid", !menu.stillValid(player));
        prepare(pos);
        SelfTest.check("screen valid: back in reach is valid again", menu.stillValid(player));

        entity.bundle().setTier(PipeType.FLUID.lane(), Tier.GOLD);
        SelfTest.check("screen valid: a changed tier is invalid", !menu.stillValid(player));
        entity.bundle().setTier(PipeType.FLUID.lane(), Tier.IRON);

        entity.bundle().removeLane(PipeType.FLUID.lane());
        entity.changed();
        SelfTest.check("screen valid: a removed lane is invalid", !menu.stillValid(player));
        SelfTest.check("screen valid: a click on an invalid menu changes nothing",
                !menu.clickMenuButton(player, PipeConfigMenu.modeButton(ConnectionMode.OFF)));

        PipeConfigMenu itemMenu = new PipeConfigMenu(2, player.getInventory(), entity, PipeType.ITEM, Face.UP);
        SelfTest.check("screen valid: another lane in the same bundle stays valid", itemMenu.stillValid(player));
        level.removeBlock(pos, false);
        SelfTest.check("screen valid: a removed bundle is invalid", !itemMenu.stillValid(player));

        BlockPos second = next();
        place(second, PipeType.ITEM, Tier.IRON);
        prepare(second);
        SelfTest.check("screen valid: a menu for a bundle that was replaced is invalid", !itemMenu.stillValid(player));
    }

    /** A second menu stands in for the client and is fed only through the synchronizer the server would use. */
    private static void checkSyncToClient() {
        BlockPos pos = next();
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.COPPER);
        prepare(pos);
        PipeConfigMenu server = new PipeConfigMenu(1, player.getInventory(), entity, PipeType.FLUID, Face.WEST);
        PipeConfigMenu client = new PipeConfigMenu(1, player.getInventory(), pos, PipeType.FLUID, Face.WEST, Tier.COPPER);
        server.setSynchronizer(new Mirror(client));
        server.broadcastFullState();

        server.clickMenuButton(player, PipeConfigMenu.modeButton(ConnectionMode.IMPORT));
        server.clickMenuButton(player, PipeConfigMenu.distributionButton(DistributionMode.LAST_TO_FIRST));
        server.setCarried(new ItemStack(ModItems.CAPACITY_UPGRADE.get(), 2));
        server.clicked(0, 0, ClickType.PICKUP, player);
        server.clicked(1, 0, ClickType.PICKUP, player);
        server.broadcastChanges();

        SelfTest.check("screen sync: the client copy shows the mode", client.mode() == ConnectionMode.IMPORT);
        SelfTest.check("screen sync: the client copy shows the distribution", client.distribution() == DistributionMode.LAST_TO_FIRST);
        SelfTest.check("screen sync: the client copy shows the upgrades",
                client.capacityUpgrades() == 2 && client.speedUpgrades() == 0
                        && client.getSlot(0).getItem().is(ModItems.CAPACITY_UPGRADE.get())
                        && client.getSlot(1).getItem().is(ModItems.CAPACITY_UPGRADE.get())
                        && client.getSlot(2).getItem().isEmpty());
        SelfTest.check("screen sync: the client's speed text matches the server's", client.rate().equals(server.rate()));
        SelfTest.check("screen sync: the client judges free slots like the server",
                client.getSlot(2).mayPlace(new ItemStack(ModItems.SPEED_UPGRADE.get()))
                        && !client.getSlot(2).mayPlace(new ItemStack(Items.STONE)));

        // Something other than the screen (the wrench, say) changes the bundle: the open screen follows.
        entity.bundle().setMode(PipeType.FLUID.lane(), Face.WEST, ConnectionMode.OFF);
        entity.changed();
        server.broadcastChanges();
        SelfTest.check("screen sync: outside changes reach an open screen", client.mode() == ConnectionMode.OFF);
    }

    private static void checkRates() {
        PipeConfigActions.Rate fluid = PipeConfigActions.rate(PipeType.FLUID, Tier.COPPER, 0, 0);
        SelfTest.check("screen rate: a plain copper fluid pipe moves 100 mB per tick",
                fluid.kind() == PipeConfigActions.RateKind.FLUID && fluid.first() == 100);
        SelfTest.check("screen rate: upgrades use the shared formula",
                PipeConfigActions.rate(PipeType.FLUID, Tier.COPPER, 3, 1).first()
                        == PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 3, 1));
        PipeConfigActions.Rate item = PipeConfigActions.rate(PipeType.ITEM, Tier.IRON, 2, 2);
        SelfTest.check("screen rate: item pipes report a batch and its interval",
                item.first() == PipeUpgrades.itemsPerOperation(Tier.IRON, 2)
                        && item.second() == PipeUpgrades.ticksPerOperation(Tier.IRON, 2));
        SelfTest.check("screen rate: redstone reports its channels",
                PipeConfigActions.rate(PipeType.REDSTONE, Tier.GOLD, 0, 0).first() == 4);
        SelfTest.check("screen rate: ME and Dense ME report fixed channels",
                PipeConfigActions.rate(PipeType.ME, Tier.COPPER, 0, 0).first() == 8
                        && PipeConfigActions.rate(PipeType.DENSE_ME, Tier.COPPER, 0, 0).first() == 32);
        SelfTest.check("screen rate: the text is a translation key with numbers",
                "screen.aeroflux.pipe_config.rate_items".equals(contentKey(item.text())));
    }

    private static void checkLayouts() {
        boolean fits = true;
        boolean apart = true;
        for (PipeType type : PipeType.values()) {
            for (Tier tier : Tier.values()) {
                PipeConfigLayout layout = PipeConfigLayout.of(type, tier);
                fits &= layout.height() <= 270 && layout.width() == 176
                        && layout.hotbarY() + PipeConfigLayout.SLOT <= layout.height()
                        && layout.inventoryX() + 9 * PipeConfigLayout.SLOT <= layout.width()
                        && layout.upgradeSlotCount() == PipeConfigActions.upgradeSlots(type, tier);
                List<PipeConfigLayout.Rect> all = new ArrayList<>(layout.modeButtons());
                all.addAll(layout.distributionButtons());
                for (int i = 0; i < all.size(); i++) {
                    PipeConfigLayout.Rect a = all.get(i);
                    fits &= a.x() >= 0 && a.x() + a.width() <= layout.width() && a.y() + a.height() <= layout.height();
                    for (int j = i + 1; j < all.size(); j++) {
                        apart &= !a.overlaps(all.get(j));
                    }
                }
                if (layout.upgradeSlotCount() > 0) {
                    fits &= layout.upgradeSlotX(layout.upgradeSlotCount() - 1) + PipeConfigLayout.SLOT <= layout.width()
                            && layout.upgradeSlotY() + PipeConfigLayout.SLOT <= layout.inventoryY();
                }
            }
        }
        SelfTest.check("screen layout: every pipe and tier fits in 176 by 270 pixels", fits);
        SelfTest.check("screen layout: no two buttons overlap", apart);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static BlockPos next() {
        nextY += 2;
        return new BlockPos(X, nextY, Z);
    }

    private static ConduitBundleBlockEntity place(BlockPos pos, PipeType type, Tier tier) {
        level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        ConduitBundleBlockEntity entity = (ConduitBundleBlockEntity) level.getBlockEntity(pos);
        entity.addPipe(type, tier);
        return entity;
    }

    /** A bundle with a tank on its south side, so the pipe has a connector to click. */
    private static ConduitBundleBlockEntity placeWithTank(BlockPos pos, PipeType type, Tier tier) {
        level.setBlock(pos.south(), ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        ConduitBundleBlockEntity entity = place(pos, type, tier);
        entity.refreshConnections();
        return entity;
    }

    /** Puts the player beside the bundle with empty hands and no open menu. */
    private static void prepare(BlockPos pos) {
        player.closeContainer();
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    /** A hit at a point given in block pixels. */
    private static BlockHitResult hit(BlockPos pos, double x, double y, double z) {
        return new BlockHitResult(new Vec3(pos.getX() + x / 16.0, pos.getY() + y / 16.0, pos.getZ() + z / 16.0),
                Direction.UP, pos, false);
    }

    /** Hands everything the server would send a player to another menu. */
    private record Mirror(AbstractContainerMenu target) implements ContainerSynchronizer {
        @Override
        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
            for (int i = 0; i < items.size(); i++) {
                target.getSlot(i).set(items.get(i).copy());
            }
            for (int i = 0; i < data.length; i++) {
                target.setData(i, data[i]);
            }
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) {
            target.getSlot(slot).set(stack.copy());
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu container, ItemStack stack) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
            target.setData(id, value);
        }
    }
}
