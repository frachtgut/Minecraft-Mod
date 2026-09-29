package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.SingularityModel;
import net.starfallen.entity.projectile.SingularityEntity;

/** A pitch-black core inside a swirling accretion disk and a lensing halo. */
public class SingularityRenderer extends EntityRenderer<SingularityEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/singularity.png");
    private static final ResourceLocation DISK = Starfallen.id("textures/entity/accretion_disk.png");
    private static final ResourceLocation HALO = Starfallen.id("textures/entity/flare.png");
    private final SingularityModel model;

    public SingularityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new SingularityModel(ctx.bakeLayer(SFModelLayers.SINGULARITY));
    }

    @Override
    public void render(SingularityEntity s, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = s.tickCount + partial;
        float grow = s.isActive() ? Mth.clamp((s.getActiveAge() + partial) / 10.0F, 0, 1) : 0.35F;
        float collapse = s.isActive() ? Mth.clamp((SingularityEntity.ACTIVE_TIME - s.getActiveAge() - partial) / 10.0F, 0, 1) : 1;
        float scale = (s.isGreater() ? 1.7F : 1.0F) * (0.3F + 0.7F * grow) * (0.4F + 0.6F * collapse);
        pose.pushPose();
        pose.translate(0, 0.5, 0);

        // Accretion disk
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(18));
        pose.mulPose(Axis.YP.rotationDegrees(age * 14));
        RenderUtil.flatQuad(pose, buffers.getBuffer(RenderType.eyes(DISK)), 3.2F * scale, 0.9F, 0.55F, 1.0F, 1.0F);
        pose.mulPose(Axis.YP.rotationDegrees(-age * 22));
        RenderUtil.flatQuad(pose, buffers.getBuffer(RenderType.eyes(DISK)), 2.2F * scale, 1.0F, 0.8F, 0.6F, 1.0F);
        pose.popPose();

        // Lensing halo
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        RenderUtil.billboard(pose, buffers.getBuffer(RenderType.eyes(HALO)), 1.6F * scale, 0.45F, 0.2F, 0.8F, 1.0F);
        pose.popPose();

        // Event horizon
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(age * 5));
        pose.mulPose(Axis.XP.rotationDegrees(age * 3));
        pose.translate(0, -0.0, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();

        pose.popPose();
        super.render(s, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SingularityEntity entity) {
        return TEXTURE;
    }
}
