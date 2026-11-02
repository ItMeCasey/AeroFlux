package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.machine.MachineMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen of every machine. The shared base lays out everything from the menu (slots, extra
 * tank gauges, and the few buttons one kind of machine has), so there is no screen per machine.
 */
public class MachineScreen extends FluxMachineScreen<MachineMenu> {
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
