package net.starfallen.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An expanding ring of force travelling along the ground. Anything standing in its path is
 * struck and hurled; jump over it to avoid it.
 */
public class ShockwaveEntity extends Entity {
    private static final EntityDataAccessor<Float> MAX_RADIUS = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> VOID = SynchedEntityData.defineId(ShockwaveEntity.class, EntityDataSerializers.BOOLEAN);
    private final Set<Integer> hit = new HashSet<>();
    @Nullable private UUID ownerId;
    private float damage = 8.0F;

    public ShockwaveEntity(EntityType<? extends ShockwaveEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static ShockwaveEntity spawn(ServerLevel level, Vec3 pos, @Nullable Entity owner, float maxRadius, float speed, float damage, boolean voidRing) {
        ShockwaveEntity s = new ShockwaveEntity(ModEntities.SHOCKWAVE.get(), level);
        s.setPos(pos.x, pos.y, pos.z);
        s.entityData.set(MAX_RADIUS, maxRadius);
        s.entityData.set(SPEED, speed);
        s.entityData.set(VOID, voidRing);
        s.ownerId = owner == null ? null : owner.getUUID();
        s.damage = damage;
        level.addFreshEntity(s);
        return s;
    }

    public float getRadius(float partial) {
        return Math.min(getMaxRadius(), (tickCount + partial) * entityData.get(SPEED));
    }

    public float getMaxRadius() {
        return entityData.get(MAX_RADIUS);
    }

    public boolean isVoid() {
        return entityData.get(VOID);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MAX_RADIUS, 12.0F);
        entityData.define(SPEED, 0.5F);
        entityData.define(VOID, false);
    }

    @Override
    public void tick() {
        super.tick();
        float r = getRadius(0);
        if (level().isClientSide) {
            int n = (int) (r * 3) + 4;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(isVoid() ? ModParticles.VOID_SPARK.get() : ModParticles.EMBER.get(),
                        getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, 0, 0.06, 0);
            }
            return;
        }
        if (r >= getMaxRadius()) {
            discard();
            return;
        }
        Entity owner = ownerId == null ? null : ((ServerLevel) level()).getEntity(ownerId);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(r + 1, 2, r + 1),
                e -> e != owner && e.isAlive() && !hit.contains(e.getId()))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (owner instanceof net.minecraft.world.entity.monster.Enemy && e instanceof net.minecraft.world.entity.monster.Enemy) continue;
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - r) > 0.9) continue;
            // Jumping over the wave avoids it.
            if (e.getY() > getY() + 0.9 || e.getY() < getY() - 2.0) continue;
            hit.add(e.getId());
            if (e.hurt(ModDamageTypes.source(level(), ModDamageTypes.SHOCKWAVE, this, owner), damage)) {
                Vec3 away = new Vec3(dx, 0, dz).normalize();
                e.setDeltaMovement(away.x * 0.8, 0.7, away.z * 0.8);
                e.hurtMarked = true;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
