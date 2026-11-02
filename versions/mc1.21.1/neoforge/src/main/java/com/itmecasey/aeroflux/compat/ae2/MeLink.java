package com.itmecasey.aeroflux.compat.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The AE2 side of a bundle's ME pipe: one grid node per bundle, exposed through the faces where
 * the ME pipe connects. AE2 then joins the nodes of neighboring bundles, cables and devices by
 * itself, so an ME pipe run carries a real ME network, as Ender IO's ME conduits do. A dense ME
 * pipe carries 32 channels instead of 8.
 *
 * <p>Only this package talks to AE2. The bundle keeps its link as a plain object and goes through
 * {@link Ae2Compat}, which is only used when AE2 is installed.
 */
public final class MeLink implements IInWorldGridNodeHost {
    private final ConduitBundleBlockEntity bundle;
    private final IManagedGridNode node;
    private final boolean dense;

    private MeLink(ConduitBundleBlockEntity bundle, boolean dense) {
        this.bundle = bundle;
        this.dense = dense;
        this.node = GridHelper.createManagedNode(this, Listener.INSTANCE)
                .setInWorldNode(true)
                .setTagName("aeroflux_me")
                .setIdlePowerUsage(0.0)
                .setVisualRepresentation(ModItems.pipe(dense ? PipeType.DENSE_ME : PipeType.ME, Tier.COPPER).get());
        node.setFlags(dense ? new GridFlags[] {GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY} : new GridFlags[] {GridFlags.PREFERRED});
    }

    /** A link for a bundle holding an ME or dense ME pipe, or {@code null} when it holds neither. */
    static @Nullable MeLink forBundle(ConduitBundleBlockEntity bundle) {
        BundleState state = bundle.bundle();
        if (state.set().contains(PipeType.DENSE_ME)) {
            return new MeLink(bundle, true);
        }
        if (state.set().contains(PipeType.ME)) {
            return new MeLink(bundle, false);
        }
        return null;
    }

    /** Whether this link still matches the pipe the bundle holds (it is rebuilt when ME changes to dense or back). */
    boolean matches(BundleState state) {
        return dense ? state.set().contains(PipeType.DENSE_ME) : state.set().contains(PipeType.ME);
    }

    /** Creates the node in the world once the bundle has ticked, as AE2 asks. */
    void start(Level level, BlockPos pos) {
        updateSides();
        GridHelper.onFirstTick(bundle, entity -> node.create(level, pos));
    }

    void stop() {
        node.destroy();
    }

    /** Exposes the node on exactly the faces where the ME pipe connects. */
    void updateSides() {
        BundleState state = bundle.bundle();
        int lane = PipeType.ME.lane();
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Face face : Face.values()) {
            if (state.isConnected(lane, face)) {
                sides.add(Direction.from3DDataValue(face.index()));
            }
        }
        node.setExposedOnSides(sides);
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction side) {
        return node.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return dense ? AECableType.DENSE_SMART : AECableType.SMART;
    }

    /** Saves the bundle when AE2 changes the node's data. */
    private enum Listener implements IGridNodeListener<MeLink> {
        INSTANCE;

        @Override
        public void onSaveChanges(MeLink link, IGridNode node) {
            link.bundle.setChanged();
        }
    }
}
