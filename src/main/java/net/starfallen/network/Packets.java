package net.starfallen.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.starfallen.event.AbilityHandler;

import java.util.function.Supplier;

/** All packet payloads. Client-bound handlers delegate to {@code ClientPacketHandler} on the client only. */
public final class Packets {
    private Packets() {}

    public record Shake(float intensity, int ticks) {
        public static void encode(Shake m, FriendlyByteBuf b) { b.writeFloat(m.intensity); b.writeVarInt(m.ticks); }
        public static Shake decode(FriendlyByteBuf b) { return new Shake(b.readFloat(), b.readVarInt()); }
        public static void handle(Shake m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientPacketHandler.shake(m.intensity, m.ticks));
        }
    }

    public record Flash(int argb, int ticks) {
        public static void encode(Flash m, FriendlyByteBuf b) { b.writeInt(m.argb); b.writeVarInt(m.ticks); }
        public static Flash decode(FriendlyByteBuf b) { return new Flash(b.readInt(), b.readVarInt()); }
        public static void handle(Flash m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientPacketHandler.flash(m.argb, m.ticks));
        }
    }

    /** kind: 0 = boss intro, 1 = boss phase two, 2 = boss death, 3 = high starseer intro. */
    public record Cinematic(int kind, int entityId, int ticks) {
        public static final int BOSS_INTRO = 0, BOSS_PHASE = 1, BOSS_DEATH = 2, STARSEER_INTRO = 3;
        public static void encode(Cinematic m, FriendlyByteBuf b) { b.writeVarInt(m.kind); b.writeVarInt(m.entityId); b.writeVarInt(m.ticks); }
        public static Cinematic decode(FriendlyByteBuf b) { return new Cinematic(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        public static void handle(Cinematic m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientPacketHandler.cinematic(m.kind, m.entityId, m.ticks));
        }
    }

    public record StarfallState(boolean active) {
        public static void encode(StarfallState m, FriendlyByteBuf b) { b.writeBoolean(m.active); }
        public static StarfallState decode(FriendlyByteBuf b) { return new StarfallState(b.readBoolean()); }
        public static void handle(StarfallState m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientPacketHandler.starfall(m.active));
        }
    }

    public record Telescope() {
        public static void encode(Telescope m, FriendlyByteBuf b) {}
        public static Telescope decode(FriendlyByteBuf b) { return new Telescope(); }
        public static void handle(Telescope m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> net.starfallen.client.ClientPacketHandler::telescope);
        }
    }

    public record TotemPop(int entityId, ItemStack stack) {
        public static void encode(TotemPop m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeItem(m.stack); }
        public static TotemPop decode(FriendlyByteBuf b) { return new TotemPop(b.readVarInt(), b.readItem()); }
        public static void handle(TotemPop m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientPacketHandler.totemPop(m.entityId, m.stack));
        }
    }

    /** Client asks the server to perform an ability. 0 = double jump, 1 = blink, 2 = comet ray boost. */
    public record Ability(int ability) {
        public static final int DOUBLE_JUMP = 0, BLINK = 1, RAY_BOOST = 2;
        public static void encode(Ability m, FriendlyByteBuf b) { b.writeVarInt(m.ability); }
        public static Ability decode(FriendlyByteBuf b) { return new Ability(b.readVarInt()); }
        public static void handle(Ability m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) AbilityHandler.handleAbility(player, m.ability);
        }
    }

    public record AbilityCooldown(int ability, int ticks) {
        public static void encode(AbilityCooldown m, FriendlyByteBuf b) { b.writeVarInt(m.ability); b.writeVarInt(m.ticks); }
        public static AbilityCooldown decode(FriendlyByteBuf b) { return new AbilityCooldown(b.readVarInt(), b.readVarInt()); }
        public static void handle(AbilityCooldown m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientPacketHandler.abilityCooldown(m.ability, m.ticks));
        }
    }
}
