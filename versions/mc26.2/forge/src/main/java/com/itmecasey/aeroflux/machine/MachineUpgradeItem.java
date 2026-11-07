package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

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

    /**
     * Says what the upgrade does and which machines take it. Vanilla marked this hook deprecated in
     * favor of tooltip data components, but the text depends only on the item, and the hook is still called.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        String key = "tooltip.aeroflux.machine_upgrade." + kind.id();
        lines.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable(key + ".fits").withStyle(ChatFormatting.DARK_GRAY));
    }
}
