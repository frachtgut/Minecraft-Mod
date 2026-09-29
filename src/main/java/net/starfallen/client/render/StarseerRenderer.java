package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.starfallen.Starfallen;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.StarseerModel;
import net.starfallen.entity.StarseerEntity;

public class StarseerRenderer extends MobRenderer<StarseerEntity, StarseerModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/starseer.png");
    private static final ResourceLocation ELITE = Starfallen.id("textures/entity/high_starseer.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/starseer_glow.png");

    public StarseerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new StarseerModel(ctx.bakeLayer(SFModelLayers.STARSEER)), 0.45F);
        addLayer(new GlowLayer<>(this, e -> GLOW, (e, p) -> e.isElite() ? new float[]{1.0F, 0.85F, 0.5F}
                : e.getSpell() != StarseerEntity.SPELL_NONE ? new float[]{1, 1, 1} : new float[]{0.7F, 0.7F, 0.75F}));
    }

    @Override
    protected void scale(StarseerEntity entity, PoseStack pose, float partial) {
        if (entity.isElite()) pose.scale(1.25F, 1.25F, 1.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(StarseerEntity entity) {
        return entity.isElite() ? ELITE : TEXTURE;
    }
}
