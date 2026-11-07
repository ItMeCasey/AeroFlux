package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.converter.ConverterMenu;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A converter's screen: the Flux gauge on the left, the output buffer as a bar on the right, and
 * between them the output per tick, what the buffer holds and a map of the faces that put power out. Numbers and units only; the words
 * are in the tooltips. Like the other screens it uses no texture and only shows what the server sent.
 */
public class ConverterScreen extends AbstractContainerScreen<ConverterMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 108;
    private static final int CONTENT_Y = 18;
    private static final int CONTENT_HEIGHT = 82;
    private static final int GAUGE_X = 8;
    private static final int BAR_X = 152;
    private static final int BAR_WIDTH = 16;
    private static final int TEXT_CENTER = 88;
    /** The face map: 14 pixel cells, under the numbers. */
    private static final int CELL = 14;
    private static final int NET_Y = 58;

    /** The output bar's fill: FE red, AE the purple of AE2's power. */
    private static final int FE_COLOR = 0xFFD8322C;
    private static final int AE_COLOR = 0xFF8B4FD8;
    private static final int BAR_FACE = 0xFF2A2A30;

    private final int accent;
    private FluxGauge gauge;

    public ConverterScreen(ConverterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.accent = 0xFF000000 | menu.tier().color();
    }

    @Override
    protected void init() {
        super.init();
        gauge = addRenderableWidget(new FluxGauge(leftPos + GAUGE_X, topPos + CONTENT_Y, 16, CONTENT_HEIGHT));
        // The faces, unfolded like the machines' side panel: top over front, left and right beside
        // it, bottom under it and back beside the bottom. A click switches power output there.
        int left = leftPos + TEXT_CENTER - (3 * CELL) / 2;
        int top = topPos + NET_Y;
        addFace(FaceLabel.TOP, left + CELL, top);
        addFace(FaceLabel.LEFT, left, top + CELL);
        addFace(FaceLabel.FRONT, left + CELL, top + CELL);
        addFace(FaceLabel.RIGHT, left + 2 * CELL, top + CELL);
        addFace(FaceLabel.BOTTOM, left + CELL, top + 2 * CELL);
        addFace(FaceLabel.BACK, left + 2 * CELL, top + 2 * CELL);
    }

    private void addFace(FaceLabel label, int x, int y) {
        addRenderableWidget(new FaceButton(label, x, y));
    }

    /** One face of the map: green where power goes out, gray where it does not. */
    private final class FaceButton extends AbstractButton {
        private final FaceLabel label;

        FaceButton(FaceLabel label, int x, int y) {
            super(x, y, CELL - 1, CELL - 1, Component.translatable("face_label.aeroflux." + label.id()));
            this.label = label;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, label.ordinal());
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = menu.outputsOn(label);
            Glyphs.bevelBox(graphics, getX(), getY(), width, on ? Glyphs.OPEN_COLOR : Glyphs.OFF_COLOR, 1.2);
            if (label == FaceLabel.FRONT) {
                graphics.fill(getX() + 2, getY() + 2, getX() + 5, getY() + 5, Glyphs.WHITE);
            }
            if (isHovered()) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
                setTooltip(Tooltip.create(getMessage().copy().append(CommonComponents.NEW_LINE).append(
                        Component.translatable(on ? "gui.aeroflux.converter.output_on" : "gui.aeroflux.converter.output_off"))));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** "FE" or "AE", the unit this converter makes. */
    private String unit() {
        return menu.type() == ConverterType.AE ? "AE" : "FE";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        gauge.update(menu.fluxAmount(), menu.fluxCapacity(), menu.fluxType(), menu.burn(), menu.burn() > 0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        List<Component> lines = gauge.tooltip(mouseX, mouseY);
        if (lines.isEmpty() && mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + BAR_WIDTH
                && mouseY >= topPos + CONTENT_Y && mouseY < topPos + CONTENT_Y + CONTENT_HEIGHT) {
            lines = List.of(Component.translatable("gui.aeroflux.converter.buffer." + unit().toLowerCase()),
                    Component.literal(menu.stored() + " / " + menu.bufferCapacity() + " " + unit()));
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);

        // The output buffer, filling from the bottom.
        int x = leftPos + BAR_X;
        int y = topPos + CONTENT_Y;
        Panel.sunken(graphics, x, y, BAR_WIDTH, CONTENT_HEIGHT, BAR_FACE);
        int capacity = Math.max(1, menu.bufferCapacity());
        int filled = (int) ((long) (CONTENT_HEIGHT - 2) * Math.min(menu.stored(), capacity) / capacity);
        if (filled > 0) {
            int color = menu.type() == ConverterType.AE ? AE_COLOR : FE_COLOR;
            graphics.fill(x + 1, y + CONTENT_HEIGHT - 1 - filled, x + BAR_WIDTH - 1, y + CONTENT_HEIGHT - 1, color);
        }

        centered(graphics, menu.outputPerTick() + " " + unit() + "/t", CONTENT_Y + 2, Panel.TEXT);
        centered(graphics, menu.burn() + " mB/t", CONTENT_Y + 14, Panel.TEXT_MUTED);
        centered(graphics, compact(menu.stored()) + " / " + compact(menu.bufferCapacity()), CONTENT_Y + 26, Panel.TEXT_MUTED);
    }

    private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
        graphics.text(font, text, leftPos + TEXT_CENTER - font.width(text) / 2, topPos + y, color, false);
    }

    /** A short number: 950, 12.5k, 1.2M. */
    private static String compact(int value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return Integer.toString(value);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 8, 7, Panel.TEXT, false);
    }
}
