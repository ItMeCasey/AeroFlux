package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.configurator.Configurable;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideConfigs;

/**
 * A machine as the Flux Configurator sees it. The machine itself only offers switches that flip,
 * the way its screen's buttons work, so the setters here flip a switch only when it is not
 * already where the paste wants it.
 */
public record MachineConfigurable(FluxMachineBlockEntity machine) implements Configurable {
    @Override
    public String kind() {
        return machine.machine().id();
    }

    @Override
    public Face facing() {
        return machine.facing();
    }

    @Override
    public SideConfigs sides() {
        return machine.sides();
    }

    @Override
    public RedstoneMode redstoneMode() {
        return machine.redstoneMode();
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        machine.setRedstoneMode(mode);
    }

    @Override
    public boolean autoImport() {
        return machine.autoImport();
    }

    @Override
    public void setAutoImport(boolean on) {
        if (machine.autoImport() != on) {
            machine.toggleAutoImport();
        }
    }

    @Override
    public boolean autoExport() {
        return machine.autoExport();
    }

    @Override
    public void setAutoExport(boolean on) {
        if (machine.autoExport() != on) {
            machine.toggleAutoExport();
        }
    }

    @Override
    public boolean hasAutoInsert() {
        return machine.machine() == MachineType.PLANT_GROWER;
    }

    @Override
    public boolean autoInsert() {
        return machine.autoInsert();
    }

    @Override
    public void setAutoInsert(boolean on) {
        if (machine.autoInsert() != on) {
            machine.toggleAutoInsert();
        }
    }

    /** Only the Magnet has a switch of its own that is a setting; the Sky Controller's buttons are actions. */
    @Override
    public boolean hasMachineMode() {
        return machine.machine() == MachineType.MAGNET;
    }

    @Override
    public int machineMode() {
        return machine.machineMode();
    }

    @Override
    public void setMachineMode(int mode) {
        if (machine.machineMode() != mode) {
            machine.machineButton(MachineConfigActions.MAGNET_MODE_BUTTON);
        }
    }

    @Override
    public void settingsChanged() {
        machine.sidesChanged();
    }
}
