package com.itmecasey.aeroflux.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Looks up how a fluid is drawn and paints it into screens, so a gauge shows exactly the sprite
 * and tint the tank block shows in the world. Both come from the fluid's client extensions, so a
 * resource pack that retextures the fluid retextures every gauge too.
 */
public final class FluidRenderUtil {
    /** Edge of a fluid sprite tile, in pixels. */
    private static final int TILE = 16;

    private FluidRenderUtil() {
    }

    /** The still sprite of a fluid from the block atlas, where the world's own fluid model finds it too. */
    public static TextureAtlasSprite stillSprite(Fluid fluid) {
        Identifier texture = IClientFluidTypeExtensions.of(fluid).getStillTexture();
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
    }

    /** The fluid's tint as ARGB. */
    public static int tint(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor();
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
