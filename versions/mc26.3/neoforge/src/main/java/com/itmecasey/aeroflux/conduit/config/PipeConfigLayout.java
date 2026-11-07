package com.itmecasey.aeroflux.conduit.config;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.EndpointFilter;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on the pipe screen, in pixels from the top left corner of the panel.
 * The menu needs the slot positions and the screen needs the rest, and both must agree, so the
 * numbers are worked out here once from the pipe type and tier. Nothing in this class touches
 * the game, which also lets the self-test check that the panel fits on a small window.
 *
 * <p>The screen has no words on it, only glyphs, colors and numbers, laid out in rows as the
 * approved mockup ({@code assets-src/tools/ui_mockups.py}) draws them: a header with the pipe's
 * glyph, its tier gem and a small cube net that marks the face; the face modes; the distribution
 * modes, for pipes that have them; the throughput with a tier bar; the upgrade slots, for pipes
 * that take upgrades; and the player inventory.
 *
 * <p>The Filter and Priority upgrades belong to this face alone, so they get their own places at
 * the right end of the upgrade row. While one is installed, a side panel to the right of the
 * screen holds its controls: the filter's whitelist toggle and 3 by 3 ghost slots, and the
 * priority with a minus and a plus button. The menu's real slots cannot move once it is built, so
 * the side panel keeps the main panel (and the inventory) exactly where it was.
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
    /** The side of the square mode buttons and of the pipe's glyph box. */
    public static final int BUTTON = 22;
    /** The side of one cell of the small cube net in the header. */
    public static final int NET_CELL = 6;
    /** How many cells the tier bar has, one per tier from copper to fluxium. */
    public static final int TIER_CELLS = 6;

    private static final int MARGIN = 8;
    private static final int BUTTON_PITCH = 27;
    private static final int HEADER_Y = 8;
    private static final int MODES_Y = 38;
    /** From the top of one row of square buttons to the top of the next row. */
    private static final int BUTTON_ROW = 32;
    /** Height of the throughput row. */
    private static final int RATE_ROW = 22;
    /** Height of the upgrade row. */
    private static final int UPGRADE_ROW = 26;
    private static final int UPGRADE_PITCH = 19;
    private static final int TIER_BAR_X = 100;
    private static final int TIER_CELL_WIDTH = 10;
    private static final int TIER_CELL_HEIGHT = 8;
    private static final int TIER_CELL_PITCH = 11;
    /** From the top of the inventory frames to the bottom of the panel: three rows, a gap, the hotbar, a margin. */
    private static final int INVENTORY_HEIGHT = 86;
    private static final int HOTBAR_OFFSET = 58;

    /** Left edge of the side panel, two pixels clear of the main panel. */
    public static final int SIDE_X = WIDTH + 2;
    /** Width of the side panel: a 3 by 3 grid of slots with a six pixel margin each side. */
    public static final int SIDE_WIDTH = 2 * 6 + 3 * SLOT;
    /** The side of the priority's minus and plus buttons. */
    public static final int SMALL_BUTTON = 16;
    private static final int SIDE_MARGIN = 6;
    private static final int FILTER_GRID_Y = 36;
    /** Height of the filter part of the side panel: toggle row, grid, margin. */
    private static final int FILTER_HEIGHT = FILTER_GRID_Y + 3 * SLOT + SIDE_MARGIN;
    /** Height of the priority part of the side panel: one row of small buttons with margins. */
    private static final int PRIORITY_HEIGHT = 8 + SMALL_BUTTON + 8;

    private final List<Rect> modeButtons = new ArrayList<>();
    private final List<Rect> distributionButtons = new ArrayList<>();
    private final int upgradeSlots;
    private final int rateY;
    private final int upgradeRowY;
    private final int inventoryFrameY;
    private final int height;
    private final boolean distribution;
    private final boolean filterSlot;
    private final boolean prioritySlot;

    private PipeConfigLayout(PipeType type, Tier tier) {
        int modes = PipeConfigActions.supportedModes(type).size();
        for (int i = 0; i < modes; i++) {
            modeButtons.add(new Rect(MARGIN + i * BUTTON_PITCH, MODES_Y, BUTTON, BUTTON));
        }
        int y = MODES_Y + BUTTON_ROW;

        distribution = PipeConfigActions.supportsDistribution(type);
        if (distribution) {
            for (int i = 0; i < DistributionMode.values().length; i++) {
                distributionButtons.add(new Rect(MARGIN + i * BUTTON_PITCH, y, BUTTON, BUTTON));
            }
            y += BUTTON_ROW;
        }

        rateY = y;
        y += RATE_ROW;

        upgradeSlots = PipeConfigActions.upgradeSlots(type, tier);
        // Every pipe that takes these also takes ordinary upgrades, so the row is always there for them.
        filterSlot = upgradeSlots > 0 && EndpointSettings.supportsFilter(type);
        prioritySlot = upgradeSlots > 0 && EndpointSettings.supportsPriority(type);
        upgradeRowY = y;
        if (upgradeSlots > 0) {
            y += UPGRADE_ROW;
        }
        inventoryFrameY = y;
        height = inventoryFrameY + INVENTORY_HEIGHT;
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

    // --- Header ------------------------------------------------------------------------------

    /** The sunken box at the top left that holds the pipe's glyph. */
    public Rect pipeBox() {
        return new Rect(MARGIN, HEADER_Y, BUTTON, BUTTON);
    }

    /** The small square in the tier's color beside the pipe's glyph, black edge included. */
    public Rect tierGem() {
        return new Rect(36, 14, 9, 9);
    }

    /** The small cube net at the top right that marks which face of the bundle this is. */
    public Rect faceNet() {
        return new Rect(WIDTH - MARGIN - 3 * NET_CELL, 10, 3 * NET_CELL, 3 * NET_CELL);
    }

    // --- Buttons -----------------------------------------------------------------------------

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

    // --- Throughput --------------------------------------------------------------------------

    /** Top of the throughput row: the resource glyph and the numbers on the left, the tier bar on the right. */
    public int rateY() {
        return rateY;
    }

    /** The whole throughput row left of the tier bar, for its tooltip. */
    public Rect rateArea() {
        return new Rect(MARGIN, rateY, TIER_BAR_X - 4 - MARGIN, 14);
    }

    /** One sunken cell of the tier bar; cells up to the pipe's tier are filled. */
    public Rect tierCell(int index) {
        return new Rect(TIER_BAR_X + index * TIER_CELL_PITCH, rateY + 1, TIER_CELL_WIDTH, TIER_CELL_HEIGHT);
    }

    // --- Upgrades ----------------------------------------------------------------------------

    /** How many upgrade slots there are; 0 for pipes that take none. */
    public int upgradeSlotCount() {
        return upgradeSlots;
    }

    /** Top of the upgrade row, the same as the top of its slot frames. */
    public int upgradeRowY() {
        return upgradeRowY;
    }

    /** Center x of the Speed upgrade glyph at the start of the upgrade row. */
    public int speedGlyphX() {
        return 14;
    }

    /** Center x of the Capacity upgrade glyph, right after the Speed one. */
    public int capacityGlyphX() {
        return 30;
    }

    /** The two upgrade glyphs together, for their tooltip. */
    public Rect upgradeGlyphs() {
        return new Rect(MARGIN, upgradeRowY, 30, SLOT);
    }

    /** X of the item inside upgrade slot {@code index}; the frame is one pixel further out. */
    public int upgradeSlotX(int index) {
        return 41 + index * UPGRADE_PITCH;
    }

    /** Y of the item inside the upgrade slots. */
    public int upgradeSlotY() {
        return upgradeRowY + 1;
    }

    // --- Filter and Priority upgrades ---------------------------------------------------------

    /** Whether the upgrade row has a place for a Filter upgrade at this face. */
    public boolean hasFilterSlot() {
        return filterSlot;
    }

    /** Whether the upgrade row has a place for a Priority upgrade at this face. */
    public boolean hasPrioritySlot() {
        return prioritySlot;
    }

    /** The frame of the Priority upgrade's place: the last one in the upgrade row. */
    public Rect priorityUpgradeSlot() {
        return new Rect(WIDTH - MARGIN - SLOT, upgradeRowY, SLOT, SLOT);
    }

    /** The frame of the Filter upgrade's place, just left of the Priority one. */
    public Rect filterUpgradeSlot() {
        return new Rect(WIDTH - MARGIN - 2 * SLOT - 1, upgradeRowY, SLOT, SLOT);
    }

    /** The filter part of the side panel, at the top. */
    public Rect filterPanel() {
        return new Rect(SIDE_X, 0, SIDE_WIDTH, FILTER_HEIGHT);
    }

    /** Where the Filter upgrade's own icon sits in the side panel, as a heading. */
    public Rect filterIcon() {
        return new Rect(SIDE_X + SIDE_MARGIN + 1, 11, 16, 16);
    }

    /** The whitelist and blacklist toggle, at the top right of the filter part. */
    public Rect filterModeButton() {
        return new Rect(SIDE_X + SIDE_WIDTH - SIDE_MARGIN - BUTTON, 8, BUTTON, BUTTON);
    }

    /** The frame of one ghost slot of the filter, row by row in a 3 by 3 grid. */
    public Rect filterSlot(int index) {
        return new Rect(SIDE_X + SIDE_MARGIN + (index % 3) * SLOT, FILTER_GRID_Y + (index / 3) * SLOT, SLOT, SLOT);
    }

    /**
     * The priority part of the side panel: below the filter part while that shows, otherwise at
     * the top on its own. The two parts overlap by their border, so together they read as one panel.
     */
    public Rect priorityPanel(boolean belowFilter) {
        return new Rect(SIDE_X, belowFilter ? FILTER_HEIGHT - 2 : 0, SIDE_WIDTH, PRIORITY_HEIGHT);
    }

    /** The minus button, at the left of the priority row. */
    public Rect priorityDownButton(boolean belowFilter) {
        return new Rect(SIDE_X + SIDE_MARGIN, priorityPanel(belowFilter).y() + 8, SMALL_BUTTON, SMALL_BUTTON);
    }

    /** The plus button, at the right of the priority row. */
    public Rect priorityUpButton(boolean belowFilter) {
        return new Rect(SIDE_X + SIDE_WIDTH - SIDE_MARGIN - SMALL_BUTTON, priorityPanel(belowFilter).y() + 8,
                SMALL_BUTTON, SMALL_BUTTON);
    }

    /** The number between the two buttons. */
    public Rect priorityValue(boolean belowFilter) {
        Rect down = priorityDownButton(belowFilter);
        Rect up = priorityUpButton(belowFilter);
        return new Rect(down.x() + down.width(), down.y(), up.x() - down.x() - down.width(), SMALL_BUTTON);
    }

    /** The whole side panel for what is installed, or {@code null} when nothing is. */
    public Rect sidePanel(boolean filter, boolean priority) {
        if (!filter && !priority) {
            return null;
        }
        int top = filter ? 0 : priorityPanel(false).y();
        Rect bottomPart = priority ? priorityPanel(filter) : filterPanel();
        return new Rect(SIDE_X, top, SIDE_WIDTH, bottomPart.y() + bottomPart.height() - top);
    }

    /** How many ghost slots the filter has; the same for item and fluid pipes. */
    public int filterSlotCount() {
        return EndpointFilter.SLOTS;
    }

    // --- Inventory ---------------------------------------------------------------------------

    /** X of the first column of the player inventory (and the hotbar). */
    public int inventoryX() {
        return MARGIN + 1;
    }

    /** Y of the top row of the player inventory. */
    public int inventoryY() {
        return inventoryFrameY + 1;
    }

    /** Y of the hotbar. */
    public int hotbarY() {
        return inventoryFrameY + HOTBAR_OFFSET + 1;
    }
}
