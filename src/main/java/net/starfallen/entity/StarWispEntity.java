package net.starfallen.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.starfallen.entity.projectile.StarBoltEntity;
import net.starfallen.registry.ModItems;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.Effects;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Star Wisp: a curious mote of living starlight. Tame one with Stardust and it becomes a
 * loyal companion that shoots star bolts at your enemies and slowly mends your wounds.
 */
public class StarWispEntity extends TamableAnimal implements FlyingAnimal {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(StarWispEntity.class, EntityDataSerializers.INT);
    public static final int VARIANTS = 5;
    private int blessCooldown = 200;

    public StarWispEntity(EntityType<? extends StarWispEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0D).add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new WispBoltGoal(this));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2D, 6.0F, 2.0F, true));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1D, Ingredient.of(ModItems.STARDUST.get()), false));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
    }

    public int getVariant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, Math.floorMod(v, VARIANTS));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        setVariant(random.nextInt(VARIANTS));
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean stardust = stack.is(ModItems.STARDUST.get());
        if (!isTame() && stardust) {
            if (!level().isClientSide) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                if (random.nextInt(3) == 0 && !net.minecraftforge.event.ForgeEventFactory.onAnimalTame(this, player)) {
                    tame(player);
                    navigation.stop();
                    setOrderedToSit(false);
                    level().broadcastEntityEvent(this, (byte) 7);
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.STAR_CHIME.get(), SoundSource.NEUTRAL, 1.0F, 1.5F);
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) SFUtil.award(sp, "wisp_friend");
                } else {
                    level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isTame() && isOwnedBy(player)) {
            if (stardust && getHealth() < getMaxHealth()) {
                if (!level().isClientSide) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    heal(6.0F);
                    level().broadcastEntityEvent(this, (byte) 7);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (!level().isClientSide) {
                setOrderedToSit(!isOrderedToSit());
                navigation.stop();
                setTarget(null);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                var p = switch (getVariant()) {
                    case 1 -> ModParticles.ASTRAL_SPARK.get();
                    case 3 -> ModParticles.VOID_SPARK.get();
                    default -> ModParticles.STAR_SPARK.get();
                };
                level().addParticle(p, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.25 + (random.nextDouble() - 0.5) * 0.4,
                        getZ() + (random.nextDouble() - 0.5) * 0.4, 0, -0.01, 0);
            }
            return;
        }
        // Gentle bob so they always look alive
        if (!onGround() && getNavigation().isDone()) {
            setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount * 0.1) * 0.004, 0));
        }
        if (isTame()) {
            LivingEntity owner = getOwner();
            if (owner != null && --blessCooldown <= 0) {
                blessCooldown = 160;
                if (owner.getHealth() < owner.getMaxHealth() && owner.distanceToSqr(this) < 144) {
                    owner.heal(1.0F);
                    Effects.line((ServerLevel) level(), position().add(0, 0.3, 0), owner.position().add(0, 1, 0), ModParticles.STAR_SPARK.get(), 0.5);
                    level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.STAR_CHIME.get(), SoundSource.NEUTRAL, 0.4F, 1.8F);
                }
            }
        } else {
            // Wild wisps fade away in daylight
            long t = level().getDayTime() % 24000L;
            if ((t < 12500L || t > 23500L) && level().canSeeSky(blockPosition()) && random.nextInt(600) == 0) {
                ((ServerLevel) level()).sendParticles(ModParticles.STAR_SPARK.get(), getX(), getY() + 0.3, getZ(), 15, 0.2, 0.2, 0.2, 0.05);
                discard();
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !isTame() && !hasCustomName();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return false;
    }

    @Override
    public boolean isFlying() {
        return !onGround();
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || super.isInvulnerableTo(source);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return dims.height * 0.5F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.WISP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.WISP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WISP_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
    }

    public static boolean checkWispSpawnRules(EntityType<StarWispEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return true;
    }

    /** Tamed wisps pelt their owner's enemies with star bolts. */
    static class WispBoltGoal extends Goal {
        private final StarWispEntity wisp;
        private int cooldown;

        WispBoltGoal(StarWispEntity wisp) {
            this.wisp = wisp;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = wisp.getTarget();
            return wisp.isTame() && !wisp.isOrderedToSit() && t != null && t.isAlive() && wisp.distanceToSqr(t) < 20 * 20;
        }

        @Override
        public void tick() {
            LivingEntity t = wisp.getTarget();
            if (t == null) return;
            wisp.getLookControl().setLookAt(t, 30, 30);
            if (--cooldown <= 0 && wisp.hasLineOfSight(t)) {
                cooldown = 24;
                Vec3 from = wisp.position().add(0, 0.3, 0);
                Vec3 dir = t.getBoundingBox().getCenter().subtract(from).normalize();
                StarBoltEntity bolt = new StarBoltEntity(wisp.level(), from.add(dir.scale(0.4)), dir.scale(0.8), wisp, 4.0F);
                bolt.setVariant(0);
                bolt.setHoming(t, 0.08);
                wisp.level().addFreshEntity(bolt);
                wisp.playSound(ModSounds.STAR_BOLT.get(), 0.6F, 1.6F);
            }
        }
    }
}
