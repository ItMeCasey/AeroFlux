package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.FluxType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The small sun or moon cell on top of a machine's Flux gauge: which flux the machine runs on, lit
 * in that flux's color while the tank holds some and dark while it is empty. A machine that runs
 * on either flux shows half a sun and half a moon, and only the half it has is lit. The gauge's
 * dark glass is the background, so the badge reads as part of the gauge.
 */
public class FluxTypeBadge extends AbstractWidget implements HoverInfo {
    /** Aero Flux gold, the same as the progress fill. */
    public static final int AERO_COLOR = 0xFFF2C94C;
    /** Moon Flux pale blue. */
    public static final int MOON_COLOR = 0xFFAFCBFF;
    /** A flux the machine takes but does not have right now. */
    private static final int UNLIT = 0xFF5C5C68;
    private static final int FRAME = 0xFF1B1B1B;
    private static final int GLASS = 0xFF30303A;

    private final Set<FluxType> accepted;
    private @Nullable FluxType held;

    /** A badge for a machine whose tank takes the given flux types. */
    public FluxTypeBadge(int x, int y, int width, int height, Set<FluxType> accepted) {
        super(x, y, width, height, Component.translatable("gui.aeroflux.badge.name"));
        this.accepted = accepted;
    }

    /** The flux in the tank, or {@code null} when it is empty. */
    public void update(@Nullable FluxType held, int amount) {
        this.held = amount > 0 ? held : null;
    }

    /** The color of a flux's glyph: its own color while the tank holds it, dark otherwise. */
    private int colorOf(FluxType type) {
        if (type != held) {
            return UNLIT;
        }
        return type == FluxType.MOON ? MOON_COLOR : AERO_COLOR;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, GLASS);
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        boolean aero = accepted.contains(FluxType.AERO);
        boolean moon = accepted.contains(FluxType.MOON);
        if (aero && moon) {
            Glyphs.draw(graphics, "flux_sun_half", centerX, centerY, colorOf(FluxType.AERO));
            Glyphs.draw(graphics, "flux_moon_half", centerX, centerY, colorOf(FluxType.MOON));
        } else if (moon) {
            Glyphs.draw(graphics, "flux_moon", centerX, centerY, colorOf(FluxType.MOON));
        } else {
            Glyphs.draw(graphics, "flux_sun", centerX, centerY, colorOf(FluxType.AERO));
        }
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return List.of();
        }
        boolean aero = accepted.contains(FluxType.AERO);
        boolean moon = accepted.contains(FluxType.MOON);
        String takes = aero && moon ? "either" : moon ? "moon" : "aero";
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.aeroflux.badge.takes." + takes));
        if (held != null && accepted.contains(held)) {
            lines.add(Component.translatable("gui.aeroflux.badge.has." + held.id()).withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("gui.aeroflux.badge.none").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
