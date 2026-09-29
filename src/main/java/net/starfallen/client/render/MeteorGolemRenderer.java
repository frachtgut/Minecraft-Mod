package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.client.model.MeteorGolemModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.MeteorGolemEntity;

public class MeteorGolemRenderer extends MobRenderer<MeteorGolemEntity, MeteorGolemModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/meteor_golem.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/meteor_golem_glow.png");

    public MeteorGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MeteorGolemModel(ctx.bakeLayer(SFModelLayers.METEOR_GOLEM)), 1.2F);
        addLayer(new GlowLayer<>(this, e -> GLOW, (e, p) -> {
            float pulse = 0.75F + 0.25F * Mth.sin((e.tickCount + p) * (e.isOverheated() ? 0.5F : 0.12F));
            return e.isOverheated() ? new float[]{1.0F, pulse, pulse * 0.6F} : new float[]{pulse, pulse, pulse};
        }));
    }

    @Override
    protected void scale(MeteorGolemEntity entity, PoseStack pose, float partial) {
        pose.scale(0.85F, 0.85F, 0.85F);
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorGolemEntity entity) {
        return TEXTURE;
    }
}
