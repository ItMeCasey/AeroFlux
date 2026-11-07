package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The small pictures the side-config window and the pipe screen use instead of words, and the
 * colors that go with them. Each glyph is a white 16 by 16 texture in
 * {@code textures/gui/glyph} whose 13 by 13 picture starts one pixel in; it is tinted with an
 * ARGB color when drawn, so one file serves every color.
 */
public final class Glyphs {
    /** White, for glyphs on colored buttons. */
    public static final int WHITE = 0xFFFFFFFF;
    /** The dark gray of glyphs on gray panels; the same as the panel's text. */
    public static final int DARK = Panel.TEXT;
    /** A shade darker, for the glyphs on unselected (sunken) tabs. */
    public static final int DARKER = 0xFF303030;

    public static final int IMPORT_COLOR = 0xFF4A7FD8;
    public static final int EXPORT_COLOR = 0xFFE8913A;
    public static final int OPEN_COLOR = 0xFF55B85A;
    public static final int OFF_COLOR = 0xFF8A8A8A;
    public static final int BLOCKED_COLOR = 0xFFC04040;

    private static final int TEXTURE = 16;
    /** From a glyph's center to its texture's corner: the picture is 13 wide and starts at pixel 1. */
    private static final int CENTER_OFFSET = 7;
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private Glyphs() {
    }

    /** Draws the glyph called {@code name} with its picture centered on the given point. */
    public static void draw(GuiGraphicsExtractor graphics, String name, int centerX, int centerY, int color) {
        Identifier texture = TEXTURES.computeIfAbsent(name, key -> Ids.mod("textures/gui/glyph/" + key + ".png"));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, centerX - CENTER_OFFSET, centerY - CENTER_OFFSET,
                0, 0, TEXTURE, TEXTURE, TEXTURE, TEXTURE, color);
    }

    // --- Which glyph and which color ---------------------------------------------------------

    /** The fill color of a face or button in a connection mode. */
    public static int modeColor(ConnectionMode mode) {
        return switch (mode) {
            case IMPORT -> IMPORT_COLOR;
            case EXPORT -> EXPORT_COLOR;
            case OPEN -> OPEN_COLOR;
            case OFF -> OFF_COLOR;
            case BLOCKED -> BLOCKED_COLOR;
        };
    }

    /** The glyph of a connection mode, such as {@code mode_import}. */
    public static String modeGlyph(ConnectionMode mode) {
        return "mode_" + mode.name().toLowerCase(Locale.ROOT);
    }

    /** The glyph of a resource tab; the glyphs are named after the resources. */
    public static String resourceGlyph(SideResource resource) {
        return resource.id();
    }

    /** The color of a resource, the same as the pipe that carries it. */
    public static int resourceColor(SideResource resource) {
        PipeType pipe = switch (resource) {
            case ITEMS -> PipeType.ITEM;
            case FLUID -> PipeType.FLUID;
            case ENERGY -> PipeType.ENERGY;
            case GAS -> PipeType.GAS;
            case REDSTONE -> PipeType.REDSTONE;
        };
        return 0xFF000000 | pipe.color();
    }

    /** The glyph of a pipe; the item pipe shares the items glyph. */
    public static String pipeGlyph(PipeType pipe) {
        return pipe == PipeType.ITEM ? "items" : pipe.id();
    }

    /**
     * The tier colors of the glyph screens. They are darker or more saturated than the tier
     * colors used elsewhere so the small tier gem and bar read against the gray panel.
     */
    public static int tierColor(Tier tier) {
        return switch (tier) {
            case COPPER -> 0xFFC86F4A;
            case IRON -> 0xFFB8BCC0;
            case GOLD -> 0xFFE8B930;
            case DIAMOND -> 0xFF4DD9D0;
            case NETHERITE -> 0xFF4A4046;
            case FLUXIUM -> 0xFF3B404B;
        };
    }

    // --- Drawing helpers ---------------------------------------------------------------------

    /** A color with each channel multiplied by {@code factor}, clamped to 255; alpha is kept. */
    public static int shade(int argb, double factor) {
        int red = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int green = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int blue = Math.min(255, (int) ((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    /**
     * A solid colored square with a bevel: a black edge, then a lighter top and left and a darker
     * bottom and right around the {@code base} color.
     *
     * @param lighten how much lighter the top and left are, such as 1.2
     */
    public static void bevelBox(GuiGraphicsExtractor graphics, int x, int y, int size, int base, double lighten) {
        graphics.fill(x, y, x + size, y + size, Panel.EDGE);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, shade(base, lighten));
        graphics.fill(x + 2, y + 2, x + size - 1, y + size - 1, shade(base, 0.72));
        graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, base);
    }
}
