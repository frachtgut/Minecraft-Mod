package net.starfallen.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.client.model.NebulaJellyModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.NebulaJellyEntity;
import org.jetbrains.annotations.Nullable;

public class NebulaJellyRenderer extends MobRenderer<NebulaJellyEntity, NebulaJellyModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/nebula_jelly.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/nebula_jelly_glow.png");

    public NebulaJellyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NebulaJellyModel(ctx.bakeLayer(SFModelLayers.NEBULA_JELLY)), 0.5F);
        addLayer(new GlowLayer<>(this, e -> GLOW, (e, p) -> {
            float f = e.zapTimer > 0 ? 1.0F : 0.7F + 0.3F * Mth.sin((e.tickCount + p) * 0.1F);
            return e.isAngry() ? new float[]{1.0F, f * 0.7F, f * 0.9F} : new float[]{f, f, f};
        }));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(NebulaJellyEntity entity, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }

    @Override
    protected int getBlockLightLevel(NebulaJellyEntity entity, BlockPos pos) {
        return Math.max(11, super.getBlockLightLevel(entity, pos));
    }

    @Override
    public ResourceLocation getTextureLocation(NebulaJellyEntity entity) {
        return TEXTURE;
    }
}
