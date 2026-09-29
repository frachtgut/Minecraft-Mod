package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.client.model.CometRayModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.CometRayEntity;

public class CometRayRenderer extends MobRenderer<CometRayEntity, CometRayModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/comet_ray.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/comet_ray_glow.png");

    public CometRayRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CometRayModel(ctx.bakeLayer(SFModelLayers.COMET_RAY)), 0.9F);
        addLayer(new GlowLayer<>(this, e -> GLOW, (e, p) -> {
            float f = e.isBoosting() ? 1.0F : 0.65F + 0.35F * Mth.sin((e.tickCount + p) * 0.08F);
            return new float[]{f, f, f};
        }));
    }

    @Override
    protected void scale(CometRayEntity entity, PoseStack pose, float partial) {
        pose.scale(1.1F, 1.1F, 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(CometRayEntity entity) {
        return TEXTURE;
    }
}
