package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import net.minecraft.world.item.Item;

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
}
