package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.starfallen.Starfallen;
import net.starfallen.client.model.AstraeonModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.boss.AstraeonEntity;
import org.joml.Matrix4f;

public class AstraeonRenderer extends MobRenderer<AstraeonEntity, AstraeonModel> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/astraeon.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/astraeon_glow.png");
    private static final ResourceLocation BEAM = Starfallen.id("textures/entity/void_beam.png");
    private static final float SCALE = 2.0F;

    public AstraeonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AstraeonModel(ctx.bakeLayer(SFModelLayers.ASTRAEON)), 2.0F);
        addLayer(new GlowLayer<>(this, e -> GLOW, (e, p) -> {
            float pulse = 0.85F + 0.15F * Mth.sin((e.tickCount + p) * 0.15F);
            if (e.getState() == AstraeonEntity.DYING) return new float[]{1, 1, 1};
            return e.isEnraged() ? new float[]{1.0F, 0.45F * pulse, 0.45F * pulse} : new float[]{pulse, pulse, pulse};
        }));
    }

    @Override
    public boolean shouldRender(AstraeonEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    protected void scale(AstraeonEntity boss, PoseStack pose, float partial) {
        float s = SCALE * Math.max(0.001F, boss.introProgress(partial));
        pose.scale(s, s, s);
    }

    @Override
    protected float getFlipDegrees(AstraeonEntity entity) {
        return 0.0F;
    }

    @Override
    protected int getBlockLightLevel(AstraeonEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(AstraeonEntity boss, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float intro = boss.introProgress(partial);
        if (intro > 0.001F) super.render(boss, yaw, partial, pose, buffers, light);
        long time = boss.level().getGameTime();
        int state = boss.getState();
        float t = boss.stateTicks + partial;

        // Awakening: a pillar of starlight pours down onto the altar
        if (state == AstraeonEntity.INTRO && t < AstraeonEntity.ROAR_AT + 10) {
            float a = Mth.clamp(t / 30.0F, 0, 1) * Mth.clamp((AstraeonEntity.ROAR_AT + 10 - t) / 20.0F, 0, 1);
            pose.pushPose();
            pose.translate(-0.5, -2, -0.5);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BEAM, partial, 1.0F, time, 0, 64,
                    new float[]{0.85F * a + 0.15F, 0.7F * a + 0.1F, 1.0F * a}, 0.4F + a * 0.6F, 1.2F + a);
            pose.popPose();
        }

        // The void beam
        if (state == AstraeonEntity.VOID_BEAM) {
            boolean firing = boss.isBeamFiring();
            float charge = Mth.clamp(t / AstraeonEntity.BEAM_CHARGE, 0, 1);
            if (firing || charge > 0.3F) {
                Vec3 eye = boss.getEyePosition(partial);
                Vec3 dir = Vec3.directionFromRotation(boss.getViewXRot(partial), boss.getViewYRot(partial));
                Vec3 end = firing ? boss.beamEnd(eye, dir) : eye.add(dir.scale(3 * charge));
                double len = end.distanceTo(eye);
                pose.pushPose();
                Vec3 base = boss.getPosition(partial);
                pose.translate(eye.x - base.x, eye.y - base.y, eye.z - base.z);
                // Rotate +Y onto the beam direction
                float yawRad = (float) Math.atan2(dir.x, dir.z);
                float pitchRad = (float) Math.acos(Mth.clamp(dir.y, -1, 1));
                pose.mulPose(Axis.YP.rotation(yawRad));
                pose.mulPose(Axis.XP.rotation(pitchRad));
                pose.translate(-0.5, 0, -0.5);
                float radius = firing ? 0.55F + 0.1F * Mth.sin((boss.tickCount + partial) * 1.3F) : 0.15F * charge;
                BeaconRenderer.renderBeaconBeam(pose, buffers, BEAM, partial, 2.0F, time, 0, Math.max(1, (int) Math.ceil(len)),
                        boss.isEnraged() ? new float[]{1.0F, 0.35F, 0.55F} : new float[]{0.8F, 0.4F, 1.0F}, radius, radius * 2.2F);
                pose.popPose();
            }
        }

        // Death: beams of light tear out of the dying star
        if (state == AstraeonEntity.DYING) {
            float f = Mth.clamp(t / AstraeonEntity.DEATH_TIME, 0, 1);
            renderDeathRays(pose, buffers, boss, f);
        }
    }

    private void renderDeathRays(PoseStack pose, MultiBufferSource buffers, AstraeonEntity boss, float f) {
        RandomSource rand = RandomSource.create(1337L);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.translate(0, boss.getBbHeight() * 0.55F, 0);
        int rays = (int) ((f + f * f) / 2.0F * 70);
        float fade = f > 0.85F ? 1.0F - (f - 0.85F) / 0.15F : 1.0F;
        for (int i = 0; i < rays; i++) {
            pose.mulPose(Axis.XP.rotationDegrees(rand.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(rand.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(rand.nextFloat() * 360.0F + f * 90.0F));
            float len = rand.nextFloat() * 14.0F + 6.0F + f * 10.0F;
            float width = rand.nextFloat() * 1.6F + 0.8F + f * 1.5F;
            Matrix4f m = pose.last().pose();
            int alpha = (int) (220 * fade);
            float h = 0.8660254F;
            // three thin triangles (quads with a repeated vertex)
            tri(vc, m, alpha, -h * width, len, -0.5F * width, h * width, len, -0.5F * width);
            tri(vc, m, alpha, h * width, len, -0.5F * width, 0, len, width);
            tri(vc, m, alpha, 0, len, width, -h * width, len, -0.5F * width);
        }
        pose.popPose();
    }

    private static void tri(VertexConsumer vc, Matrix4f m, int alpha, float x1, float y1, float z1, float x2, float y2, float z2) {
        vc.vertex(m, 0, 0, 0).color(255, 240, 210, alpha).endVertex();
        vc.vertex(m, x1, y1, z1).color(200, 120, 255, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(200, 120, 255, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(200, 120, 255, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(AstraeonEntity entity) {
        return TEXTURE;
    }
}
