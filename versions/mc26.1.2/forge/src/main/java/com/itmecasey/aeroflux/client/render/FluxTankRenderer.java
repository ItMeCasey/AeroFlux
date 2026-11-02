package com.itmecasey.aeroflux.client.render;

import com.itmecasey.aeroflux.block.entity.TankView;
import com.itmecasey.aeroflux.core.FillLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the fluid inside a tank's glass. The fluid is a box that fills the glass from the bottom
 * up to the tank's fill level, textured with the fluid's own animated sprite and tinted with its
 * flux color, and it glows a little so a full tank stays readable in the dark. It serves every
 * {@link TankView}: the Flux Tanks and the Creative Flux Tank.
 */
public class FluxTankRenderer<T extends BlockEntity & TankView> implements BlockEntityRenderer<T, FluxTankRenderer.State> {
    /** The fluid box sits just inside the glass, which spans 2 to 14 pixels. */
    private static final float INSET = 2.25F / 16.0F;
    private static final float FLOOR = 2.25F / 16.0F;
    private static final float HEIGHT = 11.5F / 16.0F;
    /** The faint glow, as a minimum block light level. */
    private static final int GLOW = 10;

    public FluxTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** What the renderer needs from a tank, copied out so drawing can happen later and off to the side. */
    public static class State extends BlockEntityRenderState {
        /** How full the tank is, from 0 to 1. */
        double fraction;
        /** The fluid's tint as ARGB. */
        int color;
        /** The animated still sprite, or null while the tank is empty. */
        @Nullable
        TextureAtlasSprite sprite;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T tank, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, cameraPosition, breakProgress);
        FluidStack fluid = tank.tank().getFluid();
        state.fraction = FillLevel.fraction(fluid.getAmount(), tank.tank().getCapacity());
        if (fluid.isEmpty() || state.fraction <= 0.0) {
            state.sprite = null;
            return;
        }
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        Identifier stillTexture = fluidExtensions.getStillTexture(fluid);
        // The fluid textures live in the blocks atlas, where the world's own fluid model finds them too.
        state.sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(stillTexture);
        state.color = fluidExtensions.getTintColor(fluid);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        int light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(state.lightCoords), GLOW),
                LightCoordsUtil.sky(state.lightCoords));
        float top = FLOOR + HEIGHT * (float) state.fraction;
        int color = state.color;
        // The moving-block translucent type reads the blocks atlas and blends, which is what a fluid wants.
        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
                drawBox(buffer, pose.pose(), sprite, color, light, INSET, FLOOR, INSET, 1.0F - INSET, top, 1.0F - INSET));
    }

    /** Draws the six faces of a box; texture coordinates follow the box so the sprite does not stretch. */
    private static void drawBox(VertexConsumer consumer, Matrix4f matrix, TextureAtlasSprite sprite,
                                int color, int light, float x1, float y1, float z1, float x2, float y2, float z2) {
        // Sprite offsets run from 0 to 1 across the whole sprite, the same as block coordinates.
        float u1 = sprite.getU(x1);
        float u2 = sprite.getU(x2);
        float zu1 = sprite.getU(z1);
        float zu2 = sprite.getU(z2);
        // Side faces use the height, measured from the bottom of the sprite.
        float v1 = sprite.getV(1.0F - y2);
        float v2 = sprite.getV(1.0F - y1);
        float tv1 = sprite.getV(z1);
        float tv2 = sprite.getV(z2);

        // Top and bottom
        quad(consumer, matrix, color, light, x1, y2, z1, u1, tv1, x1, y2, z2, u1, tv2, x2, y2, z2, u2, tv2, x2, y2, z1, u2, tv1);
        quad(consumer, matrix, color, light, x1, y1, z1, u1, tv1, x2, y1, z1, u2, tv1, x2, y1, z2, u2, tv2, x1, y1, z2, u1, tv2);
        // North and south
        quad(consumer, matrix, color, light, x2, y1, z1, u2, v2, x1, y1, z1, u1, v2, x1, y2, z1, u1, v1, x2, y2, z1, u2, v1);
        quad(consumer, matrix, color, light, x1, y1, z2, u1, v2, x2, y1, z2, u2, v2, x2, y2, z2, u2, v1, x1, y2, z2, u1, v1);
        // West and east
        quad(consumer, matrix, color, light, x1, y1, z1, zu1, v2, x1, y1, z2, zu2, v2, x1, y2, z2, zu2, v1, x1, y2, z1, zu1, v1);
        quad(consumer, matrix, color, light, x2, y1, z2, zu2, v2, x2, y1, z1, zu1, v2, x2, y2, z1, zu1, v1, x2, y2, z2, zu2, v1);
    }

    /** One quad from four corners, each given as x, y, z, u, v. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, int color, int light,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        vertex(consumer, matrix, color, light, ax, ay, az, au, av);
        vertex(consumer, matrix, color, light, bx, by, bz, bu, bv);
        vertex(consumer, matrix, color, light, cx, cy, cz, cu, cv);
        vertex(consumer, matrix, color, light, dx, dy, dz, du, dv);
    }

    /** The block vertex format carries only position, color, texture and light; there are no normals. */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, int color, int light,
                               float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setLight(light);
    }
}
