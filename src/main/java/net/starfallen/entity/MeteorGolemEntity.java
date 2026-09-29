package net.starfallen.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.starfallen.entity.projectile.MoltenRockEntity;
import net.starfallen.entity.projectile.ShockwaveEntity;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.Effects;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Meteor Golem: molten rock given fury by a fallen star. Punches, pounds the ground with
 * travelling shockwaves and hurls globs of molten meteorite. Overheats when badly hurt.
 */
public class MeteorGolemEntity extends Monster {
    public static final int STATE_IDLE = 0, STATE_POUND = 1, STATE_THROW = 2, STATE_EMERGE = 3;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MeteorGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_TICKS = SynchedEntityData.defineId(MeteorGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OVERHEATED = SynchedEntityData.defineId(MeteorGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final UUID OVERHEAT_SPEED = UUID.fromString("c3b7a6a2-9e61-4a1f-a3b0-0f5d7e2d9a11");
    public static final int POUND_WINDUP = 18, THROW_WINDUP = 16, EMERGE_TIME = 50;

    public int attackAnim;
    private int poundCooldown = 60;
    private int throwCooldown = 40;
    private int punchCooldown;

    public MeteorGolemEntity(EntityType<? extends MeteorGolemEntity> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 120.0D).add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.ARMOR, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.23D).add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.ATTACK_KNOCKBACK, 1.2D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GolemCombatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, STATE_IDLE);
        entityData.define(STATE_TICKS, 0);
        entityData.define(OVERHEATED, false);
    }

    public int getState() {
        return entityData.get(STATE);
    }

    public int getStateTicks() {
        return entityData.get(STATE_TICKS);
    }

    public boolean isOverheated() {
        return entityData.get(OVERHEATED);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        entityData.set(STATE_TICKS, 0);
    }

    /** Called when the golem is born from a meteor impact: it claws its way out of the crater. */
    public void emerge() {
        setState(STATE_EMERGE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            int st = getState();
            if (st != STATE_IDLE) entityData.set(STATE_TICKS, getStateTicks() + 1);
            if (st == STATE_EMERGE) {
                getNavigation().stop();
                if (getStateTicks() % 5 == 0) {
                    BlockState below = level().getBlockState(blockPosition().below());
                    if (!below.isAir()) {
                        ((ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.2, getZ(),
                                20, 0.8, 0.2, 0.8, 0.1);
                    }
                }
                if (getStateTicks() >= EMERGE_TIME) {
                    setState(STATE_IDLE);
                    playSound(ModSounds.GOLEM_AMBIENT.get(), 3.0F, 0.6F);
                    Effects.ring((ServerLevel) level(), position().add(0, 0.1, 0), ModParticles.SHOCKWAVE.get());
                }
            }
        } else {
            if (isOverheated() || getState() == STATE_POUND) {
                level().addParticle(ModParticles.EMBER.get(), getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.05, 0);
            }
            if (random.nextInt(4) == 0) {
                level().addParticle(ModParticles.EMBER.get(), getRandomX(0.6), getY() + getBbHeight() * 0.7, getRandomZ(0.6), 0, 0.03, 0);
            }
        }
        if (attackAnim > 0) attackAnim--;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isOverheated() && getHealth() < getMaxHealth() * 0.35F) {
            entityData.set(OVERHEATED, true);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(OVERHEAT_SPEED) == null) {
                speed.addTransientModifier(new AttributeModifier(OVERHEAT_SPEED, "Overheat", 0.35D, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            playSound(ModSounds.GOLEM_AMBIENT.get(), 3.0F, 0.9F);
            ((ServerLevel) level()).sendParticles(ParticleTypes.LAVA, getX(), getY() + 2, getZ(), 30, 0.8, 0.8, 0.8, 0);
        }
        if (punchCooldown > 0) punchCooldown--;
        if (poundCooldown > 0) poundCooldown--;
        if (throwCooldown > 0) throwCooldown--;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return getState() == STATE_EMERGE && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        attackAnim = 10;
        level().broadcastEntityEvent(this, (byte) 4);
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.45, 0));
            target.setSecondsOnFire(isOverheated() ? 5 : 2);
        }
        playSound(ModSounds.GOLEM_SLAM.get(), 1.0F, 1.4F);
        return hit;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            attackAnim = 10;
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void doPound() {
        ServerLevel level = (ServerLevel) level();
        Vec3 c = position();
        float dmg = isOverheated() ? 14.0F : 11.0F;
        Effects.slam(level, this, c, 4.5, dmg, 0.8, damageSources().mobAttack(this), ModSounds.GOLEM_SLAM.get());
        ShockwaveEntity.spawn(level, c.add(0, 0.05, 0), this, 11.0F, 0.42F, 7.0F, false);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 14, 1.5, 0.1, 1.5, 0);
    }

    private void doThrow(LivingEntity target) {
        MoltenRockEntity rock = new MoltenRockEntity(level(), this);
        Vec3 hand = position().add(0, getBbHeight() + 0.4, 0);
        rock.setPos(hand.x, hand.y, hand.z);
        double dx = target.getX() - hand.x;
        double dz = target.getZ() - hand.z;
        double dy = target.getY(0.3) - hand.y;
        double dist = Math.sqrt(dx * dx + dz * dz);
        rock.shoot(dx, dy + dist * 0.22, dz, 1.15F + (float) dist * 0.012F, 2.0F);
        level().addFreshEntity(rock);
        playSound(ModSounds.GOLEM_AMBIENT.get(), 1.5F, 1.3F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GOLEM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GOLEM_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.6F);
    }

    @Override
    protected float getSoundVolume() {
        return 1.5F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Overheated", isOverheated());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OVERHEATED, tag.getBoolean("Overheated"));
    }

    /** Chooses between punches, ground pounds and molten-rock throws. */
    static class GolemCombatGoal extends Goal {
        private final MeteorGolemEntity golem;
        private int repath;

        GolemCombatGoal(MeteorGolemEntity golem) {
            this.golem = golem;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = golem.getTarget();
            return t != null && t.isAlive() && golem.getState() != STATE_EMERGE;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || golem.getState() == STATE_POUND || golem.getState() == STATE_THROW;
        }

        @Override
        public void stop() {
            if (golem.getState() != STATE_EMERGE) golem.setState(STATE_IDLE);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = golem.getTarget();
            int state = golem.getState();
            if (state == STATE_POUND) {
                golem.getNavigation().stop();
                if (target != null) golem.getLookControl().setLookAt(target, 30, 30);
                if (golem.getStateTicks() == 1) golem.playSound(ModSounds.GOLEM_AMBIENT.get(), 2.0F, 0.7F);
                if (golem.getStateTicks() >= POUND_WINDUP) {
                    golem.doPound();
                    golem.setState(STATE_IDLE);
                    golem.poundCooldown = golem.isOverheated() ? 70 : 110;
                }
                return;
            }
            if (state == STATE_THROW) {
                golem.getNavigation().stop();
                if (target != null) golem.getLookControl().setLookAt(target, 30, 30);
                if (golem.getStateTicks() >= THROW_WINDUP) {
                    if (target != null) golem.doThrow(target);
                    golem.setState(STATE_IDLE);
                    golem.throwCooldown = golem.isOverheated() ? 50 : 80;
                }
                return;
            }
            if (target == null) return;
            golem.getLookControl().setLookAt(target, 30, 30);
            double dist = golem.distanceTo(target);
            if (dist < 6.5 && golem.poundCooldown <= 0 && golem.random.nextInt(3) == 0) {
                golem.setState(STATE_POUND);
                return;
            }
            if (dist > 7 && dist < 24 && golem.throwCooldown <= 0 && golem.hasLineOfSight(target)) {
                golem.setState(STATE_THROW);
                return;
            }
            double reach = golem.getBbWidth() * 1.3 + target.getBbWidth() * 0.5 + 0.8;
            if (dist <= reach && golem.punchCooldown <= 0) {
                golem.punchCooldown = 22;
                golem.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                golem.doHurtTarget(target);
            }
            if (--repath <= 0) {
                repath = 8;
                golem.getNavigation().moveTo(target, 1.0D);
            }
        }
    }
}
