package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.client.render.FluidRenderUtil;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * A vertical Flux gauge: a framed glass bar with tick marks every 10 percent and the real fluid
 * sprite, tinted, rising from the bottom to the fill level. The level uses {@link FillLevel}, the
 * same rule the tank block uses, so a gauge and the block it belongs to agree. The screen pushes
 * the numbers in with {@link #update} before each frame; the gauge keeps no state of its own.
 */
public class FluxGauge extends AbstractWidget implements HoverInfo {
    private static final int FRAME = 0xFF1B1B1B;
    private static final int GLASS = 0xFF30303A;
    private static final int TICK = 0x80FFFFFF;

    private int amount;
    private int capacity = 1;
    private @Nullable FluxType type;
    /** The fluid of an extra tank (water, Moon or Twilight Flux); when set it wins over {@link #type}. */
    private @Nullable Fluid fixedFluid;
    private int usePerTick;
    private boolean working;

    public FluxGauge(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("gui.aeroflux.gauge.name"));
    }

    /**
     * @param amount     Flux in the tank, in mB
     * @param capacity   the tank size, in mB
     * @param type       the flux in the tank, or {@code null} if empty
     * @param usePerTick Flux the machine burns per tick while working, in mB
     * @param working    whether the machine is burning it right now
     */
    public void update(int amount, int capacity, @Nullable FluxType type, int usePerTick, boolean working) {
        this.fixedFluid = null;
        this.amount = amount;
        this.capacity = Math.max(1, capacity);
        this.type = type;
        this.usePerTick = usePerTick;
        this.working = working;
    }

    /** Shows an extra tank: always the same fluid, and no burn rate, since the machine uses it per job. */
    public void updateFluid(int amount, int capacity, Fluid fluid) {
        this.fixedFluid = fluid;
        this.amount = amount;
        this.capacity = Math.max(1, capacity);
        this.type = null;
        this.usePerTick = 0;
        this.working = false;
    }

    /** The fluid drawn, or {@code null} for an empty Flux tank. */
    private @Nullable Fluid shownFluid() {
        if (fixedFluid != null) {
            return fixedFluid;
        }
        return type == null ? null : ModFluids.fluidOf(type);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, FRAME);
        int innerX = x + 1;
        int innerY = y + 1;
        int innerWidth = width - 2;
        int innerHeight = height - 2;
        graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight, GLASS);

        Fluid fluid = shownFluid();
        if (fluid != null && amount > 0) {
            int filled = Math.max(1, (int) Math.round(FillLevel.fraction(amount, capacity) * innerHeight));
            FluidRenderUtil.drawTiled(graphics, FluidRenderUtil.stillSprite(fluid), FluidRenderUtil.tint(fluid),
                    innerX, innerY + innerHeight - filled, innerWidth, filled);
        }

        // Tick marks every 10 percent, longer at the half.
        for (int tenth = 1; tenth < 10; tenth++) {
            int tickY = innerY + innerHeight - innerHeight * tenth / 10;
            int length = tenth == 5 ? 6 : 3;
            graphics.fill(innerX, tickY, innerX + length, tickY + 1, TICK);
        }
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return List.of();
        }
        Fluid fluid = shownFluid();
        Component name = fluid == null || amount <= 0
                ? Component.translatable("gui.aeroflux.gauge.empty")
                : fluid.getFluidType().getDescription();
        int percent = (int) Math.round(FillLevel.fraction(amount, capacity) * 100.0);
        List<Component> lines = new ArrayList<>();
        lines.add(name);
        lines.add(Component.translatable("gui.aeroflux.gauge.amount", format(amount), format(capacity)));
        lines.add(Component.translatable("gui.aeroflux.gauge.percent", percent));
        if (working) {
            lines.add(Component.translatable("gui.aeroflux.gauge.using", usePerTick));
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
