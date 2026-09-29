package net.starfallen.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.item.StarTetherItem;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import org.jetbrains.annotations.Nullable;

/**
 * The Star Tether's hook. Once it bites into a block it reels its owner in; if it strikes a
 * creature it yanks the creature towards the owner instead.
 */
public class TetherHookEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(TetherHookEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double MAX_RANGE = 48.0;
    private int anchoredTicks;
    private int life;

    public TetherHookEntity(EntityType<? extends TetherHookEntity> type, Level level) {
        super(type, level);
    }

    public TetherHookEntity(Level level, Player owner) {
        this(ModEntities.TETHER_HOOK.get(), level);
        setOwner(owner);
        Vec3 look = owner.getLookAngle();
        setPos(owner.getX() + look.x * 0.5, owner.getEyeY() - 0.2 + look.y * 0.5, owner.getZ() + look.z * 0.5);
        setDeltaMovement(look.scale(2.6));
        updateRotation();
    }

    @Nullable
    public static TetherHookEntity findActive(Level level, Player player) {
        for (TetherHookEntity hook : level.getEntitiesOfClass(TetherHookEntity.class, player.getBoundingBox().inflate(MAX_RANGE + 8))) {
            if (hook.getOwner() == player && hook.isAlive()) return hook;
        }
        return null;
    }

    public boolean isAnchored() {
        return entityData.get(ANCHORED);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ANCHORED, false);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        Entity owner = getOwner();
        if (!(owner instanceof Player player) || !player.isAlive() || player.level() != level()) {
            if (!level().isClientSide) discard();
            return;
        }
        boolean holding = player.getMainHandItem().getItem() instanceof StarTetherItem || player.getOffhandItem().getItem() instanceof StarTetherItem;
        if (!level().isClientSide && (!holding || distanceTo(player) > MAX_RANGE + 6 || life > 400)) {
            discard();
            return;
        }
        if (isAnchored()) {
            anchoredTicks++;
            Vec3 to = position().subtract(player.position().add(0, player.getBbHeight() * 0.5, 0));
            double dist = to.length();
            if (level().isClientSide) {
                // Movement is client-authoritative for players: pull the local player here.
                if (player.isLocalPlayer()) {
                    if (dist < 1.8) {
                        player.setDeltaMovement(player.getDeltaMovement().scale(0.5));
                    } else {
                        Vec3 pull = to.normalize().scale(Math.min(1.35, 0.45 + dist * 0.05));
                        player.setDeltaMovement(player.getDeltaMovement().scale(0.6).add(pull.scale(0.55)));
                    }
                }
            } else {
                player.fallDistance = 0;
                if (dist < 1.8 || anchoredTicks > 100) discard();
            }
            return;
        }
        // Flight
        if (!level().isClientSide) {
            if (distanceTo(player) > MAX_RANGE) {
                discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved() || isAnchored()) return;
            }
        }
        Vec3 vel = getDeltaMovement();
        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
        setDeltaMovement(vel.scale(0.99).add(0, -0.03, 0));
        updateRotation();
        if (level().isClientSide) {
            level().addParticle(ModParticles.ASTRAL_SPARK.get(), getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Vec3 at = result.getLocation();
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(ANCHORED, true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.ASTRAL_SPARK.get(), getX(), getY(), getZ(), 12, 0.1, 0.1, 0.1, 0.1);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        Entity owner = getOwner();
        if (owner != null && e instanceof LivingEntity le) {
            le.hurt(damageSources().thrown(this, owner), 2.0F);
            Vec3 yank = owner.position().subtract(le.position()).normalize().scale(1.4);
            le.setDeltaMovement(yank.x, 0.45, yank.z);
            le.hurtMarked = true;
        }
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e != getOwner() && e instanceof LivingEntity;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 96 * 96;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
