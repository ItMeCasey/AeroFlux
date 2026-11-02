package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.GhostSlot;
import com.itmecasey.aeroflux.client.screen.widget.GlyphButton;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.client.screen.widget.ToggleGlyphButton;
import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.conduit.config.PipeConfigLayout;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.GlyphSidePanelLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The screen of one pipe at one face. It only shows what the server has sent and turns clicks
 * into button presses on the menu; it decides nothing itself.
 *
 * <p>There are no words on it. The header shows the pipe's glyph, a gem in the tier's color and a
 * small cube net with this face marked; the face modes and distribution modes are square glyph
 * buttons; the throughput is a glyph and numbers next to a bar that fills up with the tier; the
 * upgrade slots sit behind the Speed and Capacity glyphs. Every name and sentence is in a
 * tooltip. Everything is drawn with plain fills and the glyph textures, and where things go is
 * worked out by {@link PipeConfigLayout}.
 *
 * <p>Pipes that take Filter and Priority upgrades have a place for each of them at the end of the
 * upgrade row; these belong to this face only. While one is installed, a side panel to the right
 * shows its controls: the filter's whitelist/blacklist toggle and nine ghost slots, and the
 * priority number between a minus and a plus button.
 */
public class PipeConfigScreen extends AbstractContainerScreen<PipeConfigMenu> {
    /** The face boxes of the header's cube net that are not this face. */
    private static final int NET_OTHER = 0xFF8B8B8B;
    /** The empty cells of the tier bar. */
    private static final int TIER_EMPTY = 0xFF6B6B6B;
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);

    private final PipeConfigLayout layout;
    /** The pipe's color with full alpha. */
    private final int accent;
    /** Which side of the cube net this face is, as seen from where the player stands. */
    private FaceLabel netFace = FaceLabel.FRONT;

    /** The upgrade items, made once the registries are ready, for the upgrade places and the side panel. */
    private ItemStack filterUpgrade = ItemStack.EMPTY;
    private ItemStack priorityUpgrade = ItemStack.EMPTY;
    /** The side panel's widgets; shown, hidden and moved by {@link #updateSidePanel()}. */
    private ToggleGlyphButton filterMode;
    private final List<GhostSlot> filterSlots = new ArrayList<>();
    private GlyphButton priorityDown;
    private GlyphButton priorityUp;

    public PipeConfigScreen(PipeConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
        this.layout = menu.layout();
        this.accent = 0xFF000000 | menu.pipe().color();
    }

    @Override
    protected void init() {
        super.init();
        // The net is drawn as the player sees the bundle: the side facing them is the front.
        if (minecraft != null && minecraft.player != null) {
            Face towardPlayer = Face.byIndex(minecraft.player.getDirection().getOpposite().get3DDataValue());
            netFace = FaceLabel.of(towardPlayer, menu.face());
        }

        List<ConnectionMode> modes = PipeConfigActions.supportedModes(menu.pipe());
        for (int i = 0; i < modes.size(); i++) {
            ConnectionMode mode = modes.get(i);
            PipeConfigLayout.Rect rect = layout.modeButtons().get(i);
            String key = "connection_mode.aeroflux." + mode.name().toLowerCase(Locale.ROOT);
            int buttonId = PipeConfigMenu.modeButton(mode);
            addRenderableWidget(GlyphButton.colored(leftPos + rect.x(), topPos + rect.y(), rect.width(),
                    Glyphs.modeGlyph(mode), Glyphs.modeColor(mode), () -> menu.mode() == mode,
                    () -> pressMenuButton(buttonId), Component.translatable(key), Component.translatable(key + ".tooltip")));
        }
        if (layout.hasDistribution()) {
            for (DistributionMode mode : DistributionMode.values()) {
                PipeConfigLayout.Rect rect = layout.distributionButtons().get(mode.ordinal());
                String id = mode.name().toLowerCase(Locale.ROOT);
                String key = "distribution_mode.aeroflux." + id;
                int buttonId = PipeConfigMenu.distributionButton(mode);
                addRenderableWidget(GlyphButton.raised(leftPos + rect.x(), topPos + rect.y(), rect.width(),
                        "dist_" + id, accent, () -> menu.distribution() == mode,
                        () -> pressMenuButton(buttonId), Component.translatable(key), Component.translatable(key + ".tooltip")));
            }
        }
        addEndpointWidgets();
        updateSidePanel();
    }

    /** The places for the Filter and Priority upgrades, and the side panel's controls. */
    private void addEndpointWidgets() {
        filterUpgrade = PipeConfigActions.endpointUpgradeStack(EndpointSettings.Upgrade.FILTER);
        priorityUpgrade = PipeConfigActions.endpointUpgradeStack(EndpointSettings.Upgrade.PRIORITY);
        filterSlots.clear();
        filterMode = null;
        priorityDown = null;
        priorityUp = null;

        if (layout.hasFilterSlot()) {
            PipeConfigLayout.Rect place = layout.filterUpgradeSlot();
            addRenderableWidget(new GhostSlot(leftPos + place.x(), topPos + place.y(),
                    () -> menu.hasFilter() ? filterUpgrade : ItemStack.EMPTY, filterUpgrade,
                    () -> pressMenuButton(PipeConfigMenu.FILTER_UPGRADE_BUTTON), filterUpgrade.getHoverName()));

            PipeConfigLayout.Rect toggle = layout.filterModeButton();
            String filter = "screen.aeroflux.pipe_config.filter_";
            filterMode = addRenderableWidget(new ToggleGlyphButton(leftPos + toggle.x(), topPos + toggle.y(),
                    toggle.width(), menu::isWhitelist, "filter_whitelist", "filter_blacklist",
                    Component.translatable(filter + "whitelist"), Component.translatable(filter + "whitelist.tooltip"),
                    Component.translatable(filter + "blacklist"), Component.translatable(filter + "blacklist.tooltip"),
                    () -> pressMenuButton(PipeConfigMenu.FILTER_MODE_BUTTON)));
            for (int i = 0; i < layout.filterSlotCount(); i++) {
                int slot = i;
                PipeConfigLayout.Rect rect = layout.filterSlot(i);
                filterSlots.add(addRenderableWidget(new GhostSlot(leftPos + rect.x(), topPos + rect.y(),
                        () -> menu.filterStack(slot), ItemStack.EMPTY,
                        () -> pressMenuButton(PipeConfigMenu.filterSlotButton(slot)),
                        Component.translatable("screen.aeroflux.pipe_config.filter_slot"))));
            }
        }

        if (layout.hasPrioritySlot()) {
            PipeConfigLayout.Rect place = layout.priorityUpgradeSlot();
            addRenderableWidget(new GhostSlot(leftPos + place.x(), topPos + place.y(),
                    () -> menu.hasPriority() ? priorityUpgrade : ItemStack.EMPTY, priorityUpgrade,
                    () -> pressMenuButton(PipeConfigMenu.PRIORITY_UPGRADE_BUTTON), priorityUpgrade.getHoverName()));

            String priority = "screen.aeroflux.pipe_config.priority_";
            PipeConfigLayout.Rect down = layout.priorityDownButton(false);
            priorityDown = addRenderableWidget(GlyphButton.raised(leftPos + down.x(), topPos + down.y(), down.width(),
                    "priority_down", accent, () -> false, () -> pressMenuButton(PipeConfigMenu.PRIORITY_DOWN_BUTTON),
                    Component.translatable(priority + "down"), Component.translatable(priority + "down.tooltip")));
            PipeConfigLayout.Rect up = layout.priorityUpButton(false);
            priorityUp = addRenderableWidget(GlyphButton.raised(leftPos + up.x(), topPos + up.y(), up.width(),
                    "priority_up", accent, () -> false, () -> pressMenuButton(PipeConfigMenu.PRIORITY_UP_BUTTON),
                    Component.translatable(priority + "up"), Component.translatable(priority + "up.tooltip")));
        }
    }

    /**
     * Shows the side panel's controls for the upgrades that are installed right now and hides the
     * rest. The priority row moves up to the top when there is no filter part above it. Widgets,
     * unlike slots, may move, so this runs every frame and follows the server's word at once.
     */
    private void updateSidePanel() {
        boolean filter = menu.hasFilter();
        boolean priority = menu.hasPriority();
        if (filterMode != null) {
            filterMode.visible = filter;
            for (GhostSlot slot : filterSlots) {
                slot.visible = filter;
            }
        }
        if (priorityDown != null) {
            PipeConfigLayout.Rect down = layout.priorityDownButton(filter);
            PipeConfigLayout.Rect up = layout.priorityUpButton(filter);
            priorityDown.setPosition(leftPos + down.x(), topPos + down.y());
            priorityUp.setPosition(leftPos + up.x(), topPos + up.y());
            priorityDown.visible = priority;
            priorityUp.visible = priority;
        }
    }

    /** Sends the click to the server, which applies it and sends the new values back. */
    private void pressMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    // --- Drawing -----------------------------------------------------------------------------

    /**
     * The panel, the header, the throughput row, the upgrade glyphs and the slot frames. The game
     * draws the background before the buttons, so the buttons land on top of it.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        // The pipe's color along the top says which lane this is.
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);

        drawHeader(graphics);
        drawRate(graphics);
        if (layout.upgradeSlotCount() > 0) {
            int y = topPos + layout.upgradeRowY() + PipeConfigLayout.SLOT / 2;
            Glyphs.draw(graphics, "upgrade_speed", leftPos + layout.speedGlyphX(), y, Glyphs.DARK);
            Glyphs.draw(graphics, "upgrade_capacity", leftPos + layout.capacityGlyphX(), y, Glyphs.DARK);
        }
        for (Slot slot : menu.slots) {
            Panel.sunken(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, PipeConfigLayout.SLOT);
        }
        drawSidePanel(graphics);
    }

    /**
     * The side panel behind the filter and priority controls, when either upgrade is installed:
     * a raised panel with the pipe's color along the top like the main one, the Filter upgrade's
     * icon as the heading of the filter part, and the priority number between its buttons.
     */
    private void drawSidePanel(GuiGraphicsExtractor graphics) {
        boolean filter = menu.hasFilter();
        boolean priority = menu.hasPriority();
        PipeConfigLayout.Rect side = layout.sidePanel(filter, priority);
        if (side == null) {
            return;
        }
        int x = leftPos + side.x();
        int y = topPos + side.y();
        Panel.raised(graphics, x, y, side.width(), side.height());
        graphics.fill(x + 2, y + 2, x + side.width() - 2, y + 5, accent);

        if (filter) {
            PipeConfigLayout.Rect icon = layout.filterIcon();
            graphics.fakeItem(filterUpgrade, leftPos + icon.x(), topPos + icon.y());
        }
        if (priority) {
            PipeConfigLayout.Rect value = layout.priorityValue(filter);
            String text = PipeConfigActions.priorityText(menu.priority());
            graphics.text(font, text, leftPos + value.x() + (value.width() - font.width(text) + 1) / 2,
                    topPos + value.y() + (value.height() - font.lineHeight) / 2 + 1, Panel.TEXT, false);
        }
    }

    /** Clicks on the side panel are clicks on the screen, not outside it, so they never drop the cursor stack. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        PipeConfigLayout.Rect side = layout.sidePanel(menu.hasFilter(), menu.hasPriority());
        if (side != null && side.contains(mouseX - left, mouseY - top)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    /** The pipe's glyph in a sunken box, the tier gem, and the cube net with this face in white. */
    private void drawHeader(GuiGraphicsExtractor graphics) {
        PipeConfigLayout.Rect box = layout.pipeBox();
        int boxX = leftPos + box.x();
        int boxY = topPos + box.y();
        Panel.sunken(graphics, boxX, boxY, box.width(), box.height(), Panel.SLOT_FILL);
        Glyphs.draw(graphics, Glyphs.pipeGlyph(menu.pipe()), boxX + box.width() / 2, boxY + box.height() / 2, accent);

        if (PipeTiers.isTiered(menu.pipe())) {
            PipeConfigLayout.Rect gem = layout.tierGem();
            int x = leftPos + gem.x();
            int y = topPos + gem.y();
            int color = Glyphs.tierColor(menu.tier());
            graphics.fill(x, y, x + gem.width(), y + gem.height(), Panel.EDGE);
            graphics.fill(x + 1, y + 1, x + gem.width() - 1, y + gem.height() - 1, color);
            graphics.fill(x + 1, y + 1, x + gem.width() - 1, y + 2, Glyphs.shade(color, 1.3));
        }

        PipeConfigLayout.Rect net = layout.faceNet();
        int cell = PipeConfigLayout.NET_CELL;
        for (FaceLabel label : FaceLabel.values()) {
            int x = leftPos + net.x() + GlyphSidePanelLayout.column(label) * cell;
            int y = topPos + net.y() + GlyphSidePanelLayout.row(label) * cell;
            graphics.fill(x, y, x + cell - 1, y + cell - 1, Panel.SLOT_SHADOW);
            graphics.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, label == netFace ? Glyphs.WHITE : NET_OTHER);
        }
    }

    /**
     * The throughput as a glyph and numbers: "4 / (clock) 20" for items, a single number for the
     * other pipes. On the right, a bar with one cell per tier, filled up to this pipe's tier.
     */
    private void drawRate(GuiGraphicsExtractor graphics) {
        int y = topPos + layout.rateY();
        Glyphs.draw(graphics, Glyphs.pipeGlyph(menu.pipe()), leftPos + 14, y + 6, accent);

        PipeConfigActions.Rate rate = menu.rate();
        int x = leftPos + 22;
        String amount = NUMBERS.format(rate.first());
        graphics.text(font, amount, x, y + 2, Panel.TEXT, false);
        x += font.width(amount);
        if (rate.kind() == PipeConfigActions.RateKind.ITEMS) {
            graphics.text(font, "/", x, y + 2, Panel.TEXT, false);
            x += font.width("/");
            Glyphs.draw(graphics, "ticks", x + 7, y + 6, Glyphs.DARK);
            graphics.text(font, NUMBERS.format(rate.second()), x + 15, y + 2, Panel.TEXT, false);
        }

        if (PipeTiers.isTiered(menu.pipe())) {
            int filled = menu.tier().ordinal() + 1;
            for (int i = 0; i < PipeConfigLayout.TIER_CELLS; i++) {
                PipeConfigLayout.Rect cell = layout.tierCell(i);
                Panel.sunken(graphics, leftPos + cell.x(), topPos + cell.y(), cell.width(), cell.height(),
                        i < filled ? Glyphs.tierColor(menu.tier()) : TIER_EMPTY);
            }
        }
    }

    /** No text on the panel: the title, face and numbers' meanings are all in tooltips. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    // --- Tooltips ----------------------------------------------------------------------------

    /** Draws as usual, then queues the tooltip of whatever drawn part the pointer is over. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateSidePanel();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        List<Component> lines = tooltipAt(mouseX - leftPos, mouseY - topPos);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /** The tooltip for a point in panel pixels, or an empty list. */
    private List<Component> tooltipAt(int x, int y) {
        if (layout.pipeBox().contains(x, y)) {
            return List.of(title);
        }
        if (PipeTiers.isTiered(menu.pipe())
                && (layout.tierGem().contains(x, y) || overTierBar(x, y))) {
            return List.of(Component.translatable("tier.aeroflux." + menu.tier().id()));
        }
        if (layout.faceNet().contains(x, y)) {
            return List.of(Component.translatable("screen.aeroflux.pipe_config.side", menu.faceName()));
        }
        if (layout.rateArea().contains(x, y)) {
            return List.of(Component.translatable("screen.aeroflux.pipe_config.rate"),
                    menu.rate().text().copy().withStyle(ChatFormatting.GRAY));
        }
        if (layout.upgradeSlotCount() > 0 && layout.upgradeGlyphs().contains(x, y)) {
            return List.of(Component.translatable("screen.aeroflux.pipe_config.upgrades",
                    menu.speedUpgrades() + menu.capacityUpgrades(), layout.upgradeSlotCount()));
        }
        if (layout.hasFilterSlot() && layout.filterUpgradeSlot().contains(x, y)) {
            return upgradePlaceTooltip(filterUpgrade, menu.hasFilter());
        }
        if (layout.hasPrioritySlot() && layout.priorityUpgradeSlot().contains(x, y)) {
            return upgradePlaceTooltip(priorityUpgrade, menu.hasPriority());
        }
        return sidePanelTooltip(x, y);
    }

    /** The tooltip of a Filter or Priority upgrade place: its name, then what a click does. */
    private List<Component> upgradePlaceTooltip(ItemStack upgrade, boolean installed) {
        String key = "screen.aeroflux.pipe_config." + (installed ? "endpoint_installed" : "endpoint_empty");
        return List.of(upgrade.getHoverName(), Component.translatable(key, upgrade.getHoverName())
                .withStyle(ChatFormatting.GRAY));
    }

    /** Tooltips of the side panel's drawn parts; its buttons carry their own. */
    private List<Component> sidePanelTooltip(int x, int y) {
        boolean filter = menu.hasFilter();
        String screen = "screen.aeroflux.pipe_config.";
        if (filter) {
            if (layout.filterIcon().contains(x, y)) {
                return List.of(filterUpgrade.getHoverName(),
                        Component.translatable(screen + "filter.tooltip").withStyle(ChatFormatting.GRAY));
            }
            for (int i = 0; i < layout.filterSlotCount(); i++) {
                if (layout.filterSlot(i).contains(x, y)) {
                    ItemStack entry = menu.filterStack(i);
                    if (entry.isEmpty()) {
                        String hint = menu.pipe() == PipeType.FLUID ? "filter_slot.fluid" : "filter_slot.item";
                        return List.of(Component.translatable(screen + hint).withStyle(ChatFormatting.GRAY));
                    }
                    return List.of(entry.getHoverName(),
                            Component.translatable(screen + "filter_slot.clear").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (menu.hasPriority() && layout.priorityValue(filter).contains(x, y)) {
            return List.of(Component.translatable(screen + "priority", PipeConfigActions.priorityText(menu.priority())),
                    Component.translatable(screen + "priority.tooltip").withStyle(ChatFormatting.GRAY));
        }
        return List.of();
    }

    private boolean overTierBar(int x, int y) {
        for (int i = 0; i < PipeConfigLayout.TIER_CELLS; i++) {
            if (layout.tierCell(i).contains(x, y)) {
                return true;
            }
        }
        return false;
    }
}
