package com.itmecasey.aeroflux.core.machine;

/**
 * The narrow strip docked to the right edge of a machine screen: the redstone button on top and
 * the upgrade slots in a column under it. Positions are in pixels from the top left corner of the
 * machine panel, like {@link MachineLayout}, because the menu places its upgrade slots with them
 * and the screen draws around those slots.
 *
 * <p>The strip sits just outside the panel, so it never crowds the machine's own slots, and the
 * side-config window docks beyond it when it has to open on the right.
 */
public final class UpgradeStripLayout {
    /** The strip's left edge, a pixel clear of the panel. */
    public static final int X = MachineLayout.WIDTH + 1;
    /** The strip's top edge, level with the side-config toggle on the other side. */
    public static final int Y = 6;
    public static final int WIDTH = 26;
    /** The redstone button is the size of a slot frame, so the column lines up. */
    public static final int BUTTON = MachineLayout.SLOT;

    private static final int PADDING = 4;
    private static final int SLOTS_TOP = PADDING + BUTTON + PADDING;

    private final int slotCount;

    /** A strip with {@code slotCount} upgrade slots. */
    public UpgradeStripLayout(int slotCount) {
        this.slotCount = Math.max(0, slotCount);
    }

    public int slotCount() {
        return slotCount;
    }

    /** The whole strip, frame included. */
    public MachineLayout.Rect bounds() {
        return new MachineLayout.Rect(X, Y, WIDTH, SLOTS_TOP + slotCount * MachineLayout.SLOT + PADDING);
    }

    /** The redstone mode button. */
    public MachineLayout.Rect redstoneButton() {
        return new MachineLayout.Rect(X + PADDING, Y + PADDING, BUTTON, BUTTON);
    }

    /** Where the item in an upgrade slot is drawn; its frame is {@link MachineLayout#frameOf}. */
    public MachineLayout.Point slot(int index) {
        return new MachineLayout.Point(X + PADDING + 1, Y + SLOTS_TOP + index * MachineLayout.SLOT + 1);
    }

    /** The first pixel to the right of the strip, where something docked beyond it may start. */
    public static int right() {
        return X + WIDTH;
    }
}
