package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.FluxCostBar;
import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.FluxTypeBadge;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.HoverInfo;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.client.screen.widget.ProgressArrow;
import com.itmecasey.aeroflux.client.screen.widget.RedstoneButton;
import com.itmecasey.aeroflux.client.screen.widget.SideConfigPanel;
import com.itmecasey.aeroflux.client.screen.widget.UpgradeSlots;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.GlyphSidePanelLayout;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.core.machine.UpgradeStripLayout;
import com.itmecasey.aeroflux.machine.ExtraTanks;
import com.itmecasey.aeroflux.machine.FluxMachineMenu;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared look of every machine screen, as the plan lays it out: the Flux gauge on the far left,
 * input slots, the progress arrow and output slots (the arrow and the lit front show whether it
 * works, so there is no status line), a sun or moon badge on top of the gauge for the flux the
 * machine runs on, a thin cost bar over the arrow, the side-config window
 * docked to the panel's edge, and a strip on the right with the redstone button over the upgrade
 * slots. The tier shows as a colored bar along the top; the title is just the block's name, with
 * nothing repeated beside it. Like the pipe screen it draws everything with plain fills, so it
 * needs no texture, and it decides nothing: it shows what the server sent and turns clicks into
 * menu button presses.
 */
public abstract class FluxMachineScreen<M extends FluxMachineMenu> extends AbstractContainerScreen<M> {
    /** Aero Flux gold, for the progress fill. */
    private static final int PROGRESS_FILL = 0xFFF2C94C;
    /** The flux badge's cell on top of the gauge: room for a 13 pixel glyph inside a frame. */
    private static final int BADGE_HEIGHT = 15;
    /** The cost bar sits this far above the progress arrow. */
    private static final int COST_BAR_GAP = 3;
    private static final int COST_BAR_HEIGHT = 5;

    private final MachineLayout layout;
    private final int accent;
    private FluxGauge gauge;
    private FluxTypeBadge badge;
    private ProgressArrow arrow;
    private FluxCostBar costBar;
    private SideConfigPanel sidePanel;
    private UpgradeSlots upgradeSlots;
    private RedstoneButton redstoneButton;
    /** One gauge per extra tank, in the order of {@link MachineType#extraTanks()}. */
    private final List<FluxGauge> extraGauges = new ArrayList<>();

    protected FluxMachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.layout().width();
        this.imageHeight = menu.layout().height();
        this.layout = menu.layout();
        this.accent = 0xFF000000 | menu.tier().color();
    }

    @Override
    protected void init() {
        super.init();
        // The redstone button and the upgrade slots share the strip on the panel's right edge.
        UpgradeStripLayout strip = menu.strip();
        List<ItemStack> ghosts = new ArrayList<>(List.of(new ItemStack(ModItems.SPEED_UPGRADE.get()),
                new ItemStack(ModItems.CAPACITY_UPGRADE.get())));
        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
            if (kind.fits(menu.machine())) {
                ghosts.add(new ItemStack(ModItems.machineUpgrade(kind).get()));
            }
        }
        upgradeSlots = addRenderableOnly(new UpgradeSlots(leftPos, topPos, strip, menu.tier(), new UpgradeModel(), ghosts));
        MachineLayout.Rect buttonRect = strip.redstoneButton();
        redstoneButton = addRenderableWidget(new RedstoneButton(leftPos + buttonRect.x(), topPos + buttonRect.y(),
                buttonRect.width(), menu::redstoneMode, this::sendButton));
        if (menu.machine() == MachineType.PLANT_GROWER) {
            // The seed refill switch sits under the column of input slots, the seed above the soil.
            MachineLayout.Rect lastInput = MachineLayout.frameOf(layout.inputSlot(menu.machine().inputSlots() - 1));
            addRenderableWidget(new IconButton(leftPos + lastInput.x(), topPos + lastInput.y() + lastInput.height() + 2,
                    new ItemStack(Items.WHEAT_SEEDS), "gui.aeroflux.auto_insert", MachineConfigActions.AUTO_INSERT_BUTTON,
                    () -> menu.autoInsert() ? Boolean.TRUE : Boolean.FALSE));
        }
        if (menu.machine() == MachineType.MAGNET) {
            // The whitelist switch sits under the filter slot it belongs to.
            MachineLayout.Rect filterSlot = MachineLayout.frameOf(layout.inputSlot(0));
            addRenderableWidget(new IconButton(leftPos + filterSlot.x(), topPos + filterSlot.y() + filterSlot.height() + 2,
                    new ItemStack(Items.HOPPER), "gui.aeroflux.magnet_mode", MachineConfigActions.MAGNET_MODE_BUTTON,
                    () -> menu.machineMode() != 0 ? Boolean.TRUE : Boolean.FALSE));
        }
        if (menu.machine() == MachineType.SKY_CONTROLLER) {
            // Three buttons in a row under the cooldown arrow; none of them stays on.
            MachineLayout.Rect arrowSpot = layout.arrow();
            int y = topPos + arrowSpot.y() + arrowSpot.height() + 6;
            int x = leftPos + arrowSpot.x() + arrowSpot.width() / 2 - 29;
            addRenderableWidget(new IconButton(x, y, new ItemStack(Items.SUNFLOWER), "gui.aeroflux.sky.day",
                    MachineConfigActions.SKY_DAY_BUTTON, () -> null));
            addRenderableWidget(new IconButton(x + 20, y, new ItemStack(Items.PHANTOM_MEMBRANE), "gui.aeroflux.sky.night",
                    MachineConfigActions.SKY_NIGHT_BUTTON, () -> null));
            addRenderableWidget(new IconButton(x + 40, y, new ItemStack(Items.WATER_BUCKET), "gui.aeroflux.sky.weather",
                    MachineConfigActions.SKY_WEATHER_BUTTON, () -> null));
        }
        extraGauges.clear();
        for (ExtraTank spec : menu.machine().extraTanks()) {
            MachineLayout.Rect spot = layout.extraGauge(spec.output());
            extraGauges.add(addRenderableWidget(new FluxGauge(leftPos + spot.x(), topPos + spot.y(), spot.width(), spot.height())));
        }

        // The flux badge takes the top of the gauge's spot and the gauge fills the rest below it.
        MachineLayout.Rect gaugeRect = layout.gauge();
        badge = addRenderableWidget(new FluxTypeBadge(leftPos + gaugeRect.x(), topPos + gaugeRect.y(), gaugeRect.width(),
                BADGE_HEIGHT, menu.machine().acceptedFlux()));
        gauge = addRenderableWidget(new FluxGauge(leftPos + gaugeRect.x(), topPos + gaugeRect.y() + BADGE_HEIGHT + 1,
                gaugeRect.width(), gaugeRect.height() - BADGE_HEIGHT - 1));
        MachineLayout.Rect arrowRect = layout.arrow();
        arrow = addRenderableWidget(new ProgressArrow(leftPos + arrowRect.x(), topPos + arrowRect.y(),
                arrowRect.width(), arrowRect.height(), PROGRESS_FILL));
        costBar = addRenderableWidget(new FluxCostBar(leftPos + arrowRect.x(), topPos + arrowRect.y() - COST_BAR_GAP - COST_BAR_HEIGHT,
                arrowRect.width(), COST_BAR_HEIGHT));

        // The side-config window docks to the left of the panel, or to the right, beyond the
        // upgrade strip, when there is no room.
        int room = GlyphSidePanelLayout.WIDTH + GlyphSidePanelLayout.TOGGLE;
        boolean onLeft = leftPos >= room;
        int toggleX = onLeft ? leftPos - GlyphSidePanelLayout.TOGGLE : leftPos + UpgradeStripLayout.right() + 1;
        int windowX = onLeft ? toggleX - GlyphSidePanelLayout.WIDTH : toggleX + GlyphSidePanelLayout.TOGGLE;
        sidePanel = addRenderableWidget(new SideConfigPanel(new ArrayList<>(menu.resources()), menu.facing(),
                new PanelModel(), toggleX, topPos + 6, windowX, topPos + 6));
    }

    /** Sends a menu button number to the server, the way every setting on this screen travels. */
    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** The strip outside the panel still counts as the screen, so a click there never throws the carried stack away. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return super.hasClickedOutside(mouseX, mouseY, left, top, button)
                && !menu.strip().bounds().contains(mouseX - left, mouseY - top);
    }

    /**
     * A square button with an item for an icon, for the settings a single kind of machine has: the
     * Plant Grower's seed refill, the Magnet's whitelist and the Sky Controller's three buttons. A
     * switch is outlined in green while it is on; a plain button (whose state is {@code null}) is not.
     * The tooltip is the name, then the matching {@code .on}, {@code .off} or {@code .tooltip} line.
     */
    private final class IconButton extends AbstractButton {
        private static final int SIZE = 18;
        private final ItemStack icon;
        private final String key;
        private final int buttonId;
        private final Supplier<Boolean> state;

        IconButton(int x, int y, ItemStack icon, String key, int buttonId, Supplier<Boolean> state) {
            super(x, y, SIZE, SIZE, Component.translatable(key));
            this.icon = icon;
            this.key = key;
            this.buttonId = buttonId;
            this.state = state;
        }

        @Override
        public void onPress() {
            sendButton(buttonId);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Boolean on = state.get();
            Panel.raised(graphics, getX(), getY(), SIZE, SIZE);
            graphics.renderItem(icon, getX() + 1, getY() + 1);
            if (Boolean.TRUE.equals(on)) {
                graphics.renderOutline(getX(), getY(), SIZE, SIZE, Glyphs.OPEN_COLOR);
            }
            if (isHovered()) {
                graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x40FFFFFF);
                String detail = on == null ? key + ".tooltip" : on ? key + ".on" : key + ".off";
                setTooltip(Tooltip.create(getMessage().copy().append(CommonComponents.NEW_LINE).append(
                        Component.translatable(detail))));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Hooks the upgrade column up to the menu. */
    private final class UpgradeModel implements UpgradeSlots.Model {
        @Override
        public boolean filled(int slot) {
            return menu.getSlot(menu.firstUpgradeSlot() + slot).hasItem();
        }

        @Override
        public int speedUpgrades() {
            return menu.speedUpgrades();
        }

        @Override
        public int capacityUpgrades() {
            return menu.capacityUpgrades();
        }

        @Override
        public MachineType machine() {
            return menu.machine();
        }

        @Override
        public int upgrades(MachineUpgrades.Special kind) {
            return menu.upgrades(kind);
        }
    }

    /** Hooks the side-config window up to the menu. */
    private final class PanelModel implements SideConfigPanel.Model {
        @Override
        public ConnectionMode mode(SideResource resource, Face face) {
            return menu.mode(resource, face);
        }

        @Override
        public Component neighbor(Face face) {
            if (minecraft == null || minecraft.level == null) {
                return Component.translatable("screen.aeroflux.side_config.nothing");
            }
            BlockPos pos = menu.pos().relative(Direction.from3DDataValue(face.index()));
            BlockState state = minecraft.level.getBlockState(pos);
            return state.isAir() ? Component.translatable("screen.aeroflux.side_config.nothing") : state.getBlock().getName();
        }

        @Override
        public ItemStack neighborItem(Face face, SideResource resource) {
            if (minecraft == null || minecraft.level == null) {
                return ItemStack.EMPTY;
            }
            BlockPos pos = menu.pos().relative(Direction.from3DDataValue(face.index()));
            // A pipe bundle has no item of its own, so show its pipe: the one for this tab if it has one.
            if (minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
                return pipeItem(bundle.bundle(), resource);
            }
            BlockState state = minecraft.level.getBlockState(pos);
            return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        }

        private static ItemStack pipeItem(BundleState bundle, SideResource resource) {
            PipeType shown = null;
            for (PipeType type : bundle.set().types()) {
                if (shown == null || type == pipeFor(resource)) {
                    shown = type;
                }
            }
            return shown == null ? ItemStack.EMPTY : new ItemStack(ModItems.pipe(shown, bundle.tier(shown.lane())).get());
        }

        private static PipeType pipeFor(SideResource resource) {
            return switch (resource) {
                case ITEMS -> PipeType.ITEM;
                case FLUID -> PipeType.FLUID;
                case ENERGY -> PipeType.ENERGY;
                case GAS -> PipeType.GAS;
                case REDSTONE -> PipeType.REDSTONE;
            };
        }

        @Override
        public boolean autoImport() {
            return menu.autoImport();
        }

        @Override
        public boolean autoExport() {
            return menu.autoExport();
        }

        @Override
        public void send(int command) {
            sendButton(command);
        }
    }

    /** Feeds the widgets this frame's numbers, then draws; the widget tooltips are queued last so nothing covers them. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MachineStatus status = menu.status();
        gauge.update(menu.fluxAmount(), menu.fluxCapacity(), menu.fluxType(), menu.fluxPerTick(), status == MachineStatus.RUNNING);
        arrow.update(menu.progress(), menu.totalTime());
        badge.update(menu.fluxType(), menu.fluxAmount());
        costBar.update(menu.fluxAmount(), menu.fluxPerOperation(), menu.nextTickCost());
        for (int i = 0; i < extraGauges.size(); i++) {
            extraGauges.get(i).updateFluid(menu.extraAmount(i), menu.fluxCapacity(),
                    ExtraTanks.fluid(menu.machine().extraTanks().get(i)));
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        List<HoverInfo> infos = new ArrayList<>(List.of(gauge, badge, arrow, costBar, sidePanel, redstoneButton, upgradeSlots));
        infos.addAll(extraGauges);
        for (HoverInfo info : infos) {
            List<Component> lines = info.tooltip(mouseX, mouseY);
            if (!lines.isEmpty()) {
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        // The tier's color along the top says which tier this machine is.
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);
        // The strip first, so the upgrade slots' frames land on top of it.
        upgradeSlots.drawBackground(graphics);
        for (Slot slot : menu.slots) {
            Panel.sunken(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, MachineLayout.SLOT);
        }
    }

    /**
     * The title is the block's own name, such as "Iron Furnace", and nothing else: the tier
     * is in the name and in the bar along the top already. The player's inventory needs no label.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, layout.titleX(), layout.titleY(), Panel.TEXT, false);
    }

    /** The side-config widget, so the development screenshot tour can open its window. */
    public SideConfigPanel sidePanel() {
        return sidePanel;
    }
}
