package com.itmecasey.aeroflux.client.screen.widget;

import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A square button that shows a glyph instead of a label, as the pipe screen uses them. It comes in
 * two looks: a solid {@linkplain #colored colored} box with a white glyph (the face modes) and a
 * {@linkplain #raised raised} gray box with a dark glyph (the distribution modes). The button that
 * matches the current setting gets an outline. Its name and what it does are in the tooltip, and
 * the name is also what narration reads.
 */
public class GlyphButton extends AbstractButton {
    /** Laid over a button under the pointer. */
    private static final int HOVER = 0x40FFFFFF;

    private final String glyph;
    /** The box color for a colored button, or 0 for a raised gray one. */
    private final int fill;
    private final int outline;
    private final BooleanSupplier selected;
    private final Runnable press;

    private GlyphButton(int x, int y, int size, String glyph, int fill, int outline, BooleanSupplier selected,
                        Runnable press, Component name, Component description) {
        super(x, y, size, size, name);
        this.glyph = glyph;
        this.fill = fill;
        this.outline = outline;
        this.selected = selected;
        this.press = press;
        MutableComponent tooltip = Component.empty().append(name);
        if (description != null) {
            tooltip.append("\n").append(description.copy().withStyle(ChatFormatting.GRAY));
        }
        setTooltip(Tooltip.create(tooltip));
    }

    /**
     * A solid box of {@code color} with a white glyph; selected, it gets a white outline.
     *
     * @param description what the button does, shown under its name in the tooltip, or {@code null}
     */
    public static GlyphButton colored(int x, int y, int size, String glyph, int color, BooleanSupplier selected,
                                      Runnable press, Component name, Component description) {
        return new GlyphButton(x, y, size, glyph, color, Glyphs.WHITE, selected, press, name, description);
    }

    /**
     * A raised gray box with a dark glyph; selected, it gets an outline in {@code accent}.
     *
     * @param description what the button does, shown under its name in the tooltip, or {@code null}
     */
    public static GlyphButton raised(int x, int y, int size, String glyph, int accent, BooleanSupplier selected,
                                     Runnable press, Component name, Component description) {
        return new GlyphButton(x, y, size, glyph, 0, accent, selected, press, name, description);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        press.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        if (fill != 0) {
            Glyphs.bevelBox(graphics, x, y, width, fill, 1.2);
        } else {
            Panel.raised(graphics, x, y, width, height);
        }
        if (isHovered()) {
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, HOVER);
        }
        Glyphs.draw(graphics, glyph, x + width / 2, y + height / 2, fill != 0 ? Glyphs.WHITE : Glyphs.DARK);
        if (selected.getAsBoolean()) {
            // A colored ring with a black one outside it, so it shows on any background.
            graphics.outline(x - 1, y - 1, width + 2, height + 2, outline);
            graphics.outline(x - 2, y - 2, width + 4, height + 4, Panel.EDGE);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
