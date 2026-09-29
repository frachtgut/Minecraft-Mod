package net.starfallen.entity.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import net.starfallen.util.Targeting;

/** Arrow of starlight fired by the Constellation Bow. Seeks hostile creatures and bursts on impact. */
public class StarArrowEntity extends AbstractArrow {
    private double homingStrength = 0.05;
    private int lockedTarget = -1;

    public StarArrowEntity(EntityType<? extends StarArrowEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public StarArrowEntity(Level level, LivingEntity shooter) {
        super(ModEntities.STAR_ARROW.get(), shooter, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public void setHomingStrength(double s) {
        this.homingStrength = s;
    }

    @Override
    public void tick() {
        super.tick();
        if (inGround) {
            if (!level().isClientSide && inGroundTime > 30) discard();
            return;
        }
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            level().addParticle(ModParticles.ASTRAL_SPARK.get(), getX(), getY(), getZ(), -v.x * 0.02, -v.y * 0.02, -v.z * 0.02);
            if (tickCount % 2 == 0) level().addParticle(ModParticles.STAR_SPARK.get(), getX(), getY(), getZ(), 0, 0, 0);
            return;
        }
        if (tickCount < 3 || homingStrength <= 0) return;
        Entity target = lockedTarget >= 0 ? level().getEntity(lockedTarget) : null;
        if (target == null || !target.isAlive()) {
            target = acquire();
            lockedTarget = target == null ? -1 : target.getId();
        }
        if (target != null) {
            Vec3 vel = getDeltaMovement();
            double speed = vel.length();
            Vec3 to = target.getBoundingBox().getCenter().subtract(position()).normalize();
            Vec3 nv = vel.normalize().add(to.scale(homingStrength * 2.2)).normalize().scale(speed);
            setDeltaMovement(nv);
            hasImpulse = true;
        }
    }

    private Entity acquire() {
        Entity owner = getOwner();
        Vec3 vel = getDeltaMovement().normalize();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(14),
                e -> e instanceof Enemy && (owner == null || SFUtil.isValidTarget(owner, e)))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(position());
            double dist = to.length();
            double dot = to.normalize().dot(vel);
            if (dot < 0.55) continue;
            if (!Targeting.hasLineOfSight(level(), this, position(), e)) continue;
            double score = dist * (2.0 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level() instanceof ServerLevel server) {
            Entity e = result.getEntity();
            server.sendParticles(ModParticles.STAR_SPARK.get(), e.getX(), e.getY(0.5), e.getZ(), 14, 0.2, 0.3, 0.2, 0.15);
            server.sendParticles(ModParticles.ASTRAL_SPARK.get(), e.getX(), e.getY(0.5), e.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return ModSounds.STAR_CHIME.get();
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isNoGravity() {
        return !inGround;
    }
}
