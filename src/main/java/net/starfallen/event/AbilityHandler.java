package net.starfallen.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.network.Packets;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.registry.ModTiers;
import net.starfallen.util.Effects;
import net.starfallen.util.SFUtil;

import java.util.*;

/** Server-side state for movement abilities: hammer leaps, blade dashes, double jumps, blinks. */
public final class AbilityHandler {
    private AbilityHandler() {}

    // ------------------------------------------------------------ hammer leap
    private record Leap(long start, float charge, boolean dive) {}
    private static final Map<UUID, Leap> LEAPS = new HashMap<>();

    public static void startLeap(ServerPlayer player, float charge, boolean dive) {
        LEAPS.put(player.getUUID(), new Leap(player.level().getGameTime(), charge, dive));
    }

    public static boolean isLeaping(Player player) {
        return LEAPS.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------ blade dash
    private static final class Dash {
        int ticks;
        final Vec3 velocity;
        final Set<Integer> hit = new HashSet<>();
        final float damage;

        Dash(int ticks, Vec3 velocity, float damage) {
            this.ticks = ticks;
            this.velocity = velocity;
            this.damage = damage;
        }
    }
    private static final Map<UUID, Dash> DASHES = new HashMap<>();

    public static void startDash(ServerPlayer player, Vec3 velocity, float damage) {
        DASHES.put(player.getUUID(), new Dash(6, velocity, damage));
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
    }

    // ------------------------------------------------------------ armor abilities
    private static final Map<UUID, Long> BLINK_READY = new HashMap<>();
    private static final Set<UUID> DOUBLE_JUMPED = new HashSet<>();
    public static final int BLINK_COOLDOWN = 50;

    public static boolean hasFullSet(LivingEntity e, ModTiers.Armor material) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = e.getItemBySlot(slot);
            if (!(s.getItem() instanceof ArmorItem a) || a.getMaterial() != material) return false;
        }
        return true;
    }

    public static void handleAbility(ServerPlayer player, int ability) {
        if (!player.isAlive() || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        if (ability == Packets.Ability.DOUBLE_JUMP) {
            if (!hasFullSet(player, ModTiers.Armor.STARFORGED) || player.onGround() || DOUBLE_JUMPED.contains(player.getUUID())) return;
            DOUBLE_JUMPED.add(player.getUUID());
            player.fallDistance = 0;
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DOUBLE_JUMP.get(), SoundSource.PLAYERS, 0.8F, 1.0F + level.random.nextFloat() * 0.2F);
            level.sendParticles(ModParticles.STAR_SPARK.get(), player.getX(), player.getY(), player.getZ(), 16, 0.3, 0.05, 0.3, 0.06);
            Effects.ring(level, player.position(), ModParticles.SHOCKWAVE.get());
        } else if (ability == Packets.Ability.RAY_BOOST) {
            if (player.getVehicle() instanceof net.starfallen.entity.CometRayEntity ray && ray.isOwnedBy(player)) ray.startBoost();
        } else if (ability == Packets.Ability.BLINK) {
            if (!hasFullSet(player, ModTiers.Armor.VOIDWALKER)) return;
            long now = level.getGameTime();
            if (BLINK_READY.getOrDefault(player.getUUID(), 0L) > now) return;
            if (blink(level, player, 11.0)) {
                BLINK_READY.put(player.getUUID(), now + BLINK_COOLDOWN);
                SFNetwork.abilityCooldown(player, Packets.Ability.BLINK, BLINK_COOLDOWN);
            }
        }
    }

    /** Teleports the player along their view up to {@code range} blocks. */
    public static boolean blink(ServerLevel level, ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double dist = hit.getLocation().distanceTo(eye) - 0.7;
        Vec3 dest = null;
        for (double d = dist; d > 1.0; d -= 0.5) {
            Vec3 eyeAt = eye.add(look.scale(d));
            Vec3 feet = eyeAt.subtract(0, player.getEyeHeight(), 0);
            AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
            if (level.noCollision(player, box)) {
                dest = feet;
                break;
            }
        }
        if (dest == null) return false;
        Vec3 from = player.position();
        level.sendParticles(ModParticles.VOID_SPARK.get(), from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.6, 0.3, 0.2);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.fallDistance = 0;
        level.sendParticles(ModParticles.VOID_SPARK.get(), dest.x, dest.y + 1, dest.z, 30, 0.3, 0.6, 0.3, 0.1);
        Effects.line(level, from.add(0, 1, 0), dest.add(0, 1, 0), ModParticles.VOID_SPARK.get(), 0.6);
        level.playSound(null, from.x, from.y, from.z, ModSounds.RIFT_BLINK.get(), SoundSource.PLAYERS, 0.9F, 1.1F);
        level.playSound(null, dest.x, dest.y, dest.z, ModSounds.RIFT_BLINK.get(), SoundSource.PLAYERS, 0.9F, 1.3F);
        return true;
    }

    // ------------------------------------------------------------ ticking

    static void tickPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        if (player.onGround()) DOUBLE_JUMPED.remove(id);

        Leap leap = LEAPS.get(id);
        if (leap != null) {
            long age = level.getGameTime() - leap.start;
            if (age > 3 && (player.onGround() || player.isInWater() || player.onClimbable())) {
                LEAPS.remove(id);
                if (!player.isInWater()) hammerSlam(player, leap, player.fallDistance);
            } else if (age > 200) {
                LEAPS.remove(id);
            } else if (age % 2 == 0) {
                level.sendParticles(ModParticles.EMBER.get(), player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.2, 0.3, 0.2, 0.02);
            }
        }

        Dash dash = DASHES.get(id);
        if (dash != null) {
            player.fallDistance = 0;
            if (dash.ticks-- > 0) {
                player.setDeltaMovement(dash.velocity);
                player.hurtMarked = true;
                level.sendParticles(ModParticles.ASTRAL_SPARK.get(), player.getX(), player.getY() + 1, player.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                level.sendParticles(ModParticles.STAR_SPARK.get(), player.getX(), player.getY() + 1, player.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.3),
                        e -> SFUtil.isValidTarget(player, e) && !dash.hit.contains(e.getId()))) {
                    dash.hit.add(e.getId());
                    e.invulnerableTime = 0;
                    e.hurt(player.damageSources().playerAttack(player), dash.damage);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY(0.5), e.getZ(), 1, 0, 0, 0, 0);
                }
            } else {
                DASHES.remove(id);
                player.setDeltaMovement(dash.velocity.scale(0.25));
                player.hurtMarked = true;
                // Starlight Echo: every enemy the dash cut through is struck again a moment later.
                final List<Integer> ids = new ArrayList<>(dash.hit);
                final float echo = dash.damage * 0.8F;
                if (!ids.isEmpty()) {
                    SFScheduler.schedule(level, 14, lvl -> {
                        for (int eid : ids) {
                            Entity e = lvl.getEntity(eid);
                            if (e instanceof LivingEntity le && le.isAlive()) {
                                le.invulnerableTime = 0;
                                le.hurt(ModDamageTypes.source(lvl, ModDamageTypes.STAR_BOLT, player, player), echo);
                                lvl.sendParticles(ModParticles.NOVA_FLASH.get(), le.getX(), le.getY(0.5), le.getZ(), 1, 0, 0, 0, 0);
                                lvl.sendParticles(ModParticles.STAR_SPARK.get(), le.getX(), le.getY(0.5), le.getZ(), 20, 0.3, 0.4, 0.3, 0.15);
                                lvl.playSound(null, le.getX(), le.getY(), le.getZ(), ModSounds.STAR_CHIME.get(), SoundSource.PLAYERS, 1.0F, 0.6F);
                            }
                        }
                    });
                }
            }
        }
    }

    private static void hammerSlam(ServerPlayer player, Leap leap, float fall) {
        ServerLevel level = player.serverLevel();
        float damage = Math.min(40F, 7F + leap.charge * 5F + fall * 1.4F);
        double radius = Math.min(7.0, 3.2 + leap.charge * 1.5 + fall * 0.12);
        Effects.slam(level, player, player.position(), radius, damage, 0.75, player.damageSources().playerAttack(player), ModSounds.HAMMER_SLAM.get());
        level.sendParticles(ModParticles.NOVA_FLASH.get(), player.getX(), player.getY() + 0.3, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.3, player.getZ(), 12, radius * 0.3, 0.1, radius * 0.3, 0);
        player.fallDistance = 0;
        if (fall > 12) SFUtil.award(player, "skyfall");
    }

    /** Called from the fall event. Returns true to cancel fall damage. */
    static boolean onFall(ServerPlayer player, float distance) {
        UUID id = player.getUUID();
        Leap leap = LEAPS.remove(id);
        if (leap != null) {
            hammerSlam(player, leap, distance);
            return true;
        }
        if (DASHES.containsKey(id)) return true;
        if (distance > 3.5F && hasFullSet(player, ModTiers.Armor.METEORIC)) {
            ServerLevel level = player.serverLevel();
            float damage = Math.min(28F, 3F + distance * 0.9F);
            double radius = Math.min(6.0, 2.5 + distance * 0.15);
            Effects.slam(level, player, player.position(), radius, damage, 0.55, player.damageSources().playerAttack(player), ModSounds.METEOR_IMPACT.get());
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 30, radius * 0.4, 0.1, radius * 0.4, 0.04);
            return true;
        }
        return false;
    }

    static void clear(UUID id) {
        LEAPS.remove(id);
        DASHES.remove(id);
        DOUBLE_JUMPED.remove(id);
    }
}
