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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;

/**
 * Astral Mimic. Looks exactly like a treasure chest - right up until you try to open it.
 * Then it bites, and chases you across the room in great snapping hops.
 */
public class AstralMimicEntity extends Monster {
    private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(AstralMimicEntity.class, EntityDataSerializers.BOOLEAN);
    public int biteAnim;
    public float lidOpen, prevLidOpen;
    private int hopCooldown;
    private int proximityTicks;
    private int idleTicks;

    public AstralMimicEntity(EntityType<? extends AstralMimicEntity> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 50.0D).add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 6.0D).add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AWAKE, false);
    }

    public boolean isAwake() {
        return entityData.get(AWAKE);
    }

    /** Snap the dormant mimic to the block grid, facing like a placed chest. */
    public void settle(BlockPos pos, float yaw) {
        float snapped = Math.round(yaw / 90.0F) * 90.0F;
        moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, snapped, 0);
        setYHeadRot(snapped);
        yBodyRot = snapped;
        setPersistenceRequired();
    }

    public void wake(Player trigger) {
        if (isAwake()) return;
        entityData.set(AWAKE, true);
        setTarget(trigger);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.MIMIC_REVEAL.get(), SoundSource.HOSTILE, 1.4F, 1.0F);
        setDeltaMovement(0, 0.5, 0);
        hopCooldown = 12;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        }
        if (trigger instanceof net.minecraft.server.level.ServerPlayer sp) SFUtil.award(sp, "surprise");
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAwake()) {
            if (!level().isClientSide && !player.isCreative()) {
                wake(player);
                bite(player);
            } else if (!level().isClientSide) {
                wake(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!isAwake() && source.getEntity() instanceof Player p && !level().isClientSide) wake(p);
        return super.hurt(source, amount);
    }

    private void bite(LivingEntity target) {
        biteAnim = 10;
        level().broadcastEntityEvent(this, (byte) 4);
        level().playSound(null, getX(), getY(), getZ(), ModSounds.MIMIC_CHOMP.get(), SoundSource.HOSTILE, 1.2F, 0.9F + random.nextFloat() * 0.2F);
        doHurtTarget(target);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) biteAnim = 10;
        else super.handleEntityEvent(id);
    }

    @Override
    public void tick() {
        super.tick();
        prevLidOpen = lidOpen;
        if (biteAnim > 0) biteAnim--;
        float targetOpen = !isAwake() ? 0.0F : biteAnim > 5 ? 1.0F : biteAnim > 0 ? 0.1F : 0.55F + 0.15F * Mth.sin(tickCount * 0.35F);
        lidOpen += (targetOpen - lidOpen) * 0.45F;
        if (level().isClientSide && isAwake() && random.nextInt(4) == 0) {
            level().addParticle(ModParticles.STAR_SPARK.get(), getRandomX(0.5), getY() + 0.8, getRandomZ(0.5), 0, 0.03, 0);
        }
    }

    @Override
    protected void customServerAiStep() {
        if (!isAwake()) {
            // Dormant: a perfectly ordinary chest. Wakes when someone lingers right beside it.
            setDeltaMovement(0, getDeltaMovement().y, 0);
            Player near = level().getNearestPlayer(this, 1.9);
            if (near != null && !near.isCreative() && !near.isSpectator()) {
                if (++proximityTicks > 16) wake(near);
            } else {
                proximityTicks = 0;
            }
            return;
        }
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            target = level().getNearestPlayer(this, 16);
            if (target != null && ((Player) target).isCreative()) target = null;
            setTarget(target);
        }
        if (target == null) {
            if (++idleTicks > 200) {
                entityData.set(AWAKE, false);
                idleTicks = 0;
            }
            return;
        }
        idleTicks = 0;
        getLookControl().setLookAt(target, 30, 30);
        float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180F / Math.PI)) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, 20));
        yBodyRot = getYRot();
        if (onGround()) {
            if (distanceToSqr(target) < 2.2 * 2.2 && biteAnim <= 0 && hopCooldown <= 6) {
                bite(target);
                hopCooldown = 16;
            } else if (--hopCooldown <= 0) {
                Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
                double d = to.length();
                Vec3 dir = d < 1.0E-3 ? Vec3.ZERO : to.normalize();
                double power = Math.min(0.75, 0.3 + d * 0.06);
                setDeltaMovement(dir.x * power, 0.48, dir.z * power);
                hasImpulse = true;
                hopCooldown = 14 + random.nextInt(6);
                playSound(ModSounds.MIMIC_CHOMP.get(), 0.4F, 1.6F);
            }
        } else if (hopCooldown > 0) {
            hopCooldown--;
        }
    }

    @Override
    public boolean isPushable() {
        return isAwake();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isAwake() && isAlive();
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isAwake() ? ModSounds.MIMIC_CHOMP.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return net.minecraft.sounds.SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.CHEST_CLOSE;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", isAwake());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AWAKE, tag.getBoolean("Awake"));
    }
}
