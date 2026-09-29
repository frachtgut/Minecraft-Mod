package net.starfallen.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;

/** A glob of molten meteorite hurled by a Meteor Golem. Bursts into flame on impact. */
public class MoltenRockEntity extends ThrowableProjectile {
    public MoltenRockEntity(EntityType<? extends MoltenRockEntity> type, Level level) {
        super(type, level);
    }

    public MoltenRockEntity(Level level, LivingEntity owner) {
        super(ModEntities.MOLTEN_ROCK.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected float getGravity() {
        return 0.045F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ModParticles.EMBER.get(), getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
            level().addParticle(ModParticles.METEOR_SMOKE.get(), getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = result.getLocation();
        var owner = getOwner();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.4),
                e -> e != owner && e.isAlive() && (owner == null || SFUtil.isValidTarget(owner, e)))) {
            if (e.hurt(ModDamageTypes.source(server, ModDamageTypes.METEOR, this, owner), 8.0F)) {
                e.setSecondsOnFire(4);
            }
        }
        server.playSound(null, c.x, c.y, c.z, ModSounds.METEOR_IMPACT.get(), SoundSource.HOSTILE, 1.2F, 1.5F);
        server.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 12, 0.5, 0.2, 0.5, 0);
        server.sendParticles(ModParticles.EMBER.get(), c.x, c.y + 0.3, c.z, 25, 0.4, 0.3, 0.4, 0.2);
        server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        server.sendParticles(ModParticles.SHOCKWAVE.get(), c.x, c.y + 0.15, c.z, 1, 0, 0, 0, 0);
        discard();
    }
}
