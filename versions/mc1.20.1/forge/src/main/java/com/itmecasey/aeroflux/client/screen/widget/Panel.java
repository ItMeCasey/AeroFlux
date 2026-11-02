package com.itmecasey.aeroflux.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Plain-fill drawing shared by the machine screens and their widgets: the raised gray panel and the
 * sunken slot frame in the vanilla style, with no texture needed. Colors are ARGB.
 */
public final class Panel {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int LIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int EDGE = 0xFF000000;
    public static final int SLOT_FILL = 0xFF8B8B8B;
    public static final int SLOT_SHADOW = 0xFF373737;
    public static final int TEXT = 0xFF404040;
    public static final int TEXT_MUTED = 0xFF6B6B6B;

    private Panel() {
    }

    /** A raised panel with a black outline. */
    public static void raised(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, EDGE);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, LIGHT);
        graphics.fill(x + 2, y + 2, right - 1, bottom - 1, SHADOW);
        graphics.fill(x + 2, y + 2, right - 2, bottom - 2, PANEL);
    }

    /** A sunken square frame of {@code size} pixels, such as the 18 pixel frame around a slot. */
    public static void sunken(GuiGraphics graphics, int x, int y, int size) {
        sunken(graphics, x, y, size, size, SLOT_FILL);
    }

    /** A sunken frame of any size, filled with {@code face}. */
    public static void sunken(GuiGraphics graphics, int x, int y, int width, int height, int face) {
        graphics.fill(x, y, x + width, y + height, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + width, y + height, LIGHT);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, face);
    }
}
