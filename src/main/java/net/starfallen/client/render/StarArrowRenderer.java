package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.starfallen.Starfallen;
import net.starfallen.entity.projectile.StarArrowEntity;

public class StarArrowRenderer extends ArrowRenderer<StarArrowEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/star_arrow.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/star_bolt.png");

    public StarArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(StarArrowEntity arrow, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(arrow, yaw, partial, pose, buffers, light);
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees((arrow.tickCount + partial) * 15));
        RenderUtil.billboard(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0.28F, 0.55F, 0.95F, 1.0F, 1.0F);
        pose.popPose();
    }

    @Override
    protected int getBlockLightLevel(StarArrowEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(StarArrowEntity entity) {
        return TEXTURE;
    }
}
