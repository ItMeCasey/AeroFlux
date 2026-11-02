package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.core.CollectorForecast;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A tank's screen: a tall Flux gauge on the left and, beside it, what the tank holds, how full it
 * is, and how fast Flux went in and came out over the last second, with the time until it is full
 * or empty at that pace. Like the other screens it uses no texture and only shows what the server sent.
 */
public class TankScreen extends AbstractContainerScreen<TankMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 104;
    private static final int CONTENT_Y = 18;
    private static final int GAUGE_X = 8;
    private static final int GAUGE_WIDTH = 22;
    private static final int GAUGE_HEIGHT = 78;
    private static final int INFO_X = 36;
    private static final int INFO_WIDTH = 132;
    private static final int GLYPH_X = INFO_X + 10;
    private static final int TEXT_X = INFO_X + 20;
    private static final int ROW_Y = CONTENT_Y + 9;
    private static final int ROW_STEP = 12;
    /** The fill bar under the amount. */
    private static final int BAR_HEIGHT = 6;

    /** The info panel is dark like the gauge's glass, so the gold and pale blue glyphs stand out on it. */
    private static final int INFO_FACE = 0xFF2E3140;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int BAR_FACE = 0xFF2A2A30;

    private final int accent;
    private FluxGauge gauge;

    public TankScreen(TankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.accent = 0xFF000000 | menu.tier().color();
    }

    @Override
    protected void init() {
        super.init();
        gauge = addRenderableWidget(new FluxGauge(leftPos + GAUGE_X, topPos + CONTENT_Y, GAUGE_WIDTH, GAUGE_HEIGHT));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        gauge.update(menu.amount(), menu.capacity(), menu.fluxType(), 0, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        List<Component> lines = gauge.tooltip(mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);
        Panel.sunken(graphics, leftPos + INFO_X, topPos + CONTENT_Y, INFO_WIDTH, GAUGE_HEIGHT, INFO_FACE);

        FluxType type = menu.fluxType();
        int amount = menu.amount();
        int capacity = Math.max(1, menu.capacity());
        boolean empty = type == null || amount <= 0;

        // What it holds, then how much of how much.
        Component contents = empty ? Component.translatable("gui.aeroflux.gauge.empty")
                : Component.translatable("fluid_type.aeroflux." + type.id() + "_flux");
        row(graphics, 0, "fluid", empty ? Glyphs.OFF_COLOR : 0xFF000000 | type.tint(), contents, empty ? MUTED : TEXT);
        row(graphics, 1, "upgrade_capacity", TEXT,
                Component.translatable("gui.aeroflux.gauge.amount", format(amount), format(capacity)), TEXT);

        // How full, as a number and a bar.
        int percent = (int) Math.round(FillLevel.fraction(amount, capacity) * 100.0);
        int barY = topPos + ROW_Y + 2 * ROW_STEP - BAR_HEIGHT / 2;
        int barX = leftPos + TEXT_X;
        int barWidth = INFO_X + INFO_WIDTH - 6 - TEXT_X - 34;
        Panel.sunken(graphics, barX, barY, barWidth, BAR_HEIGHT, BAR_FACE);
        int filled = (int) Math.round(FillLevel.fraction(amount, capacity) * (barWidth - 2));
        if (filled > 0 && !empty) {
            graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + BAR_HEIGHT - 1, 0xFF000000 | type.tint());
        }
        String percentText = Component.translatable("gui.aeroflux.tank.percent", percent).getString();
        graphics.text(font, percentText, leftPos + INFO_X + INFO_WIDTH - 5 - font.width(percentText), barY - 1, TEXT, false);

        // The flows over the last second, and where they lead.
        double in = menu.inPerTick();
        double out = menu.outPerTick();
        row(graphics, 3, "mode_import", in > 0 ? Glyphs.IMPORT_COLOR : Glyphs.OFF_COLOR,
                Component.translatable("gui.aeroflux.tank.in", decimal(in)), in > 0 ? TEXT : MUTED);
        row(graphics, 4, "mode_export", out > 0 ? Glyphs.EXPORT_COLOR : Glyphs.OFF_COLOR,
                Component.translatable("gui.aeroflux.tank.out", decimal(out)), out > 0 ? TEXT : MUTED);
        row(graphics, 5, "ticks", TEXT, outlook(amount, capacity, in - out), TEXT);
    }

    /** "Full in", "Empty in" or "Steady", at the net rate. */
    private static Component outlook(int amount, int capacity, double net) {
        if (net > 0) {
            long ticks = CollectorForecast.ticksToFull(amount, capacity, net);
            return ticks == 0 ? Component.translatable("gui.aeroflux.collector.full.now")
                    : Component.translatable("gui.aeroflux.collector.full.in", CollectorForecast.shortDuration(ticks));
        }
        if (net < 0 && amount > 0) {
            // Emptying is filling the other way round: the missing amount is what is left.
            long ticks = CollectorForecast.ticksToFull(0, amount, -net);
            return Component.translatable("gui.aeroflux.tank.empty_in", CollectorForecast.shortDuration(ticks));
        }
        return Component.translatable("gui.aeroflux.tank.steady");
    }

    private void row(GuiGraphicsExtractor graphics, int index, String glyph, int glyphColor, Component text, int textColor) {
        int centerY = topPos + ROW_Y + index * ROW_STEP;
        Glyphs.draw(graphics, glyph, leftPos + GLYPH_X, centerY, glyphColor);
        String shown = font.plainSubstrByWidth(text.getString(), INFO_X + INFO_WIDTH - 4 - TEXT_X);
        graphics.text(font, shown, leftPos + TEXT_X, centerY - 4, textColor, false);
    }

    private static String format(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 8, 7, Panel.TEXT, false);
    }
}
