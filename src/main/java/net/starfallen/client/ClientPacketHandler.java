package net.starfallen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.starfallen.client.fx.ClientFx;
import net.starfallen.client.fx.StarfallSky;
import net.starfallen.network.Packets;

/** Client-side handling of Starfallen packets (only ever class-loaded on the client). */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void shake(float intensity, int ticks) {
        ClientFx.shake(intensity, ticks);
    }

    public static void flash(int argb, int ticks) {
        ClientFx.flash(argb, ticks);
    }

    public static void cinematic(int kind, int entityId, int ticks) {
        ClientFx.cinematic(kind, entityId, ticks);
    }

    public static void starfall(boolean active) {
        StarfallSky.setActive(active);
    }

    public static void telescope() {
        ClientFx.telescope();
    }

    public static void totemPop(int entityId, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return;
        mc.particleEngine.createTrackingEmitter(e, ParticleTypes.TOTEM_OF_UNDYING, 30);
        if (e == mc.player) {
            mc.gameRenderer.displayItemActivation(stack);
            ClientFx.flash(0xB0FFE9A8, 16);
        }
    }

    public static void abilityCooldown(int ability, int ticks) {
        if (ability == Packets.Ability.BLINK) ClientFx.blinkCooldown(ticks);
    }
}
