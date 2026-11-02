package com.itmecasey.aeroflux.core.configurator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideConfigs;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MachineSettingsTest {
    /** A machine with no game behind it. */
    static final class FakeMachine implements Configurable {
        final String kind;
        Face facing;
        final SideConfigs sides;
        RedstoneMode redstone = RedstoneMode.ALWAYS;
        boolean autoImport;
        boolean autoExport;
        final boolean hasAutoInsert;
        boolean autoInsert;
        final boolean hasMachineMode;
        int machineMode;
        int saves;

        FakeMachine(String kind, Face facing, Set<SideResource> used, boolean hasAutoInsert, boolean hasMachineMode) {
            this.kind = kind;
            this.facing = facing;
            this.sides = new SideConfigs(used);
            this.hasAutoInsert = hasAutoInsert;
            this.hasMachineMode = hasMachineMode;
        }

        static FakeMachine furnace(Face facing) {
            return new FakeMachine("furnace", facing, EnumSet.of(SideResource.ITEMS, SideResource.FLUID), false, false);
        }

        @Override public String kind() { return kind; }
        @Override public Face facing() { return facing; }
        @Override public SideConfigs sides() { return sides; }
        @Override public RedstoneMode redstoneMode() { return redstone; }
        @Override public void setRedstoneMode(RedstoneMode mode) { redstone = mode; }
        @Override public boolean autoImport() { return autoImport; }
        @Override public void setAutoImport(boolean on) { autoImport = on; }
        @Override public boolean autoExport() { return autoExport; }
        @Override public void setAutoExport(boolean on) { autoExport = on; }
        @Override public boolean hasAutoInsert() { return hasAutoInsert; }
        @Override public boolean autoInsert() { return autoInsert; }
        @Override public void setAutoInsert(boolean on) { autoInsert = on; }
        @Override public boolean hasMachineMode() { return hasMachineMode; }
        @Override public int machineMode() { return machineMode; }
        @Override public void setMachineMode(int mode) { machineMode = mode; }
        @Override public void settingsChanged() { saves++; }

        void set(SideResource resource, FaceLabel label, ConnectionMode mode) {
            sides.apply(com.itmecasey.aeroflux.core.machine.SideCommand.set(resource, label.faceFor(facing), mode));
        }

        ConnectionMode at(SideResource resource, FaceLabel label) {
            return sides.mode(resource, label.faceFor(facing));
        }
    }

    private static FakeMachine configuredFurnace(Face facing) {
        FakeMachine machine = FakeMachine.furnace(facing);
        machine.set(SideResource.ITEMS, FaceLabel.BACK, ConnectionMode.IMPORT);
        machine.set(SideResource.ITEMS, FaceLabel.LEFT, ConnectionMode.EXPORT);
        machine.set(SideResource.ITEMS, FaceLabel.FRONT, ConnectionMode.BLOCKED);
        machine.set(SideResource.FLUID, FaceLabel.TOP, ConnectionMode.OFF);
        machine.redstone = RedstoneMode.HIGH;
        machine.autoImport = true;
        return machine;
    }

    @Test
    void aPasteCopiesEveryFieldOntoTheSameKind() {
        FakeMachine source = configuredFurnace(Face.NORTH);
        FakeMachine target = FakeMachine.furnace(Face.NORTH);
        MachineSettings.PasteReport report = MachineSettings.capture(source).applyTo(target);

        assertTrue(report.changed());
        assertTrue(report.skipped().isEmpty());
        assertEquals(source.sides, target.sides);
        assertEquals(RedstoneMode.HIGH, target.redstone);
        assertTrue(target.autoImport);
        assertFalse(target.autoExport);
        assertEquals(1, target.saves, "the target is saved once");
    }

    @Test
    void faceSettingsFollowTheTargetsFacing() {
        FakeMachine source = configuredFurnace(Face.NORTH);
        FakeMachine target = FakeMachine.furnace(Face.EAST);
        MachineSettings.capture(source).applyTo(target);

        for (FaceLabel label : FaceLabel.values()) {
            assertEquals(source.at(SideResource.ITEMS, label), target.at(SideResource.ITEMS, label), label.name());
        }
        // The source's back is south; the target faces east, so its back is west.
        assertEquals(ConnectionMode.IMPORT, target.sides.mode(SideResource.ITEMS, Face.WEST));
        assertEquals(ConnectionMode.BLOCKED, target.sides.mode(SideResource.ITEMS, Face.EAST));
    }

    @Test
    void pastingTheSameSettingsAgainChangesNothing() {
        FakeMachine source = configuredFurnace(Face.SOUTH);
        FakeMachine target = FakeMachine.furnace(Face.WEST);
        MachineSettings settings = MachineSettings.capture(source);
        settings.applyTo(target);
        MachineSettings.PasteReport again = settings.applyTo(target);
        assertFalse(again.changed());
        assertEquals(1, target.saves);
    }

    @Test
    void fieldsTheTargetLacksAreSkippedAndListed() {
        FakeMachine grower = new FakeMachine("plant_grower", Face.NORTH,
                EnumSet.of(SideResource.ITEMS, SideResource.FLUID, SideResource.ENERGY), true, false);
        grower.autoInsert = true;
        FakeMachine furnace = FakeMachine.furnace(Face.NORTH);
        MachineSettings.PasteReport report = MachineSettings.capture(grower).applyTo(furnace);

        assertEquals(EnumSet.of(Setting.ENERGY_SIDES, Setting.AUTO_INSERT), report.skipped());
        assertTrue(report.applied().contains(Setting.ITEM_SIDES));
        assertTrue(report.applied().contains(Setting.REDSTONE_MODE));
    }

    @Test
    void aMachineSwitchOnlyCarriesToTheSameKind() {
        FakeMachine magnet = new FakeMachine("magnet", Face.NORTH, EnumSet.of(SideResource.ITEMS), false, true);
        magnet.machineMode = 1;
        FakeMachine otherMagnet = new FakeMachine("magnet", Face.NORTH, EnumSet.of(SideResource.ITEMS), false, true);
        FakeMachine spawner = new FakeMachine("spawner", Face.NORTH, EnumSet.of(SideResource.ITEMS), false, true);
        MachineSettings settings = MachineSettings.capture(magnet);

        settings.applyTo(otherMagnet);
        assertEquals(1, otherMagnet.machineMode);
        MachineSettings.PasteReport report = settings.applyTo(spawner);
        assertEquals(0, spawner.machineMode);
        assertTrue(report.skipped().contains(Setting.MACHINE_MODE));
    }

    @Test
    void contentsListWhatWasCopied() {
        FakeMachine grower = new FakeMachine("plant_grower", Face.NORTH,
                EnumSet.of(SideResource.ITEMS, SideResource.FLUID), true, false);
        assertEquals(EnumSet.of(Setting.ITEM_SIDES, Setting.FLUID_SIDES, Setting.REDSTONE_MODE, Setting.AUTO_IMPORT,
                Setting.AUTO_EXPORT, Setting.AUTO_INSERT), MachineSettings.capture(grower).contents());
    }

    @Test
    void theTextFormRoundTrips() {
        FakeMachine magnet = new FakeMachine("magnet", Face.WEST, EnumSet.of(SideResource.ITEMS, SideResource.FLUID), true, true);
        magnet.machineMode = 1;
        magnet.autoExport = true;
        magnet.redstone = RedstoneMode.PULSE;
        magnet.set(SideResource.FLUID, FaceLabel.RIGHT, ConnectionMode.BLOCKED);
        MachineSettings settings = MachineSettings.capture(magnet);

        String text = settings.encode();
        assertEquals(settings, MachineSettings.decode(text));
        assertTrue(text.startsWith("v1;kind=magnet;rs=pulse;imp=0;exp=1;ins=0;mm=1;"), text);
    }

    @Test
    void damagedTextIsRefused() {
        assertNull(MachineSettings.decode(null));
        assertNull(MachineSettings.decode(""));
        assertNull(MachineSettings.decode("v9;kind=furnace"));
        assertNull(MachineSettings.decode("v1;rs=high"));
        assertNull(MachineSettings.decode("v1;kind=furnace;items=IIII"));
        assertNull(MachineSettings.decode("v1;kind=furnace;items=IIIIIZ"));
        assertNull(MachineSettings.decode("v1;kind=furnace;mm=x"));
        assertNull(MachineSettings.decode("v1;kind=furnace;nonsense"));
    }

    @Test
    void anUnknownRedstoneModeReadsAsAlways() {
        MachineSettings settings = MachineSettings.decode("v1;kind=furnace;rs=sideways;imp=1;exp=0");
        assertEquals(RedstoneMode.ALWAYS, settings.redstoneMode());
        assertTrue(settings.autoImport());
    }
}
