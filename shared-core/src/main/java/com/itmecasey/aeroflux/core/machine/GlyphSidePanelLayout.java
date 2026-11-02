package com.itmecasey.aeroflux.core.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the glyph-only side-config window: the same job as {@link SidePanelLayout}, but
 * for the redesign where the window has no words in it, only square tabs with a resource glyph,
 * an unfolded cube of solid colored faces and three square glyph buttons. Numbers are in pixels
 * from the window's top left corner and follow {@code assets-src/tools/ui_mockups.py}.
 *
 * <p>The older {@link SidePanelLayout} stays as it is, because the builds that still draw the
 * text version of the window compile against it.
 *
 * <p>From top to bottom: a row of square tabs, the cube (top above front; left, front and right in
 * a row; bottom below front and back below right), the All Off, Copy and Reset buttons, and under
 * them the Auto Import and Auto Export switches.
 */
public final class GlyphSidePanelLayout {
    public static final int WIDTH = 96;
    public static final int HEIGHT = 152;
    /** The side of the square button that opens and closes the window. */
    public static final int TOGGLE = SidePanelLayout.TOGGLE;
    /** Distance between two faces of the cube; each face box is one pixel smaller. */
    public static final int CELL = 22;
    /** The side of a face box. */
    public static final int FACE = CELL - 1;
    /** The side of the three square buttons under the cube. */
    public static final int BUTTON = 22;
    /**
     * The largest a tab may be. Five tabs share the width at exactly this size; with fewer the
     * tabs keep this size and sit in the middle, so the cube and buttons still fit the window.
     */
    public static final int MAX_TAB = 17;

    private static final int PADDING = 4;
    private static final int TAB_Y = 4;
    private static final int CUBE_GAP = 6;
    private static final int BUTTON_GAP = 5;
    private static final int BUTTON_PITCH = 26;

    private final int tabCount;
    private final int tabSize;

    /** A window with one tab for each of {@code tabCount} resources. */
    public GlyphSidePanelLayout(int tabCount) {
        this.tabCount = Math.max(1, tabCount);
        this.tabSize = Math.min(MAX_TAB, (WIDTH - 2 * PADDING) / this.tabCount);
    }

    public int tabCount() {
        return tabCount;
    }

    /** The side of every tab. */
    public int tabSize() {
        return tabSize;
    }

    /** The square tab for a resource number; the row of tabs is centered. */
    public MachineLayout.Rect tab(int index) {
        int left = (WIDTH - tabCount * tabSize) / 2;
        return new MachineLayout.Rect(left + index * tabSize, TAB_Y, tabSize, tabSize);
    }

    /** Left edge of the cube's three columns. */
    public int cubeX() {
        return (WIDTH - 3 * CELL) / 2;
    }

    /** Top edge of the cube, a little below the tabs. */
    public int cubeY() {
        return TAB_Y + tabSize + CUBE_GAP;
    }

    /** The column of a face in the unfolded cube, 0 to 2. */
    public static int column(FaceLabel label) {
        return switch (label) {
            case LEFT -> 0;
            case RIGHT, BACK -> 2;
            default -> 1;
        };
    }

    /** The row of a face in the unfolded cube, 0 to 2. Back sits under right, next to bottom. */
    public static int row(FaceLabel label) {
        return switch (label) {
            case TOP -> 0;
            case BOTTOM, BACK -> 2;
            default -> 1;
        };
    }

    /** The box of a face. */
    public MachineLayout.Rect face(FaceLabel label) {
        return new MachineLayout.Rect(cubeX() + column(label) * CELL, cubeY() + row(label) * CELL, FACE, FACE);
    }

    /** The "All Off" button. */
    public MachineLayout.Rect allOffButton() {
        return button(0);
    }

    /** The "Copy to all faces" button. */
    public MachineLayout.Rect copyButton() {
        return button(1);
    }

    /** The "Reset" button. */
    public MachineLayout.Rect resetButton() {
        return button(2);
    }

    /** The Auto Import switch, left of the middle on the bottom row. */
    public MachineLayout.Rect autoImportButton() {
        return switchButton(0);
    }

    /** The Auto Export switch, right of the middle on the bottom row. */
    public MachineLayout.Rect autoExportButton() {
        return switchButton(1);
    }

    private MachineLayout.Rect button(int index) {
        int rowWidth = 2 * BUTTON_PITCH + BUTTON;
        int y = cubeY() + 3 * CELL + BUTTON_GAP;
        return new MachineLayout.Rect((WIDTH - rowWidth) / 2 + index * BUTTON_PITCH, y, BUTTON, BUTTON);
    }

    /** The bottom row has two buttons, centered under the three above. */
    private MachineLayout.Rect switchButton(int index) {
        int rowWidth = BUTTON_PITCH + BUTTON;
        int y = cubeY() + 3 * CELL + BUTTON_GAP + BUTTON_PITCH;
        return new MachineLayout.Rect((WIDTH - rowWidth) / 2 + index * BUTTON_PITCH, y, BUTTON, BUTTON);
    }

    /** Every clickable rectangle, for checks. */
    public List<MachineLayout.Rect> clickable() {
        List<MachineLayout.Rect> all = new ArrayList<>();
        for (int i = 0; i < tabCount; i++) {
            all.add(tab(i));
        }
        for (FaceLabel label : FaceLabel.values()) {
            all.add(face(label));
        }
        all.add(allOffButton());
        all.add(copyButton());
        all.add(resetButton());
        all.add(autoImportButton());
        all.add(autoExportButton());
        return all;
    }

    /** What is at a point given in window pixels. The parts are the same as the older window's. */
    public SidePanelLayout.Hit hit(double x, double y) {
        for (int i = 0; i < tabCount; i++) {
            if (tab(i).contains(x, y)) {
                return new SidePanelLayout.Hit(SidePanelLayout.Part.TAB, i);
            }
        }
        for (FaceLabel label : FaceLabel.values()) {
            if (face(label).contains(x, y)) {
                return new SidePanelLayout.Hit(SidePanelLayout.Part.FACE, label.ordinal());
            }
        }
        if (allOffButton().contains(x, y)) {
            return new SidePanelLayout.Hit(SidePanelLayout.Part.ALL_OFF, 0);
        }
        if (copyButton().contains(x, y)) {
            return new SidePanelLayout.Hit(SidePanelLayout.Part.COPY_TO_ALL, 0);
        }
        if (resetButton().contains(x, y)) {
            return new SidePanelLayout.Hit(SidePanelLayout.Part.RESET, 0);
        }
        if (autoImportButton().contains(x, y)) {
            return new SidePanelLayout.Hit(SidePanelLayout.Part.AUTO_IMPORT, 0);
        }
        if (autoExportButton().contains(x, y)) {
            return new SidePanelLayout.Hit(SidePanelLayout.Part.AUTO_EXPORT, 0);
        }
        return SidePanelLayout.Hit.NOTHING;
    }
}
