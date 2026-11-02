package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.FluxGauge;
import com.itmecasey.aeroflux.client.screen.widget.HoverInfo;
import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.client.screen.widget.ProgressArrow;
import com.itmecasey.aeroflux.client.screen.widget.SideConfigPanel;
import com.itmecasey.aeroflux.client.screen.widget.StatusLine;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.SidePanelLayout;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.machine.FluxMachineMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared look of every machine screen, as the plan lays it out: the Flux gauge on the far left,
 * input slots, the progress arrow, output slots, a status line under them, and the side-config
 * window docked to the panel's edge. The tier shows as a colored bar along the top and its name
 * on the right of the title. Like the pipe screen it draws everything with plain fills, so it
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
    private StatusLine statusLine;
    private SideConfigPanel sidePanel;

    protected FluxMachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
        this.layout = menu.layout();
        this.accent = 0xFF000000 | menu.tier().color();
        this.inventoryLabelY = layout.inventoryY() - 10;
    }

    @Override
    protected void init() {
        super.init();
        MachineLayout.Rect gaugeRect = layout.gauge();
        gauge = addRenderableWidget(new FluxGauge(leftPos + gaugeRect.x(), topPos + gaugeRect.y(), gaugeRect.width(), gaugeRect.height()));
        MachineLayout.Rect arrowRect = layout.arrow();
        arrow = addRenderableWidget(new ProgressArrow(leftPos + arrowRect.x(), topPos + arrowRect.y(),
                arrowRect.width(), arrowRect.height(), PROGRESS_FILL));
        MachineLayout.Rect statusRect = layout.status();
        statusLine = addRenderableWidget(new StatusLine(leftPos + statusRect.x(), topPos + statusRect.y(),
                statusRect.width(), statusRect.height()));

        // The side-config window docks to the left of the panel, or to the right when there is no room.
        int room = SidePanelLayout.WIDTH + SidePanelLayout.TOGGLE;
        boolean onLeft = leftPos >= room;
        int toggleX = onLeft ? leftPos - SidePanelLayout.TOGGLE : leftPos + imageWidth;
        int windowX = onLeft ? toggleX - SidePanelLayout.WIDTH : toggleX + SidePanelLayout.TOGGLE;
        sidePanel = addRenderableWidget(new SideConfigPanel(new ArrayList<>(menu.resources()), menu.facing(),
                new PanelModel(), toggleX, topPos + 6, windowX, topPos + 6));
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
        public void send(int command) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, command);
            }
        }
    }

    /** Feeds the widgets this frame's numbers, then draws; the widget tooltips are queued last so nothing covers them. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MachineStatus status = menu.status();
        gauge.update(menu.fluxAmount(), menu.fluxCapacity(), menu.fluxType(), menu.fluxPerTick(), status == MachineStatus.RUNNING);
        arrow.update(menu.progress(), menu.totalTime());
        statusLine.update(status);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (HoverInfo info : new HoverInfo[] {gauge, arrow, statusLine, sidePanel}) {
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
        for (Slot slot : menu.slots) {
            Panel.sunken(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, MachineLayout.SLOT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, layout.titleX(), layout.titleY(), Panel.TEXT, false);
        Component tier = Component.translatable("tier.aeroflux." + menu.tier().id());
        graphics.text(font, tier, imageWidth - 8 - font.width(tier), layout.titleY(), Panel.TEXT_MUTED, false);
        graphics.text(font, playerInventoryTitle, layout.inventoryX(), inventoryLabelY, Panel.TEXT, false);
    }
}
