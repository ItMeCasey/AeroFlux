package com.itmecasey.aeroflux.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
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

    /** The still sprite of a fluid from the block atlas. */
    public static TextureAtlasSprite stillSprite(Fluid fluid) {
        ResourceLocation texture = IClientFluidTypeExtensions.of(fluid).getStillTexture();
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    /** The fluid's tint as ARGB. */
    public static int tint(Fluid fluid) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor();
    }

    /**
     * Fills a rectangle with the fluid sprite, tiled and anchored at the bottom so a rising level
     * shows the same pattern, and tinted. The rectangle is clipped, so the top row may be a part of a tile.
     */
    public static void drawTiled(GuiGraphics graphics, TextureAtlasSprite sprite, int argb, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float red = (argb >> 16 & 0xFF) / 255.0F;
        float green = (argb >> 8 & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        float alpha = (argb >>> 24) / 255.0F;
        graphics.enableScissor(x, y, x + width, y + height);
        for (int tileY = y + height - TILE; tileY > y - TILE; tileY -= TILE) {
            for (int tileX = x; tileX < x + width; tileX += TILE) {
                graphics.blit(tileX, tileY, 0, TILE, TILE, sprite, red, green, blue, alpha);
            }
        }
        graphics.disableScissor();
    }
}
