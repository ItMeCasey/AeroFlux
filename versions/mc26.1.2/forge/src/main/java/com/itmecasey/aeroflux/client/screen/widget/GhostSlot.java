package com.itmecasey.aeroflux.client.screen.widget;

import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Something that looks like an inventory slot but is not one: the pipe screen's filter entries and
 * the places for the Filter and Priority upgrades. It draws a slot frame with whatever stack the
 * menu last synced and turns a click into a menu button press; the server then decides what the
 * click means from the stack on the cursor. When it shows nothing, it can show a faded hint of
 * what belongs there instead.
 */
public class GhostSlot extends AbstractWidget {
    /** Laid over a hint item so it reads as a hint, the way vanilla's recipe ghosts do. */
    private static final int HINT_VEIL = 0xA08B8B8B;
    private static final int HOVER = 0x80FFFFFF;

    private final Supplier<ItemStack> shown;
    private final ItemStack hint;
    private final Runnable press;

    /**
     * @param x     left edge of the 18 pixel frame, in screen pixels
     * @param y     top edge of the frame
     * @param shown what the slot holds right now; {@link ItemStack#EMPTY} for nothing
     * @param hint  what to show faded while it holds nothing, or {@link ItemStack#EMPTY} for no hint
     * @param press what a click does
     */
    public GhostSlot(int x, int y, Supplier<ItemStack> shown, ItemStack hint, Runnable press, Component name) {
        super(x, y, 18, 18, name);
        this.shown = shown;
        this.hint = hint;
        this.press = press;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        Panel.sunken(graphics, x, y, width);
        ItemStack stack = shown.get();
        if (!stack.isEmpty()) {
            graphics.fakeItem(stack, x + 1, y + 1);
        } else if (!hint.isEmpty()) {
            graphics.fakeItem(hint, x + 1, y + 1);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, HINT_VEIL);
        }
        if (isMouseOver(mouseX, mouseY)) {
            // The same white wash vanilla lays over a hovered slot.
            graphics.fill(x + 1, y + 1, x + 17, y + 17, HOVER);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y()) || (event.button() != 0 && event.button() != 1)) {
            return false;
        }
        press.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
