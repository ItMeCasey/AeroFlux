package com.itmecasey.aeroflux.client.render;

import com.itmecasey.aeroflux.block.entity.TankView;
import com.itmecasey.aeroflux.core.FillLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;

/**
 * Draws the fluid inside a tank's glass. The fluid is a box that fills the glass from the bottom
 * up to the tank's fill level, textured with the fluid's own animated sprite and tinted with its
 * flux color, and it glows a little so a full tank stays readable in the dark. It serves every
 * {@link TankView}: the Flux Tanks and the Creative Flux Tank.
 */
public class FluxTankRenderer<T extends BlockEntity & TankView> implements BlockEntityRenderer<T> {
    /** The fluid box sits just inside the glass, which spans 2 to 14 pixels. */
    private static final float INSET = 2.25F / 16.0F;
    private static final float FLOOR = 2.25F / 16.0F;
    private static final float HEIGHT = 11.5F / 16.0F;
    /** The faint glow, as a minimum block light level. */
    private static final int GLOW = 10;

    public FluxTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T tank, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FluidStack fluid = tank.tank().getFluidInTank(0);
        double fraction = FillLevel.fraction(fluid.getAmount(), tank.tank().getTankCapacity(0));
        if (fluid.isEmpty() || fraction <= 0.0) {
            return;
        }

        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int color = fluidExtensions.getTintColor(fluid);
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), GLOW), LightTexture.sky(packedLight));

        float top = FLOOR + HEIGHT * (float) fraction;
        VertexConsumer consumer = buffer.getBuffer(RenderType.translucent());
        drawBox(consumer, poseStack.last().pose(), poseStack.last(), sprite, color, light,
                INSET, FLOOR, INSET, 1.0F - INSET, top, 1.0F - INSET);
    }

    /** Draws the six faces of a box; texture coordinates follow the box so the sprite does not stretch. */
    private static void drawBox(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, TextureAtlasSprite sprite,
                                int color, int light, float x1, float y1, float z1, float x2, float y2, float z2) {
        float u1 = sprite.getU(x1 * 16.0F);
        float u2 = sprite.getU(x2 * 16.0F);
        float zu1 = sprite.getU(z1 * 16.0F);
        float zu2 = sprite.getU(z2 * 16.0F);
        // Side faces use the height, measured from the bottom of the sprite.
        float v1 = sprite.getV(16.0F - y2 * 16.0F);
        float v2 = sprite.getV(16.0F - y1 * 16.0F);
        float tv1 = sprite.getV(z1 * 16.0F);
        float tv2 = sprite.getV(z2 * 16.0F);

        // Top and bottom
        quad(consumer, matrix, pose, color, light, 0, 1, 0, x1, y2, z1, u1, tv1, x1, y2, z2, u1, tv2, x2, y2, z2, u2, tv2, x2, y2, z1, u2, tv1);
        quad(consumer, matrix, pose, color, light, 0, -1, 0, x1, y1, z1, u1, tv1, x2, y1, z1, u2, tv1, x2, y1, z2, u2, tv2, x1, y1, z2, u1, tv2);
        // North and south
        quad(consumer, matrix, pose, color, light, 0, 0, -1, x2, y1, z1, u2, v2, x1, y1, z1, u1, v2, x1, y2, z1, u1, v1, x2, y2, z1, u2, v1);
        quad(consumer, matrix, pose, color, light, 0, 0, 1, x1, y1, z2, u1, v2, x2, y1, z2, u2, v2, x2, y2, z2, u2, v1, x1, y2, z2, u1, v1);
        // West and east
        quad(consumer, matrix, pose, color, light, -1, 0, 0, x1, y1, z1, zu1, v2, x1, y1, z2, zu2, v2, x1, y2, z2, zu2, v1, x1, y2, z1, zu1, v1);
        quad(consumer, matrix, pose, color, light, 1, 0, 0, x2, y1, z2, zu2, v2, x2, y1, z1, zu1, v2, x2, y2, z1, zu1, v1, x2, y2, z2, zu2, v1);
    }

    /** One quad from four corners, each given as x, y, z, u, v. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, int color, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        vertex(consumer, matrix, pose, color, light, nx, ny, nz, ax, ay, az, au, av);
        vertex(consumer, matrix, pose, color, light, nx, ny, nz, bx, by, bz, bu, bv);
        vertex(consumer, matrix, pose, color, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vertex(consumer, matrix, pose, color, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, int color, int light,
                               float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
