package com.itmecasey.aeroflux.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import java.util.EnumSet;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The AE converter's place in an ME network: a grid node on every side, and each tick the
 * converter's AE goes into the network's energy store, the way an Energy Acceptor feeds it, so the
 * converter keeps a network running on its own.
 */
final class ConverterAeLink implements IInWorldGridNodeHost {
    private final FluxConverterBlockEntity converter;
    private final IManagedGridNode node;

    ConverterAeLink(FluxConverterBlockEntity converter) {
        this.converter = converter;
        this.node = GridHelper.createManagedNode(this, Listener.INSTANCE)
                .setInWorldNode(true)
                .setTagName("aeroflux_converter")
                .setIdlePowerUsage(0.0)
                .setVisualRepresentation(converter.getBlockState().getBlock())
                .setExposedOnSides(sides(converter));
    }

    /** The faces the converter outputs on, which are the faces AE2 may connect through. */
    private static EnumSet<Direction> sides(FluxConverterBlockEntity converter) {
        EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if (converter.outputsOn(side)) {
                sides.add(side);
            }
        }
        return sides;
    }

    void updateSides() {
        node.setExposedOnSides(sides(converter));
    }

    void start() {
        GridHelper.onFirstTick(converter, entity -> node.create(entity.getLevel(), entity.getBlockPos()));
    }

    void stop() {
        node.destroy();
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction side) {
        return node.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return AECableType.SMART;
    }

    /**
     * Pushes the converter's buffer into the network's energy store, as much as it will take. This
     * is what AE2's own Energy Acceptor does every tick, and it keeps a network powered steadily
     * instead of waiting for the network to come and draw.
     */
    void push() {
        double offer = converter.stored();
        if (offer <= 0) {
            return;
        }
        node.ifPresent(grid -> {
            double left = grid.getEnergyService().injectPower(offer, Actionable.MODULATE);
            converter.extractAe(offer - left, false);
        });
    }

    private enum Listener implements IGridNodeListener<ConverterAeLink> {
        INSTANCE;

        @Override
        public void onSaveChanges(ConverterAeLink link, IGridNode node) {
            link.converter.setChanged();
        }
    }
}
