package net.starfallen.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModItems;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.ClientInput;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Comet Ray: a vast, gentle manta that glides through the night sky trailing stardust.
 * Hatch one from a Stellar Egg (or befriend a wild one with Stardust) and ride it into the sky.
 * W = fly where you look, Space = climb, Sprint = comet boost.
 */
public class CometRayEntity extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> BOOSTING = SynchedEntityData.defineId(CometRayEntity.class, EntityDataSerializers.BOOLEAN);
    private int boostTicks;
    private int boostCooldown;
    private int serverBoostTicks;
    private float circleAngle;
    @Nullable private Vec3 anchor;
    public float flap, prevFlap;

    public CometRayEntity(EntityType<? extends CometRayEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.6D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BOOSTING, false);
    }

    public boolean isBoosting() {
        return entityData.get(BOOSTING);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTame()) {
            if (stack.is(ModItems.STARDUST.get()) || stack.is(ModItems.STAR_FRAGMENT.get())) {
                if (!level().isClientSide) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    boolean fragment = stack.is(ModItems.STAR_FRAGMENT.get());
                    if ((fragment || random.nextInt(4) == 0) && !net.minecraftforge.event.ForgeEventFactory.onAnimalTame(this, player)) {
                        tame(player);
                        level().broadcastEntityEvent(this, (byte) 7);
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) SFUtil.award(sp, "comet_rider");
                    } else {
                        level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (isOwnedBy(player)) {
            if (stack.is(ModItems.STARDUST.get()) && getHealth() < getMaxHealth()) {
                if (!level().isClientSide) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    heal(8.0F);
                    level().broadcastEntityEvent(this, (byte) 7);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (player.isSecondaryUseActive()) {
                if (!level().isClientSide) setOrderedToSit(!isOrderedToSit());
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (!level().isClientSide) {
                setOrderedToSit(false);
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (isTame() && getFirstPassenger() instanceof Player p && isOwnedBy(p)) return p;
        return null;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        setYRot(Mth.approachDegrees(getYRot(), rider.getYRot(), 12.0F));
        yRotO = getYRot();
        setXRot(rider.getXRot() * 0.6F);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        return new Vec3(rider.xxa * 0.6, 0, rider.zza);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) getAttributeValue(Attributes.FLYING_SPEED);
    }

    @Override
    public void travel(Vec3 input) {
        if (isVehicle() && getControllingPassenger() instanceof Player rider) {
            if (isControlledByLocalInstance()) {
                boolean boost = ClientInput.SPRINT.getAsBoolean();
                if (boost && boostCooldown <= 0 && boostTicks <= 0 && input.z > 0) {
                    boostTicks = 50;
                    boostCooldown = 120;
                    ClientInput.onRayBoost(this);
                }
                if (boostTicks > 0) boostTicks--;
                if (boostCooldown > 0) boostCooldown--;
                double speed = 0.55 * (boostTicks > 0 ? 2.1 : 1.0);
                Vec3 look = rider.getLookAngle();
                Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                Vec3 desired = look.scale(input.z * speed).add(right.scale(-input.x * speed * 0.7));
                if (ClientInput.JUMP.getAsBoolean()) desired = desired.add(0, 0.45, 0);
                Vec3 vel = getDeltaMovement().scale(0.88).add(desired.scale(0.14));
                if (input.z == 0 && !ClientInput.JUMP.getAsBoolean()) vel = vel.add(0, Math.sin(tickCount * 0.08) * 0.004, 0);
                setDeltaMovement(vel);
                move(MoverType.SELF, vel);
            } else {
                setDeltaMovement(Vec3.ZERO);
            }
            calculateEntityAnimation(false);
            return;
        }
        // Unridden flight: simple drag model, AI sets velocity in aiStep
        if (isControlledByLocalInstance()) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.91));
        }
        calculateEntityAnimation(false);
    }

    @Override
    public void tick() {
        super.tick();
        prevFlap = flap;
        float speed = (float) getDeltaMovement().length();
        flap += 0.12F + speed * 0.5F;
        setNoGravity(true);
        if (level().isClientSide) {
            float yaw = yBodyRot * ((float) Math.PI / 180F);
            double tx = getX() + Math.sin(yaw) * 1.6;
            double tz = getZ() - Math.cos(yaw) * 1.6;
            int n = isBoosting() ? 4 : 1;
            for (int i = 0; i < n; i++) {
                level().addParticle(isBoosting() ? ModParticles.EMBER.get() : ModParticles.ASTRAL_SPARK.get(),
                        tx + (random.nextDouble() - 0.5) * 0.3, getY() + 0.3, tz + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ModParticles.STAR_SPARK.get(), getRandomX(1.0), getY() + 0.4, getRandomZ(1.0), 0, -0.02, 0);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (serverBoostTicks > 0) serverBoostTicks--;
        entityData.set(BOOSTING, serverBoostTicks > 0);
        if (isVehicle()) {
            fallDistance = 0;
            return;
        }
        LivingEntity owner = getOwner();
        Vec3 goal;
        if (isTame() && owner != null && !isOrderedToSit()) {
            if (distanceToSqr(owner) > 64 * 64) {
                teleportTo(owner.getX(), owner.getY() + 3, owner.getZ());
            }
            circleAngle += 0.05F;
            goal = owner.position().add(Math.cos(circleAngle) * 4, 3.0 + Math.sin(tickCount * 0.05) * 0.5, Math.sin(circleAngle) * 4);
        } else if (isTame()) {
            if (anchor == null) anchor = position();
            goal = anchor.add(0, Math.sin(tickCount * 0.05) * 0.3, 0);
        } else {
            // Wild: glide in lazy circles high above the ground; drift down towards anyone holding stardust
            Player lure = level().getNearestPlayer(this, 24);
            if (lure != null && (lure.getMainHandItem().is(ModItems.STARDUST.get()) || lure.getOffhandItem().is(ModItems.STARDUST.get()))) {
                goal = lure.position().add(0, 2.5, 0).add(lure.getLookAngle().multiply(1, 0, 1).scale(3));
            } else {
                if (anchor == null || tickCount % 400 == 0) {
                    int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
                    anchor = new Vec3(getX(), Math.max(ground + 18, getY() - 4), getZ());
                }
                circleAngle += 0.02F;
                goal = anchor.add(Math.cos(circleAngle) * 14, Math.sin(circleAngle * 2) * 2, Math.sin(circleAngle) * 14);
            }
        }
        if (isOrderedToSit()) anchor = anchor == null ? position() : anchor;
        if (!isOrderedToSit() && isTame()) anchor = null;
        Vec3 to = goal.subtract(position());
        double d = to.length();
        if (d > 0.3) {
            Vec3 dir = to.normalize();
            HitResult hit = level().clip(new ClipContext(position(), position().add(dir.scale(2.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) dir = dir.add(0, 0.8, 0).normalize();
            double speed = Math.min(0.32, 0.05 + d * 0.03);
            setDeltaMovement(getDeltaMovement().scale(0.85).add(dir.scale(speed * 0.15)));
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180F / Math.PI)) - 90.0F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 4.0F));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    /** Server: the rider triggered a comet boost. */
    public void startBoost() {
        serverBoostTicks = 50;
        entityData.set(BOOSTING, true);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.RAY_BOOST.get(), SoundSource.NEUTRAL, 1.5F, 1.0F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            double y = getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset();
            float yaw = yBodyRot * ((float) Math.PI / 180F);
            move.accept(passenger, getX() + Math.sin(yaw) * 0.2, y, getZ() - Math.cos(yaw) * 0.2);
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return getBbHeight() * 0.55;
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
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !isTame() && !hasCustomName();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)
                || super.isInvulnerableTo(source);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.RAY_AMBIENT.get();
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
    public int getAmbientSoundInterval() {
        return 320;
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
