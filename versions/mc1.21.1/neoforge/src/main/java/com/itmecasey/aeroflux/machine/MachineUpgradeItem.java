package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * An upgrade only machines take: Efficiency, Fortune, Silk Touch or Purity. It goes into a
 * machine's upgrade slots through the machine's screen, and only into machines it does something
 * in (see {@link MachineUpgrades.Special#fits}). Pipes have no use for it.
 */
public class MachineUpgradeItem extends Item {
    private final MachineUpgrades.Special kind;

    public MachineUpgradeItem(MachineUpgrades.Special kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    /** What this upgrade does. */
    public MachineUpgrades.Special kind() {
        return kind;
    }

    /** Says what the upgrade does and which machines take it. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String key = "tooltip.aeroflux.machine_upgrade." + kind.id();
        lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(key + ".fits").withStyle(ChatFormatting.DARK_GRAY));
    }
}
