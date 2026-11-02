package com.itmecasey.aeroflux.conduit.config;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.EndpointUpgradeItem;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.EndpointFilter;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModMenus;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The menu behind the pipe screen: the settings of one pipe (one lane of a bundle) at one face.
 *
 * <p>The server's copy reads straight from the {@link ConduitBundleBlockEntity}; the client's
 * copy only displays what the server sends. The numbers the screen needs travel as data slots,
 * and the upgrade stacks as ordinary slot contents. Every change comes back as a button click
 * ({@link #clickMenuButton}) or a slot click, and the server applies it through
 * {@link PipeConfigActions}, so a modified client cannot set anything the rules forbid.
 *
 * <p>The Filter and Priority upgrades of this face, and the filter's entries, are not slots: the
 * slots of a menu cannot move or appear once it is built, and the screen shows these only while
 * the upgrades are installed. Their state travels as data slots and a click on them is a button
 * press, which the server answers using the stack on the player's cursor.
 */
public class PipeConfigMenu extends AbstractContainerMenu {
    /** Button ids {@code 0..4} set the face mode to that {@link ConnectionMode} ordinal. */
    public static final int MODE_BUTTON_BASE = 0;
    /** Button ids from here set the distribution mode to that {@link DistributionMode} ordinal. */
    public static final int DISTRIBUTION_BUTTON_BASE = 16;
    /** Switches the filter between whitelist and blacklist. */
    public static final int FILTER_MODE_BUTTON = 32;
    /** Lowers the priority by one. */
    public static final int PRIORITY_DOWN_BUTTON = 33;
    /** Raises the priority by one. */
    public static final int PRIORITY_UP_BUTTON = 34;
    /** Puts the Filter upgrade on the cursor into this face, or takes the installed one out. */
    public static final int FILTER_UPGRADE_BUTTON = 35;
    /** Puts the Priority upgrade on the cursor into this face, or takes the installed one out. */
    public static final int PRIORITY_UPGRADE_BUTTON = 36;
    /** Button ids from here set filter slot {@code id - base} from the cursor stack, or clear it. */
    public static final int FILTER_SLOT_BUTTON_BASE = 40;

    // Indexes of the synced values. Data slots travel as shorts, which is plenty for these.
    private static final int DATA_MODE = 0;
    private static final int DATA_DISTRIBUTION = 1;
    private static final int DATA_SPEED_UPGRADES = 2;
    private static final int DATA_CAPACITY_UPGRADES = 3;
    /** Bit flags: filter installed, priority installed, filter is a whitelist. */
    private static final int DATA_ENDPOINT = 4;
    private static final int DATA_PRIORITY = 5;
    /**
     * The filter entries as registry numbers plus one (0 is empty). Data slots only carry 16 bits,
     * so each entry takes two: the low half, then the high half.
     */
    private static final int DATA_FILTER = 6;
    private static final int DATA_COUNT = DATA_FILTER + 2 * EndpointFilter.SLOTS;

    private static final int FLAG_FILTER = 1;
    private static final int FLAG_PRIORITY = 2;
    private static final int FLAG_WHITELIST = 4;

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
    public static PipeConfigMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
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
    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, PipeType pipe, Face face, Tier tier) {
        buf.writeBlockPos(pos);
        buf.writeEnum(pipe);
        buf.writeEnum(face);
        buf.writeEnum(tier);
    }

    /**
     * The pipe's own name, such as "Copper Fluid Pipe" or "Dense ME Pipe", the same as the item
     * the player placed. The name already says the tier, so nothing is added to it.
     */
    public static Component title(PipeType pipe, Tier tier) {
        return new ItemStack(ModItems.pipe(pipe, tier).get()).getHoverName();
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

    /** Whether this pipe can take a Filter upgrade at all, so the screen shows its place. */
    public boolean supportsFilter() {
        return layout.hasFilterSlot();
    }

    /** Whether this pipe can take a Priority upgrade at all, so the screen shows its place. */
    public boolean supportsPriority() {
        return layout.hasPrioritySlot();
    }

    /** Whether a Filter upgrade is installed at this face, as last synced. */
    public boolean hasFilter() {
        return (data.get(DATA_ENDPOINT) & FLAG_FILTER) != 0;
    }

    /** Whether a Priority upgrade is installed at this face, as last synced. */
    public boolean hasPriority() {
        return (data.get(DATA_ENDPOINT) & FLAG_PRIORITY) != 0;
    }

    /** Whether the filter lists what may pass (true) or what may not. */
    public boolean isWhitelist() {
        return (data.get(DATA_ENDPOINT) & FLAG_WHITELIST) != 0;
    }

    /** The face's priority, as last synced. */
    public int priority() {
        return data.get(DATA_PRIORITY);
    }

    /**
     * What to draw in a filter slot: the item itself for an item pipe, the fluid's bucket for a
     * fluid pipe (or a plain bucket named after the fluid, for fluids that have no bucket).
     *
     * @return the stack, or {@link ItemStack#EMPTY} for an empty slot
     */
    public ItemStack filterStack(int slot) {
        int low = data.get(DATA_FILTER + 2 * slot) & 0xFFFF;
        int high = data.get(DATA_FILTER + 2 * slot + 1) & 0xFFFF;
        int code = high << 16 | low;
        if (code == 0) {
            return ItemStack.EMPTY;
        }
        if (pipe == PipeType.FLUID) {
            Fluid fluid = BuiltInRegistries.FLUID.byId(code - 1);
            if (fluid == null || fluid == Fluids.EMPTY) {
                return ItemStack.EMPTY;
            }
            Item bucket = fluid.getBucket();
            if (bucket != Items.AIR) {
                return new ItemStack(bucket);
            }
            ItemStack stand = new ItemStack(Items.BUCKET);
            stand.set(DataComponents.ITEM_NAME, fluid.getFluidType().getDescription());
            return stand;
        }
        Item item = BuiltInRegistries.ITEM.byId(code - 1);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** The button id that sets one filter slot. */
    public static int filterSlotButton(int slot) {
        return FILTER_SLOT_BUTTON_BASE + slot;
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
        int filterSlot = id - FILTER_SLOT_BUTTON_BASE;
        if (filterSlot >= 0 && filterSlot < EndpointFilter.SLOTS) {
            // A ghost slot: the cursor stack is only looked at, never used up.
            return PipeConfigActions.setFilterEntry(entity, pipe, face, filterSlot, getCarried());
        }
        return switch (id) {
            case FILTER_MODE_BUTTON -> PipeConfigActions.toggleFilterMode(entity, pipe, face);
            case PRIORITY_DOWN_BUTTON -> PipeConfigActions.adjustPriority(entity, pipe, face, -1);
            case PRIORITY_UP_BUTTON -> PipeConfigActions.adjustPriority(entity, pipe, face, 1);
            case FILTER_UPGRADE_BUTTON -> clickEndpointUpgrade(EndpointSettings.Upgrade.FILTER);
            case PRIORITY_UPGRADE_BUTTON -> clickEndpointUpgrade(EndpointSettings.Upgrade.PRIORITY);
            default -> false;
        };
    }

    /**
     * A click on the Filter or Priority upgrade place of this face. With the matching upgrade on
     * the cursor and none installed, one goes in. With an empty cursor, or more of the same upgrade
     * that still has room, the installed one comes out onto the cursor, its settings forgotten.
     */
    private boolean clickEndpointUpgrade(EndpointSettings.Upgrade upgrade) {
        ItemStack carried = getCarried();
        boolean installed = entity.endpoints().has(pipe.lane(), face, upgrade);
        boolean matching = carried.getItem() instanceof EndpointUpgradeItem item && item.upgrade() == upgrade;
        if (!installed) {
            if (matching && PipeConfigActions.installEndpointUpgrade(entity, pipe, face, upgrade)) {
                carried.shrink(1);
                setCarried(carried);
                return true;
            }
            return false;
        }
        if (carried.isEmpty()) {
            if (PipeConfigActions.removeEndpointUpgrade(entity, pipe, face, upgrade)) {
                setCarried(PipeConfigActions.endpointUpgradeStack(upgrade));
                return true;
            }
        } else if (matching && carried.getCount() < carried.getMaxStackSize()
                && PipeConfigActions.removeEndpointUpgrade(entity, pipe, face, upgrade)) {
            carried.grow(1);
            setCarried(carried);
            return true;
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
        if (index >= upgradeSlots && stack.getItem() instanceof EndpointUpgradeItem upgrade) {
            // Not a slot: a Filter or Priority upgrade goes straight into this face, one at a time.
            if (entity == null || !PipeConfigActions.installEndpointUpgrade(entity, pipe, face, upgrade.upgrade())) {
                return ItemStack.EMPTY;
            }
            stack.shrink(1);
            slot.setChanged();
            return original;
        }
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
                case DATA_ENDPOINT -> endpointFlags();
                case DATA_PRIORITY -> entity.endpoints().priority(lane, face);
                default -> index >= DATA_FILTER && index < DATA_COUNT ? filterHalf(index - DATA_FILTER) : 0;
            };
        }

        private int endpointFlags() {
            EndpointSettings endpoints = entity.endpoints();
            int lane = pipe.lane();
            EndpointFilter filter = endpoints.filter(lane, face);
            return (filter != null ? FLAG_FILTER : 0)
                    | (endpoints.has(lane, face, EndpointSettings.Upgrade.PRIORITY) ? FLAG_PRIORITY : 0)
                    | (filter != null && filter.isWhitelist() ? FLAG_WHITELIST : 0);
        }

        /** One half of a filter entry's registry number plus one; 0 for an empty or unknown entry. */
        private int filterHalf(int offset) {
            EndpointFilter filter = entity.endpoints().filter(pipe.lane(), face);
            String entry = filter == null ? null : filter.entry(offset / 2);
            ResourceLocation id = entry == null ? null : ResourceLocation.tryParse(entry);
            int code = 0;
            if (id != null) {
                code = pipe == PipeType.FLUID
                        ? BuiltInRegistries.FLUID.getOptional(id).map(fluid -> BuiltInRegistries.FLUID.getId(fluid) + 1).orElse(0)
                        : BuiltInRegistries.ITEM.getOptional(id).map(item -> BuiltInRegistries.ITEM.getId(item) + 1).orElse(0);
            }
            return offset % 2 == 0 ? code & 0xFFFF : code >>> 16;
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
