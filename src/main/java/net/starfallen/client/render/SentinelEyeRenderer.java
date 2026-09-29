package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.blockentity.SentinelEyeBlockEntity;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.gen.SentinelEyeGeometry;

/** A floating eyeball inside the sentinel's cage that follows the nearest intruder. */
public class SentinelEyeRenderer implements BlockEntityRenderer<SentinelEyeBlockEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/sentinel_eye.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/sentinel_eye_glow.png");
    private final ModelPart eye;

    public SentinelEyeRenderer(BlockEntityRendererProvider.Context ctx) {
        this.eye = ctx.bakeLayer(SFModelLayers.SENTINEL_EYE).getChild("eye");
    }

    public static LayerDefinition createEyeLayer() {
        return SentinelEyeGeometry.create();
    }

    @Override
    public void render(SentinelEyeBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float yaw = Mth.rotLerp(partial, be.prevYaw * Mth.RAD_TO_DEG, be.yaw * Mth.RAD_TO_DEG);
        float pitch = Mth.lerp(partial, be.prevPitch, be.pitch) * Mth.RAD_TO_DEG;
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partial;
        float charge = be.charge / (float) SentinelEyeBlockEntity.CHARGE_TIME;
        pose.pushPose();
        pose.translate(0.5, 0.5 + Mth.sin(time * 0.1F) * 0.03F, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        float s = 1.0F + charge * 0.12F;
        pose.scale(s, s, s);
        eye.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        float glow = 0.4F + charge * 0.6F;
        eye.render(pose, buffers.getBuffer(RenderType.eyes(GLOW)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F);
        pose.popPose();
    }
}
