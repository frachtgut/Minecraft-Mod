package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.starfallen.Starfallen;
import net.starfallen.client.model.MeteorModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.projectile.MeteorEntity;
import net.starfallen.world.CraterBuilder;

/** A tumbling burning rock wrapped in a fiery corona with a long comet tail. */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/meteor.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/meteor_glow.png");
    private static final ResourceLocation FLARE = Starfallen.id("textures/entity/flare.png");
    private static final ResourceLocation TAIL = Starfallen.id("textures/environment/shooting_star.png");
    private final MeteorModel model;

    public MeteorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new MeteorModel(ctx.bakeLayer(SFModelLayers.METEOR));
    }

    @Override
    public boolean shouldRender(MeteorEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(MeteorEntity m, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float size = m.getSize();
        float age = m.tickCount + partial;
        boolean golden = m.getLoot() == CraterBuilder.Loot.GOLDEN;
        boolean egg = m.getLoot() == CraterBuilder.Loot.EGG;
        float r = 1.0F, g = golden ? 0.85F : 0.55F, b = golden ? 0.3F : egg ? 0.9F : 0.2F;
        pose.pushPose();
        pose.translate(0, m.getBbHeight() * 0.5F, 0);

        // Comet tail stretched back along the velocity
        Vec3 vel = m.getDeltaMovement();
        if (vel.lengthSqr() > 1.0E-4) {
            pose.pushPose();
            Vec3 dir = vel.normalize();
            float yawRad = (float) Math.atan2(dir.x, dir.z);
            float pitchRad = (float) Math.acos(Mth.clamp(-dir.y, -1, 1));
            pose.mulPose(Axis.YP.rotation(yawRad));
            pose.mulPose(Axis.XP.rotation(-pitchRad));
            float len = 6.0F + size * 5.0F;
            float w = 0.9F * size;
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TAIL));
            var mat = pose.last().pose();
            var nor = pose.last().normal();
            for (int k = 0; k < 2; k++) {
                float cx = k == 0 ? w : 0, cz = k == 0 ? 0 : w;
                RenderUtil.vertex(vc, mat, nor, -cx, 0, -cz, 1, 0, r, g, b, 1);
                RenderUtil.vertex(vc, mat, nor, cx, 0, cz, 1, 1, r, g, b, 1);
                RenderUtil.vertex(vc, mat, nor, cx * 0.2F, len, cz * 0.2F, 0, 1, r, g, b, 1);
                RenderUtil.vertex(vc, mat, nor, -cx * 0.2F, len, -cz * 0.2F, 0, 0, r, g, b, 1);
            }
            pose.popPose();
        }

        // Corona
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(age * 12));
        float pulse = 1.0F + 0.1F * Mth.sin(age * 0.9F);
        RenderUtil.billboard(pose, buffers.getBuffer(RenderType.eyes(FLARE)), size * 1.7F * pulse, r, g, b, 1.0F);
        pose.popPose();

        // The rock
        pose.pushPose();
        pose.scale(size, size, size);
        pose.mulPose(Axis.XP.rotationDegrees(age * 9));
        pose.mulPose(Axis.ZP.rotationDegrees(age * 6));
        pose.scale(-1, -1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(TEXTURE)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g + 0.2F, b + 0.2F, 1);
        pose.popPose();

        pose.popPose();
        super.render(m, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return TEXTURE;
    }
}
