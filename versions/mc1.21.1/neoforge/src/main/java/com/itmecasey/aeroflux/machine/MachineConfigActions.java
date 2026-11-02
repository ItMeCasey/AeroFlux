package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideCommand;

/**
 * Everything the machine screen's settings can make a machine do, with no screen or menu in sight.
 * The menu and the self-test call these, so the rules are written once: a number that is not a
 * command does nothing, a command for a resource the machine does not use does nothing, and a
 * command that changes something saves it and tells the neighbors (see
 * {@link FluxMachineBlockEntity#sidesChanged}). The redstone button shares the same menu button
 * numbers, in a range of its own (see {@link RedstoneMode#buttonId()}).
 */
public final class MachineConfigActions {
    /** The plant grower's seed refill switch; side commands use 0 to 499 and redstone 500 and up. */
    public static final int AUTO_INSERT_BUTTON = 600;
    /** The Auto Import and Auto Export switches in the side window. */
    public static final int AUTO_IMPORT_BUTTON = 601;
    public static final int AUTO_EXPORT_BUTTON = 602;
    /** The Sky Controller's three buttons. */
    public static final int SKY_DAY_BUTTON = 610;
    public static final int SKY_NIGHT_BUTTON = 611;
    public static final int SKY_WEATHER_BUTTON = 612;
    /** The Magnet's whitelist and blacklist switch. */
    public static final int MAGNET_MODE_BUTTON = 620;
    /** Buttons from here up to 699 belong to one kind of machine; see {@link FluxMachineBlockEntity#machineButton}. */
    private static final int MACHINE_BUTTONS = 610;
    private static final int MACHINE_BUTTONS_END = 700;

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
     * Picks a redstone mode.
     *
     * @return true if the mode changed
     */
    public static boolean setRedstoneMode(FluxMachineBlockEntity machine, RedstoneMode mode) {
        return !machine.isRemoved() && machine.setRedstoneMode(mode);
    }

    /**
     * Applies what a menu button number stands for: a redstone mode or a side command.
     *
     * @return false if the number stands for nothing or nothing changed
     */
    public static boolean applyButton(FluxMachineBlockEntity machine, int buttonId) {
        if (buttonId == AUTO_INSERT_BUTTON) {
            machine.toggleAutoInsert();
            return true;
        }
        if (buttonId >= MACHINE_BUTTONS && buttonId < MACHINE_BUTTONS_END) {
            return !machine.isRemoved() && machine.machineButton(buttonId);
        }
        if (buttonId == AUTO_IMPORT_BUTTON) {
            machine.toggleAutoImport();
            return true;
        }
        if (buttonId == AUTO_EXPORT_BUTTON) {
            machine.toggleAutoExport();
            return true;
        }
        RedstoneMode mode = RedstoneMode.fromButton(buttonId);
        if (mode != null) {
            return setRedstoneMode(machine, mode);
        }
        SideCommand command = SideCommand.decode(buttonId);
        return command != null && apply(machine, command);
    }
}
