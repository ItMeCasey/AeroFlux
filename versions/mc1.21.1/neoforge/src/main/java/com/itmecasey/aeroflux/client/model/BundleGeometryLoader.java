package com.itmecasey.aeroflux.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

/** Reads the {@code aeroflux:conduit_bundle} model type. The model file only names textures. */
public class BundleGeometryLoader implements IGeometryLoader<BundleGeometry> {
    @Override
    public BundleGeometry read(JsonObject jsonObject, JsonDeserializationContext context) {
        return new BundleGeometry();
    }
}
