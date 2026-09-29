package net.starfallen.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

/**
 * A miniature black hole. Travels briefly, then anchors itself and drags everything nearby
 * inward before collapsing in a violent implosion.
 */
public class SingularityEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GREATER = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTIVE_AGE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    public static final int TRAVEL_TIME = 22;
    public static final int ACTIVE_TIME = 90;
    private int travel;

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    /** @param greater Astraeon's version: bigger, stronger, hurts players. */
    public SingularityEntity(Level level, @Nullable Entity owner, Vec3 pos, Vec3 velocity, boolean greater) {
        this(ModEntities.SINGULARITY.get(), level);
        setOwner(owner);
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(velocity);
        entityData.set(GREATER, greater);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ACTIVE, false);
        entityData.define(GREATER, false);
        entityData.define(ACTIVE_AGE, 0);
    }

    public boolean isActive() {
        return entityData.get(ACTIVE);
    }

    public boolean isGreater() {
        return entityData.get(GREATER);
    }

    public int getActiveAge() {
        return entityData.get(ACTIVE_AGE);
    }

    public float radius() {
        return isGreater() ? 13.0F : 9.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isActive()) {
            Vec3 vel = getDeltaMovement();
            Vec3 to = position().add(vel);
            boolean stop = ++travel >= TRAVEL_TIME;
            if (!level().isClientSide) {
                HitResult hit = level().clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.MISS) {
                    to = hit.getLocation().subtract(vel.normalize().scale(0.8));
                    stop = true;
                }
                Entity owner = getOwner();
                if (!level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(vel).inflate(0.5),
                        e -> e != owner && e.isAlive() && (owner == null || SFUtil.isValidTarget(owner, e))).isEmpty()) {
                    stop = true;
                }
                if (stop) {
                    entityData.set(ACTIVE, true);
                    setDeltaMovement(Vec3.ZERO);
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.SINGULARITY_HUM.get(), SoundSource.PLAYERS, 1.5F, 0.6F);
                }
            }
            setPos(to.x, to.y, to.z);
            if (level().isClientSide) {
                level().addParticle(ModParticles.VOID_SPARK.get(), getX(), getY() + 0.5, getZ(), 0, 0, 0);
            }
            return;
        }

        int age = getActiveAge();
        float r = radius();
        Vec3 c = position().add(0, 0.5, 0);
        if (level().isClientSide) {
            clientVortex(c, r, age);
            return;
        }
        entityData.set(ACTIVE_AGE, age + 1);
        Entity owner = getOwner();
        boolean greater = isGreater();
        for (Entity e : level().getEntities(this, new AABB(c, c).inflate(r))) {
            if (e == owner || e instanceof SingularityEntity || e.isSpectator()) continue;
            if (e instanceof Player p && (p.isCreative() || (!greater && owner instanceof Player op && !op.canHarmPlayer(p)))) continue;
            if (e instanceof LivingEntity && owner != null && !greater && !SFUtil.isValidTarget(owner, e)) continue;
            if (!(e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile)) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double dist = to.length();
            if (dist > r || dist < 0.01) continue;
            double strength = (greater ? 0.11 : 0.14) * (1.0 - dist / r) + 0.02;
            if (e instanceof LivingEntity le) strength *= 1.0 - 0.7 * le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            Vec3 pull = to.normalize().scale(strength);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.9).add(pull));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity le && dist < 2.2 && age % 10 == 0) {
                le.hurt(ModDamageTypes.source(level(), ModDamageTypes.VOID_REND, this, owner), greater ? 4.0F : 3.0F);
            }
        }
        if (age % 20 == 0) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.SINGULARITY_HUM.get(), SoundSource.PLAYERS, 1.2F, 0.5F + age / (float) ACTIVE_TIME);
        }
        if (age >= ACTIVE_TIME) collapse((ServerLevel) level(), c, owner);
    }

    private void clientVortex(Vec3 c, float r, int age) {
        int n = isGreater() ? 8 : 5;
        for (int i = 0; i < n; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double rr = r * (0.4 + random.nextDouble() * 0.6);
            Vec3 p = c.add(Math.sin(phi) * Math.cos(theta) * rr, Math.cos(phi) * rr * 0.5, Math.sin(phi) * Math.sin(theta) * rr);
            Vec3 in = c.subtract(p).scale(0.08);
            Vec3 swirl = new Vec3(-in.z, 0, in.x).scale(1.2);
            level().addParticle(ModParticles.VOID_SPARK.get(), p.x, p.y, p.z, in.x + swirl.x, in.y, in.z + swirl.z);
        }
        if (age % 3 == 0) level().addParticle(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 0, 0, 0);
    }

    private void collapse(ServerLevel level, Vec3 c, @Nullable Entity owner) {
        boolean greater = isGreater();
        float dmg = greater ? 14.0F : 12.0F;
        double r = greater ? 7.5 : 5.5;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e != owner && e.isAlive() && (greater || owner == null || SFUtil.isValidTarget(owner, e)))) {
            if (e instanceof Player p && p.isCreative()) continue;
            double d = e.position().distanceTo(c);
            if (d > r) continue;
            e.invulnerableTime = 0;
            if (e.hurt(ModDamageTypes.source(level, ModDamageTypes.VOID_REND, this, owner), dmg * (float) (1.0 - 0.5 * d / r))) {
                Vec3 away = e.position().subtract(c).normalize();
                e.setDeltaMovement(away.x * 1.1, 0.9, away.z * 1.1);
                e.hurtMarked = true;
            }
        }
        level.playSound(null, c.x, c.y, c.z, ModSounds.SINGULARITY_COLLAPSE.get(), SoundSource.PLAYERS, 2.5F, 1.0F);
        level.sendParticles(ModParticles.NOVA_FLASH.get(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.VOID_SHOCKWAVE.get(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.VOID_SPARK.get(), c.x, c.y, c.z, 80, 0.3, 0.3, 0.3, 0.6);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 1.0, 0.5, 1.0, 0);
        SFNetwork.shakeAround(level, c, 32, greater ? 1.2F : 0.8F, 16);
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 160 * 160;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
