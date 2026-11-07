package com.itmecasey.aeroflux.client.screen.widget;

import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A raised gray glyph button for a setting with two states, such as a filter's whitelist and
 * blacklist. It shows the glyph of the current state, and its tooltip names that state and says
 * what it does; a click asks for the other one. It looks like a raised {@link GlyphButton}.
 */
public class ToggleGlyphButton extends AbstractButton {
    private static final int HOVER = 0x40FFFFFF;

    private final BooleanSupplier on;
    private final String onGlyph;
    private final String offGlyph;
    private final Component onName;
    private final Component offName;
    private final Tooltip onTooltip;
    private final Tooltip offTooltip;
    private final Runnable press;
    /** The state the tooltip was last set for, so it is only swapped when the state changes. */
    private boolean shownOn;

    /**
     * @param on         whether the setting is in its first state right now
     * @param onGlyph    the glyph for the first state
     * @param offGlyph   the glyph for the second state
     * @param onName     the first state's name; also what narration reads
     * @param onDetails  what the first state does
     * @param offName    the second state's name
     * @param offDetails what the second state does
     */
    public ToggleGlyphButton(int x, int y, int size, BooleanSupplier on, String onGlyph, String offGlyph,
                             Component onName, Component onDetails, Component offName, Component offDetails,
                             Runnable press) {
        super(x, y, size, size, onName);
        this.on = on;
        this.onGlyph = onGlyph;
        this.offGlyph = offGlyph;
        this.onName = onName;
        this.offName = offName;
        this.onTooltip = tooltip(onName, onDetails);
        this.offTooltip = tooltip(offName, offDetails);
        this.press = press;
        this.shownOn = on.getAsBoolean();
        setTooltip(shownOn ? onTooltip : offTooltip);
        setMessage(shownOn ? onName : offName);
    }

    private static Tooltip tooltip(Component name, Component details) {
        return Tooltip.create(Component.empty().append(name).append("\n")
                .append(details.copy().withStyle(ChatFormatting.GRAY)));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        press.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean state = on.getAsBoolean();
        if (state != shownOn) {
            shownOn = state;
            setTooltip(state ? onTooltip : offTooltip);
            setMessage(state ? onName : offName);
        }
        int x = getX();
        int y = getY();
        Panel.raised(graphics, x, y, width, height);
        if (isHovered()) {
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, HOVER);
        }
        Glyphs.draw(graphics, state ? onGlyph : offGlyph, x + width / 2, y + height / 2, Glyphs.DARK);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
