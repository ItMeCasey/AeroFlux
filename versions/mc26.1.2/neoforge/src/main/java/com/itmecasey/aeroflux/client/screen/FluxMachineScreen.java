package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.Glyphs;
import com.itmecasey.aeroflux.client.screen.widget.HoverInfo;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.client.screen.widget.ProgressArrow;
import com.itmecasey.aeroflux.client.screen.widget.RedstoneButton;
import com.itmecasey.aeroflux.client.screen.widget.SideConfigPanel;
import com.itmecasey.aeroflux.client.screen.widget.UpgradeSlots;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.GlyphSidePanelLayout;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.core.machine.UpgradeStripLayout;
import com.itmecasey.aeroflux.machine.FluxMachineMenu;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared look of every machine screen, as the plan lays it out: the Flux gauge on the far left,
 * input slots, the progress arrow and output slots (the arrow and the lit front show whether it
 * works, so there is no status line), the side-config window
 * docked to the panel's edge, and a strip on the right with the redstone button over the upgrade
 * slots. The tier shows as a colored bar along the top; the title is just the block's name, with
 * nothing repeated beside it. Like the pipe screen it draws everything with plain fills, so it
 * needs no texture, and it decides nothing: it shows what the server sent and turns clicks into
 * menu button presses.
 */
public abstract class FluxMachineScreen<M extends FluxMachineMenu> extends AbstractContainerScreen<M> {
    /** Aero Flux gold, for the progress fill. */
    private static final int PROGRESS_FILL = 0xFFF2C94C;

    private final MachineLayout layout;
    private final int accent;
    private FluxGauge gauge;
    private ProgressArrow arrow;
    private SideConfigPanel sidePanel;
    private UpgradeSlots upgradeSlots;
    private RedstoneButton redstoneButton;

    protected FluxMachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
        this.layout = menu.layout();
        this.accent = 0xFF000000 | menu.tier().color();
    }

    @Override
    protected void init() {
        super.init();
        // The redstone button and the upgrade slots share the strip on the panel's right edge.
        UpgradeStripLayout strip = menu.strip();
        upgradeSlots = addRenderableOnly(new UpgradeSlots(leftPos, topPos, strip, menu.tier(), new UpgradeModel(),
                List.of(new ItemStack(ModItems.SPEED_UPGRADE.get()), new ItemStack(ModItems.CAPACITY_UPGRADE.get()))));
        MachineLayout.Rect buttonRect = strip.redstoneButton();
        redstoneButton = addRenderableWidget(new RedstoneButton(leftPos + buttonRect.x(), topPos + buttonRect.y(),
                buttonRect.width(), menu::redstoneMode, this::sendButton));
        if (menu.machine() == MachineType.PLANT_GROWER) {
            // The seed refill switch sits right under the seed slot it fills.
            MachineLayout.Rect seedSlot = MachineLayout.frameOf(layout.inputSlot(0));
            addRenderableWidget(new AutoInsertButton(leftPos + seedSlot.x(), topPos + seedSlot.y() + seedSlot.height() + 2));
        }

        MachineLayout.Rect gaugeRect = layout.gauge();
        gauge = addRenderableWidget(new FluxGauge(leftPos + gaugeRect.x(), topPos + gaugeRect.y(), gaugeRect.width(), gaugeRect.height()));
        MachineLayout.Rect arrowRect = layout.arrow();
        arrow = addRenderableWidget(new ProgressArrow(leftPos + arrowRect.x(), topPos + arrowRect.y(),
                arrowRect.width(), arrowRect.height(), PROGRESS_FILL));

        // The side-config window docks to the left of the panel, or to the right, beyond the
        // upgrade strip, when there is no room.
        int room = GlyphSidePanelLayout.WIDTH + GlyphSidePanelLayout.TOGGLE;
        boolean onLeft = leftPos >= room;
        int toggleX = onLeft ? leftPos - GlyphSidePanelLayout.TOGGLE : leftPos + UpgradeStripLayout.right() + 1;
        int windowX = onLeft ? toggleX - GlyphSidePanelLayout.WIDTH : toggleX + GlyphSidePanelLayout.TOGGLE;
        sidePanel = addRenderableWidget(new SideConfigPanel(new ArrayList<>(menu.resources()), menu.facing(),
                new PanelModel(), toggleX, topPos + 6, windowX, topPos + 6));
    }

    /** Sends a menu button number to the server, the way every setting on this screen travels. */
    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** The strip outside the panel still counts as the screen, so a click there never throws the carried stack away. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top)
                && !menu.strip().bounds().contains(mouseX - left, mouseY - top);
    }

    /**
     * The plant grower's "auto insert seeds" switch: a seed icon, outlined in green while it is on.
     * While on, the grower moves seeds from its outputs back into its input by itself.
     */
    private final class AutoInsertButton extends AbstractButton {
        private static final int SIZE = 18;
        private final ItemStack icon = new ItemStack(Items.WHEAT_SEEDS);

        AutoInsertButton(int x, int y) {
            super(x, y, SIZE, SIZE, Component.translatable("gui.aeroflux.auto_insert"));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            sendButton(MachineConfigActions.AUTO_INSERT_BUTTON);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = menu.autoInsert();
            Panel.raised(graphics, getX(), getY(), SIZE, SIZE);
            graphics.item(icon, getX() + 1, getY() + 1);
            if (on) {
                graphics.outline(getX(), getY(), SIZE, SIZE, Glyphs.OPEN_COLOR);
            }
            if (isHovered()) {
                graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x40FFFFFF);
                setTooltip(Tooltip.create(getMessage().copy().append(CommonComponents.NEW_LINE).append(
                        Component.translatable(on ? "gui.aeroflux.auto_insert.on" : "gui.aeroflux.auto_insert.off"))));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Hooks the upgrade column up to the menu. */
    private final class UpgradeModel implements UpgradeSlots.Model {
        @Override
        public boolean filled(int slot) {
            return menu.getSlot(menu.firstUpgradeSlot() + slot).hasItem();
        }

        @Override
        public int speedUpgrades() {
            return menu.speedUpgrades();
        }

        @Override
        public int capacityUpgrades() {
            return menu.capacityUpgrades();
        }
    }

    /** Hooks the side-config window up to the menu. */
    private final class PanelModel implements SideConfigPanel.Model {
        @Override
        public ConnectionMode mode(SideResource resource, Face face) {
            return menu.mode(resource, face);
        }

        @Override
        public Component neighbor(Face face) {
            if (minecraft == null || minecraft.level == null) {
                return Component.translatable("screen.aeroflux.side_config.nothing");
            }
            BlockPos pos = menu.pos().relative(Direction.from3DDataValue(face.index()));
            BlockState state = minecraft.level.getBlockState(pos);
            return state.isAir() ? Component.translatable("screen.aeroflux.side_config.nothing") : state.getBlock().getName();
        }

        @Override
        public ItemStack neighborItem(Face face, SideResource resource) {
            if (minecraft == null || minecraft.level == null) {
                return ItemStack.EMPTY;
            }
            BlockPos pos = menu.pos().relative(Direction.from3DDataValue(face.index()));
            // A pipe bundle has no item of its own, so show its pipe: the one for this tab if it has one.
            if (minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
                return pipeItem(bundle.bundle(), resource);
            }
            BlockState state = minecraft.level.getBlockState(pos);
            return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        }

        private static ItemStack pipeItem(BundleState bundle, SideResource resource) {
            PipeType shown = null;
            for (PipeType type : bundle.set().types()) {
                if (shown == null || type == pipeFor(resource)) {
                    shown = type;
                }
            }
            return shown == null ? ItemStack.EMPTY : new ItemStack(ModItems.pipe(shown, bundle.tier(shown.lane())).get());
        }

        private static PipeType pipeFor(SideResource resource) {
            return switch (resource) {
                case ITEMS -> PipeType.ITEM;
                case FLUID -> PipeType.FLUID;
                case ENERGY -> PipeType.ENERGY;
                case GAS -> PipeType.GAS;
                case REDSTONE -> PipeType.REDSTONE;
            };
        }

        @Override
        public boolean autoImport() {
            return menu.autoImport();
        }

        @Override
        public boolean autoExport() {
            return menu.autoExport();
        }

        @Override
        public void send(int command) {
            sendButton(command);
        }
    }

    /** Feeds the widgets this frame's numbers, then draws; the widget tooltips are queued last so nothing covers them. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MachineStatus status = menu.status();
        gauge.update(menu.fluxAmount(), menu.fluxCapacity(), menu.fluxType(), menu.fluxPerTick(), status == MachineStatus.RUNNING);
        arrow.update(menu.progress(), menu.totalTime());

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (HoverInfo info : new HoverInfo[] {gauge, arrow, sidePanel, redstoneButton, upgradeSlots}) {
            List<Component> lines = info.tooltip(mouseX, mouseY);
            if (!lines.isEmpty()) {
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, leftPos, topPos, imageWidth, imageHeight);
        // The tier's color along the top says which tier this machine is.
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 5, accent);
        // The strip first, so the upgrade slots' frames land on top of it.
        upgradeSlots.drawBackground(graphics);
        for (Slot slot : menu.slots) {
            Panel.sunken(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, MachineLayout.SLOT);
        }
    }

    /**
     * The title is the block's own name, such as "Iron Flux Furnace", and nothing else: the tier
     * is in the name and in the bar along the top already. The player's inventory needs no label.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, layout.titleX(), layout.titleY(), Panel.TEXT, false);
    }

    /** The side-config widget, so the development screenshot tour can open its window. */
    public SideConfigPanel sidePanel() {
        return sidePanel;
    }
}
