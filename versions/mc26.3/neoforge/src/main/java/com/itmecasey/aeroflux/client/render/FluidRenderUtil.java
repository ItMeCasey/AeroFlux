package com.itmecasey.aeroflux.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Looks up how a fluid is drawn and paints it into screens, so a gauge shows exactly the sprite
 * and tint the tank block shows in the world. Both come from the fluid's model, which the client
 * setup registers, so a resource pack that retextures the fluid retextures every gauge too.
 */
public final class FluidRenderUtil {
    /** Edge of a fluid sprite tile, in pixels. */
    private static final int TILE = 16;

    private FluidRenderUtil() {
    }

    private static FluidModel model(Fluid fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
    }

    /** The still sprite of a fluid from the block atlas. */
    public static TextureAtlasSprite stillSprite(Fluid fluid) {
        return model(fluid).stillMaterial().sprite();
    }

    /** The fluid's tint as opaque ARGB, or white if it has none. */
    public static int tint(Fluid fluid) {
        FluidState state = fluid.defaultFluidState();
        var source = model(fluid).fluidTintSource();
        return source == null ? 0xFFFFFFFF : 0xFF000000 | source.color(state);
    }

    /**
     * Fills a rectangle with the fluid sprite, tiled and anchored at the bottom so a rising level
     * shows the same pattern, and tinted. The rectangle is clipped, so the top row may be a part of a tile.
     */
    public static void drawTiled(GuiGraphicsExtractor graphics, TextureAtlasSprite sprite, int argb, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + height);
        for (int tileY = y + height - TILE; tileY > y - TILE; tileY -= TILE) {
            for (int tileX = x; tileX < x + width; tileX += TILE) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tileX, tileY, TILE, TILE, argb);
            }
        }
        graphics.disableScissor();
    }
}
