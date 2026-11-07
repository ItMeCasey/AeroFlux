package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.machine.ProcessTimer;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A progress arrow that fills from left to right with how far the job has come, drawn from plain
 * fills so it needs no texture. Hovering it shows the percentage and the seconds left. The screen
 * pushes the numbers in with {@link #update}.
 */
public class ProgressArrow extends AbstractWidget implements HoverInfo {
    private static final int TRACK = 0xFF5A5A5A;
    private static final int TRACK_EDGE = 0xFF3A3A3A;
    /** Ticks per second, for the time left. */
    private static final int TPS = 20;

    private final int fill;
    private int progress;
    private int total;

    /** An arrow of the given size, filled with {@code fillColor} (ARGB). */
    public ProgressArrow(int x, int y, int width, int height, int fillColor) {
        super(x, y, width, height, Component.translatable("gui.aeroflux.progress.name"));
        this.fill = fillColor;
    }

    public void update(int progress, int total) {
        this.progress = progress;
        this.total = total;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int filledColumns = (int) Math.round(ProcessTimer.fraction(progress, total) * width);
        for (int column = 0; column < width; column++) {
            int half = halfHeightAt(column);
            int top = getY() + height / 2 - half;
            int bottom = getY() + height / 2 + half;
            graphics.fill(getX() + column, top, getX() + column + 1, bottom, column < filledColumns ? fill : TRACK);
            if (column >= filledColumns) {
                // A darker line along the top and bottom edge of the empty part gives it depth.
                graphics.fill(getX() + column, top, getX() + column + 1, top + 1, TRACK_EDGE);
                graphics.fill(getX() + column, bottom - 1, getX() + column + 1, bottom, TRACK_EDGE);
            }
        }
    }

    /**
     * Half the arrow's height at a column: a shaft a third of the height for the first two thirds
     * of the width, then a head that narrows to a point.
     */
    private int halfHeightAt(int column) {
        int shaftEnd = width * 2 / 3;
        if (column < shaftEnd) {
            return Math.max(1, height / 6);
        }
        int headLength = width - shaftEnd;
        return Math.max(1, (width - column) * (height / 2) / Math.max(1, headLength));
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY) || total <= 0) {
            return List.of();
        }
        int percent = (int) Math.round(ProcessTimer.fraction(progress, total) * 100.0);
        String seconds = String.format(Locale.US, "%.1f", (total - progress) / (double) TPS);
        return List.of(Component.translatable("gui.aeroflux.progress.percent", percent),
                Component.translatable("gui.aeroflux.progress.left", seconds));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
