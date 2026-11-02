package com.itmecasey.aeroflux.client.screen.widget;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.SideCommand;
import com.itmecasey.aeroflux.core.machine.SideConfig;
import com.itmecasey.aeroflux.core.machine.SidePanelLayout;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The small window docked beside a machine screen where a player sets what each face does. A
 * toggle button on the screen's edge opens and closes it. Inside are a tab for each kind of
 * resource the machine uses, an unfolded cube of the six faces (front, back, left, right, top and
 * bottom as seen from the machine's front), and the "All Off", "Copy" and "Reset" buttons.
 *
 * <p>A click on a face goes to the next mode in the order Import, Export, Both, Off, Blocked (a
 * right-click goes back). The widget decides nothing: it sends a {@link SideCommand} through the
 * {@link Model} and shows whatever mode the server last reported. Where things are is worked out
 * by {@link SidePanelLayout}, which is unit tested.
 */
public class SideConfigPanel extends AbstractWidget implements HoverInfo {
    /** What the panel needs from the screen. */
    public interface Model {
        /** The mode of a face as last synced from the server. */
        ConnectionMode mode(SideResource resource, Face face);

        /** What is on the other side of a face of the machine, as a name. */
        Component neighbor(Face face);

        /** Sends an encoded {@link SideCommand} to the server. */
        void send(int command);
    }

    private static final int IMPORT_COLOR = 0xFF4A7FD8;
    private static final int EXPORT_COLOR = 0xFFE8913A;
    private static final int BOTH_COLOR = 0xFF55B85A;
    private static final int OFF_COLOR = 0xFF8A8A8A;
    private static final int BLOCKED_COLOR = 0xFFC04040;
    private static final int SELECTED = 0xFFFFFFFF;
    /** The mouse button number of a right-click. */
    private static final int RIGHT_BUTTON = 1;

    private final SidePanelLayout layout;
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
        super(toggleX, toggleY, SidePanelLayout.TOGGLE, SidePanelLayout.TOGGLE,
                Component.translatable("screen.aeroflux.side_config.title"));
        this.layout = new SidePanelLayout(resources.size());
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

    private SideResource resource() {
        return resources.get(Math.min(tab, resources.size() - 1));
    }

    private boolean overToggle(double mouseX, double mouseY) {
        return mouseX >= toggleX && mouseX < toggleX + SidePanelLayout.TOGGLE
                && mouseY >= toggleY && mouseY < toggleY + SidePanelLayout.TOGGLE;
    }

    private boolean overWindow(double mouseX, double mouseY) {
        return open && mouseX >= windowX && mouseX < windowX + SidePanelLayout.WIDTH
                && mouseY >= windowY && mouseY < windowY + SidePanelLayout.HEIGHT;
    }

    /** The widget counts as under the pointer over the toggle and, when open, the window, so clicks never fall through to slots. */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return overToggle(mouseX, mouseY) || overWindow(mouseX, mouseY);
    }

    private static int color(ConnectionMode mode) {
        return switch (mode) {
            case IMPORT -> IMPORT_COLOR;
            case EXPORT -> EXPORT_COLOR;
            case OPEN -> BOTH_COLOR;
            case OFF -> OFF_COLOR;
            case BLOCKED -> BLOCKED_COLOR;
        };
    }

    private static MutableComponent modeName(ConnectionMode mode) {
        return Component.translatable(modeKey(mode));
    }

    private static String modeKey(ConnectionMode mode) {
        return "machine_mode.aeroflux." + mode.name().toLowerCase(Locale.ROOT);
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        drawToggle(graphics);
        if (!open) {
            return;
        }
        Panel.raised(graphics, windowX, windowY, SidePanelLayout.WIDTH, SidePanelLayout.HEIGHT);

        for (int i = 0; i < resources.size(); i++) {
            MachineLayout.Rect rect = layout.tab(i);
            int x = windowX + rect.x();
            int y = windowY + rect.y();
            graphics.fill(x, y, x + rect.width(), y + rect.height(), i == tab ? Panel.LIGHT : Panel.SHADOW);
            graphics.fill(x + 1, y + 1, x + rect.width() - 1, y + rect.height() - 1, i == tab ? Panel.PANEL : Panel.SLOT_FILL);
            Component label = Component.translatable("side_resource.aeroflux." + resources.get(i).id());
            graphics.text(font, label, x + (rect.width() - font.width(label)) / 2, y + 2, Panel.TEXT, false);
        }

        for (FaceLabel label : FaceLabel.values()) {
            drawFace(graphics, font, label);
        }

        drawButton(graphics, font, layout.allOffButton(), "screen.aeroflux.side_config.all_off");
        drawButton(graphics, font, layout.copyButton(), "screen.aeroflux.side_config.copy_all");
        drawButton(graphics, font, layout.resetButton(), "screen.aeroflux.side_config.reset");

        // What the pointer is over, named under the buttons.
        SidePanelLayout.Hit hit = layout.hit(mouseX - windowX, mouseY - windowY);
        if (hit.part() == SidePanelLayout.Part.FACE) {
            FaceLabel label = FaceLabel.values()[hit.index()];
            Component text = Component.translatable("screen.aeroflux.side_config.face_mode",
                    Component.translatable("face_label.aeroflux." + label.id()),
                    modeName(model.mode(resource(), label.faceFor(facing))));
            graphics.text(font, text, windowX + 4, windowY + layout.infoY(), Panel.TEXT_MUTED, false);
        }
    }

    /** The toggle: a small cube-like icon of four colored squares. */
    private void drawToggle(GuiGraphicsExtractor graphics) {
        Panel.raised(graphics, toggleX, toggleY, SidePanelLayout.TOGGLE, SidePanelLayout.TOGGLE);
        graphics.fill(toggleX + 4, toggleY + 4, toggleX + 8, toggleY + 8, IMPORT_COLOR);
        graphics.fill(toggleX + 8, toggleY + 4, toggleX + 12, toggleY + 8, EXPORT_COLOR);
        graphics.fill(toggleX + 4, toggleY + 8, toggleX + 8, toggleY + 12, BOTH_COLOR);
        graphics.fill(toggleX + 8, toggleY + 8, toggleX + 12, toggleY + 12, OFF_COLOR);
    }

    private void drawFace(GuiGraphicsExtractor graphics, Font font, FaceLabel label) {
        MachineLayout.Rect rect = layout.face(label);
        int x = windowX + rect.x();
        int y = windowY + rect.y();
        ConnectionMode mode = model.mode(resource(), label.faceFor(facing));
        graphics.fill(x, y, x + rect.width(), y + rect.height(), Panel.EDGE);
        graphics.fill(x + 1, y + 1, x + rect.width() - 1, y + rect.height() - 1, color(mode));
        if (label == selectedFace) {
            graphics.outline(x - 1, y - 1, rect.width() + 2, rect.height() + 2, SELECTED);
        }
        if (mode == ConnectionMode.BLOCKED) {
            // A cross says "closed" without relying on the red alone.
            for (int step = 2; step < rect.width() - 2; step++) {
                graphics.fill(x + step, y + step, x + step + 1, y + step + 1, SELECTED);
                graphics.fill(x + rect.width() - 1 - step, y + step, x + rect.width() - step, y + step + 1, SELECTED);
            }
        } else {
            Component text = Component.translatable("face_label.aeroflux." + label.id() + ".short");
            graphics.text(font, text, x + (rect.width() - font.width(text)) / 2, y + (rect.height() - 8) / 2 + 1, 0xFFFFFFFF, true);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, Font font, MachineLayout.Rect rect, String key) {
        int x = windowX + rect.x();
        int y = windowY + rect.y();
        graphics.fill(x, y, x + rect.width(), y + rect.height(), Panel.SHADOW);
        graphics.fill(x + 1, y + 1, x + rect.width() - 1, y + rect.height() - 1, Panel.PANEL);
        Component label = Component.translatable(key);
        graphics.text(font, label, x + (rect.width() - font.width(label)) / 2, y + 2, Panel.TEXT, false);
    }

    // --- Clicking ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (overToggle(mouseX, mouseY) && button == 0) {
            open = !open;
            return true;
        }
        if (!overWindow(mouseX, mouseY)) {
            return false;
        }
        SidePanelLayout.Hit hit = layout.hit(mouseX - windowX, mouseY - windowY);
        switch (hit.part()) {
            case TAB -> tab = hit.index();
            case FACE -> {
                FaceLabel label = FaceLabel.values()[hit.index()];
                Face face = label.faceFor(facing);
                selectedFace = label;
                ConnectionMode next = SideConfig.cycle(model.mode(resource(), face), button == RIGHT_BUTTON);
                model.send(SideCommand.set(resource(), face, next).encode());
            }
            case ALL_OFF -> model.send(SideCommand.allTo(resource(), ConnectionMode.OFF).encode());
            case COPY_TO_ALL -> model.send(SideCommand.copyToAll(resource(), selectedFace.faceFor(facing)).encode());
            case RESET -> model.send(SideCommand.reset(resource()).encode());
            default -> {
            }
        }
        return true;
    }

    // --- Tooltip -----------------------------------------------------------------------------

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (overToggle(mouseX, mouseY)) {
            return List.of(Component.translatable("screen.aeroflux.side_config.title"),
                    Component.translatable("screen.aeroflux.side_config.toggle_hint").withStyle(ChatFormatting.GRAY));
        }
        if (!overWindow(mouseX, mouseY)) {
            return List.of();
        }
        SidePanelLayout.Hit hit = layout.hit(mouseX - windowX, mouseY - windowY);
        List<Component> lines = new ArrayList<>();
        switch (hit.part()) {
            case FACE -> {
                FaceLabel label = FaceLabel.values()[hit.index()];
                Face face = label.faceFor(facing);
                ConnectionMode mode = model.mode(resource(), face);
                lines.add(Component.translatable("face_label.aeroflux." + label.id()));
                lines.add(modeName(mode).withColor(color(mode) & 0xFFFFFF));
                lines.add(Component.translatable(modeKey(mode) + ".tooltip").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("screen.aeroflux.side_config.neighbor", model.neighbor(face))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            case ALL_OFF -> lines.add(Component.translatable("screen.aeroflux.side_config.all_off.tooltip"));
            case COPY_TO_ALL -> lines.add(Component.translatable("screen.aeroflux.side_config.copy_all.tooltip"));
            case RESET -> lines.add(Component.translatable("screen.aeroflux.side_config.reset.tooltip"));
            default -> {
            }
        }
        return lines;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
