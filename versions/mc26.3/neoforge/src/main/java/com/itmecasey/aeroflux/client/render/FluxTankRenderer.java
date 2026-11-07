package com.itmecasey.aeroflux.client.render;

import com.itmecasey.aeroflux.block.entity.TankView;
import com.itmecasey.aeroflux.core.FillLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

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

    /** What the renderer needs to know about one tank, copied out of the block entity each frame. */
    public static class State extends BlockEntityRenderState {
        /** The still sprite of the fluid, or null while the tank is empty. */
        @Nullable
        TextureAtlasSprite sprite;
        int color = -1;
        float fraction;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T tank, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;

        ResourceHandler<FluidResource> handler = tank.tank();
        FluidResource resource = handler.getResource(0);
        state.fraction = (float) FillLevel.fraction(handler.getAmountAsLong(0), handler.getCapacityAsLong(0, resource));
        if (resource.isEmpty() || state.fraction <= 0.0F) {
            return;
        }

        // The fluid's model is registered in ClientSetup; it knows the sprite and the flux tint.
        Fluid fluid = resource.getFluid();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
        state.sprite = model.stillMaterial().sprite();
        state.color = model.fluidTintSource() != null ? model.fluidTintSource().color(fluid.defaultFluidState()) : -1;
        state.lightCoords = LightCoordsUtil.pack(
                Math.max(LightCoordsUtil.block(state.lightCoords), GLOW), LightCoordsUtil.sky(state.lightCoords));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        float top = FLOOR + HEIGHT * state.fraction;
        // The sprite knows which atlas it sits on (the blocks atlas); the entity pipeline blends and lights it.
        RenderType layer = RenderTypes.entityTranslucent(sprite.atlasLocation());
        collector.submitCustomGeometry(poseStack, layer, (pose, consumer) ->
                drawBox(consumer, pose, sprite, state.color, state.lightCoords,
                        INSET, FLOOR, INSET, 1.0F - INSET, top, 1.0F - INSET));
    }

    /** Draws the six faces of a box; texture coordinates follow the box so the sprite does not stretch. */
    private static void drawBox(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite,
                                int color, int light, float x1, float y1, float z1, float x2, float y2, float z2) {
        // In 26.1 the sprite takes a 0 to 1 fraction of itself, not pixels as it did in 1.21.
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
        quad(consumer, pose, color, light, 0, 1, 0, x1, y2, z1, u1, tv1, x1, y2, z2, u1, tv2, x2, y2, z2, u2, tv2, x2, y2, z1, u2, tv1);
        quad(consumer, pose, color, light, 0, -1, 0, x1, y1, z1, u1, tv1, x2, y1, z1, u2, tv1, x2, y1, z2, u2, tv2, x1, y1, z2, u1, tv2);
        // North and south
        quad(consumer, pose, color, light, 0, 0, -1, x2, y1, z1, u2, v2, x1, y1, z1, u1, v2, x1, y2, z1, u1, v1, x2, y2, z1, u2, v1);
        quad(consumer, pose, color, light, 0, 0, 1, x1, y1, z2, u1, v2, x2, y1, z2, u2, v2, x2, y2, z2, u2, v1, x1, y2, z2, u1, v1);
        // West and east
        quad(consumer, pose, color, light, -1, 0, 0, x1, y1, z1, zu1, v2, x1, y1, z2, zu2, v2, x1, y2, z2, zu2, v1, x1, y2, z1, zu1, v1);
        quad(consumer, pose, color, light, 1, 0, 0, x2, y1, z2, zu2, v2, x2, y1, z1, zu1, v2, x2, y2, z1, zu1, v1, x2, y2, z2, zu2, v1);
    }

    /** One quad from four corners, each given as x, y, z, u, v. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int color, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        vertex(consumer, pose, color, light, nx, ny, nz, ax, ay, az, au, av);
        vertex(consumer, pose, color, light, nx, ny, nz, bx, by, bz, bu, bv);
        vertex(consumer, pose, color, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vertex(consumer, pose, color, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int color, int light,
                               float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
