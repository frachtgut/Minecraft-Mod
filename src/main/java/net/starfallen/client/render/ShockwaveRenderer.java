package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.starfallen.Starfallen;
import net.starfallen.entity.projectile.ShockwaveEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A glowing ring of force skimming along the ground. */
public class ShockwaveRenderer extends EntityRenderer<ShockwaveEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/shockwave.png");

    public ShockwaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(ShockwaveEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ShockwaveEntity wave, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float r = wave.getRadius(partial);
        float fade = 1.0F - Mth.clamp(r / wave.getMaxRadius(), 0, 1) * 0.7F;
        float cr = wave.isVoid() ? 0.8F : 1.0F, cg = wave.isVoid() ? 0.45F : 0.75F, cb = wave.isVoid() ? 1.0F : 0.35F;
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int segs = Math.max(24, (int) (r * 6));
        float inner = Math.max(0, r - 0.9F), outer = r + 0.35F, h = 0.9F;
        for (int i = 0; i < segs; i++) {
            float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = i / (float) segs * 8, u1 = (i + 1) / (float) segs * 8;
            // ground ring
            RenderUtil.vertex(vc, m, n, c0 * inner, 0.05F, s0 * inner, u0, 1, cr, cg, cb, fade);
            RenderUtil.vertex(vc, m, n, c0 * outer, 0.05F, s0 * outer, u0, 0, cr, cg, cb, fade);
            RenderUtil.vertex(vc, m, n, c1 * outer, 0.05F, s1 * outer, u1, 0, cr, cg, cb, fade);
            RenderUtil.vertex(vc, m, n, c1 * inner, 0.05F, s1 * inner, u1, 1, cr, cg, cb, fade);
            // standing wall of light
            RenderUtil.vertex(vc, m, n, c0 * r, 0.0F, s0 * r, u0, 0, cr, cg, cb, fade);
            RenderUtil.vertex(vc, m, n, c0 * r, h, s0 * r, u0, 1, cr, cg, cb, 0);
            RenderUtil.vertex(vc, m, n, c1 * r, h, s1 * r, u1, 1, cr, cg, cb, 0);
            RenderUtil.vertex(vc, m, n, c1 * r, 0.0F, s1 * r, u1, 0, cr, cg, cb, fade);
            RenderUtil.vertex(vc, m, n, c1 * r, 0.0F, s1 * r, u1, 0, cr, cg, cb, fade);
            RenderUtil.vertex(vc, m, n, c1 * r, h, s1 * r, u1, 1, cr, cg, cb, 0);
            RenderUtil.vertex(vc, m, n, c0 * r, h, s0 * r, u0, 1, cr, cg, cb, 0);
            RenderUtil.vertex(vc, m, n, c0 * r, 0.0F, s0 * r, u0, 0, cr, cg, cb, fade);
        }
        super.render(wave, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShockwaveEntity entity) {
        return TEXTURE;
    }
}
