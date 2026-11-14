package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.chunkloader.ChunkLoaderMenu;
import com.itmecasey.aeroflux.client.ChunkAreaPreview;
import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.chunkloader.LoaderStatus;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

/**
 * The chunk loader screen, built from the machine screens' widgets: the Flux gauge on the left, a
 * map of the loaded area in the middle with the loader's own chunk marked, and on the right a
 * status glyph, the area size, the runtime left at the current draw and the draw itself. Numbers
 * and glyphs only; the words live in the tooltips. Like the machine screens it needs no texture
 * and decides nothing, it only shows what the server sent.
 */
public class ChunkLoaderScreen extends AbstractContainerScreen<ChunkLoaderMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 108;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 7;

    private static final int GAUGE_X = 8;
    private static final int CONTENT_Y = 18;
    private static final int GAUGE_WIDTH = 16;
    private static final int CONTENT_HEIGHT = 82;

    /** The map's sunken frame; the cells fill the 80 by 80 inside it. */
    private static final int MAP_X = 32;
    private static final int MAP_SIZE = 82;
    private static final int MAP_INNER = MAP_SIZE - 2;

    /** The column on the right, and its center line. */
    private static final int INFO_X = 122;
    private static final int INFO_CENTER = 147;
    private static final int STATUS_SIZE = 18;
    private static final int TIME_GLYPH_Y = 51;
    private static final int TIME_TEXT_Y = 61;
    private static final int RATE_TEXT_Y = 76;
    /** The Show Area button, under the draw. */
    private static final int SHOW_AREA_SIZE = 18;
    private static final int SHOW_AREA_Y = 86;

    private static final int MAP_FACE = 0xFF2A2A30;
    private static final int CELL_IDLE = 0xFF5A5A62;
    private static final int CENTER_MARK = 0xFFFFFFFF;
    private static final int GRACE_COLOR = 0xFFE8913A;

    private final int accent;
    private FluxGauge gauge;

    public ChunkLoaderScreen(ChunkLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.accent = 0xFF000000 | menu.tier().color();
    }

    @Override
    protected void init() {
        super.init();
        gauge = addRenderableWidget(new FluxGauge(leftPos + GAUGE_X, topPos + CONTENT_Y, GAUGE_WIDTH, CONTENT_HEIGHT));
        addRenderableWidget(new ShowAreaButton(leftPos + INFO_CENTER - SHOW_AREA_SIZE / 2, topPos + SHOW_AREA_Y));
    }

    /** "Show Area": closes the screen and traces the loaded chunks' borders in the world for a while. */
    private final class ShowAreaButton extends AbstractButton {
        private final ItemStack icon = new ItemStack(Items.MAP);

        ShowAreaButton(int x, int y) {
            super(x, y, SHOW_AREA_SIZE, SHOW_AREA_SIZE, Component.translatable("gui.aeroflux.chunk_loader.show_area"));
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        public void onPress() {
            ChunkAreaPreview.show(menu.centerChunk(), ChunkLoaderMath.radius(menu.tier()));
            onClose();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Panel.raised(graphics, getX(), getY(), SHOW_AREA_SIZE, SHOW_AREA_SIZE);
            graphics.renderItem(icon, getX() + 1, getY() + 1);
            if (isHovered()) {
                graphics.fill(getX(), getY(), getX() + SHOW_AREA_SIZE, getY() + SHOW_AREA_SIZE, 0x40FFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // --- Map geometry ------------------------------------------------------------------------

    /** Pixels per chunk cell, the gap included: 80 at copper down to 7 at fluxium. */
    private int cell() {
        return MAP_INNER / ChunkLoaderMath.side(menu.tier());
    }

    /** Where the first cell starts, so the grid sits in the middle of the frame. */
    private int gridLeft() {
        return leftPos + MAP_X + 1 + (MAP_INNER - cell() * ChunkLoaderMath.side(menu.tier())) / 2;
    }

    private int gridTop() {
        return topPos + CONTENT_Y + 1 + (MAP_INNER - cell() * ChunkLoaderMath.side(menu.tier())) / 2;
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LoaderStatus status = menu.status();
        int draw = (int) Math.ceil(menu.costPerTickMilli() / (double) ChunkLoaderMath.MILLI);
        gauge.update(menu.fluxAmount(), menu.capacity(), menu.fluxType(), draw, status.holdsTickets());

        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> lines = gauge.tooltip(mouseX, mouseY);
        if (lines.isEmpty()) {
            lines = tooltip(mouseX, mouseY, status);
        }
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        // The tier's color along the top, as on the machine screens.
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);

        LoaderStatus status = menu.status();
        drawMap(graphics, status);

        // Status: a colored box with the glyph that says the most about it.
        int boxX = leftPos + INFO_X;
        int boxY = topPos + CONTENT_Y;
        Glyphs.bevelBox(graphics, boxX, boxY, STATUS_SIZE, statusColor(status), 1.2);
        Glyphs.draw(graphics, statusGlyph(status), boxX + STATUS_SIZE / 2, boxY + STATUS_SIZE / 2, Glyphs.WHITE);

        int side = ChunkLoaderMath.side(menu.tier());
        graphics.drawString(font, side + "x" + side, boxX + STATUS_SIZE + 4, boxY + 5, Panel.TEXT, false);

        // Runtime left at the current draw, under a clock glyph.
        Glyphs.draw(graphics, "ticks", leftPos + INFO_CENTER, topPos + TIME_GLYPH_Y, Glyphs.DARK);
        centered(graphics, ChunkLoaderMath.formatTicks(menu.runtimeTicks()), TIME_TEXT_Y, Panel.TEXT);
        centered(graphics, ChunkLoaderMath.formatMilli(menu.costPerTickMilli()) + " mB/t", RATE_TEXT_Y, Panel.TEXT_MUTED);
    }

    /** The square of chunks the loader covers; lit while it holds them, gray while it does not. */
    private void drawMap(GuiGraphics graphics, LoaderStatus status) {
        Panel.sunken(graphics, leftPos + MAP_X, topPos + CONTENT_Y, MAP_SIZE, MAP_SIZE, MAP_FACE);
        int side = ChunkLoaderMath.side(menu.tier());
        int cell = cell();
        int left = gridLeft();
        int top = gridTop();
        int lit = litColor(status);
        int center = side / 2;
        for (int row = 0; row < side; row++) {
            for (int col = 0; col < side; col++) {
                int x = left + col * cell;
                int y = top + row * cell;
                // One pixel of gap between cells reads as the chunk border.
                graphics.fill(x, y, x + cell - 1, y + cell - 1, lit);
                if (row == center && col == center) {
                    graphics.renderOutline(x, y, cell - 1, cell - 1, CENTER_MARK);
                    int dot = Math.max(1, cell / 4);
                    int middle = x + (cell - 1) / 2;
                    int middleY = y + (cell - 1) / 2;
                    graphics.fill(middle - dot / 2, middleY - dot / 2, middle - dot / 2 + dot, middleY - dot / 2 + dot, accent);
                }
            }
        }
    }

    private int litColor(LoaderStatus status) {
        if (!status.holdsTickets()) {
            return CELL_IDLE;
        }
        FluxType type = menu.fluxType();
        int tint = type == null ? FluxType.AERO.tint() : type.tint();
        int color = 0xFF000000 | tint;
        // A loader in its grace period shows its chunks a little dimmer: they are about to go.
        return status == LoaderStatus.GRACE ? Glyphs.shade(color, 0.7) : color;
    }

    private void centered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, leftPos + INFO_CENTER - font.width(text) / 2, topPos + y, color, false);
    }

    private static int statusColor(LoaderStatus status) {
        return switch (status) {
            case ACTIVE -> Glyphs.OPEN_COLOR;
            case GRACE -> GRACE_COLOR;
            case NO_FLUX -> Glyphs.OFF_COLOR;
            case DISABLED, DIMENSION, CAP_REACHED -> Glyphs.BLOCKED_COLOR;
        };
    }

    private static String statusGlyph(LoaderStatus status) {
        return switch (status) {
            case ACTIVE -> "mode_open";
            case GRACE -> "ticks";
            case NO_FLUX -> "mode_off";
            case DISABLED, DIMENSION, CAP_REACHED -> "mode_blocked";
        };
    }

    // --- Tooltips ----------------------------------------------------------------------------

    private List<Component> tooltip(int mouseX, int mouseY, LoaderStatus status) {
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (inside(x, y, INFO_X, CONTENT_Y, STATUS_SIZE, STATUS_SIZE)) {
            return List.of(Component.translatable("gui.aeroflux.chunk_loader.status." + status.id()));
        }
        if (inside(x, y, INFO_X, TIME_GLYPH_Y - 8, WIDTH - INFO_X - 4, RATE_TEXT_Y + 9 - (TIME_GLYPH_Y - 8))) {
            return List.of(Component.translatable("gui.aeroflux.chunk_loader.runtime"),
                    Component.translatable("gui.aeroflux.chunk_loader.draw",
                            ChunkLoaderMath.formatMilli(menu.costPerTickMilli()), ChunkLoaderMath.chunkCount(menu.tier())));
        }
        int cell = cell();
        int side = ChunkLoaderMath.side(menu.tier());
        int col = Math.floorDiv(mouseX - gridLeft(), cell);
        int row = Math.floorDiv(mouseY - gridTop(), cell);
        if (col >= 0 && col < side && row >= 0 && row < side) {
            ChunkPos center = menu.centerChunk();
            int radius = ChunkLoaderMath.radius(menu.tier());
            int chunkX = center.x + col - radius;
            int chunkZ = center.z + row - radius;
            return List.of(Component.translatable("gui.aeroflux.chunk_loader.chunk", chunkX, chunkZ),
                    Component.translatable(status.holdsTickets()
                            ? "gui.aeroflux.chunk_loader.loaded" : "gui.aeroflux.chunk_loader.not_loaded"));
        }
        return List.of();
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    /** The title is the block's own name and nothing else; there is no inventory to label. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, TITLE_X, TITLE_Y, Panel.TEXT, false);
    }
}
