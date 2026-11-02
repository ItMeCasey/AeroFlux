package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.client.model.BundleModelData;
import com.itmecasey.aeroflux.compat.ae2.Ae2Compat;
import com.itmecasey.aeroflux.conduit.transfer.TransferEngine;
import com.itmecasey.aeroflux.conduit.transfer.TransferNetworks;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Remembers what is inside a {@link ConduitBundleBlock}: the pipes, their connections and their
 * modes. The data itself lives in a {@link BundleState}; this class saves it, sends it to
 * clients and keeps it up to date with the neighbors. On the server it also ticks, handing the
 * bundle's share of the transfer work to {@link TransferEngine}.
 */
public class ConduitBundleBlockEntity extends BlockEntity {
    private static final String TAG_BUNDLE = "bundle";
    private static final String TAG_REDSTONE = "redstone";
    /** The Filter and Priority upgrades at single faces. Missing in saves from before they existed. */
    private static final String TAG_ENDPOINTS = "endpoints";

    private BundleState bundle = new BundleState();
    private EndpointSettings endpoints = new EndpointSettings();
    /** The redstone level this bundle outputs at each face (indexed like {@link Face}); 0 where it exports nothing. */
    private final int[] redstoneOutput = new int[6];
    /** The AE2 grid node of the ME pipe, when AE2 is installed and there is one; see {@link Ae2Compat}. */
    @Nullable
    private Object meLink;

    public ConduitBundleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONDUIT_BUNDLE.get(), pos, state);
    }

    /** The bundle's data. Callers that change it must call {@link #changed()} afterwards. */
    public BundleState bundle() {
        return bundle;
    }

    /**
     * The Filter and Priority upgrades installed at single faces, and their settings. Callers
     * that change them must call {@link #changed()} afterwards, which also makes the transfer
     * engine pick the new settings up.
     */
    public EndpointSettings endpoints() {
        return endpoints;
    }

    /** Server tick: lets the transfer engine move things for the pipes in this bundle. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ConduitBundleBlockEntity entity) {
        if (level instanceof ServerLevel serverLevel && !entity.bundle.set().isEmpty()) {
            TransferEngine.tick(serverLevel, entity);
        }
    }

    /**
     * Adds a lowest-tier pipe if its lane is free.
     *
     * @return false if the lane is already taken
     */
    public boolean addPipe(PipeType type) {
        return addPipe(type, Tier.COPPER);
    }

    /**
     * Adds a pipe of the given tier if its lane is free.
     *
     * @return false if the lane is already taken
     */
    public boolean addPipe(PipeType type, Tier tier) {
        if (bundle.set().hasLaneFor(type)) {
            return false;
        }
        bundle.addPipe(type, tier);
        endpoints.clearLane(type.lane());
        refreshConnections();
        // Adding a pipe changes the bundle even when no connection changed, so always save and sync.
        changed();
        return true;
    }

    /** Looks at the neighbors again and updates the connections. Does nothing on the client. */
    public void refreshConnections() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (Connections.refresh(level, worldPosition, bundle)) {
            changed();
        }
    }

    /** The AE2 link of the ME pipe, or {@code null}; only {@link Ae2Compat} looks inside it. */
    @Nullable
    public Object meLink() {
        return meLink;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncMeLink();
    }

    /** Keeps the ME pipe's AE2 node in step with the bundle, and tells AE2 when it comes or goes. */
    private void syncMeLink() {
        Object before = meLink;
        meLink = Ae2Compat.sync(this, meLink);
        if (meLink != before && level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** Saves, redraws on clients, and lets neighboring bundles recheck their connections. */
    public void changed() {
        setChanged();
        if (level != null) {
            TransferNetworks.topologyChanged(level);
            syncMeLink();
            clearStaleRedstone();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    /** Called when the block entity goes away (broken or unloaded); the transfer engine forgets this position. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        Ae2Compat.stop(meLink);
        meLink = null;
        if (level != null) {
            TransferNetworks.forget(level, worldPosition);
        }
    }

    // --- Redstone output ---------------------------------------------------------------------

    /** Whether a redstone pipe in this bundle currently pushes a signal out through this face. */
    public boolean exportsRedstone(Face face) {
        int lane = PipeType.REDSTONE.lane();
        return bundle.set().contains(PipeType.REDSTONE)
                && bundle.isConnected(lane, face)
                && bundle.isExternal(lane, face)
                && bundle.mode(lane, face).pushesToNeighbor();
    }

    /** The redstone level this bundle outputs toward a neighbor on the given side; 0 if none. */
    public int redstoneOutput(Direction side) {
        Face face = Face.byIndex(side.get3DDataValue());
        return exportsRedstone(face) ? redstoneOutput[face.index()] : 0;
    }

    /**
     * Sets the output at one face. Neighbors are told only when the value really changes, so a
     * steady signal costs no block updates.
     */
    public void setRedstoneOutput(Face face, int value) {
        int next = exportsRedstone(face) ? value : 0;
        if (redstoneOutput[face.index()] == next) {
            return;
        }
        redstoneOutput[face.index()] = next;
        setChanged();
        if (level != null && !level.isClientSide) {
            Direction side = Direction.from3DDataValue(face.index());
            BlockPos target = worldPosition.relative(side);
            Block block = getBlockState().getBlock();
            level.neighborChanged(target, block, worldPosition);
            // Strong power also reaches the neighbors of the block we power.
            level.updateNeighborsAtExceptFromFacing(target, block, side.getOpposite());
        }
    }

    /** Zeroes outputs at faces that no longer export, for instance after the wrench switched one off. */
    private void clearStaleRedstone() {
        for (Face face : Face.values()) {
            if (redstoneOutput[face.index()] != 0 && !exportsRedstone(face)) {
                setRedstoneOutput(face, 0);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray(TAG_BUNDLE, bundle.serialize());
        tag.putIntArray(TAG_REDSTONE, redstoneOutput);
        if (!endpoints.isEmpty()) {
            tag.putString(TAG_ENDPOINTS, endpoints.encode());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bundle = BundleState.deserialize(tag.getIntArray(TAG_BUNDLE));
        int[] saved = tag.getIntArray(TAG_REDSTONE);
        for (int i = 0; i < redstoneOutput.length; i++) {
            redstoneOutput[i] = i < saved.length ? saved[i] : 0;
        }
        endpoints = EndpointSettings.decode(tag.getString(TAG_ENDPOINTS));
        endpoints.retainValid(bundle.set());
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Client side: new contents arrived from the server. Adding a pipe, joining a neighbor or
     * attaching to a machine leaves the block state as it was, so nothing else tells the chunk
     * renderer to rebuild; without this the new pipe or arm stays invisible until something else
     * nearby changes. {@link #loadAdditional} has already asked for fresh model data.
     */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(connection, packet);
        if (level != null && level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    /** AE2 finds the ME pipe's grid node through this; the bundle offers no other capability. */
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> node = Ae2Compat.gridNode(cap, meLink);
        return node.isPresent() ? node : super.getCapability(cap, side);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(BundleModelData.BUNDLE, bundle.copy()).build();
    }
}
