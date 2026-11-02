package com.itmecasey.aeroflux.core.configurator;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideConfig;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A copy of one machine's settings, as the Flux Configurator holds it: the face modes of every
 * resource the machine uses, its redstone mode, Auto Import and Auto Export, and the switches only
 * some machines have.
 *
 * <p>Face modes are kept by what the machine calls the face (front, left, top and so on), not by
 * compass direction, so a setup copied from a machine facing north lands the same way on one
 * facing east: whatever went into the back still goes into the back.
 *
 * <p>Instances never change. They turn into a short line of text and back (see {@link #encode}),
 * which is how the item stores them; the text is readable, so a curious player looking at the
 * item's data can follow it.
 */
public final class MachineSettings {
    /** The first word of every encoded copy; a different word means a format this code cannot read. */
    private static final String FORMAT = "v1";
    /** One letter per mode, in {@link ConnectionMode} order: Open, Import, Export, Off, Blocked. */
    private static final String MODE_LETTERS = "OIEFB";

    private final String kind;
    private final Map<SideResource, ConnectionMode[]> sides;
    private final RedstoneMode redstone;
    private final boolean autoImport;
    private final boolean autoExport;
    /** {@code null} when the machine has no seed refill switch. */
    private final Boolean autoInsert;
    /** {@code null} when the machine has no switch of its own. */
    private final Integer machineMode;

    private MachineSettings(String kind, Map<SideResource, ConnectionMode[]> sides, RedstoneMode redstone,
                            boolean autoImport, boolean autoExport, Boolean autoInsert, Integer machineMode) {
        this.kind = kind;
        this.sides = sides;
        this.redstone = redstone;
        this.autoImport = autoImport;
        this.autoExport = autoExport;
        this.autoInsert = autoInsert;
        this.machineMode = machineMode;
    }

    /** Reads the settings of a block. */
    public static MachineSettings capture(Configurable source) {
        Map<SideResource, ConnectionMode[]> sides = new EnumMap<>(SideResource.class);
        Face facing = source.facing();
        for (SideResource resource : source.sides().used()) {
            ConnectionMode[] byLabel = new ConnectionMode[FaceLabel.values().length];
            for (FaceLabel label : FaceLabel.values()) {
                byLabel[label.ordinal()] = source.sides().mode(resource, label.faceFor(facing));
            }
            sides.put(resource, byLabel);
        }
        return new MachineSettings(source.kind(), sides, source.redstoneMode(), source.autoImport(), source.autoExport(),
                source.hasAutoInsert() ? source.autoInsert() : null,
                source.hasMachineMode() ? source.machineMode() : null);
    }

    /** The kind of block the settings were copied from. */
    public String kind() {
        return kind;
    }

    public RedstoneMode redstoneMode() {
        return redstone;
    }

    public boolean autoImport() {
        return autoImport;
    }

    public boolean autoExport() {
        return autoExport;
    }

    /** The mode at a face, named the way the machine names it; {@code null} if the copy has no settings for the resource. */
    public ConnectionMode mode(SideResource resource, FaceLabel label) {
        ConnectionMode[] byLabel = sides.get(resource);
        return byLabel == null ? null : byLabel[label.ordinal()];
    }

    /** Everything this copy holds, in a fixed order, for the summary the player sees. */
    public Set<Setting> contents() {
        Set<Setting> contents = EnumSet.noneOf(Setting.class);
        for (SideResource resource : sides.keySet()) {
            contents.add(Setting.sidesOf(resource));
        }
        contents.add(Setting.REDSTONE_MODE);
        contents.add(Setting.AUTO_IMPORT);
        contents.add(Setting.AUTO_EXPORT);
        if (autoInsert != null) {
            contents.add(Setting.AUTO_INSERT);
        }
        if (machineMode != null) {
            contents.add(Setting.MACHINE_MODE);
        }
        return contents;
    }

    /**
     * What one paste did.
     *
     * @param applied the settings the target took, whether or not they differed from before
     * @param skipped the settings the target has no use for
     * @param changed whether anything about the target is different now
     */
    public record PasteReport(Set<Setting> applied, Set<Setting> skipped, boolean changed) {
    }

    /**
     * Writes these settings onto a block. Face settings for resources the target does not use are
     * skipped, and so are machine-only switches, unless the target is the same kind of machine:
     * the Magnet's whitelist means nothing to a furnace. The target is told once, at the end, if
     * anything changed.
     */
    public PasteReport applyTo(Configurable target) {
        Set<Setting> applied = EnumSet.noneOf(Setting.class);
        Set<Setting> skipped = EnumSet.noneOf(Setting.class);
        boolean changed = false;

        Face facing = target.facing();
        for (Map.Entry<SideResource, ConnectionMode[]> entry : sides.entrySet()) {
            SideResource resource = entry.getKey();
            if (!target.sides().uses(resource)) {
                skipped.add(Setting.sidesOf(resource));
                continue;
            }
            SideConfig config = new SideConfig(ConnectionMode.OFF);
            for (FaceLabel label : FaceLabel.values()) {
                config.set(label.faceFor(facing), entry.getValue()[label.ordinal()]);
            }
            changed |= target.sides().packed(resource) != config.pack();
            target.sides().restore(resource, config.pack());
            applied.add(Setting.sidesOf(resource));
        }

        if (target.redstoneMode() != redstone) {
            target.setRedstoneMode(redstone);
            changed = true;
        }
        applied.add(Setting.REDSTONE_MODE);
        if (target.autoImport() != autoImport) {
            target.setAutoImport(autoImport);
            changed = true;
        }
        applied.add(Setting.AUTO_IMPORT);
        if (target.autoExport() != autoExport) {
            target.setAutoExport(autoExport);
            changed = true;
        }
        applied.add(Setting.AUTO_EXPORT);

        if (autoInsert != null) {
            if (target.hasAutoInsert()) {
                if (target.autoInsert() != autoInsert) {
                    target.setAutoInsert(autoInsert);
                    changed = true;
                }
                applied.add(Setting.AUTO_INSERT);
            } else {
                skipped.add(Setting.AUTO_INSERT);
            }
        }
        if (machineMode != null) {
            if (target.hasMachineMode() && target.kind().equals(kind)) {
                if (target.machineMode() != machineMode) {
                    target.setMachineMode(machineMode);
                    changed = true;
                }
                applied.add(Setting.MACHINE_MODE);
            } else {
                skipped.add(Setting.MACHINE_MODE);
            }
        }

        if (changed) {
            target.settingsChanged();
        }
        return new PasteReport(applied, skipped, changed);
    }

    // --- Text form ---------------------------------------------------------------------------

    /**
     * The settings as one line of text, such as
     * {@code v1;kind=furnace;rs=high;imp=1;exp=0;items=IEOOIE;fluid=IIIIII}. The six letters of a
     * resource are its front, back, left, right, top and bottom faces.
     */
    public String encode() {
        StringBuilder out = new StringBuilder(FORMAT);
        out.append(";kind=").append(kind);
        out.append(";rs=").append(redstone.id());
        out.append(";imp=").append(autoImport ? 1 : 0);
        out.append(";exp=").append(autoExport ? 1 : 0);
        if (autoInsert != null) {
            out.append(";ins=").append(autoInsert ? 1 : 0);
        }
        if (machineMode != null) {
            out.append(";mm=").append(machineMode);
        }
        for (Map.Entry<SideResource, ConnectionMode[]> entry : sides.entrySet()) {
            out.append(';').append(entry.getKey().id()).append('=');
            for (ConnectionMode mode : entry.getValue()) {
                out.append(MODE_LETTERS.charAt(mode.ordinal()));
            }
        }
        return out.toString();
    }

    /**
     * Reads text made by {@link #encode}.
     *
     * @return the settings, or {@code null} if the text is not a copy this version can read
     */
    public static MachineSettings decode(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(";");
        if (parts.length < 2 || !FORMAT.equals(parts[0])) {
            return null;
        }
        String kind = null;
        RedstoneMode redstone = RedstoneMode.ALWAYS;
        boolean autoImport = false;
        boolean autoExport = false;
        Boolean autoInsert = null;
        Integer machineMode = null;
        Map<SideResource, ConnectionMode[]> sides = new EnumMap<>(SideResource.class);
        for (int i = 1; i < parts.length; i++) {
            int split = parts[i].indexOf('=');
            if (split <= 0) {
                return null;
            }
            String key = parts[i].substring(0, split);
            String value = parts[i].substring(split + 1);
            switch (key) {
                case "kind" -> kind = value;
                case "rs" -> redstone = RedstoneMode.byId(value);
                case "imp" -> autoImport = "1".equals(value);
                case "exp" -> autoExport = "1".equals(value);
                case "ins" -> autoInsert = "1".equals(value);
                case "mm" -> {
                    try {
                        machineMode = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                default -> {
                    SideResource resource = resourceById(key);
                    ConnectionMode[] modes = resource == null ? null : modes(value);
                    if (modes == null) {
                        return null;
                    }
                    sides.put(resource, modes);
                }
            }
        }
        if (kind == null || kind.isEmpty()) {
            return null;
        }
        return new MachineSettings(kind, sides, redstone, autoImport, autoExport, autoInsert, machineMode);
    }

    private static SideResource resourceById(String id) {
        for (SideResource resource : SideResource.values()) {
            if (resource.id().equals(id)) {
                return resource;
            }
        }
        return null;
    }

    private static ConnectionMode[] modes(String letters) {
        if (letters.length() != FaceLabel.values().length) {
            return null;
        }
        ConnectionMode[] modes = new ConnectionMode[letters.length()];
        for (int i = 0; i < letters.length(); i++) {
            int index = MODE_LETTERS.indexOf(letters.charAt(i));
            if (index < 0) {
                return null;
            }
            modes[i] = ConnectionMode.values()[index];
        }
        return modes;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MachineSettings that)) {
            return false;
        }
        if (!kind.equals(that.kind) || redstone != that.redstone || autoImport != that.autoImport
                || autoExport != that.autoExport || !Objects.equals(autoInsert, that.autoInsert)
                || !Objects.equals(machineMode, that.machineMode) || !sides.keySet().equals(that.sides.keySet())) {
            return false;
        }
        for (Map.Entry<SideResource, ConnectionMode[]> entry : sides.entrySet()) {
            if (!Arrays.equals(entry.getValue(), that.sides.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return encode().hashCode();
    }

    @Override
    public String toString() {
        return encode();
    }
}
