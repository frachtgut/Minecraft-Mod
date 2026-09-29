package net.starfallen.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.starfallen.Starfallen;

public final class SFNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Starfallen.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Packets.Shake.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.Shake::encode).decoder(Packets.Shake::decode).consumerMainThread(Packets.Shake::handle).add();
        CHANNEL.messageBuilder(Packets.Flash.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.Flash::encode).decoder(Packets.Flash::decode).consumerMainThread(Packets.Flash::handle).add();
        CHANNEL.messageBuilder(Packets.Cinematic.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.Cinematic::encode).decoder(Packets.Cinematic::decode).consumerMainThread(Packets.Cinematic::handle).add();
        CHANNEL.messageBuilder(Packets.StarfallState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.StarfallState::encode).decoder(Packets.StarfallState::decode).consumerMainThread(Packets.StarfallState::handle).add();
        CHANNEL.messageBuilder(Packets.Telescope.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.Telescope::encode).decoder(Packets.Telescope::decode).consumerMainThread(Packets.Telescope::handle).add();
        CHANNEL.messageBuilder(Packets.TotemPop.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.TotemPop::encode).decoder(Packets.TotemPop::decode).consumerMainThread(Packets.TotemPop::handle).add();
        CHANNEL.messageBuilder(Packets.Ability.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.Ability::encode).decoder(Packets.Ability::decode).consumerMainThread(Packets.Ability::handle).add();
        CHANNEL.messageBuilder(Packets.AbilityCooldown.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.AbilityCooldown::encode).decoder(Packets.AbilityCooldown::decode).consumerMainThread(Packets.AbilityCooldown::handle).add();
    }

    // ------------------------------------------------------------------ helpers

    public static void shake(ServerPlayer player, float intensity, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packets.Shake(intensity, ticks));
    }

    /** Shakes every player near {@code center}, fading with distance. */
    public static void shakeAround(ServerLevel level, Vec3 center, double radius, float intensity, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(center);
            if (d < radius) {
                float f = (float) (1.0 - d / radius);
                shake(p, intensity * (0.35F + 0.65F * f), ticks);
            }
        }
    }

    public static void flash(ServerPlayer player, int argb, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packets.Flash(argb, ticks));
    }

    public static void flashAround(ServerLevel level, Vec3 center, double radius, int argb, int ticks) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(center) < radius) flash(p, argb, ticks);
        }
    }

    public static void cinematic(ServerPlayer player, int kind, int entityId, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packets.Cinematic(kind, entityId, ticks));
    }

    public static void starfallState(ServerPlayer player, boolean active) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packets.StarfallState(active));
    }

    public static void starfallStateAll(ServerLevel level, boolean active) {
        for (ServerPlayer p : level.players()) starfallState(p, active);
    }

    public static void telescopeZoom(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packets.Telescope());
    }

    public static void totemPop(ServerPlayer player, ItemStack stack) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Packets.TotemPop(player.getId(), stack));
    }

    public static void abilityCooldown(ServerPlayer player, int ability, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packets.AbilityCooldown(ability, ticks));
    }

    public static void sendTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private SFNetwork() {}
}
