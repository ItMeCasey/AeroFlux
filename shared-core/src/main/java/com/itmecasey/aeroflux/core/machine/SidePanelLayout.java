package com.itmecasey.aeroflux.core.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the small side-config window docked beside a machine screen, in pixels from the
 * window's top left corner, plus the question "what is at this point". The window is drawn and
 * clicked by a widget; keeping the numbers and the hit test here lets a unit test check that
 * nothing overlaps and that every click lands where the picture says.
 *
 * <p>From top to bottom: a tab for each resource the machine uses, an unfolded cube showing the
 * six faces (top above front, then left, front, right and back in a row, bottom below), three
 * buttons, and a line of text for what the pointer is over.
 */
public final class SidePanelLayout {
    /** What a point on the window is over. */
    public enum Part {
        NOTHING,
        /** A resource tab; the hit's index says which one. */
        TAB,
        /** A face of the cube; the hit's index is the {@link FaceLabel} ordinal. */
        FACE,
        ALL_OFF,
        COPY_TO_ALL,
        RESET
    }

    /** The part under a point and its index (the tab number or face label ordinal, otherwise 0). */
    public record Hit(Part part, int index) {
        static final Hit NOTHING = new Hit(Part.NOTHING, 0);
    }

    public static final int WIDTH = 96;
    public static final int HEIGHT = 110;
    /** The side of the square button that opens and closes the window, on the machine screen's edge. */
    public static final int TOGGLE = 16;
    public static final int CELL = 18;

    private static final int PADDING = 4;
    private static final int TAB_Y = 4;
    private static final int TAB_HEIGHT = 12;
    private static final int CUBE_X = 12;
    private static final int CUBE_Y = 22;
    private static final int BUTTON_Y = 82;
    private static final int BUTTON_HEIGHT = 12;
    private static final int BUTTON_WIDTH = 28;
    private static final int BUTTON_GAP = 2;
    private static final int INFO_Y = 98;

    private final int tabCount;

    /** A window with one tab for each of {@code tabCount} resources. */
    public SidePanelLayout(int tabCount) {
        this.tabCount = Math.max(1, tabCount);
    }

    public int tabCount() {
        return tabCount;
    }

    /** The tab for a resource number, evenly dividing the window's width. */
    public MachineLayout.Rect tab(int index) {
        int usable = WIDTH - 2 * PADDING;
        int left = PADDING + index * usable / tabCount;
        int right = PADDING + (index + 1) * usable / tabCount;
        return new MachineLayout.Rect(left, TAB_Y, right - left - 1, TAB_HEIGHT);
    }

    /** Where a face sits in the unfolded cube. */
    public MachineLayout.Rect face(FaceLabel label) {
        int column;
        int row;
        switch (label) {
            case TOP -> {
                column = 1;
                row = 0;
            }
            case LEFT -> {
                column = 0;
                row = 1;
            }
            case FRONT -> {
                column = 1;
                row = 1;
            }
            case RIGHT -> {
                column = 2;
                row = 1;
            }
            case BACK -> {
                column = 3;
                row = 1;
            }
            default -> {
                column = 1;
                row = 2;
            }
        }
        return new MachineLayout.Rect(CUBE_X + column * CELL, CUBE_Y + row * CELL, CELL - 1, CELL - 1);
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

    private static MachineLayout.Rect button(int index) {
        return new MachineLayout.Rect(PADDING + index * (BUTTON_WIDTH + BUTTON_GAP), BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    /** The y of the text line under the buttons. */
    public int infoY() {
        return INFO_Y;
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
        return all;
    }

    /** What is at a point given in window pixels. */
    public Hit hit(double x, double y) {
        for (int i = 0; i < tabCount; i++) {
            if (tab(i).contains(x, y)) {
                return new Hit(Part.TAB, i);
            }
        }
        for (FaceLabel label : FaceLabel.values()) {
            if (face(label).contains(x, y)) {
                return new Hit(Part.FACE, label.ordinal());
            }
        }
        if (allOffButton().contains(x, y)) {
            return new Hit(Part.ALL_OFF, 0);
        }
        if (copyButton().contains(x, y)) {
            return new Hit(Part.COPY_TO_ALL, 0);
        }
        if (resetButton().contains(x, y)) {
            return new Hit(Part.RESET, 0);
        }
        return Hit.NOTHING;
    }
}
