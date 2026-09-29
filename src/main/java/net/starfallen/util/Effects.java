package net.starfallen.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModParticles;

import java.util.List;

/** Shared "juice": area slams, rings, bursts. All methods are server-side. */
public final class Effects {
    private Effects() {}

    /**
     * Ground slam: radial damage, knock-up, debris, a ring particle and camera shake.
     *
     * @return entities that were hit
     */
    public static List<LivingEntity> slam(ServerLevel level, Entity user, Vec3 center, double radius, float damage, double knockUp,
                                          DamageSource source, SoundEvent sound) {
        level.playSound(null, center.x, center.y, center.z, sound, SoundSource.PLAYERS, 2.0F, 0.8F + level.random.nextFloat() * 0.2F);
        ring(level, center.add(0, 0.1, 0), ModParticles.SHOCKWAVE.get());
        BlockPos below = BlockPos.containing(center.x, center.y - 0.5, center.z);
        BlockState ground = level.getBlockState(below);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, center.y + 0.2, center.z,
                    (int) (40 * radius / 3), radius * 0.45, 0.2, radius * 0.45, 0.25);
        }
        level.sendParticles(ModParticles.EMBER.get(), center.x, center.y + 0.3, center.z, 20, radius * 0.3, 0.2, radius * 0.3, 0.15);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 2, 0.5, 0.2, 0.5, 0);
        SFNetwork.shakeAround(level, center, radius * 5, (float) Math.min(1.2, 0.3 + radius * 0.1), 14);

        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2.5, radius),
                e -> SFUtil.isValidTarget(user, e) && e.position().distanceTo(center) <= radius + e.getBbWidth() / 2);
        for (LivingEntity e : hit) {
            double dist = e.position().distanceTo(center);
            float falloff = (float) (1.0 - 0.5 * Math.min(1.0, dist / radius));
            if (e.hurt(source, damage * falloff)) {
                Vec3 away = e.position().subtract(center).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                double kb = (1.0 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)) * 0.9;
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * kb, knockUp * (0.4 + 0.6 * kb / 0.9), away.z * kb));
                e.hurtMarked = true;
            }
        }
        return hit;
    }

    public static void ring(ServerLevel level, Vec3 pos, ParticleOptions ring) {
        level.sendParticles(ring, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
    }

    /** A spherical burst of particles. */
    public static void burst(ServerLevel level, Vec3 pos, ParticleOptions particle, int count, double speed) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, 0.1, 0.1, 0.1, speed);
    }

    /** Particles along a line (trails, beams, tethers). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double spacing) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) return;
        Vec3 step = d.scale(spacing / len);
        Vec3 p = from;
        for (double t = 0; t <= len; t += spacing) {
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            p = p.add(step);
        }
    }
}
