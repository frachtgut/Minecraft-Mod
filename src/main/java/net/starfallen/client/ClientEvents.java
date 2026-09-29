package net.starfallen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.starfallen.client.fx.BossMusic;
import net.starfallen.client.fx.ClientFx;
import net.starfallen.client.fx.StarfallSky;
import net.starfallen.entity.boss.AstraeonEntity;
import net.starfallen.event.AbilityHandler;
import net.starfallen.network.Packets;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModTiers;

/** Client-side Forge bus handlers. */
public final class ClientEvents {
    private static boolean wasJumpDown;
    private static boolean doubleJumped;
    private static float bossFog, prevBossFog;
    private static boolean bossEnraged;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientFx.tick();
        if (mc.level == null || mc.player == null) return;
        StarfallSky.tick(mc.level);

        // Boss proximity: eclipse, fog and music
        AstraeonEntity boss = null;
        double best = 96 * 96;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof AstraeonEntity a && a.isAlive() && a.distanceToSqr(mc.player) < best) {
                best = a.distanceToSqr(mc.player);
                boss = a;
            }
        }
        StarfallSky.setBossNear(boss != null);
        prevBossFog = bossFog;
        bossFog += ((boss != null ? 1.0F : 0.0F) - bossFog) * 0.04F;
        bossEnraged = boss != null && boss.isEnraged();
        BossMusic.tick(mc, boss);

        LocalPlayer player = mc.player;
        // Voidwalker blink
        while (ClientSetup.BLINK_KEY.consumeClick()) {
            if (AbilityHandler.hasFullSet(player, ModTiers.Armor.VOIDWALKER) && ClientFx.getBlinkCooldown() <= 0) {
                SFNetwork.CHANNEL.sendToServer(new Packets.Ability(Packets.Ability.BLINK));
            }
        }
        // Starforged double jump
        boolean jump = mc.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.onClimbable()) doubleJumped = false;
        if (jump && !wasJumpDown && !player.onGround() && !doubleJumped && !player.isInWater() && !player.isPassenger()
                && !player.getAbilities().flying && !player.onClimbable() && AbilityHandler.hasFullSet(player, ModTiers.Armor.STARFORGED)) {
            doubleJumped = true;
            Vec3 v = player.getDeltaMovement();
            Vec3 look = player.getLookAngle().multiply(1, 0, 1);
            player.setDeltaMovement(v.x * 1.1 + look.x * 0.15, 0.62, v.z * 1.1 + look.z * 0.15);
            player.fallDistance = 0;
            SFNetwork.CHANNEL.sendToServer(new Packets.Ability(Packets.Ability.DOUBLE_JUMP));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                mc.level.addParticle(ModParticles.STAR_SPARK.get(), player.getX() + Math.cos(a) * 0.5, player.getY(), player.getZ() + Math.sin(a) * 0.5,
                        Math.cos(a) * 0.08, -0.05, Math.sin(a) * 0.08);
            }
        }
        wasJumpDown = jump;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] s = ClientFx.shakeAngles((float) event.getPartialTick());
        if (s != null) {
            event.setYaw(event.getYaw() + s[0]);
            event.setPitch(event.getPitch() + s[1]);
            event.setRoll(event.getRoll() + s[2]);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (ClientFx.zooming()) event.setFOV(event.getFOV() * ClientFx.fovModifier((float) event.getPartialTick()));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float partial = (float) event.getPartialTick();
        float boss = Mth.lerp(partial, prevBossFog, bossFog);
        float star = StarfallSky.intensity(partial);
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        if (star > 0.01F) {
            float f = 0.35F * star;
            r = Mth.lerp(f, r, 0.22F);
            g = Mth.lerp(f, g, 0.1F);
            b = Mth.lerp(f, b, 0.35F);
        }
        if (boss > 0.01F) {
            float f = 0.6F * boss;
            r = Mth.lerp(f, r, bossEnraged ? 0.32F : 0.18F);
            g = Mth.lerp(f, g, 0.03F);
            b = Mth.lerp(f, b, bossEnraged ? 0.12F : 0.3F);
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            StarfallSky.render(event.getPoseStack(), event.getProjectionMatrix(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFx.reset();
        StarfallSky.reset();
        BossMusic.stop();
    }
}
