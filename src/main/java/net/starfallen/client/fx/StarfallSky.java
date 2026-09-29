package net.starfallen.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.starfallen.Starfallen;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Sky effects: during a Starfall the night sky fills with shooting stars and a shimmering
 * nebula; while Astraeon lives, the sun (or moon) is swallowed by a burning eclipse.
 */
public final class StarfallSky {
    private static final ResourceLocation STREAK = Starfallen.id("textures/environment/shooting_star.png");
    private static final ResourceLocation NEBULA = Starfallen.id("textures/environment/nebula.png");
    private static final ResourceLocation ECLIPSE = Starfallen.id("textures/environment/eclipse.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean active;
    private static float intensity, prevIntensity;
    private static float eclipse, prevEclipse;
    private static boolean bossNear;
    private static int ticks;

    private record Streak(Vec3 start, Vec3 dir, float length, float speed, int life, int born) {}

    private static final List<Streak> STREAKS = new ArrayList<>();

    private StarfallSky() {}

    public static void setActive(boolean on) {
        active = on;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setBossNear(boolean near) {
        bossNear = near;
    }

    public static float intensity(float partial) {
        return Mth.lerp(partial, prevIntensity, intensity);
    }

    public static float eclipse(float partial) {
        return Mth.lerp(partial, prevEclipse, eclipse);
    }

    public static void reset() {
        active = false;
        intensity = prevIntensity = 0;
        eclipse = prevEclipse = 0;
        STREAKS.clear();
    }

    public static void tick(ClientLevel level) {
        ticks++;
        prevIntensity = intensity;
        prevEclipse = eclipse;
        boolean overworld = level.dimension() == Level.OVERWORLD;
        float target = active && overworld ? 1.0F : 0.0F;
        intensity += (target - intensity) * 0.02F;
        float eTarget = bossNear ? 1.0F : 0.0F;
        eclipse += (eTarget - eclipse) * 0.03F;
        STREAKS.removeIf(s -> ticks - s.born > s.life);
        if (intensity > 0.05F && RANDOM.nextFloat() < 0.18F * intensity) {
            double yaw = RANDOM.nextDouble() * Math.PI * 2;
            double pitch = Math.toRadians(25 + RANDOM.nextDouble() * 55);
            Vec3 start = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            // Mostly falling, sideways
            Vec3 side = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(RANDOM.nextBoolean() ? 1 : -1);
            Vec3 dir = side.add(0, -0.35 - RANDOM.nextDouble() * 0.4, 0).normalize();
            STREAKS.add(new Streak(start, dir, 0.12F + RANDOM.nextFloat() * 0.18F, 0.012F + RANDOM.nextFloat() * 0.02F,
                    18 + RANDOM.nextInt(14), ticks));
        }
    }

    public static void render(PoseStack pose, Matrix4f projection, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float in = intensity(partial);
        float ec = eclipse(partial);
        if (in <= 0.01F && ec <= 0.01F) return;
        float dayFactor = Mth.clamp((float) Math.cos(level.getTimeOfDay(partial) * Math.PI * 2) * 2.0F + 0.5F, 0.0F, 1.0F);
        float nightFactor = 1.0F - dayFactor * 0.8F;

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        if (in > 0.01F) {
            // Nebula band
            RenderSystem.setShaderTexture(0, NEBULA);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(ticks * 0.01F + partial * 0.01F + 30F));
            pose.mulPose(Axis.XP.rotationDegrees(-20F));
            Matrix4f m = pose.last().pose();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float a = 0.55F * in * nightFactor;
            float scroll = (ticks + partial) * 0.0002F;
            int segs = 16;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
                float u0 = i / (float) segs * 2 + scroll, u1 = (i + 1) / (float) segs * 2 + scroll;
                float r = 100;
                float y0 = 25, y1 = 60;
                vertex(buf, m, (float) Math.cos(a0) * r, y0, (float) Math.sin(a0) * r, u0, 1, 1, 1, 1, 0);
                vertex(buf, m, (float) Math.cos(a1) * r, y0, (float) Math.sin(a1) * r, u1, 1, 1, 1, 1, 0);
                vertex(buf, m, (float) Math.cos(a1) * r * 0.7F, y1, (float) Math.sin(a1) * r * 0.7F, u1, 0, 1, 1, 1, a);
                vertex(buf, m, (float) Math.cos(a0) * r * 0.7F, y1, (float) Math.sin(a0) * r * 0.7F, u0, 0, 1, 1, 1, a);
            }
            BufferUploader.drawWithShader(buf.end());
            pose.popPose();

            // Shooting stars
            RenderSystem.setShaderTexture(0, STREAK);
            Matrix4f m2 = pose.last().pose();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Streak s : STREAKS) {
                float age = ticks - s.born + partial;
                float lifeF = age / s.life;
                float fade = Mth.sin(Mth.clamp(lifeF, 0, 1) * (float) Math.PI) * in * nightFactor;
                Vec3 head = s.start.add(s.dir.scale(age * s.speed)).normalize().scale(95);
                Vec3 tail = s.start.add(s.dir.scale(age * s.speed - s.length)).normalize().scale(95);
                Vec3 along = head.subtract(tail);
                Vec3 perp = along.cross(head).normalize().scale(0.7);
                quad(buf, m2, tail.subtract(perp), tail.add(perp), head.add(perp), head.subtract(perp), fade);
            }
            BufferUploader.drawWithShader(buf.end());
        }

        if (ec > 0.01F) {
            // Eclipse over the sun and the moon
            RenderSystem.setShaderTexture(0, ECLIPSE);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partial) * 360.0F));
            Matrix4f m3 = pose.last().pose();
            float size = 42F;
            float pulse = 1.0F + 0.06F * Mth.sin((ticks + partial) * 0.15F);
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float s = size * pulse;
            vertex(buf, m3, -s, 99, -s, 0, 0, 1, 1, 1, ec);
            vertex(buf, m3, s, 99, -s, 1, 0, 1, 1, 1, ec);
            vertex(buf, m3, s, 99, s, 1, 1, 1, 1, 1, ec);
            vertex(buf, m3, -s, 99, s, 0, 1, 1, 1, 1, ec);
            vertex(buf, m3, -s, -99, s, 0, 0, 1, 1, 1, ec * 0.8F);
            vertex(buf, m3, s, -99, s, 1, 0, 1, 1, 1, ec * 0.8F);
            vertex(buf, m3, s, -99, -s, 1, 1, 1, 1, 1, ec * 0.8F);
            vertex(buf, m3, -s, -99, -s, 0, 1, 1, 1, 1, ec * 0.8F);
            BufferUploader.drawWithShader(buf.end());
            // The dark disc itself (normal blending)
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            float dr = 13F;
            buf.vertex(m3, 0, 98.5F, 0).color(0.02F, 0.0F, 0.05F, ec).endVertex();
            for (int i = 0; i <= 32; i++) {
                double ang = -i * Math.PI * 2 / 32;
                buf.vertex(m3, (float) Math.cos(ang) * dr, 98.5F, (float) Math.sin(ang) * dr).color(0.02F, 0.0F, 0.05F, ec).endVertex();
            }
            BufferUploader.drawWithShader(buf.end());
            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buf, Matrix4f m, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        buf.vertex(m, x, y, z).uv(u, v).color(r, g, b, a).endVertex();
    }

    private static void quad(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        buf.vertex(m, (float) a.x, (float) a.y, (float) a.z).uv(0, 0).color(1F, 1F, 1F, alpha).endVertex();
        buf.vertex(m, (float) b.x, (float) b.y, (float) b.z).uv(0, 1).color(1F, 1F, 1F, alpha).endVertex();
        buf.vertex(m, (float) c.x, (float) c.y, (float) c.z).uv(1, 1).color(1F, 1F, 1F, alpha).endVertex();
        buf.vertex(m, (float) d.x, (float) d.y, (float) d.z).uv(1, 0).color(1F, 1F, 1F, alpha).endVertex();
    }
}
