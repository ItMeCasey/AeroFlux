package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.machine.MachineMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen of all eight machines. The shared base already lays out everything from the menu, so
 * a machine only differs in its slots and recipes, not in its screen.
 */
public class MachineScreen extends FluxMachineScreen<MachineMenu> {
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
