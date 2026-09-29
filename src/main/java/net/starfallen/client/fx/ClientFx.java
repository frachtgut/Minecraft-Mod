package net.starfallen.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.starfallen.network.Packets;

/**
 * Client-side screen effects: camera shake, full-screen flashes, cinematic letterboxing with
 * boss title cards, the telescope zoom and the blink cooldown indicator.
 */
public final class ClientFx {
    private static final RandomSource RANDOM = RandomSource.create();

    // Camera shake ("trauma" model)
    private static float trauma;
    private static int shakeTicks;
    private static float shakeTime;

    // Flash
    private static int flashColor;
    private static int flashTicks, flashMax;

    // Cinematic
    private static int cineKind = -1;
    private static int cineTicks, cineMax;
    private static int cineEntity;

    // Telescope
    private static int zoomTicks;
    public static final int ZOOM_TIME = 90;

    // Blink cooldown indicator
    private static int blinkCooldown, blinkCooldownMax;

    private ClientFx() {}

    public static void shake(float intensity, int ticks) {
        trauma = Math.min(2.5F, Math.max(trauma, intensity));
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    public static void flash(int argb, int ticks) {
        flashColor = argb;
        flashTicks = ticks;
        flashMax = ticks;
    }

    public static void cinematic(int kind, int entityId, int ticks) {
        cineKind = kind;
        cineEntity = entityId;
        cineTicks = 0;
        cineMax = ticks;
    }

    public static void telescope() {
        zoomTicks = ZOOM_TIME;
    }

    public static void blinkCooldown(int ticks) {
        blinkCooldown = ticks;
        blinkCooldownMax = ticks;
    }

    public static int getBlinkCooldown() {
        return blinkCooldown;
    }

    public static void tick() {
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks == 0) trauma = 0;
            else trauma *= 0.94F;
        }
        shakeTime += 1.0F;
        if (flashTicks > 0) flashTicks--;
        if (cineKind >= 0 && ++cineTicks > cineMax) cineKind = -1;
        if (zoomTicks > 0) zoomTicks--;
        if (blinkCooldown > 0) blinkCooldown--;
    }

    public static void reset() {
        trauma = 0;
        shakeTicks = 0;
        flashTicks = 0;
        cineKind = -1;
        zoomTicks = 0;
        blinkCooldown = 0;
    }

    /** Camera offsets in degrees: yaw, pitch, roll. */
    public static float[] shakeAngles(float partial) {
        if (shakeTicks <= 0 || trauma <= 0.01F) return null;
        float t = shakeTime + partial;
        float amp = trauma * trauma * 2.2F;
        Minecraft mc = Minecraft.getInstance();
        float scale = mc.options.screenEffectScale().get().floatValue();
        amp *= Math.max(0.25F, scale);
        return new float[]{
                amp * (Mth.sin(t * 1.9F) * 0.6F + Mth.sin(t * 4.3F + 1.3F) * 0.4F),
                amp * (Mth.sin(t * 2.3F + 2.1F) * 0.6F + Mth.sin(t * 5.1F) * 0.4F),
                amp * 0.6F * Mth.sin(t * 3.1F + 0.7F)};
    }

    public static double fovModifier(float partial) {
        if (zoomTicks <= 0) return 1.0;
        float t = (ZOOM_TIME - zoomTicks + partial) / ZOOM_TIME;
        float in = Mth.clamp(t / 0.2F, 0, 1);
        float out = Mth.clamp((1 - t) / 0.25F, 0, 1);
        float z = Math.min(in, out);
        return 1.0 - 0.8 * (z * z * (3 - 2 * z));
    }

    public static boolean zooming() {
        return zoomTicks > 0;
    }

    // ------------------------------------------------------------------ overlay

    public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui && cineKind < 0 && flashTicks <= 0) return;
        RenderSystem.enableBlend();

        // Telescope vignette
        if (zoomTicks > 0) {
            float t = (ZOOM_TIME - zoomTicks + partial) / ZOOM_TIME;
            float a = Math.min(Mth.clamp(t / 0.15F, 0, 1), Mth.clamp((1 - t) / 0.2F, 0, 1));
            int alpha = (int) (a * 255);
            int r = Math.min(w, h) / 2 - 10;
            // Dark mask outside a circular eyepiece, approximated with bands
            for (int y = 0; y < h; y += 2) {
                double dy = (y - h / 2.0) / r;
                if (Math.abs(dy) >= 1) {
                    g.fill(0, y, w, y + 2, alpha << 24);
                } else {
                    int half = (int) (Math.sqrt(1 - dy * dy) * r);
                    g.fill(0, y, w / 2 - half, y + 2, alpha << 24);
                    g.fill(w / 2 + half, y, w, y + 2, alpha << 24);
                }
            }
        }

        // Cinematic letterbox and title cards
        if (cineKind >= 0) {
            float t = cineTicks + partial;
            float in = Mth.clamp(t / 20F, 0, 1);
            float out = Mth.clamp((cineMax - t) / 20F, 0, 1);
            float bar = Math.min(in, out);
            int bh = (int) (h * 0.12F * bar);
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
            renderTitleCard(g, mc.font, w, h, t);
        }

        // Flash
        if (flashTicks > 0) {
            float a = (flashTicks - partial) / (float) flashMax;
            int baseA = (flashColor >>> 24) & 0xFF;
            int alpha = (int) (baseA * Mth.clamp(a, 0, 1));
            g.fill(0, 0, w, h, (alpha << 24) | (flashColor & 0xFFFFFF));
        }

        // Blink cooldown pip
        if (blinkCooldown > 0 && !mc.options.hideGui) {
            float f = blinkCooldown / (float) Math.max(1, blinkCooldownMax);
            int cx = w / 2, y = h / 2 + 14;
            int len = 20;
            g.fill(cx - len / 2, y, cx + len / 2, y + 2, 0x80000000);
            g.fill(cx - len / 2, y, cx - len / 2 + (int) (len * (1 - f)), y + 2, 0xFFC04DFF);
        }
        RenderSystem.disableBlend();
    }

    private static void renderTitleCard(GuiGraphics g, Font font, int w, int h, float t) {
        Component title = null, sub = null;
        float start = 0, end = 0;
        int color = 0xFFFFFF;
        switch (cineKind) {
            case Packets.Cinematic.BOSS_INTRO -> {
                title = Component.translatable("title.starfallen.astraeon");
                sub = Component.translatable("title.starfallen.astraeon.sub");
                start = 128;
                end = cineMax - 4;
                color = 0xF3D9FF;
            }
            case Packets.Cinematic.BOSS_PHASE -> {
                title = Component.translatable("title.starfallen.phase2");
                sub = Component.translatable("title.starfallen.phase2.sub");
                start = 36;
                end = cineMax - 6;
                color = 0xFF8FA0;
            }
            case Packets.Cinematic.BOSS_DEATH -> {
                title = Component.translatable("title.starfallen.victory");
                sub = Component.translatable("title.starfallen.victory.sub");
                start = cineMax - 60;
                end = cineMax - 2;
                color = 0xFFE9A8;
            }
            case Packets.Cinematic.STARSEER_INTRO -> {
                title = Component.translatable("entity.starfallen.high_starseer");
                sub = Component.translatable("title.starfallen.high_starseer.sub");
                start = 8;
                end = cineMax - 4;
                color = 0xFFD37A;
            }
            default -> {}
        }
        if (title == null || t < start || t > end) return;
        float local = (t - start) / (end - start);
        float alpha = Math.min(Mth.clamp(local / 0.15F, 0, 1), Mth.clamp((1 - local) / 0.2F, 0, 1));
        int a = (int) (alpha * 255);
        if (a < 5) return;
        float scale = 3.2F + local * 0.4F;
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h * 0.36F, 0);
        g.pose().scale(scale, scale, 1);
        String s = title.getString();
        int tw = font.width(s);
        // Letter-spaced, shadowed title
        g.drawString(font, s, -tw / 2, 0, (a << 24) | color, true);
        g.pose().popPose();
        if (sub != null) {
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.36F + 38, 0);
            g.pose().scale(1.4F, 1.4F, 1);
            int sw = font.width(sub);
            g.drawString(font, sub, -sw / 2, 0, (a << 24) | 0xBBBBCC, true);
            g.pose().popPose();
        }
        // Thin divider line
        int lineW = (int) (w * 0.3F * alpha);
        g.fill(w / 2 - lineW / 2, (int) (h * 0.36F + 32), w / 2 + lineW / 2, (int) (h * 0.36F + 33), (a << 24) | color);
    }

    public static boolean cinematicActive() {
        return cineKind >= 0;
    }

    public static Entity cinematicEntity() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || cineKind < 0 ? null : mc.level.getEntity(cineEntity);
    }

    @SuppressWarnings("unused")
    private static float rand() {
        return RANDOM.nextFloat();
    }
}
