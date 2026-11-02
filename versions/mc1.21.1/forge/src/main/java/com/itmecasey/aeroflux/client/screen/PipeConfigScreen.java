package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.conduit.config.PipeConfigLayout;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The screen of one pipe at one face. It only shows what the server has sent and turns clicks
 * into button presses on the menu; it decides nothing itself. Everything is drawn with plain
 * fills, so it needs no texture: the pipe's own color is the accent on the top edge and on
 * the selected buttons. Where things go is worked out by {@link PipeConfigLayout}.
 */
public class PipeConfigScreen extends AbstractContainerScreen<PipeConfigMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int PANEL_EDGE = 0xFF000000;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_MUTED = 0xFF6B6B6B;

    private final PipeConfigLayout layout;
    /** The pipe's color with full alpha. */
    private final int accent;

    public PipeConfigScreen(PipeConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        this.accent = 0xFF000000 | menu.pipe().color();
        this.imageWidth = layout.width();
        this.imageHeight = layout.height();
    }

    @Override
    protected void init() {
        super.init();
        List<ConnectionMode> modes = PipeConfigActions.supportedModes(menu.pipe());
        for (int i = 0; i < modes.size(); i++) {
            ConnectionMode mode = modes.get(i);
            PipeConfigLayout.Rect rect = layout.modeButtons().get(i);
            String key = "connection_mode.aeroflux." + mode.name().toLowerCase(Locale.ROOT);
            addPanelButton(rect, Component.translatable(key), Component.translatable(key + ".tooltip"),
                    PipeConfigMenu.modeButton(mode));
        }
        if (layout.hasDistribution()) {
            for (DistributionMode mode : DistributionMode.values()) {
                PipeConfigLayout.Rect rect = layout.distributionButtons().get(mode.ordinal());
                String key = "distribution_mode.aeroflux." + mode.name().toLowerCase(Locale.ROOT);
                addPanelButton(rect, Component.translatable(key), Component.translatable(key + ".tooltip"),
                        PipeConfigMenu.distributionButton(mode));
            }
        }
    }

    /** A button placed in panel coordinates that presses a menu button when clicked. */
    private void addPanelButton(PipeConfigLayout.Rect rect, Component label, Component tooltip, int buttonId) {
        addRenderableWidget(Button.builder(label, button -> pressMenuButton(buttonId))
                .bounds(leftPos + rect.x(), topPos + rect.y(), rect.width(), rect.height())
                .tooltip(Tooltip.create(tooltip))
                .build());
    }

    /** Sends the click to the server, which applies it and sends the new values back. */
    private void pressMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int right = x + imageWidth;
        int bottom = y + imageHeight;

        // A raised panel, the way the vanilla screens draw one.
        graphics.fill(x, y, right, bottom, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL_LIGHT);
        graphics.fill(x + 2, y + 2, right - 1, bottom - 1, PANEL_SHADOW);
        graphics.fill(x + 2, y + 2, right - 2, bottom - 2, PANEL);
        // The pipe's color along the top says which lane this is.
        graphics.fill(x + 2, y + 2, right - 2, y + 5, accent);

        for (Slot slot : menu.slots) {
            drawSlotFrame(graphics, x + slot.x - 1, y + slot.y - 1);
        }
    }

    /** A sunken 18 by 18 frame around a slot. */
    private static void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + PipeConfigLayout.SLOT, y + PipeConfigLayout.SLOT, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + PipeConfigLayout.SLOT, y + PipeConfigLayout.SLOT, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + PipeConfigLayout.SLOT - 1, y + PipeConfigLayout.SLOT - 1, SLOT_FILL);
    }

    /**
     * Texts, and the outline on the buttons that are selected. This runs after the buttons are
     * drawn and with the origin at the panel's corner, so the layout numbers apply as they are.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, layout.titleX(), layout.titleY(), TEXT, false);
        graphics.drawString(font, Component.translatable("screen.aeroflux.pipe_config.side", menu.faceName()),
                layout.titleX(), layout.sideY(), TEXT_MUTED, false);

        graphics.drawString(font, Component.translatable("screen.aeroflux.pipe_config.modes"),
                layout.modeButtons().get(0).x(), layout.headingY(), TEXT_MUTED, false);
        int selectedMode = PipeConfigActions.supportedModes(menu.pipe()).indexOf(menu.mode());
        if (selectedMode >= 0) {
            outline(graphics, layout.modeButtons().get(selectedMode));
        }

        if (layout.hasDistribution()) {
            graphics.drawString(font, Component.translatable("screen.aeroflux.pipe_config.distribution"),
                    layout.rightX(), layout.headingY(), TEXT_MUTED, false);
            outline(graphics, layout.distributionButtons().get(menu.distribution().ordinal()));
        }

        graphics.drawString(font, Component.translatable("screen.aeroflux.pipe_config.rate"),
                layout.rightX(), layout.rateY(), TEXT_MUTED, false);
        List<FormattedCharSequence> lines = font.split(menu.rate().text(), layout.rightWidth());
        for (int i = 0; i < lines.size() && i < PipeConfigLayout.RATE_LINES; i++) {
            graphics.drawString(font, lines.get(i), layout.rightX(),
                    layout.rateY() + PipeConfigLayout.LINE_HEIGHT * (i + 1), TEXT, false);
        }

        if (layout.upgradeSlotCount() > 0) {
            graphics.drawString(font, Component.translatable("screen.aeroflux.pipe_config.upgrades",
                            menu.speedUpgrades() + menu.capacityUpgrades(), layout.upgradeSlotCount()),
                    layout.titleX(), layout.upgradeHeadingY(), TEXT_MUTED, false);
        }
    }

    /** One pixel of the pipe's color around a button. */
    private void outline(GuiGraphics graphics, PipeConfigLayout.Rect rect) {
        graphics.renderOutline(rect.x() - 1, rect.y() - 1, rect.width() + 2, rect.height() + 2, accent);
    }
}
