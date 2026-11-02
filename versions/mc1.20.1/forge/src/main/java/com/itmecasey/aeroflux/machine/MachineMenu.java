package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The menu of all eight machines. What differs between them (the slots) comes from the machine
 * type, and which items each slot accepts comes from {@link MachineInputs}, so no per-machine
 * menu class is needed.
 */
public class MachineMenu extends FluxMachineMenu {
    /** Server side: a menu reading live from a machine. */
    public MachineMenu(int containerId, Inventory inventory, FluxMachineBlockEntity entity) {
        super(ModMenus.machine(entity.machine()).get(), containerId, inventory,
                new OpenData(entity.getBlockPos(), entity.machine(), entity.tier(), entity.facing()), entity);
    }

    private MachineMenu(int containerId, Inventory inventory, OpenData open) {
        super(ModMenus.machine(open.machine()).get(), containerId, inventory, open, null);
    }

    /** Builds the client's copy from what {@link FluxMachineBlockEntity#writeOpenData} wrote. */
    public static MachineMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new MachineMenu(containerId, inventory, readOpenData(buf));
    }

    @Override
    protected boolean acceptsInput(int slot, ItemStack stack) {
        return MachineInputs.accepts(machine(), level(), slot, stack);
    }
}
