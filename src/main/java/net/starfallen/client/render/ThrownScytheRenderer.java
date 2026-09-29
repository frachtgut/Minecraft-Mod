package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.starfallen.entity.projectile.ThrownScytheEntity;

/** The thrown scythe spins flat like a sawblade. */
public class ThrownScytheRenderer extends EntityRenderer<ThrownScytheEntity> {
    private final ItemRenderer items;

    public ThrownScytheRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(ThrownScytheEntity scythe, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.25, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-(scythe.tickCount + partial) * 42));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(2.2F, 2.2F, 2.2F);
        items.renderStatic(scythe.getStack(), ItemDisplayContext.FIXED, RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                scythe.level(), scythe.getId());
        pose.popPose();
        super.render(scythe, yaw, partial, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownScytheEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
