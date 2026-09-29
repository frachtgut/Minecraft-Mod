package net.starfallen.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

/**
 * Nebula Jelly: a drifting, translucent sky-jellyfish that appears on clear nights. Peaceful
 * unless provoked - then its star-lit tendrils crackle with lightning.
 */
public class NebulaJellyEntity extends FlyingMob {
    public int pulseTimer;
    public int zapTimer;
    @Nullable private Vec3 wanderTarget;
    private int wanderTime;
    private int angerTime;
    private int zapCooldown = 30;

    public NebulaJellyEntity(EntityType<? extends NebulaJellyEntity> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0D).add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D).add(Attributes.MOVEMENT_SPEED, 0.2D);
    }

    public boolean isAngry() {
        return angerTime > 0 && getTarget() != null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (pulseTimer > 0) pulseTimer--;
        if (zapTimer > 0) zapTimer--;
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                level().addParticle(random.nextBoolean() ? ModParticles.VOID_SPARK.get() : ModParticles.ASTRAL_SPARK.get(),
                        getRandomX(0.8), getY() + random.nextDouble() * 0.6, getRandomZ(0.8), 0, -0.03, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (angerTime > 0) angerTime--;
        if (target != null && (!target.isAlive() || angerTime <= 0 || distanceToSqr(target) > 32 * 32
                || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            target = null;
        }

        Vec3 goal;
        if (target != null) {
            goal = target.position().add(0, 4.5, 0);
        } else {
            if (wanderTarget == null || --wanderTime <= 0 || wanderTarget.distanceToSqr(position()) < 4) {
                pickWanderTarget();
            }
            goal = wanderTarget;
        }
        // Pulse propulsion
        if (tickCount % 32 == 0 && goal != null) {
            Vec3 dir = goal.subtract(position());
            double len = dir.length();
            if (len > 1.0) {
                Vec3 push = dir.normalize().scale(Math.min(0.42, 0.12 + len * 0.02));
                setDeltaMovement(getDeltaMovement().add(push.x, push.y + 0.05, push.z));
                level().broadcastEntityEvent(this, (byte) 60);
                pulseTimer = 20;
            }
        }
        // Gentle sinking between pulses
        setDeltaMovement(getDeltaMovement().add(0, -0.004, 0));

        if (target != null) {
            getLookControl().setLookAt(target, 10, 10);
            if (--zapCooldown <= 0 && distanceToSqr(target) < 13 * 13 && hasLineOfSight(target)) {
                zapCooldown = 40 + random.nextInt(20);
                zap(target);
            }
            // Sting anything directly beneath the tendrils
            if (tickCount % 20 == 0) {
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(0, -3.5, 0).inflate(0.4),
                        e -> e != this && !(e instanceof NebulaJellyEntity))) {
                    e.hurt(damageSources().mobAttack(this), 3.0F);
                }
            }
        }
        // Fade away with the dawn
        long t = level().getDayTime() % 24000L;
        if ((t < 12500L || t > 23500L) && level().canSeeSky(blockPosition()) && !isPersistenceRequired() && random.nextInt(500) == 0) {
            ((ServerLevel) level()).sendParticles(ModParticles.ASTRAL_SPARK.get(), getX(), getY() + 0.7, getZ(), 30, 0.5, 0.5, 0.5, 0.05);
            discard();
        }
    }

    private void pickWanderTarget() {
        wanderTime = 160 + random.nextInt(160);
        double x = getX() + (random.nextDouble() - 0.5) * 32;
        double z = getZ() + (random.nextDouble() - 0.5) * 32;
        double y;
        if (level().canSeeSky(blockPosition())) {
            int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
            y = ground + 8 + random.nextDouble() * 18;
        } else {
            y = getY() + (random.nextDouble() - 0.5) * 6;
        }
        Vec3 dest = new Vec3(x, y, z);
        HitResult hit = level().clip(new ClipContext(position(), dest, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        wanderTarget = hit.getType() == HitResult.Type.MISS ? dest : hit.getLocation().subtract(dest.subtract(position()).normalize().scale(2));
    }

    private void zap(LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        Vec3 from = position().add(0, 0.2, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        // Jagged lightning tendril
        Vec3 prev = from;
        int segs = 8;
        for (int i = 1; i <= segs; i++) {
            Vec3 p = from.lerp(to, i / (double) segs);
            if (i < segs) p = p.add((random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.9);
            net.starfallen.util.Effects.line(level, prev, p, ModParticles.ASTRAL_SPARK.get(), 0.3);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.1);
            prev = p;
        }
        level.playSound(null, getX(), getY(), getZ(), ModSounds.JELLY_ZAP.get(), SoundSource.HOSTILE, 1.3F, 0.9F + random.nextFloat() * 0.3F);
        if (target.hurt(ModDamageTypes.source(level, ModDamageTypes.STAR_BOLT, this, this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), this);
        }
        level.broadcastEntityEvent(this, (byte) 61);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 60) pulseTimer = 20;
        else if (id == 61) zapTimer = 10;
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof NebulaJellyEntity)) {
            if (!(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                setTarget(attacker);
                angerTime = 600;
                // Nearby jellies join in
                for (NebulaJellyEntity other : level().getEntitiesOfClass(NebulaJellyEntity.class, getBoundingBox().inflate(16))) {
                    if (other != this && other.getTarget() == null) {
                        other.setTarget(attacker);
                        other.angerTime = 400;
                    }
                }
            }
        }
        return hurt;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !hasCustomName();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.JELLY_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return net.minecraft.sounds.SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WISP_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 180;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Anger", angerTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        angerTime = tag.getInt("Anger");
    }
}
