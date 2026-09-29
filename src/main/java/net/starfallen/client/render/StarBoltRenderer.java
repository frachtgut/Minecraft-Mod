package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.entity.projectile.StarBoltEntity;

public class StarBoltRenderer extends EntityRenderer<StarBoltEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/star_bolt.png");
    private static final float[][] COLORS = {{1.0F, 0.85F, 0.4F}, {0.8F, 0.4F, 1.0F}, {0.45F, 0.95F, 1.0F}};

    public StarBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(StarBoltEntity bolt, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float[] c = COLORS[Math.floorMod(bolt.getVariant(), COLORS.length)];
        float age = bolt.tickCount + partial;
        pose.pushPose();
        pose.translate(0, 0.2, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(age * 20));
        float s = 0.35F + 0.05F * Mth.sin(age * 1.5F);
        var vc = buffers.getBuffer(RenderType.eyes(TEXTURE));
        RenderUtil.billboard(pose, vc, s, c[0], c[1], c[2], 1.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(45));
        RenderUtil.billboard(pose, vc, s * 0.6F, 1, 1, 1, 1.0F);
        pose.popPose();
        super.render(bolt, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StarBoltEntity entity) {
        return TEXTURE;
    }
}
