package net.starfallen.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;

/**
 * Void Stalker. A tall, starless silhouette that cannot move while anyone is looking at it.
 * Look away - even for a moment - and it is suddenly much, much closer.
 */
public class VoidStalkerEntity extends Monster {
    private static final EntityDataAccessor<Boolean> OBSERVED = SynchedEntityData.defineId(VoidStalkerEntity.class, EntityDataSerializers.BOOLEAN);
    private int unobservedTicks;
    private int teleportCooldown = 60;
    private float frozenYaw, frozenHeadYaw;
    /** Client: animation clock that stops while observed so the stalker freezes mid-pose. */
    public float animClock;
    public float prevAnimClock;

    public VoidStalkerEntity(EntityType<? extends VoidStalkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D).add(Attributes.FOLLOW_RANGE, 48.0D).add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, p -> !((Player) p).isCreative()));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OBSERVED, false);
    }

    public boolean isObserved() {
        return entityData.get(OBSERVED);
    }

    /** True if the player is looking roughly at us with a clear line of sight. */
    public boolean isSeenBy(Player player) {
        if (player.isSpectator() || !player.isAlive() || player.hasEffect(MobEffects.BLINDNESS)) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double best = -1;
        for (double h : new double[]{0.15, 0.55, 0.92}) {
            Vec3 point = position().add(0, getBbHeight() * h, 0);
            Vec3 to = point.subtract(eye);
            double dist = to.length();
            if (dist > 64) return false;
            double dot = to.normalize().dot(look);
            if (dot > best) best = dot;
        }
        if (best < 0.52) return false;
        for (double h : new double[]{0.25, 0.85}) {
            Vec3 point = position().add(0, getBbHeight() * h, 0);
            if (level().clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private boolean anyoneWatching() {
        for (Player p : level().players()) {
            if (p.distanceToSqr(this) < 64 * 64 && isSeenBy(p)) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            boolean watched = anyoneWatching();
            if (watched != isObserved()) {
                entityData.set(OBSERVED, watched);
                updateControlFlags();
                if (watched) {
                    frozenYaw = getYRot();
                    frozenHeadYaw = getYHeadRot();
                    getNavigation().stop();
                    setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
                } else {
                    unobservedTicks = 0;
                }
            }
        }
        super.tick();
        if (isObserved()) {
            setYRot(frozenYaw);
            yRotO = frozenYaw;
            yBodyRot = frozenYaw;
            yBodyRotO = frozenYaw;
            setYHeadRot(frozenHeadYaw);
            yHeadRotO = frozenHeadYaw;
        }
        if (level().isClientSide) {
            prevAnimClock = animClock;
            if (!isObserved()) animClock += 1.0F;
            if (!isObserved() && random.nextInt(3) == 0) {
                level().addParticle(ModParticles.VOID_SPARK.get(), getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            }
        } else {
            frozenYaw = isObserved() ? frozenYaw : getYRot();
            frozenHeadYaw = isObserved() ? frozenHeadYaw : getYHeadRot();
        }
    }

    @Override
    protected void updateControlFlags() {
        super.updateControlFlags();
        if (isObserved()) {
            // Frozen: every movement, look and jump goal is suspended.
            goalSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, false);
            goalSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK, false);
            goalSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP, false);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isObserved()) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
            setXxa(0);
            setZza(0);
            setSpeed(0);
            setJumping(false);
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            return;
        }
        unobservedTicks++;
        LivingEntity target = getTarget();
        if (target instanceof Player player && --teleportCooldown <= 0) {
            teleportCooldown = 40 + random.nextInt(40);
            if (distanceTo(player) > 16 && random.nextInt(2) == 0) teleportBehind(player);
        }
    }

    /** Teleports somewhere near the target that nobody is looking at. */
    private void teleportBehind(Player target) {
        Vec3 look = target.getViewVector(1.0F).multiply(1, 0, 1).normalize();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = Math.PI + (random.nextDouble() - 0.5) * 1.6;
            double dist = 8 + random.nextDouble() * 6;
            Vec3 dir = look.yRot((float) angle);
            Vec3 dest = target.position().add(dir.scale(dist));
            BlockPos base = BlockPos.containing(dest.x, target.getY() + 4, dest.z);
            for (int dy = 0; dy < 10; dy++) {
                BlockPos p = base.below(dy);
                if (level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)
                        && level().noCollision(this, getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(position())))) {
                    Vec3 old = position();
                    Vec3 np = Vec3.atBottomCenterOf(p);
                    setPos(np.x, np.y, np.z);
                    if (anyoneWatching()) {
                        setPos(old.x, old.y, old.z);
                        break;
                    }
                    ((ServerLevel) level()).sendParticles(ModParticles.VOID_SPARK.get(), old.x, old.y + 1.4, old.z, 20, 0.3, 0.8, 0.3, 0.02);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, np.x, np.y + 1.4, np.z, 10, 0.3, 0.8, 0.3, 0.01);
                    getNavigation().stop();
                    return;
                }
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean behind = target instanceof Player p && !isSeenBy(p);
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            if (behind) {
                level().playSound(null, getX(), getY(), getZ(), ModSounds.STALKER_SCREAM.get(), SoundSource.HOSTILE, 1.6F, 0.9F + random.nextFloat() * 0.2F);
            }
        }
        return hit;
    }

    @Override
    public boolean isPushable() {
        return !isObserved() && super.isPushable();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isObserved()) super.knockback(strength, x, z);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isObserved() ? null : ModSounds.STALKER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STALKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.STALKER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.STONE_STEP, 0.15F, 0.5F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return dims.height * 0.9F;
    }

    public float getAnimClock(float partial) {
        return Mth.lerp(partial, prevAnimClock, animClock);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
