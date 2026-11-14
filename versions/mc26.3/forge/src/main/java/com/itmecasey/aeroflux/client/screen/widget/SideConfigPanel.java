package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.GlyphSidePanelLayout;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.SideCommand;
import com.itmecasey.aeroflux.core.machine.SideConfig;
import com.itmecasey.aeroflux.core.machine.SidePanelLayout;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The small window docked beside a machine screen where a player sets what each face does. A
 * toggle button on the screen's edge opens and closes it. Inside are a square tab for each kind of
 * resource the machine uses, an unfolded cube of the six faces (front, back, left, right, top and
 * bottom as seen from the machine's front), and the All Off, Copy and Reset buttons. The window
 * has no words in it: tabs and buttons are glyphs, a face is a box in its mode's color, and the
 * names are in the tooltips.
 *
 * <p>A click on a face goes to the next mode in the order Import, Export, Both, Off, Blocked (a
 * right-click goes back). The widget decides nothing: it sends a {@link SideCommand} through the
 * {@link Model} and shows whatever mode the server last reported. Where things are is worked out
 * by {@link GlyphSidePanelLayout}, which is unit tested.
 */
public class SideConfigPanel extends AbstractWidget implements HoverInfo {
    /** What the panel needs from the screen. */
    public interface Model {
        /** The mode of a face as last synced from the server. */
        ConnectionMode mode(SideResource resource, Face face);

        /** What is on the other side of a face of the machine, as a name. */
        Component neighbor(Face face);

        /**
         * What is on the other side of a face, as an item: the block's own, or for a pipe bundle the
         * pipe that carries {@code resource} (any of its pipes if none does). Empty for air.
         */
        ItemStack neighborItem(Face face, SideResource resource);

        /** Whether Auto Import is on, as last synced. */
        boolean autoImport();

        /** Whether Auto Export is on, as last synced. */
        boolean autoExport();

        /** Sends an encoded {@link SideCommand}, or one of the auto switches' button numbers, to the server. */
        void send(int command);
    }

    /** Laid over a button under the pointer, the way the redstone button lights up. */
    private static final int HOVER = 0x40FFFFFF;
    private static final int SELECTED = 0xFFFFFFFF;
    private static final SidePanelLayout.Hit NOTHING = new SidePanelLayout.Hit(SidePanelLayout.Part.NOTHING, 0);

    private final GlyphSidePanelLayout layout;
    private final List<SideResource> resources;
    private final Face facing;
    private final Model model;
    private final int toggleX;
    private final int toggleY;
    private final int windowX;
    private final int windowY;

    private boolean open;
    private int tab;
    private FaceLabel selectedFace = FaceLabel.FRONT;

    /**
     * @param toggleX x of the toggle button, in screen pixels
     * @param toggleY y of the toggle button
     * @param windowX x of the window's left edge when open
     * @param windowY y of the window's top edge when open
     */
    public SideConfigPanel(List<SideResource> resources, Face facing, Model model,
                           int toggleX, int toggleY, int windowX, int windowY) {
        super(toggleX, toggleY, GlyphSidePanelLayout.TOGGLE, GlyphSidePanelLayout.TOGGLE,
                Component.translatable("screen.aeroflux.side_config.title"));
        this.layout = new GlyphSidePanelLayout(resources.size());
        this.resources = List.copyOf(resources);
        this.facing = facing;
        this.model = model;
        this.toggleX = toggleX;
        this.toggleY = toggleY;
        this.windowX = windowX;
        this.windowY = windowY;
    }

    /** Whether the window is open. */
    public boolean isOpen() {
        return open;
    }

    /** Opens or closes the window, as a click on the toggle does. */
    public void setOpen(boolean open) {
        this.open = open;
    }

    private SideResource resource() {
        return resources.get(Math.min(tab, resources.size() - 1));
    }

    private boolean overToggle(double mouseX, double mouseY) {
        return mouseX >= toggleX && mouseX < toggleX + GlyphSidePanelLayout.TOGGLE
                && mouseY >= toggleY && mouseY < toggleY + GlyphSidePanelLayout.TOGGLE;
    }

    private boolean overWindow(double mouseX, double mouseY) {
        return open && mouseX >= windowX && mouseX < windowX + GlyphSidePanelLayout.WIDTH
                && mouseY >= windowY && mouseY < windowY + GlyphSidePanelLayout.HEIGHT;
    }

    /** The widget counts as under the pointer over the toggle and, when open, the window, so clicks never fall through to slots. */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return overToggle(mouseX, mouseY) || overWindow(mouseX, mouseY);
    }

    /** What part of the open window a pointer in screen pixels is over. */
    private SidePanelLayout.Hit hitAt(double mouseX, double mouseY) {
        return overWindow(mouseX, mouseY) ? layout.hit(mouseX - windowX, mouseY - windowY) : NOTHING;
    }

    private static MutableComponent modeName(ConnectionMode mode) {
        return Component.translatable(modeKey(mode));
    }

    private static String modeKey(ConnectionMode mode) {
        return "machine_mode.aeroflux." + mode.name().toLowerCase(Locale.ROOT);
    }

    // --- Drawing -----------------------------------------------------------------------------

    /** Numbers follow the approved mockup, {@code assets-src/tools/ui_mockups.py}. */
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawToggle(graphics);
        if (!open) {
            return;
        }
        Panel.raised(graphics, windowX, windowY, GlyphSidePanelLayout.WIDTH, GlyphSidePanelLayout.HEIGHT);
        for (int i = 0; i < resources.size(); i++) {
            drawTab(graphics, i);
        }
        for (FaceLabel label : FaceLabel.values()) {
            drawFace(graphics, label);
        }

        SidePanelLayout.Hit hit = hitAt(mouseX, mouseY);
        // Copy takes the mode of the face clicked last, so pointing at Copy shows which face that is.
        if (hit.part() == SidePanelLayout.Part.COPY_TO_ALL) {
            MachineLayout.Rect rect = layout.face(selectedFace);
            graphics.outline(windowX + rect.x() - 1, windowY + rect.y() - 1, rect.width() + 2, rect.height() + 2, SELECTED);
        }
        drawButton(graphics, layout.allOffButton(), "action_all_off", hit.part() == SidePanelLayout.Part.ALL_OFF);
        drawButton(graphics, layout.copyButton(), "action_copy", hit.part() == SidePanelLayout.Part.COPY_TO_ALL);
        drawButton(graphics, layout.resetButton(), "action_reset", hit.part() == SidePanelLayout.Part.RESET);
        drawSwitch(graphics, layout.autoImportButton(), "mode_import", model.autoImport(), hit.part() == SidePanelLayout.Part.AUTO_IMPORT);
        drawSwitch(graphics, layout.autoExportButton(), "mode_export", model.autoExport(), hit.part() == SidePanelLayout.Part.AUTO_EXPORT);
    }

    /** An on/off switch: pressed in with a green bar while on, standing up while off, like the tabs. */
    private void drawSwitch(GuiGraphicsExtractor graphics, MachineLayout.Rect rect, String glyph, boolean on, boolean hovered) {
        int x = windowX + rect.x();
        int y = windowY + rect.y();
        int size = rect.width();
        if (on) {
            Panel.sunken(graphics, x, y, size, size, Panel.SLOT_FILL);
            graphics.fill(x + 3, y + size - 4, x + size - 3, y + size - 2, Glyphs.OPEN_COLOR);
            Glyphs.draw(graphics, glyph, x + size / 2, y + size / 2, Glyphs.DARK);
        } else {
            Panel.raised(graphics, x, y, size, size);
            Glyphs.draw(graphics, glyph, x + size / 2, y + size / 2 - 1, Glyphs.DARKER);
        }
        if (hovered) {
            graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, HOVER);
        }
    }

    /** The toggle: a small cube-like icon of four colored squares. */
    private void drawToggle(GuiGraphicsExtractor graphics) {
        Panel.raised(graphics, toggleX, toggleY, GlyphSidePanelLayout.TOGGLE, GlyphSidePanelLayout.TOGGLE);
        graphics.fill(toggleX + 4, toggleY + 4, toggleX + 8, toggleY + 8, Glyphs.IMPORT_COLOR);
        graphics.fill(toggleX + 8, toggleY + 4, toggleX + 12, toggleY + 8, Glyphs.EXPORT_COLOR);
        graphics.fill(toggleX + 4, toggleY + 8, toggleX + 8, toggleY + 12, Glyphs.OPEN_COLOR);
        graphics.fill(toggleX + 8, toggleY + 8, toggleX + 12, toggleY + 12, Glyphs.OFF_COLOR);
    }

    /**
     * A square tab with the resource's glyph. The chosen tab is pressed in, like a held-down
     * button, with a bar of the resource's color along its bottom; the others stand up, ready to click.
     */
    private void drawTab(GuiGraphicsExtractor graphics, int index) {
        MachineLayout.Rect rect = layout.tab(index);
        int x = windowX + rect.x();
        int y = windowY + rect.y();
        int size = rect.width();
        String glyph = Glyphs.resourceGlyph(resources.get(index));
        if (index == tab) {
            Panel.sunken(graphics, x, y, size, size, Panel.SLOT_FILL);
            graphics.fill(x + 3, y + size - 4, x + size - 3, y + size - 2, Glyphs.resourceColor(resources.get(index)));
            Glyphs.draw(graphics, glyph, x + size / 2, y + size / 2, Glyphs.DARK);
        } else {
            Panel.raised(graphics, x, y, size, size);
            Glyphs.draw(graphics, glyph, x + size / 2, y + size / 2 - 1, Glyphs.DARKER);
        }
    }

    /**
     * One face of the cube: a box in the mode's color with the neighboring block's item on it (so
     * a glance shows what each face touches), a cross when blocked, a white dot on the front.
     */
    private void drawFace(GuiGraphicsExtractor graphics, FaceLabel label) {
        MachineLayout.Rect rect = layout.face(label);
        int x = windowX + rect.x();
        int y = windowY + rect.y();
        ConnectionMode mode = model.mode(resource(), label.faceFor(facing));
        Glyphs.bevelBox(graphics, x, y, rect.width(), Glyphs.modeColor(mode), 1.25);
        ItemStack neighbor = model.neighborItem(label.faceFor(facing), resource());
        if (!neighbor.isEmpty()) {
            // An item is 16 pixels; the box keeps a ring of its mode color around it.
            int inset = (rect.width() - 16) / 2;
            graphics.item(neighbor, x + inset, y + inset);
        }
        if (mode == ConnectionMode.BLOCKED) {
            // The cross says "closed" without relying on the red alone.
            Glyphs.draw(graphics, "mode_blocked", x + GlyphSidePanelLayout.CELL / 2, y + GlyphSidePanelLayout.CELL / 2, Glyphs.WHITE);
        }
        if (label == FaceLabel.FRONT) {
            graphics.fill(x + 3, y + 3, x + 6, y + 6, Glyphs.WHITE);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, MachineLayout.Rect rect, String glyph, boolean hovered) {
        int x = windowX + rect.x();
        int y = windowY + rect.y();
        Panel.raised(graphics, x, y, rect.width(), rect.height());
        if (hovered) {
            graphics.fill(x + 2, y + 2, x + rect.width() - 2, y + rect.height() - 2, HOVER);
        }
        Glyphs.draw(graphics, glyph, x + rect.width() / 2, y + rect.height() / 2, Glyphs.DARK);
    }

    // --- Clicking ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (overToggle(mouseX, mouseY) && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            open = !open;
            return true;
        }
        if (!overWindow(mouseX, mouseY)) {
            return false;
        }
        SidePanelLayout.Hit hit = hitAt(mouseX, mouseY);
        switch (hit.part()) {
            case TAB -> tab = hit.index();
            case FACE -> {
                FaceLabel label = FaceLabel.values()[hit.index()];
                Face face = label.faceFor(facing);
                selectedFace = label;
                ConnectionMode next = SideConfig.cycle(model.mode(resource(), face), button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                model.send(SideCommand.set(resource(), face, next).encode());
            }
            case ALL_OFF -> model.send(SideCommand.allTo(resource(), ConnectionMode.OFF).encode());
            case COPY_TO_ALL -> model.send(SideCommand.copyToAll(resource(), selectedFace.faceFor(facing)).encode());
            case RESET -> model.send(SideCommand.reset(resource()).encode());
            case AUTO_IMPORT -> model.send(MachineConfigActions.AUTO_IMPORT_BUTTON);
            case AUTO_EXPORT -> model.send(MachineConfigActions.AUTO_EXPORT_BUTTON);
            default -> {
            }
        }
        return true;
    }

    // --- Tooltip -----------------------------------------------------------------------------

    /** With no words in the window, the tooltips carry every name. */
    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (overToggle(mouseX, mouseY)) {
            return List.of(Component.translatable("screen.aeroflux.side_config.title"));
        }
        SidePanelLayout.Hit hit = hitAt(mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        switch (hit.part()) {
            case TAB -> lines.add(Component.translatable("side_resource.aeroflux." + resources.get(hit.index()).id()));
            case FACE -> {
                FaceLabel label = FaceLabel.values()[hit.index()];
                Face face = label.faceFor(facing);
                ConnectionMode mode = model.mode(resource(), face);
                lines.add(Component.translatable("face_label.aeroflux." + label.id()));
                lines.add(modeName(mode).withColor(Glyphs.modeColor(mode) & 0xFFFFFF));
                lines.add(Component.translatable("screen.aeroflux.side_config.neighbor", model.neighbor(face))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            case ALL_OFF -> button(lines, "screen.aeroflux.side_config.all_off");
            case COPY_TO_ALL -> button(lines, "screen.aeroflux.side_config.copy_all");
            case RESET -> button(lines, "screen.aeroflux.side_config.reset");
            case AUTO_IMPORT -> autoSwitch(lines, "screen.aeroflux.side_config.auto_import", model.autoImport());
            case AUTO_EXPORT -> autoSwitch(lines, "screen.aeroflux.side_config.auto_export", model.autoExport());
            default -> {
            }
        }
        return lines;
    }

    /** A button's name. */
    private static void button(List<Component> lines, String key) {
        lines.add(Component.translatable(key));
    }

    /** A switch's name with On or Off. */
    private static void autoSwitch(List<Component> lines, String key, boolean on) {
        lines.add(Component.translatable(key).append(": ").append(Component.translatable(on ? "options.on" : "options.off")
                .withColor(on ? Glyphs.OPEN_COLOR & 0xFFFFFF : 0xAAAAAA)));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
