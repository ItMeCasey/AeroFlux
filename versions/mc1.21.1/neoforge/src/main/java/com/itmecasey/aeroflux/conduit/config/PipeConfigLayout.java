package com.itmecasey.aeroflux.conduit.config;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on the pipe screen, in pixels from the top left corner of the panel.
 * The menu needs the slot positions and the screen needs the rest, and both must agree, so the
 * numbers are worked out here once from the pipe type and tier. Nothing in this class touches
 * the game, which also lets the self-test check that the panel fits on a small window.
 *
 * <p>The panel has two columns above the upgrade slots: the face modes on the left, and on the
 * right the distribution modes (when the pipe has any) with the throughput text under them.
 */
public final class PipeConfigLayout {
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

    public static final int WIDTH = 176;
    /** The size of a slot frame; the item sits one pixel inside it. */
    public static final int SLOT = 18;
    /** How many lines of text the throughput block reserves. */
    public static final int RATE_LINES = 2;
    public static final int LINE_HEIGHT = 10;

    private static final int MARGIN = 8;
    private static final int LEFT_X = MARGIN;
    private static final int LEFT_WIDTH = 68;
    private static final int RIGHT_X = 82;
    private static final int RIGHT_WIDTH = WIDTH - MARGIN - RIGHT_X;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_PITCH = 15;
    private static final int TITLE_Y = 6;
    private static final int SIDE_Y = 17;
    private static final int HEADING_Y = 28;
    private static final int FIRST_BUTTON_Y = 38;
    private static final int SECTION_GAP = 6;

    private final List<Rect> modeButtons = new ArrayList<>();
    private final List<Rect> distributionButtons = new ArrayList<>();
    private final int upgradeSlots;
    private final int rateY;
    private final int upgradeHeadingY;
    private final int upgradeSlotsY;
    private final int inventoryY;
    private final int hotbarY;
    private final int height;
    private final boolean distribution;

    private PipeConfigLayout(PipeType type, Tier tier) {
        int modes = PipeConfigActions.supportedModes(type).size();
        for (int i = 0; i < modes; i++) {
            modeButtons.add(new Rect(LEFT_X, FIRST_BUTTON_Y + i * BUTTON_PITCH, LEFT_WIDTH, BUTTON_HEIGHT));
        }
        int leftEnd = FIRST_BUTTON_Y + modes * BUTTON_PITCH - 1;

        distribution = PipeConfigActions.supportsDistribution(type);
        int rightTop = HEADING_Y;
        if (distribution) {
            for (int i = 0; i < DistributionMode.values().length; i++) {
                distributionButtons.add(new Rect(RIGHT_X, FIRST_BUTTON_Y + i * BUTTON_PITCH, RIGHT_WIDTH, BUTTON_HEIGHT));
            }
            rightTop = FIRST_BUTTON_Y + DistributionMode.values().length * BUTTON_PITCH - 1 + SECTION_GAP;
        }
        // One heading line, then the lines of the rate itself.
        rateY = rightTop;
        int rightEnd = rateY + LINE_HEIGHT + RATE_LINES * LINE_HEIGHT;

        int bottom = Math.max(leftEnd, rightEnd);
        upgradeSlots = PipeConfigActions.upgradeSlots(type, tier);
        if (upgradeSlots > 0) {
            upgradeHeadingY = bottom + SECTION_GAP;
            upgradeSlotsY = upgradeHeadingY + LINE_HEIGHT;
            bottom = upgradeSlotsY + SLOT;
        } else {
            upgradeHeadingY = 0;
            upgradeSlotsY = 0;
        }
        inventoryY = bottom + SECTION_GAP + 2;
        hotbarY = inventoryY + 3 * SLOT + 4;
        height = hotbarY + SLOT + MARGIN - 1;
    }

    /** The layout for one pipe at one tier. */
    public static PipeConfigLayout of(PipeType type, Tier tier) {
        return new PipeConfigLayout(type, tier);
    }

    public int width() {
        return WIDTH;
    }

    public int height() {
        return height;
    }

    public int titleX() {
        return MARGIN;
    }

    public int titleY() {
        return TITLE_Y;
    }

    /** The line under the title that names the face. */
    public int sideY() {
        return SIDE_Y;
    }

    public int headingY() {
        return HEADING_Y;
    }

    /** One button per supported mode, in the order of {@link PipeConfigActions#supportedModes}. */
    public List<Rect> modeButtons() {
        return modeButtons;
    }

    public boolean hasDistribution() {
        return distribution;
    }

    /** One button per {@link DistributionMode}; empty for pipes without distribution. */
    public List<Rect> distributionButtons() {
        return distributionButtons;
    }

    /** Left edge of the right column, which holds the distribution buttons and the rate. */
    public int rightX() {
        return RIGHT_X;
    }

    public int rightWidth() {
        return RIGHT_WIDTH;
    }

    /** Top of the throughput block: its heading, with the rate text on the lines below. */
    public int rateY() {
        return rateY;
    }

    /** How many upgrade slots there are; 0 for pipes that take none. */
    public int upgradeSlotCount() {
        return upgradeSlots;
    }

    public int upgradeHeadingY() {
        return upgradeHeadingY;
    }

    /** X of the item inside upgrade slot {@code index}. */
    public int upgradeSlotX(int index) {
        return MARGIN + index * SLOT;
    }

    /** Y of the item inside the upgrade slots. */
    public int upgradeSlotY() {
        return upgradeSlotsY;
    }

    /** X of the first column of the player inventory (and the hotbar). */
    public int inventoryX() {
        return MARGIN;
    }

    /** Y of the top row of the player inventory. */
    public int inventoryY() {
        return inventoryY;
    }

    /** Y of the hotbar. */
    public int hotbarY() {
        return hotbarY;
    }
}
