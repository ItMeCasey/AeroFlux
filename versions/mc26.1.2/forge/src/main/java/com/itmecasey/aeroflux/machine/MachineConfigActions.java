package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.SideCommand;

/**
 * Everything the side-config window can make a machine do, with no screen or menu in sight. The
 * menu and the self-test call these, so the rules are written once: a number that is not a command
 * does nothing, a command for a resource the machine does not use does nothing, and a command that
 * changes something saves it and tells the neighbors (see {@link FluxMachineBlockEntity#sidesChanged}).
 */
public final class MachineConfigActions {
    private MachineConfigActions() {
    }

    /**
     * Applies a command.
     *
     * @return true if any face changed
     */
    public static boolean apply(FluxMachineBlockEntity machine, SideCommand command) {
        if (machine.isRemoved() || !machine.sides().apply(command)) {
            return false;
        }
        machine.sidesChanged();
        return true;
    }

    /**
     * Applies the command a menu button number stands for.
     *
     * @return false if the number is not a command or the command changed nothing
     */
    public static boolean applyButton(FluxMachineBlockEntity machine, int buttonId) {
        SideCommand command = SideCommand.decode(buttonId);
        return command != null && apply(machine, command);
    }
}
