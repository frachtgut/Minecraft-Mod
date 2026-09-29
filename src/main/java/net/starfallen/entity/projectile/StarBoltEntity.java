package net.starfallen.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

/**
 * A bolt of condensed starlight. Used by Star Wisps, Starseers, sentinel eyes and Astraeon.
 * Optionally homes in on a target.
 */
public class StarBoltEntity extends Projectile {
    /** 0 = golden (friendly), 1 = void (hostile), 2 = cyan. */
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(StarBoltEntity.class, EntityDataSerializers.INT);
    private float damage = 4.0F;
    private int homingTargetId = -1;
    private double homingStrength;
    private int life;

    public StarBoltEntity(EntityType<? extends StarBoltEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public StarBoltEntity(Level level, Vec3 pos, Vec3 velocity, @Nullable Entity owner, float damage) {
        this(ModEntities.STAR_BOLT.get(), level);
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(velocity);
        setOwner(owner);
        this.damage = damage;
        setVariant(owner instanceof net.minecraft.world.entity.player.Player || owner instanceof net.minecraft.world.entity.TamableAnimal ? 0 : 1);
        updateRotation();
    }

    public void setHoming(@Nullable Entity target, double strength) {
        this.homingTargetId = target == null ? -1 : target.getId();
        this.homingStrength = strength;
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, v);
    }

    public int getVariant() {
        return entityData.get(VARIANT);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VARIANT, 1);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        Vec3 vel = getDeltaMovement();
        if (!level().isClientSide && homingTargetId >= 0 && life > 3) {
            Entity target = level().getEntity(homingTargetId);
            if (target != null && target.isAlive()) {
                Vec3 to = target.getBoundingBox().getCenter().subtract(position());
                double speed = vel.length();
                vel = vel.normalize().add(to.normalize().scale(homingStrength * 2.0)).normalize().scale(speed);
                setDeltaMovement(vel);
                hasImpulse = true;
            }
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            onHit(hit);
        }
        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
        updateRotation();
        if (level().isClientSide) {
            int v = getVariant();
            var p = v == 0 ? ModParticles.STAR_SPARK.get() : v == 1 ? ModParticles.VOID_SPARK.get() : ModParticles.ASTRAL_SPARK.get();
            for (int i = 0; i < 2; i++) {
                level().addParticle(p, getX() - vel.x * i * 0.5, getY() + 0.2 - vel.y * i * 0.5, getZ() - vel.z * i * 0.5,
                        (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);
            }
        } else if (life > 120 || isInWater()) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (!super.canHitEntity(e)) return false;
        Entity owner = getOwner();
        if (owner == null) return e instanceof LivingEntity;
        if (e == owner) return false;
        // Friendly bolts never hurt their owner's pets/allies, hostile bolts never hurt other hostiles.
        if (getVariant() == 0) return SFUtil.isValidTarget(owner, e);
        return !(e instanceof net.minecraft.world.entity.monster.Enemy) || owner instanceof net.minecraft.world.entity.player.Player;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target.hurt(ModDamageTypes.source(level(), ModDamageTypes.STAR_BOLT, this, owner), damage)) {
            if (owner instanceof LivingEntity lo) doEnchantDamageEffects(lo, target);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            var p = getVariant() == 0 ? ModParticles.STAR_SPARK.get() : getVariant() == 1 ? ModParticles.VOID_SPARK.get() : ModParticles.ASTRAL_SPARK.get();
            server.sendParticles(p, getX(), getY(), getZ(), 12, 0.1, 0.1, 0.1, 0.12);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("Variant", getVariant());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        setVariant(tag.getInt("Variant"));
    }
}
