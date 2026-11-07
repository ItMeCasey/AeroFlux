package com.itmecasey.aeroflux.client.model;

import com.itmecasey.aeroflux.core.conduit.BundleState;
import net.neoforged.neoforge.model.data.ModelProperty;

/** The model data property that carries a bundle's contents from its block entity to its model. */
public final class BundleModelData {
    /** A snapshot of the bundle, safe to read on the chunk-meshing threads. */
    public static final ModelProperty<BundleState> BUNDLE = new ModelProperty<>();

    private BundleModelData() {
    }
}
