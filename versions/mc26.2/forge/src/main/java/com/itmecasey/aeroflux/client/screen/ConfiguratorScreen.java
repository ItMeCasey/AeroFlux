package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.core.configurator.MachineSettings;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorActions;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorPayload;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorStackData;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The Flux Configurator's preset screen. The eight slots are listed on the left; clicking one
 * selects it, and copy and paste then use it. The right side shows what the selected slot holds,
 * with a name box to rename it and buttons to clear it and to undo the last paste.
 *
 * <p>Every button only sends a {@link ConfiguratorPayload}; the server changes the item, and the
 * changed item comes back to the client on its own, which is when this screen shows the change.
 */
public class ConfiguratorScreen extends Screen {
    private static final int WIDTH = 264;
    private static final int HEIGHT = 206;
    private static final int MARGIN = 10;
    private static final int SLOT_WIDTH = 104;
    private static final int SLOT_HEIGHT = 18;
    private static final int DETAIL_X = MARGIN + SLOT_WIDTH + 10;
    /** The side of one colored face square in the face grid. */
    private static final int CELL = 9;

    private final InteractionHand hand;
    private final List<Button> slotButtons = new ArrayList<>();
    private int left;
    private int top;
    private EditBox nameBox;
    private Button renameButton;
    private Button clearButton;
    /** The slot the name box was last filled from, so typing is not overwritten while the slot stays the same. */
    private int nameBoxSlot = -1;

    public ConfiguratorScreen(InteractionHand hand) {
        super(Component.translatable("item.aeroflux.flux_configurator"));
        this.hand = hand;
    }

    /** Opens the screen for the Configurator in a hand; handed to the item by the client setup. */
    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreenAndShow(new ConfiguratorScreen(hand));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        slotButtons.clear();
        for (int slot = 0; slot < ConfiguratorPresets.SLOTS; slot++) {
            int index = slot;
            slotButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> send(ConfiguratorPayload.SELECT, index, ""))
                    .bounds(left + MARGIN, top + 24 + slot * (SLOT_HEIGHT + 2), SLOT_WIDTH, SLOT_HEIGHT).build()));
        }

        int detailWidth = WIDTH - DETAIL_X - MARGIN;
        int bottom = top + HEIGHT - MARGIN;
        nameBox = addRenderableWidget(new EditBox(font, left + DETAIL_X, bottom - 62, detailWidth, 16,
                Component.translatable("gui.aeroflux.configurator.name")));
        nameBox.setMaxLength(ConfiguratorPresets.NAME_MAX);
        renameButton = addRenderableWidget(Button.builder(Component.translatable("gui.aeroflux.configurator.rename"),
                        button -> send(ConfiguratorPayload.RENAME, presets().selected(), nameBox.getValue()))
                .bounds(left + DETAIL_X, bottom - 42, detailWidth / 2 - 1, 18).build());
        clearButton = addRenderableWidget(Button.builder(Component.translatable("gui.aeroflux.configurator.clear"),
                        button -> send(ConfiguratorPayload.CLEAR, presets().selected(), ""))
                .bounds(left + DETAIL_X + detailWidth / 2 + 1, bottom - 42, detailWidth / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.aeroflux.configurator.undo"),
                        button -> send(ConfiguratorPayload.UNDO, 0, ""))
                .bounds(left + DETAIL_X, bottom - 20, detailWidth, 18).build());
        nameBoxSlot = -1;
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        ItemStack stack = stack();
        if (!stack.is(ModItems.FLUX_CONFIGURATOR.get())) {
            // The Configurator left the hand (dropped, swapped); the screen has nothing to show.
            onClose();
            return;
        }
        refresh();
    }

    /** Brings the slot labels and buttons in line with the item, which the server may have just changed. */
    private void refresh() {
        ConfiguratorPresets presets = presets();
        for (int slot = 0; slot < slotButtons.size(); slot++) {
            Component label = ConfiguratorActions.presetLabel(presets, slot);
            Component text = slot == presets.selected()
                    ? Component.literal("> ").append(label).append(" <")
                    : label;
            slotButtons.get(slot).setMessage(text);
        }
        ConfiguratorPresets.Preset current = presets.current();
        if (nameBoxSlot != presets.selected()) {
            nameBox.setValue(current == null ? "" : current.name());
            nameBoxSlot = presets.selected();
        }
        nameBox.active = current != null;
        nameBox.setEditable(current != null);
        renameButton.active = current != null;
        clearButton.active = current != null;
    }

    private void send(int action, int slot, String name) {
        new ConfiguratorPayload(hand == InteractionHand.OFF_HAND, action, slot, name).sendToServer();
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private ConfiguratorPresets presets() {
        return ConfiguratorStackData.presets(stack());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, left + MARGIN, top + MARGIN, Panel.TEXT, false);

        ConfiguratorPresets presets = presets();
        ConfiguratorPresets.Preset current = presets.current();
        int x = left + DETAIL_X;
        int y = top + 24;
        if (current == null) {
            graphics.text(font, Component.translatable("gui.aeroflux.configurator.empty"), x, y, Panel.TEXT_MUTED, false);
            graphics.text(font, Component.translatable("gui.aeroflux.configurator.hint"), x, y + 12, Panel.TEXT_MUTED, false);
            return;
        }

        MachineSettings settings = current.settings();
        graphics.text(font, ConfiguratorActions.kindName(settings.kind()), x, y, Panel.TEXT, false);
        y += 12;
        graphics.text(font, Component.translatable("redstone_mode.aeroflux." + settings.redstoneMode().id()), x, y,
                Panel.TEXT_MUTED, false);
        y += 10;
        graphics.text(font, Component.translatable("gui.aeroflux.configurator.auto",
                onOff(settings.autoImport()), onOff(settings.autoExport())), x, y, Panel.TEXT_MUTED, false);
        drawFaceGrid(graphics, settings, x, y + 14);
    }

    /**
     * The face settings as a small grid: one column per face (front, back, left, right, top,
     * bottom) and one row of colored squares per resource, in the side panel's colors.
     */
    private void drawFaceGrid(GuiGraphicsExtractor graphics, MachineSettings settings, int x, int y) {
        int labelWidth = 36;
        for (FaceLabel label : FaceLabel.values()) {
            Component face = Component.translatable("face_label.aeroflux." + label.id() + ".short");
            int cellX = x + labelWidth + label.ordinal() * (CELL + 4);
            graphics.text(font, face, cellX + (CELL - font.width(face)) / 2, y, Panel.TEXT_MUTED, false);
        }
        y += 10;
        for (SideResource resource : SideResource.values()) {
            if (settings.mode(resource, FaceLabel.FRONT) == null) {
                continue;
            }
            graphics.text(font, Component.translatable("side_resource.aeroflux." + resource.id()), x, y + 1, Panel.TEXT, false);
            for (FaceLabel label : FaceLabel.values()) {
                ConnectionMode mode = settings.mode(resource, label);
                int cellX = x + labelWidth + label.ordinal() * (CELL + 4);
                graphics.fill(cellX, y, cellX + CELL, y + CELL, Panel.EDGE);
                graphics.fill(cellX + 1, y + 1, cellX + CELL - 1, y + CELL - 1, Glyphs.modeColor(mode));
            }
            y += CELL + 3;
        }
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "gui.aeroflux.configurator.on" : "gui.aeroflux.configurator.off");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
