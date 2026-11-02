package com.itmecasey.aeroflux.core.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on a machine screen, in pixels from the top left corner of the panel. The
 * menu needs the slot positions and the screen needs the rest, and both must agree, so the numbers
 * are worked out here once from the number of input and output slots. Nothing in this class
 * touches the game, which also lets a unit test check that every machine fits its panel.
 *
 * <p>The shape follows the plan: the Flux gauge on the far left, input slots, then the progress
 * arrow in the middle, then output slots, with a status line underneath and the player's
 * inventory at the bottom.
 */
public final class MachineLayout {
    /** A rectangle in panel pixels. */
    public record Rect(int x, int y, int width, int height) {
        /** Whether a point lies inside. */
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        /** Whether the two rectangles share any area. */
        public boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    /** A point in panel pixels; for a slot this is where the item is drawn. */
    public record Point(int x, int y) {
    }

    public static final int WIDTH = 176;
    public static final int HEIGHT = 166;
    /** The size of a slot frame; the item sits one pixel inside it. */
    public static final int SLOT = 18;
    /** Slots per column before a machine with many slots starts another one. */
    public static final int SLOTS_PER_COLUMN = 3;

    private static final int MARGIN = 8;
    private static final int MIDDLE_Y = 44;
    private static final int INPUT_X = 48;
    private static final int OUTPUT_X = 114;
    /** Where the last input column goes when there are three of them (the Crafter's grid). */
    private static final int WIDE_INPUT_X = 66;
    /** How far the arrow and outputs move right to make room for that third column. */
    private static final int WIDE_SHIFT = WIDE_INPUT_X - INPUT_X;

    private static final Rect GAUGE = new Rect(MARGIN, 17, 18, 56);
    private static final Rect ARROW = new Rect(72, 36, 24, 16);
    /** The gauge of an extra input tank, between the Flux gauge and the input slots. */
    private static final Rect EXTRA_INPUT_GAUGE = new Rect(29, 17, 14, 56);
    /** The gauge of an extra output tank, near the right edge past the output slots. */
    private static final Rect EXTRA_OUTPUT_GAUGE = new Rect(154, 17, 14, 56);
    private static final Rect STATUS = new Rect(30, 72, 138, 10);
    private static final int TITLE_X = MARGIN;
    private static final int TITLE_Y = 7;
    private static final int INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final List<Point> inputs = new ArrayList<>();
    private final List<Point> outputs = new ArrayList<>();

    private final int shift;

    private MachineLayout(int inputCount, int outputCount) {
        // More than two columns of inputs push the arrow and outputs to the right.
        this.shift = inputCount > 2 * SLOTS_PER_COLUMN ? WIDE_SHIFT : 0;
        place(inputs, inputCount, INPUT_X + shift, -SLOT);
        place(outputs, outputCount, OUTPUT_X + shift, SLOT);
    }

    /** The layout for a machine with the given number of input and output slots. */
    public static MachineLayout of(int inputCount, int outputCount) {
        return new MachineLayout(inputCount, outputCount);
    }

    /** Fills columns of up to {@link #SLOTS_PER_COLUMN} slots, each centered on the middle line. */
    private static void place(List<Point> into, int count, int firstX, int columnStep) {
        for (int index = 0; index < count; index++) {
            int column = index / SLOTS_PER_COLUMN;
            int inColumn = Math.min(SLOTS_PER_COLUMN, count - column * SLOTS_PER_COLUMN);
            int row = index % SLOTS_PER_COLUMN;
            int frameTop = MIDDLE_Y - inColumn * SLOT / 2 + row * SLOT;
            into.add(new Point(firstX + column * columnStep, frameTop + 1));
        }
    }

    /** The framed Flux gauge, including its frame. */
    public Rect gauge() {
        return GAUGE;
    }

    /** The progress arrow. */
    public Rect arrow() {
        return new Rect(ARROW.x() + shift, ARROW.y(), ARROW.width(), ARROW.height());
    }

    /** The gauge of an extra tank: input tanks sit left of the inputs, output tanks at the right edge. */
    public Rect extraGauge(boolean output) {
        return output ? EXTRA_OUTPUT_GAUGE : EXTRA_INPUT_GAUGE;
    }

    /** The status lamp and text line. */
    public Rect status() {
        return STATUS;
    }

    public int titleX() {
        return TITLE_X;
    }

    public int titleY() {
        return TITLE_Y;
    }

    public int inputCount() {
        return inputs.size();
    }

    public int outputCount() {
        return outputs.size();
    }

    /** Where the item in an input slot is drawn. */
    public Point inputSlot(int index) {
        return inputs.get(index);
    }

    /** Where the item in an output slot is drawn. */
    public Point outputSlot(int index) {
        return outputs.get(index);
    }

    /** The frame around a slot whose item is at the given point. */
    public static Rect frameOf(Point item) {
        return new Rect(item.x() - 1, item.y() - 1, SLOT, SLOT);
    }

    public int inventoryX() {
        return MARGIN;
    }

    public int inventoryY() {
        return INVENTORY_Y;
    }

    public int hotbarY() {
        return HOTBAR_Y;
    }

    public int width() {
        return WIDTH;
    }

    public int height() {
        return HEIGHT;
    }
}
