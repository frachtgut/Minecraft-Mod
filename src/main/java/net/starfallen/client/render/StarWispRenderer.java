package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.starfallen.Starfallen;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.StarWispModel;
import net.starfallen.entity.StarWispEntity;
import org.jetbrains.annotations.Nullable;

public class StarWispRenderer extends MobRenderer<StarWispEntity, StarWispModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/star_wisp.png");
    /** gold, cyan, rose, violet, white */
    private static final float[][] TINTS = {{1.0F, 0.86F, 0.45F}, {0.45F, 0.95F, 1.0F}, {1.0F, 0.55F, 0.8F}, {0.75F, 0.5F, 1.0F}, {0.95F, 0.97F, 1.0F}};

    public StarWispRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new StarWispModel(ctx.bakeLayer(SFModelLayers.STAR_WISP)), 0.15F);
    }

    @Override
    public void render(StarWispEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float[] t = TINTS[Math.floorMod(entity.getVariant(), TINTS.length)];
        getModel().tint(t[0], t[1], t[2]);
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(StarWispEntity entity, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucentEmissive(TEXTURE);
    }

    @Override
    protected int getBlockLightLevel(StarWispEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(StarWispEntity entity) {
        return TEXTURE;
    }
}
