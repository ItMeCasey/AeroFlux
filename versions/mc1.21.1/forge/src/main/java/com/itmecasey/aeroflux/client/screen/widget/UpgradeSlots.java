package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.UpgradeStripLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The upgrade column on the machine screen's right-hand strip. The slots themselves belong to the
 * menu, so this only dresses them: a faded ghost of an upgrade in every empty slot (Speed and
 * Capacity take turns, since either fits) and a tooltip with what the installed upgrades add up
 * to. It is added as a renderable only, so clicks go straight to the slots underneath.
 */
public class UpgradeSlots extends AbstractWidget implements HoverInfo {
    /** What the widget needs from the screen. */
    public interface Model {
        /** Whether an upgrade slot holds an upgrade. */
        boolean filled(int slot);

        int speedUpgrades();

        int capacityUpgrades();

        /** The kind of machine, which decides the machine-only upgrades it takes. */
        MachineType machine();

        /** How many machine-only upgrades of one kind are installed. */
        int upgrades(MachineUpgrades.Special kind);
    }

    /** Laid over a ghost item so it reads as a hint, the way vanilla's recipe ghosts do. */
    private static final int GHOST_VEIL = 0xA08B8B8B;
    /** How long each ghost icon shows before the other one, in milliseconds. */
    private static final long GHOST_SWAP_MS = 1_200;

    private final UpgradeStripLayout strip;
    private final Tier tier;
    private final Model model;
    private final int panelX;
    private final int panelY;
    private final List<ItemStack> ghosts;

    /**
     * @param panelX the machine panel's left edge, in screen pixels
     * @param panelY the machine panel's top edge
     * @param ghosts the upgrade items to show in empty slots, in turn
     */
    public UpgradeSlots(int panelX, int panelY, UpgradeStripLayout strip, Tier tier, Model model, List<ItemStack> ghosts) {
        super(panelX + strip.bounds().x(), panelY + strip.bounds().y(), strip.bounds().width(), strip.bounds().height(),
                Component.translatable("gui.aeroflux.upgrades.name"));
        this.strip = strip;
        this.tier = tier;
        this.model = model;
        this.panelX = panelX;
        this.panelY = panelY;
        this.ghosts = List.copyOf(ghosts);
    }

    /** The strip's raised background. The screen calls this before it draws the slot frames on top. */
    public void drawBackground(GuiGraphics graphics) {
        Panel.raised(graphics, getX(), getY(), width, height);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (ghosts.isEmpty()) {
            return;
        }
        ItemStack ghost = ghosts.get((int) (System.currentTimeMillis() / GHOST_SWAP_MS % ghosts.size()));
        for (int i = 0; i < strip.slotCount(); i++) {
            if (!model.filled(i)) {
                int x = panelX + strip.slot(i).x();
                int y = panelY + strip.slot(i).y();
                graphics.renderFakeItem(ghost, x, y);
                graphics.fill(x, y, x + 16, y + 16, GHOST_VEIL);
            }
        }
    }

    /** The slot whose frame is under the pointer, or -1. */
    private int slotAt(int mouseX, int mouseY) {
        for (int i = 0; i < strip.slotCount(); i++) {
            if (MachineLayout.frameOf(strip.slot(i)).contains(mouseX - panelX, mouseY - panelY)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The totals, shown over the strip and over empty slots. A filled slot shows its item's own
     * tooltip instead, so the two never fight over the same spot.
     */
    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        boolean overStrip = mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height;
        int slot = slotAt(mouseX, mouseY);
        boolean overButton = strip.redstoneButton().contains(mouseX - panelX, mouseY - panelY);
        if (!overStrip || overButton || (slot >= 0 && model.filled(slot))) {
            return List.of();
        }
        int speed = model.speedUpgrades();
        int capacity = model.capacityUpgrades();
        int efficiency = model.upgrades(MachineUpgrades.Special.EFFICIENCY);
        int installed = speed + capacity;
        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
            installed += model.upgrades(kind);
        }
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.aeroflux.upgrades.title", installed, strip.slotCount()));
        lines.add(Component.translatable("gui.aeroflux.upgrades.speed", speed, MachineUpgrades.speedPercent(speed),
                MachineUpgrades.fluxPerTick(tier, speed, efficiency)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.aeroflux.upgrades.capacity", capacity,
                numbers.format(StorageSizes.machine(tier, capacity))).withStyle(ChatFormatting.GRAY));
        MachineType machine = model.machine();
        List<Component> takes = new ArrayList<>();
        takes.add(Component.translatable("upgrade.aeroflux.speed"));
        takes.add(Component.translatable("upgrade.aeroflux.capacity"));
        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
            if (kind.fits(machine)) {
                lines.add(specialLine(kind, machine, model.upgrades(kind)).withStyle(ChatFormatting.GRAY));
                takes.add(Component.translatable("upgrade.aeroflux." + kind.id()));
            }
        }
        lines.add(Component.translatable("gui.aeroflux.upgrades.hint", ComponentUtils.formatList(takes, Component.literal(", ")))
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** The tooltip line for a machine-only upgrade: how many are in and what they add up to. */
    private static MutableComponent specialLine(MachineUpgrades.Special kind, MachineType machine, int count) {
        String key = "gui.aeroflux.upgrades." + kind.id();
        return switch (kind) {
            case EFFICIENCY -> Component.translatable(key, count, MachineUpgrades.efficiencyPercent(count));
            case FORTUNE -> machine == MachineType.SIFTER
                    ? Component.translatable(key + ".sifter", count, count * MachineUpgrades.SIFTER_FORTUNE_POINTS)
                    : Component.translatable(key, count, MachineUpgrades.FORTUNE_CHANCE);
            case SILK_TOUCH -> Component.translatable(count > 0 ? key + ".on" : key + ".off");
            case PURITY -> Component.translatable(key, count, MachineUpgrades.PURITY_CHANCE);
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
