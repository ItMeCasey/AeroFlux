package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The small redstone control button at the top of the machine screen's right-hand strip. Its icon
 * shows the current mode (gunpowder for "always on", dust for "on with signal", a torch for "off
 * with signal", a repeater for "pulse"); a click asks the server for the next mode and a
 * right-click for the one before. Like the side-config window it decides nothing and only shows
 * the mode the server last reported.
 */
public class RedstoneButton extends AbstractWidget implements HoverInfo {
    private static final ItemStack ALWAYS_ICON = new ItemStack(Items.GUNPOWDER);
    private static final ItemStack HIGH_ICON = new ItemStack(Items.REDSTONE);
    private static final ItemStack LOW_ICON = new ItemStack(Items.REDSTONE_TORCH);
    private static final ItemStack PULSE_ICON = new ItemStack(Items.REPEATER);

    private final Supplier<RedstoneMode> mode;
    private final IntConsumer send;

    /**
     * @param mode where to read the synced mode
     * @param send sends a menu button number to the server
     */
    public RedstoneButton(int x, int y, int size, Supplier<RedstoneMode> mode, IntConsumer send) {
        super(x, y, size, size, Component.translatable("gui.aeroflux.redstone.name"));
        this.mode = mode;
        this.send = send;
    }

    private static ItemStack icon(RedstoneMode mode) {
        return switch (mode) {
            case ALWAYS -> ALWAYS_ICON;
            case HIGH -> HIGH_ICON;
            case LOW -> LOW_ICON;
            case PULSE -> PULSE_ICON;
        };
    }

    private static Component modeName(RedstoneMode mode) {
        return Component.translatable("redstone_mode.aeroflux." + mode.id());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Panel.raised(graphics, getX(), getY(), width, height);
        if (isMouseOver(mouseX, mouseY)) {
            graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0x40FFFFFF);
        }
        graphics.item(icon(mode.get()), getX() + (width - 16) / 2, getY() + (height - 16) / 2);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (!isMouseOver(event.x(), event.y()) || (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT)) {
            return false;
        }
        send.accept(mode.get().cycle(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT).buttonId());
        return true;
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return List.of();
        }
        RedstoneMode current = mode.get();
        return List.of(Component.translatable("gui.aeroflux.redstone.mode", modeName(current)));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
