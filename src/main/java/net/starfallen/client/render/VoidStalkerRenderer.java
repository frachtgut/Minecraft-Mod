package net.starfallen.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.VoidStalkerModel;
import net.starfallen.entity.VoidStalkerEntity;

public class VoidStalkerRenderer extends MobRenderer<VoidStalkerEntity, VoidStalkerModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/void_stalker.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/void_stalker_glow.png");

    public VoidStalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VoidStalkerModel(ctx.bakeLayer(SFModelLayers.VOID_STALKER)), 0.4F);
        addLayer(new GlowLayer<>(this, e -> GLOW, (e, p) -> {
            // Its eye burns brighter when nobody is watching
            float f = e.isObserved() ? 0.55F : 0.85F + 0.15F * Mth.sin((e.tickCount + p) * 0.3F);
            return new float[]{f, f, f};
        }));
    }

    @Override
    public ResourceLocation getTextureLocation(VoidStalkerEntity entity) {
        return TEXTURE;
    }
}
