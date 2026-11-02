package com.itmecasey.aeroflux.conduit.config;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModMenus;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The menu behind the pipe screen: the settings of one pipe (one lane of a bundle) at one face.
 *
 * <p>The server's copy reads straight from the {@link ConduitBundleBlockEntity}; the client's
 * copy only displays what the server sends. The numbers the screen needs travel as data slots,
 * and the upgrade stacks as ordinary slot contents. Every change comes back as a button click
 * ({@link #clickMenuButton}) or a slot click, and the server applies it through
 * {@link PipeConfigActions}, so a modified client cannot set anything the rules forbid.
 */
public class PipeConfigMenu extends AbstractContainerMenu {
    /** Button ids {@code 0..4} set the face mode to that {@link ConnectionMode} ordinal. */
    public static final int MODE_BUTTON_BASE = 0;
    /** Button ids from here set the distribution mode to that {@link DistributionMode} ordinal. */
    public static final int DISTRIBUTION_BUTTON_BASE = 16;

    // Indexes of the synced values. Data slots travel as shorts, which is plenty for these.
    private static final int DATA_MODE = 0;
    private static final int DATA_DISTRIBUTION = 1;
    private static final int DATA_SPEED_UPGRADES = 2;
    private static final int DATA_CAPACITY_UPGRADES = 3;
    private static final int DATA_COUNT = 4;

    /** How far from the bundle a player may be and keep the screen open, like a chest. */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final BlockPos pos;
    private final PipeType pipe;
    private final Face face;
    private final Tier tier;
    private final PipeConfigLayout layout;
    private final ContainerData data;
    /** The server's bundle; {@code null} on the client, which has nothing to check against. */
    private final ConduitBundleBlockEntity entity;

    /** Server side: a menu reading live from a bundle. */
    public PipeConfigMenu(int containerId, Inventory inventory, ConduitBundleBlockEntity entity, PipeType pipe, Face face) {
        this(containerId, inventory, entity, entity.getBlockPos(), pipe, face, entity.bundle().tier(pipe.lane()));
    }

    /** Client side: a menu that waits for the server to fill it in. */
    public PipeConfigMenu(int containerId, Inventory inventory, BlockPos pos, PipeType pipe, Face face, Tier tier) {
        this(containerId, inventory, null, pos, pipe, face, tier);
    }

    private PipeConfigMenu(int containerId, Inventory inventory, ConduitBundleBlockEntity entity, BlockPos pos,
                           PipeType pipe, Face face, Tier tier) {
        super(ModMenus.PIPE_CONFIG.get(), containerId);
        this.entity = entity;
        this.pos = pos;
        this.pipe = pipe;
        this.face = face;
        this.tier = tier;
        this.layout = PipeConfigLayout.of(pipe, tier);
        this.data = entity == null ? new SimpleContainerData(DATA_COUNT) : new BundleData(entity, pipe, face);
        addDataSlots(data);

        int slots = layout.upgradeSlotCount();
        Container upgrades = entity == null ? new ClientUpgrades(slots) : new UpgradeContainer(entity, pipe, slots);
        for (int i = 0; i < slots; i++) {
            addSlot(new UpgradeSlot(upgrades, i, layout.upgradeSlotX(i), layout.upgradeSlotY()));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col,
                        layout.inventoryX() + col * PipeConfigLayout.SLOT, layout.inventoryY() + row * PipeConfigLayout.SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, layout.inventoryX() + col * PipeConfigLayout.SLOT, layout.hotbarY()));
        }
    }

    /** Builds the client's copy from what {@link #open} wrote. */
    public static PipeConfigMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        PipeType pipe = buf.readEnum(PipeType.class);
        Face face = buf.readEnum(Face.class);
        Tier tier = buf.readEnum(Tier.class);
        return new PipeConfigMenu(containerId, inventory, pos, pipe, face, tier);
    }

    /** Opens the screen for a player. Does nothing for a pipe that is not in the bundle. */
    public static void open(ServerPlayer player, ConduitBundleBlockEntity entity, PipeType pipe, Face face) {
        if (!entity.bundle().set().contains(pipe)) {
            return;
        }
        Tier tier = entity.bundle().tier(pipe.lane());
        player.openMenu(
                new SimpleMenuProvider((id, inventory, p) -> new PipeConfigMenu(id, inventory, entity, pipe, face), title(pipe, tier)),
                buf -> writeOpenData(buf, entity.getBlockPos(), pipe, face, tier));
    }

    /** What the client needs to build its copy; {@link #fromNetwork} reads it back in the same order. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, PipeType pipe, Face face, Tier tier) {
        buf.writeBlockPos(pos);
        buf.writeEnum(pipe);
        buf.writeEnum(face);
        buf.writeEnum(tier);
    }

    /** "Fluid Pipe (Copper)", or just "Dense ME Pipe" for the pipes that have no tiers. */
    public static Component title(PipeType pipe, Tier tier) {
        Component name = Component.translatable("pipe.aeroflux." + pipe.id());
        return PipeTiers.isTiered(pipe)
                ? Component.translatable("screen.aeroflux.pipe_config.title", name, Component.translatable("tier.aeroflux." + tier.id()))
                : Component.translatable("screen.aeroflux.pipe_config.title_untiered", name);
    }

    // --- What the screen reads ---------------------------------------------------------------

    public PipeType pipe() {
        return pipe;
    }

    public Face face() {
        return face;
    }

    public Tier tier() {
        return tier;
    }

    public PipeConfigLayout layout() {
        return layout;
    }

    /** The mode at this face, as last synced. */
    public ConnectionMode mode() {
        return ConnectionMode.values()[Math.floorMod(data.get(DATA_MODE), ConnectionMode.values().length)];
    }

    /** The pipe's distribution mode, as last synced. */
    public DistributionMode distribution() {
        return DistributionMode.values()[Math.floorMod(data.get(DATA_DISTRIBUTION), DistributionMode.values().length)];
    }

    public int speedUpgrades() {
        return data.get(DATA_SPEED_UPGRADES);
    }

    public int capacityUpgrades() {
        return data.get(DATA_CAPACITY_UPGRADES);
    }

    /** The speed of the pipe with the upgrades it has now. */
    public PipeConfigActions.Rate rate() {
        return PipeConfigActions.rate(pipe, tier, speedUpgrades(), capacityUpgrades());
    }

    /** The face's name as a translatable component. */
    public Component faceName() {
        return Component.translatable("face.aeroflux." + face.name().toLowerCase(Locale.ROOT));
    }

    /** The button id that sets a connection mode. */
    public static int modeButton(ConnectionMode mode) {
        return MODE_BUTTON_BASE + mode.ordinal();
    }

    /** The button id that sets a distribution mode. */
    public static int distributionButton(DistributionMode mode) {
        return DISTRIBUTION_BUTTON_BASE + mode.ordinal();
    }

    // --- What the server does ----------------------------------------------------------------

    /**
     * A click on one of the screen's buttons. The id says what to set; a value the pipe does
     * not support, or a menu whose pipe has gone, changes nothing.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || !stillValid(player)) {
            return false;
        }
        int mode = id - MODE_BUTTON_BASE;
        if (mode >= 0 && mode < ConnectionMode.values().length) {
            return PipeConfigActions.setMode(entity, pipe, face, ConnectionMode.values()[mode]);
        }
        int distribution = id - DISTRIBUTION_BUTTON_BASE;
        if (distribution >= 0 && distribution < DistributionMode.values().length) {
            return PipeConfigActions.setDistribution(entity, pipe, DistributionMode.values()[distribution]);
        }
        return false;
    }

    /** Shift-click: upgrades go to the first free upgrade slot, and out of the slots into the inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int upgradeSlots = layout.upgradeSlotCount();
        if (index < upgradeSlots) {
            if (!moveItemStackTo(stack, upgradeSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(stack.getItem() instanceof UpgradeItem) || !moveItemStackTo(stack, 0, upgradeSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Valid while the player is near, the bundle is still there and still holds this pipe at
     * the tier the screen was built for.
     */
    @Override
    public boolean stillValid(Player player) {
        BlockEntity found = player.level().getBlockEntity(pos);
        if (!(found instanceof ConduitBundleBlockEntity bundle) || (entity != null && bundle != entity)) {
            return false;
        }
        return PipeConfigActions.laneMatches(bundle, pipe, tier)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }

    // --- Pieces ------------------------------------------------------------------------------

    /** One upgrade slot: takes upgrade items, one at a time, into the first free place. */
    private final class UpgradeSlot extends Slot {
        UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Judged from the synced counts, so the client and the server agree.
            return stack.getItem() instanceof UpgradeItem
                    && getContainerSlot() <= speedUpgrades() + capacityUpgrades();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** What the client's upgrade slots hold; the server sends the real contents. */
    private static final class ClientUpgrades extends SimpleContainer {
        ClientUpgrades(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The synced numbers, read from the bundle each time the server checks for changes. */
    private record BundleData(ConduitBundleBlockEntity entity, PipeType pipe, Face face) implements ContainerData {
        @Override
        public int get(int index) {
            BundleState bundle = entity.bundle();
            int lane = pipe.lane();
            return switch (index) {
                case DATA_MODE -> bundle.mode(lane, face).ordinal();
                case DATA_DISTRIBUTION -> bundle.distribution(lane).ordinal();
                case DATA_SPEED_UPGRADES -> bundle.speedUpgrades(lane);
                case DATA_CAPACITY_UPGRADES -> bundle.capacityUpgrades(lane);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read only: every change goes through PipeConfigActions.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
