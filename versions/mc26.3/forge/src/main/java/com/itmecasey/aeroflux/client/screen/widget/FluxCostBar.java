package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.machine.FluxCost;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A thin bar over the progress arrow that weighs the Flux in the tank against what the current job
 * costs: it fills as far as the tank pays for one whole job, so a full bar means at least one job's
 * worth is in the tank. When the tank cannot pay for the next tick the bar turns red and pulses,
 * because that is when the machine stalls. The numbers come from the server through the menu.
 */
public class FluxCostBar extends AbstractWidget implements HoverInfo {
    private static final int FACE = 0xFF2A2A30;
    private static final int FILL = 0xFFF2C94C;
    private static final int SHORT = 0xFFD83A3A;
    private static final int SHORT_DIM = 0xFF8A2A2A;
    /** How long one red pulse takes, in milliseconds. */
    private static final long PULSE_MILLIS = 800;

    private int amount;
    private int perOperation;
    private int nextTick;

    public FluxCostBar(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("gui.aeroflux.cost.name"));
    }

    /**
     * @param amount       Flux in the tank, in mB
     * @param perOperation what the current job takes from start to finish, in mB; 0 with no job
     * @param nextTick     what the next tick takes, in mB
     */
    public void update(int amount, int perOperation, int nextTick) {
        this.amount = amount;
        this.perOperation = perOperation;
        this.nextTick = nextTick;
    }

    /** Whether there is a job the tank cannot pay the next tick of. */
    private boolean cannotPayNextTick() {
        return perOperation > 0 && !FluxCost.coversNextTick(amount, nextTick);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        Panel.sunken(graphics, x, y, width, height, FACE);
        int inner = width - 2;
        if (cannotPayNextTick()) {
            // The whole bar pulses between two reds, so a stalled machine is easy to spot.
            double phase = (Util.getMillis() % PULSE_MILLIS) / (double) PULSE_MILLIS;
            int color = phase < 0.5 ? SHORT : SHORT_DIM;
            graphics.fill(x + 1, y + 1, x + 1 + inner, y + height - 1, color);
            return;
        }
        int filled = (int) Math.round(FluxCost.coverFraction(amount, perOperation) * inner);
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, FILL);
        }
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(getMessage());
        if (perOperation <= 0) {
            lines.add(Component.translatable("gui.aeroflux.cost.no_job").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable("gui.aeroflux.cost.per_operation", format(perOperation)));
        lines.add(Component.translatable("gui.aeroflux.cost.in_tank", format(amount),
                FluxCost.operationsCovered(amount, perOperation)));
        lines.add(Component.translatable("gui.aeroflux.cost.next_tick", format(nextTick)));
        if (cannotPayNextTick()) {
            lines.add(Component.translatable("gui.aeroflux.cost.short").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private static String format(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
