package com.itmecasey.aeroflux.compat.create;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The client side of the Create support; only called with Create installed. */
public final class CreateClient {
    private CreateClient() {
    }

    /** Gives the Stress converter its turning shaft. */
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateContent.STRESS_CONVERTER.get(), StressConverterRenderer::new);
    }
}
