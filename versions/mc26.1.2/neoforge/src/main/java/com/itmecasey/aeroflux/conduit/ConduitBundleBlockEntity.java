package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.client.model.BundleModelData;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

/**
 * Remembers what is inside a {@link ConduitBundleBlock}: the pipes, their connections and their
 * modes. The data itself lives in a {@link BundleState}; this class saves it, sends it to
 * clients and keeps it up to date with the neighbors.
 */
public class ConduitBundleBlockEntity extends BlockEntity {
    private static final String TAG_BUNDLE = "bundle";

    private BundleState bundle = new BundleState();

    public ConduitBundleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONDUIT_BUNDLE.get(), pos, state);
    }

    /** The bundle's data. Callers that change it must call {@link #changed()} afterwards. */
    public BundleState bundle() {
        return bundle;
    }

    /**
     * Adds a pipe if its lane is free.
     *
     * @return false if the lane is already taken
     */
    public boolean addPipe(PipeType type) {
        if (bundle.set().hasLaneFor(type)) {
            return false;
        }
        bundle.addPipe(type);
        refreshConnections();
        // Refreshing only reports a change when a connection moved, and a lone new pipe may not
        // have moved any, so the new contents are saved and sent here regardless.
        changed();
        return true;
    }

    /** Looks at the neighbors again and updates the connections. Does nothing on the client. */
    public void refreshConnections() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (Connections.refresh(level, worldPosition, bundle)) {
            changed();
        }
    }

    /** Saves, redraws on clients, and lets neighboring bundles recheck their connections. */
    public void changed() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray(TAG_BUNDLE, bundle.serialize());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        bundle = BundleState.deserialize(input.getIntArray(TAG_BUNDLE).orElse(null));
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(BundleModelData.BUNDLE, bundle.copy()).build();
    }
}
