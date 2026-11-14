package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.block.menu.CollectorMenu;
import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.FluxTypeBadge;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.core.CollectionMath;
import com.itmecasey.aeroflux.core.CollectorForecast;
import com.itmecasey.aeroflux.core.CollectorForecast.SkyPhase;
import com.itmecasey.aeroflux.core.DimensionRules.TimeSource;
import com.itmecasey.aeroflux.core.FluxType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

/**
 * A collector's screen: a tall Flux gauge for the buffer and, beside it, the sky panel. The panel
 * starts with a strip of the whole day (gold while Aero Flux is made, blue while Moon Flux is,
 * gray in the gaps) with a marker at the current time, then one row each for what the sky offers
 * now, whether the sky is open (and what blocks it), the weather, the moon, the output rate and
 * when the buffer will be full. Each row has a glyph and a short line; the details are in its
 * tooltip. Like the other screens it uses no texture and only shows what the server sent.
 */
public class CollectorScreen extends AbstractContainerScreen<CollectorMenu> {
    private static final int WIDTH = 196;
    private static final int HEIGHT = 128;
    private static final int CONTENT_Y = 18;
    private static final int GAUGE_X = 8;
    private static final int GAUGE_WIDTH = 22;
    private static final int GAUGE_HEIGHT = 102;
    /** The sky panel: a sunken box right of the gauge. */
    private static final int SKY_X = 36;
    private static final int SKY_WIDTH = 152;
    /** The day strip across the top of the sky panel. */
    private static final int STRIP_X = SKY_X + 4;
    private static final int STRIP_Y = CONTENT_Y + 4;
    private static final int STRIP_WIDTH = SKY_WIDTH - 8;
    private static final int STRIP_HEIGHT = 7;
    /** The rows under the strip: glyph centers and text start. */
    private static final int ROW_Y = CONTENT_Y + 18;
    private static final int ROW_STEP = 14;
    private static final int GLYPH_X = SKY_X + 10;
    private static final int TEXT_X = SKY_X + 20;

    /** The info panel is dark like the gauge's glass, so the gold and pale blue glyphs stand out on it. */
    private static final int SKY_FACE = 0xFF2E3140;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF9A9AA8;
    private static final int GAP_COLOR = 0xFF6E6E78;
    private static final int NIGHT_COLOR = 0xFF5B6BC8;
    private static final int MARKER = 0xFFFFFFFF;
    private static final int GOOD = 0xFF6CCF6F;
    private static final int BAD = 0xFFFF6B6B;
    private static final int TICKS_PER_HOUR = 1_000;

    private final int accent;
    private FluxGauge gauge;
    /** The rows drawn this frame, kept for their tooltips. */
    private final List<Row> rows = new ArrayList<>();

    /** One line of the sky panel: what to draw and what its tooltip says. */
    private record Row(String glyph, int glyphColor, Component text, int textColor, List<Component> tooltip) {
    }

    public CollectorScreen(CollectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.accent = 0xFF000000 | menu.tier().color();
    }

    @Override
    protected void init() {
        super.init();
        gauge = addRenderableWidget(new FluxGauge(leftPos + GAUGE_X, topPos + CONTENT_Y, GAUGE_WIDTH, GAUGE_HEIGHT));
    }

    // --- The rows ----------------------------------------------------------------------------

    /** Works out this frame's rows from the menu. */
    private void buildRows() {
        rows.clear();
        rows.add(offerRow());
        rows.add(skyRow());
        rows.add(weatherRow());
        rows.add(moonRow());
        rows.add(rateRow());
        rows.add(fullRow());
    }

    /** What the sky offers now: day and Aero Flux, night and Moon Flux, or a gap with neither. */
    private Row offerRow() {
        FluxType offered = menu.offered();
        TimeSource source = menu.timeSource();
        String when = switch (source) {
            case ALWAYS_DAY -> "always_day";
            case ALWAYS_NIGHT -> "always_night";
            case ALWAYS_BOTH -> "always_both";
            default -> CollectorForecast.phase(menu.timeOfDay()).name().toLowerCase(Locale.ROOT);
        };
        Component whenText = Component.translatable("gui.aeroflux.collector.phase." + when);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(whenText);
        if (source == TimeSource.DIMENSION_TIME || source == TimeSource.OVERWORLD_TIME) {
            tooltip.add(Component.translatable("gui.aeroflux.collector.clock", clock(menu.timeOfDay()),
                    CollectorForecast.shortDuration(CollectorForecast.ticksLeftInPhase(menu.timeOfDay()))));
        }
        if (offered == null) {
            return new Row("ticks", Glyphs.OFF_COLOR, Component.translatable("gui.aeroflux.collector.offer.none", whenText),
                    MUTED, tooltip);
        }
        String glyph = offered == FluxType.MOON ? "flux_moon" : "flux_sun";
        int color = offered == FluxType.MOON ? FluxTypeBadge.MOON_COLOR : FluxTypeBadge.AERO_COLOR;
        Component flux = fluxName(offered);
        if (menu.bufferLocked()) {
            return new Row(glyph, color, Component.translatable("gui.aeroflux.collector.offer.locked", whenText), BAD, tooltip);
        }
        return new Row(glyph, color, Component.translatable("gui.aeroflux.collector.offer.making", whenText, flux),
                TEXT, tooltip);
    }

    /** Whether the sky is open, and what blocks it when it is not. */
    private Row skyRow() {
        if (!menu.needsSky()) {
            return new Row("sky_clear", Glyphs.OFF_COLOR, Component.translatable("gui.aeroflux.collector.sky.not_needed"),
                    MUTED, List.of());
        }
        if (menu.skyVisible()) {
            return new Row("sky_clear", GOOD, Component.translatable("gui.aeroflux.collector.sky.open"), TEXT,
                    List.of());
        }
        Block blocker = menu.blocker();
        if (blocker == null) {
            return new Row("mode_blocked", BAD, Component.translatable("gui.aeroflux.collector.sky.blocked"), BAD,
                    List.of());
        }
        BlockPos at = menu.blockerPos();
        return new Row("mode_blocked", BAD, Component.translatable("gui.aeroflux.collector.sky.blocked_by", blocker.getName()), BAD,
                List.of(Component.translatable("gui.aeroflux.collector.sky.blocked_by", blocker.getName()),
                        Component.translatable("gui.aeroflux.collector.sky.blocked_at", at.getX(), at.getY(), at.getZ())));
    }

    /** The weather and what it does to the flux on offer. */
    private Row weatherRow() {
        if (menu.ignoresWeather()) {
            return new Row("weather", Glyphs.OFF_COLOR, Component.translatable("gui.aeroflux.collector.weather.ignored"),
                    MUTED, List.of());
        }
        String weather = menu.thundering() ? "thunder" : menu.raining() ? "rain" : "clear";
        int percent = (int) Math.round(menu.weatherFactor() * 100.0);
        int color = percent >= 100 ? Glyphs.IMPORT_COLOR : Glyphs.EXPORT_COLOR;
        return new Row("weather", color, Component.translatable("gui.aeroflux.collector.weather." + weather, percent), TEXT, List.of());
    }

    /** The moon's phase and how strong Moon Flux is under it. */
    private Row moonRow() {
        int phase = menu.moonPhase();
        int percent = (int) Math.round(CollectionMath.moonPhaseFactor(phase) * 100.0);
        Component name = Component.translatable("gui.aeroflux.collector.moon." + Math.floorMod(phase, 8));
        return new Row("flux_moon", FluxTypeBadge.MOON_COLOR, Component.translatable("gui.aeroflux.collector.moon", name, percent),
                TEXT, List.of());
    }

    /** What the collector made last tick, and what it passes on to its neighbors. */
    private Row rateRow() {
        double rate = menu.ratePerTick();
        double out = menu.outPerTick();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.aeroflux.collector.rate.tooltip", decimal(rate)));
        tooltip.add(Component.translatable("gui.aeroflux.collector.out.tooltip", decimal(out)));
        return new Row("fluid", rate > 0 ? GOOD : Glyphs.OFF_COLOR,
                Component.translatable("gui.aeroflux.collector.rate", decimal(rate)), rate > 0 ? TEXT : MUTED, tooltip);
    }

    /** When the buffer will be full at the current rates. */
    private Row fullRow() {
        long ticks = menu.ticksToFull();
        List<Component> tooltip = List.of();
        if (ticks == 0) {
            return new Row("ticks", BAD, Component.translatable("gui.aeroflux.collector.full.now"), TEXT, tooltip);
        }
        if (ticks < 0) {
            return new Row("ticks", Glyphs.OFF_COLOR, Component.translatable("gui.aeroflux.collector.full.never"),
                    MUTED, tooltip);
        }
        return new Row("ticks", TEXT, Component.translatable("gui.aeroflux.collector.full.in",
                CollectorForecast.shortDuration(ticks)), TEXT, tooltip);
    }

    private static Component fluxName(FluxType type) {
        return Component.translatable("fluid_type.aeroflux." + type.id() + "_flux");
    }

    /** A Minecraft clock reading: day tick 0 is 6:00 in the morning. */
    private static String clock(int timeOfDay) {
        int hours = (timeOfDay / TICKS_PER_HOUR + 6) % 24;
        int minutes = (timeOfDay % TICKS_PER_HOUR) * 60 / TICKS_PER_HOUR;
        return String.format(Locale.ROOT, "%d:%02d", hours, minutes);
    }

    private static String decimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        gauge.update(menu.amount(), menu.capacity(), menu.storedFlux(), 0, false);
        buildRows();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        List<Component> lines = gauge.tooltip(mouseX, mouseY);
        if (lines.isEmpty()) {
            lines = hoveredRow(mouseX - leftPos, mouseY - topPos);
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /** The tooltip under a point in panel pixels: the day strip's or a row's. */
    private List<Component> hoveredRow(int x, int y) {
        if (x < SKY_X || x >= SKY_X + SKY_WIDTH) {
            return List.of();
        }
        if (y >= STRIP_Y - 2 && y < STRIP_Y + STRIP_HEIGHT + 2) {
            return rows.isEmpty() ? List.of() : rows.get(0).tooltip();
        }
        int index = Math.floorDiv(y - (ROW_Y - ROW_STEP / 2), ROW_STEP);
        return index >= 0 && index < rows.size() ? rows.get(index).tooltip() : List.of();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);
        Panel.sunken(graphics, leftPos + SKY_X, topPos + CONTENT_Y, SKY_WIDTH, GAUGE_HEIGHT, SKY_FACE);
        drawDayStrip(graphics);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int centerY = topPos + ROW_Y + i * ROW_STEP;
            Glyphs.draw(graphics, row.glyph(), leftPos + GLYPH_X, centerY, row.glyphColor());
            String text = font.plainSubstrByWidth(row.text().getString(), SKY_X + SKY_WIDTH - 4 - TEXT_X);
            graphics.text(font, text, leftPos + TEXT_X, centerY - 4, row.textColor(), false);
        }
    }

    /**
     * The whole day as a strip, morning on the left: gold where Aero Flux is made, blue where Moon
     * Flux is, gray in the gaps, and a white marker at the current time. A dimension without days
     * shows its one setting across the whole strip, both colors stacked when it makes both.
     */
    private void drawDayStrip(GuiGraphicsExtractor graphics) {
        int x = leftPos + STRIP_X;
        int y = topPos + STRIP_Y;
        graphics.fill(x - 1, y - 1, x + STRIP_WIDTH + 1, y + STRIP_HEIGHT + 1, Panel.SLOT_SHADOW);
        TimeSource source = menu.timeSource();
        for (int column = 0; column < STRIP_WIDTH; column++) {
            long time = (long) column * CollectionMath.DAY_LENGTH / STRIP_WIDTH;
            int top = colorAt(source, time, true);
            int bottom = colorAt(source, time, false);
            int half = STRIP_HEIGHT / 2;
            graphics.fill(x + column, y, x + column + 1, y + half + 1, top);
            graphics.fill(x + column, y + half + 1, x + column + 1, y + STRIP_HEIGHT, bottom);
        }
        if (source == TimeSource.DIMENSION_TIME || source == TimeSource.OVERWORLD_TIME) {
            int markerX = x + (int) ((long) menu.timeOfDay() * STRIP_WIDTH / CollectionMath.DAY_LENGTH);
            graphics.fill(markerX, y - 2, markerX + 1, y + STRIP_HEIGHT + 2, MARKER);
        }
    }

    /** The strip's color at a time; {@code top} picks the upper half, which differs only when both are made. */
    private static int colorAt(TimeSource source, long time, boolean top) {
        return switch (source) {
            case ALWAYS_DAY -> FluxTypeBadge.AERO_COLOR;
            case ALWAYS_NIGHT -> NIGHT_COLOR;
            case ALWAYS_BOTH -> top ? FluxTypeBadge.AERO_COLOR : NIGHT_COLOR;
            default -> phaseColor(CollectorForecast.phase(time));
        };
    }

    private static int phaseColor(SkyPhase phase) {
        if (phase == SkyPhase.DAY) {
            return FluxTypeBadge.AERO_COLOR;
        }
        return phase == SkyPhase.NIGHT ? NIGHT_COLOR : GAP_COLOR;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 8, 7, Panel.TEXT, false);
    }
}
