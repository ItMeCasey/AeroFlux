package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.machine.MachineStatus;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A small status lamp and a line of text: what the machine is doing, with the exact reason in the
 * tooltip. The lamp color follows the plan (green running, yellow idle, red out of Flux, blue
 * output full) and the text carries the same information, so the color is never the only signal.
 */
public class StatusLine extends AbstractWidget implements HoverInfo {
    private static final int LAMP = 6;

    private MachineStatus status = MachineStatus.IDLE;

    public StatusLine(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("gui.aeroflux.status.name"));
    }

    public void update(MachineStatus status) {
        this.status = status;
    }

    private static int lampColor(MachineStatus status) {
        return switch (status) {
            case RUNNING -> 0xFF4CAF50;
            case IDLE -> 0xFFE0B83A;
            case NO_FLUX -> 0xFFD04545;
            case OUTPUT_FULL -> 0xFF4A7FD8;
        };
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int lampY = getY() + (height - LAMP) / 2;
        graphics.fill(getX(), lampY, getX() + LAMP, lampY + LAMP, 0xFF1B1B1B);
        graphics.fill(getX() + 1, lampY + 1, getX() + LAMP - 1, lampY + LAMP - 1, lampColor(status));
        graphics.drawString(Minecraft.getInstance().font, Component.translatable("machine.aeroflux.status." + status.id()),
                getX() + LAMP + 4, getY() + (height - 8) / 2 + 1, Panel.TEXT, false);
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        return isMouseOver(mouseX, mouseY)
                ? List.of(Component.translatable("machine.aeroflux.status." + status.id() + ".tooltip"))
                : List.of();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
