package net.starfallen.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.starfallen.config.SFConfig;
import net.starfallen.entity.CometRayEntity;
import net.starfallen.entity.projectile.MeteorEntity;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModSounds;
import net.starfallen.world.CraterBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Starfall nights: on some nights the sky breaks open and meteors rain down around every
 * player in the Overworld, carving craters full of meteorite, stardust and fallen stars.
 */
public final class StarfallManager {
    private static final Map<UUID, Integer> TIMERS = new HashMap<>();
    private static final String DATA_NAME = "starfallen_starfall";

    public static final class Data extends SavedData {
        boolean active;
        long endTime;
        long lastRolledDay = -1;
        int count;

        static Data load(CompoundTag tag) {
            Data d = new Data();
            d.active = tag.getBoolean("Active");
            d.endTime = tag.getLong("EndTime");
            d.lastRolledDay = tag.getLong("LastRolledDay");
            d.count = tag.getInt("Count");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Active", active);
            tag.putLong("EndTime", endTime);
            tag.putLong("LastRolledDay", lastRolledDay);
            tag.putInt("Count", count);
            return tag;
        }
    }

    private static Data data(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    public static boolean isActive(ServerLevel level) {
        return data(level).active;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().overworld();
        if (level == null) return;
        Data d = data(level);
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long tod = dayTime % 24000L;
        if (!d.active) {
            if (tod >= 13000L && tod < 14000L && d.lastRolledDay != day) {
                d.lastRolledDay = day;
                d.setDirty();
                if (day >= SFConfig.STARFALL_FIRST_NIGHT.get() && level.random.nextDouble() < SFConfig.STARFALL_CHANCE.get()) {
                    start(level, 0);
                }
            }
            return;
        }
        boolean over = d.endTime > 0 ? level.getGameTime() >= d.endTime : (tod < 12500L || tod >= 23200L);
        if (over) {
            stop(level);
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            int t = TIMERS.getOrDefault(player.getUUID(), 40) - 1;
            if (t <= 0) {
                int min = SFConfig.METEOR_MIN_INTERVAL.get();
                int max = Math.max(min + 1, SFConfig.METEOR_MAX_INTERVAL.get());
                t = min + level.random.nextInt(max - min);
                spawnMeteorNear(level, player);
            }
            TIMERS.put(player.getUUID(), t);
            // Rare sight: a wild Comet Ray drifting through the falling stars
            if (level.random.nextInt(3600) == 0) spawnWildRay(level, player);
        }
    }

    /** @param duration ticks, or 0 to last until dawn (or 6000 ticks if it is day). */
    public static void start(ServerLevel level, int duration) {
        ServerLevel overworld = level.getServer().overworld();
        Data d = data(overworld);
        long tod = overworld.getDayTime() % 24000L;
        boolean night = tod >= 12500L && tod < 23200L;
        d.active = true;
        d.endTime = duration > 0 ? overworld.getGameTime() + duration : night ? 0 : overworld.getGameTime() + 6000;
        d.count++;
        d.setDirty();
        TIMERS.clear();
        SFNetwork.starfallStateAll(overworld, true);
        if (SFConfig.ANNOUNCE_STARFALL.get()) {
            for (ServerPlayer p : overworld.players()) {
                SFNetwork.sendTitle(p, Component.translatable("title.starfallen.starfall").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.translatable("title.starfallen.starfall.sub").withStyle(ChatFormatting.LIGHT_PURPLE));
                overworld.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.STARFALL_BEGIN.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
                TIMERS.put(p.getUUID(), 60 + overworld.random.nextInt(80));
            }
        }
    }

    public static void stop(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        Data d = data(overworld);
        if (!d.active) return;
        d.active = false;
        d.endTime = 0;
        d.setDirty();
        SFNetwork.starfallStateAll(overworld, false);
        for (ServerPlayer p : overworld.players()) {
            p.displayClientMessage(Component.translatable("message.starfallen.starfall_end").withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static CraterBuilder.Loot rollLoot(RandomSource random) {
        if (random.nextDouble() < SFConfig.GOLDEN_METEOR_CHANCE.get()) return CraterBuilder.Loot.GOLDEN;
        if (random.nextFloat() < 0.22F) {
            return random.nextDouble() < SFConfig.EGG_METEOR_CHANCE.get() ? CraterBuilder.Loot.EGG : CraterBuilder.Loot.LARGE;
        }
        return CraterBuilder.Loot.NORMAL;
    }

    public static float sizeFor(CraterBuilder.Loot loot, RandomSource random) {
        return switch (loot) {
            case LARGE, EGG -> 2.0F + random.nextFloat() * 0.6F;
            case GOLDEN -> 1.6F;
            default -> 0.9F + random.nextFloat() * 0.5F;
        };
    }

    /** Drops a Starfall meteor somewhere around (but never on top of) the player. */
    public static void spawnMeteorNear(ServerLevel level, ServerPlayer player) {
        RandomSource r = level.random;
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = 28 + r.nextDouble() * 70;
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        launch(level, x, z, rollLoot(r));
    }

    /** A meteor that falls somewhere in front of the player (telescope, commands). */
    public static void spawnMeteorInView(ServerLevel level, ServerPlayer player, float largeChance) {
        RandomSource r = level.random;
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        double dist = 40 + r.nextDouble() * 30;
        Vec3 t = player.position().add(look.yRot((float) ((r.nextDouble() - 0.5) * 0.6)).scale(dist));
        CraterBuilder.Loot loot = r.nextFloat() < largeChance ? CraterBuilder.Loot.LARGE : rollLoot(r);
        launch(level, t.x, t.z, loot);
    }

    public static MeteorEntity launch(ServerLevel level, double x, double z, CraterBuilder.Loot loot) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        Vec3 target = new Vec3(x, y, z);
        double a = level.random.nextDouble() * Math.PI * 2;
        MeteorEntity m = MeteorEntity.strike(level, null, target, new Vec3(Math.cos(a), 0, Math.sin(a)), sizeFor(loot, level.random), MeteorEntity.Kind.EVENT, 18.0F);
        m.withLoot(loot);
        return m;
    }

    private static void spawnWildRay(ServerLevel level, ServerPlayer player) {
        CometRayEntity ray = ModEntities.COMET_RAY.get().create(level);
        if (ray == null) return;
        double a = level.random.nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(a) * 30, z = player.getZ() + Math.sin(a) * 30;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z) + 18;
        ray.moveTo(x, y, z, level.random.nextFloat() * 360, 0);
        ray.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(ray);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SFNetwork.starfallState(sp, sp.level().dimension() == Level.OVERWORLD && isActive(sp.serverLevel()));
        }
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SFNetwork.starfallState(sp, event.getTo() == Level.OVERWORLD && isActive(sp.serverLevel()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TIMERS.remove(event.getEntity().getUUID());
    }

    private StarfallManager() {}
}
