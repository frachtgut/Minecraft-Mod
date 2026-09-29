package net.starfallen.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.starfallen.config.SFConfig;
import net.starfallen.entity.MeteorGolemEntity;
import net.starfallen.entity.StarWispEntity;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import net.starfallen.world.CraterBuilder;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A falling meteor. Streaks across the sky trailing fire and smoke, then slams into the
 * ground: shockwave, camera shake, and - for Starfall meteors - a crater full of treasure.
 */
public class MeteorEntity extends Entity {
    public enum Kind { EVENT, STAFF, BOSS, SEER }

    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOOT = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> TARGET = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);

    @Nullable private UUID ownerId;
    private float damage = 12.0F;
    private int life;
    private boolean soundPlayed;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Calls a meteor down onto {@code target}, approaching from {@code approach} (horizontal).
     */
    public static MeteorEntity strike(ServerLevel level, @Nullable Entity owner, Vec3 target, Vec3 approach, float size, Kind kind, float damage) {
        MeteorEntity m = new MeteorEntity(ModEntities.METEOR.get(), level);
        double height = kind == Kind.EVENT ? 130 : 46;
        double back = kind == Kind.EVENT ? 70 : 24;
        Vec3 dir = approach.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : approach.normalize();
        Vec3 start = target.subtract(dir.scale(back)).add(0, height, 0);
        double maxY = level.getMaxBuildHeight() + 60;
        if (start.y > maxY) start = new Vec3(start.x, maxY, start.z);
        double speed = kind == Kind.EVENT ? 2.1 : 1.45;
        Vec3 vel = target.subtract(start).normalize().scale(speed);
        m.setPos(start.x, start.y, start.z);
        m.setDeltaMovement(vel);
        m.setSize(size);
        m.entityData.set(KIND, kind.ordinal());
        m.entityData.set(TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        m.ownerId = owner == null ? null : owner.getUUID();
        m.damage = damage;
        level.addFreshEntity(m);
        return m;
    }

    public MeteorEntity withLoot(CraterBuilder.Loot loot) {
        entityData.set(LOOT, loot.ordinal());
        return this;
    }

    public void setSize(float size) {
        entityData.set(SIZE, Mth.clamp(size, 0.3F, 4.0F));
        refreshDimensions();
    }

    public float getSize() {
        return entityData.get(SIZE);
    }

    public Kind getKind() {
        return Kind.values()[Mth.clamp(entityData.get(KIND), 0, Kind.values().length - 1)];
    }

    public CraterBuilder.Loot getLoot() {
        return CraterBuilder.Loot.values()[Mth.clamp(entityData.get(LOOT), 0, CraterBuilder.Loot.values().length - 1)];
    }

    public Vec3 getTarget() {
        Vector3f v = entityData.get(TARGET);
        return new Vec3(v.x, v.y, v.z);
    }

    /** Meteors with a visible warning circle on the ground (called strikes). */
    public boolean showsWarning() {
        return getKind() != Kind.EVENT;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SIZE, 1.0F);
        entityData.define(KIND, 0);
        entityData.define(LOOT, 0);
        entityData.define(TARGET, new Vector3f());
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(getSize());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (SIZE.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        Vec3 vel = getDeltaMovement();
        Vec3 from = position();
        Vec3 to = from.add(vel);
        if (level().isClientSide) {
            if (!soundPlayed) {
                soundPlayed = true;
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientHooks.meteorSound(this));
            }
            clientTrail(vel);
            setPos(to.x, to.y, to.z);
            return;
        }
        if (life > 600 || getY() < level().getMinBuildHeight() - 10) {
            discard();
            return;
        }
        // Collision with terrain
        BlockHitResult hit = level().clip(new ClipContext(from, to.add(vel.normalize().scale(getSize() * 0.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(hit.getLocation(), hit.getBlockPos());
            return;
        }
        // Collision with creatures
        AABB swept = getBoundingBox().expandTowards(vel).inflate(0.3);
        Entity owner = getOwnerEntity();
        for (Entity e : level().getEntities(this, swept, e -> e instanceof LivingEntity && e.isAlive() && e != owner && !e.isSpectator())) {
            impact(e.position(), e.blockPosition().below());
            return;
        }
        setPos(to.x, to.y, to.z);
    }

    private void clientTrail(Vec3 vel) {
        float size = getSize();
        int n = (int) (3 + size * 3);
        for (int i = 0; i < n; i++) {
            double f = random.nextDouble();
            double x = getX() - vel.x * f + (random.nextDouble() - 0.5) * size * 0.8;
            double y = getY() + getBbHeight() * 0.5 - vel.y * f + (random.nextDouble() - 0.5) * size * 0.8;
            double z = getZ() - vel.z * f + (random.nextDouble() - 0.5) * size * 0.8;
            level().addParticle(ModParticles.METEOR_SMOKE.get(), x, y, z, vel.x * 0.05, vel.y * 0.05 + 0.02, vel.z * 0.05);
            if (i % 2 == 0) {
                level().addParticle(ModParticles.EMBER.get(), x, y, z, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15);
            }
        }
        if (getLoot() == CraterBuilder.Loot.GOLDEN || getLoot() == CraterBuilder.Loot.EGG) {
            level().addParticle(ModParticles.STAR_SPARK.get(), getX(), getY() + getBbHeight() * 0.5, getZ(), 0, 0, 0);
        }
        if (showsWarning() && life % 2 == 0) {
            Vec3 t = getTarget();
            double r = 1.2 + size * 2.0;
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(ModParticles.EMBER.get(), t.x + Math.cos(a) * r, t.y + 0.15, t.z + Math.sin(a) * r, 0, 0.02, 0);
            }
            level().addParticle(ModParticles.RUNE.get(), t.x, t.y + 0.2, t.z, 0, 0.05, 0);
        }
    }

    @Nullable
    private Entity getOwnerEntity() {
        if (ownerId == null || !(level() instanceof ServerLevel server)) return null;
        return server.getEntity(ownerId);
    }

    private void impact(Vec3 pos, BlockPos blockPos) {
        ServerLevel level = (ServerLevel) level();
        float size = getSize();
        Kind kind = getKind();
        Entity owner = getOwnerEntity();
        double radius = 1.6 + size * 2.2;

        // Damage
        DamageSource src = ModDamageTypes.source(level, ModDamageTypes.METEOR, this, owner);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(radius + 1),
                e -> e.isAlive() && e != owner && (owner == null || SFUtil.isValidTarget(owner, e) || kind == Kind.EVENT))) {
            double d = e.position().distanceTo(pos);
            if (d > radius + 0.5) continue;
            float f = (float) (1.0 - 0.6 * (d / radius));
            if (e.hurt(src, damage * f)) {
                Vec3 away = e.position().subtract(pos).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.9 * f, 0.5 + 0.4 * f, away.z * 0.9 * f));
                e.hurtMarked = true;
                e.setSecondsOnFire(3);
            }
        }

        // Spectacle
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.METEOR_IMPACT.get(), SoundSource.HOSTILE, 3.0F + size * 2.0F, 0.85F + random.nextFloat() * 0.3F);
        for (ServerPlayer p : level.players()) {
            double dist = p.position().distanceTo(pos);
            if (dist < 256) {
                level.sendParticles(p, ModParticles.NOVA_FLASH.get(), true, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
                level.sendParticles(p, ModParticles.SHOCKWAVE.get(), true, pos.x, pos.y + 0.2, pos.z, 1, 0, 0, 0, 0);
                level.sendParticles(p, ModParticles.METEOR_SMOKE.get(), true, pos.x, pos.y + 1, pos.z, (int) (20 * size), size, size * 0.6, size, 0.08);
                level.sendParticles(p, ModParticles.EMBER.get(), true, pos.x, pos.y + 1, pos.z, (int) (40 * size), size * 0.6, size * 0.4, size * 0.6, 0.35);
            }
            if (dist < 32) {
                level.sendParticles(p, size > 1.4F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, false, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
                level.sendParticles(p, ParticleTypes.LAVA, false, pos.x, pos.y + 0.5, pos.z, (int) (10 * size), size * 0.5, 0.2, size * 0.5, 0);
            }
        }
        SFNetwork.shakeAround(level, pos, 24 + size * 20, Math.min(1.6F, 0.4F + size * 0.35F), 18 + (int) (size * 6));
        if (size >= 1.5F) SFNetwork.flashAround(level, pos, 20 + size * 8, 0x60FFD9A0, 8);

        // Terrain
        boolean crater = switch (kind) {
            case EVENT -> SFConfig.METEOR_CRATERS.get();
            case STAFF -> SFConfig.STAFF_CRATERS.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            default -> false;
        };
        if (crater && !nearContainers(level, blockPos, (int) radius + 4)) {
            int craterRadius = kind == Kind.EVENT ? Math.max(2, Math.round(1.5F + size * 2.2F)) : Math.max(2, Math.round(size * 1.6F));
            CraterBuilder.Loot loot = kind == Kind.EVENT ? getLoot() : CraterBuilder.Loot.NONE;
            BlockPos floor = CraterBuilder.carve(level, blockPos, craterRadius, random, true, loot, 3);
            if (kind == Kind.EVENT) afterEventImpact(level, floor, loot);
        }
        // Smoke column that lingers
        if (kind == Kind.EVENT) {
            final Vec3 smokeAt = pos;
            for (int i = 1; i <= 8; i++) {
                net.starfallen.event.SFScheduler.schedule(level, i * 10, lvl ->
                        lvl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, smokeAt.x, smokeAt.y + 0.5, smokeAt.z, 4, 0.8, 0.2, 0.8, 0.01));
            }
        }
        discard();
    }

    private static boolean nearContainers(ServerLevel level, BlockPos pos, int r) {
        int cx0 = (pos.getX() - r) >> 4, cx1 = (pos.getX() + r) >> 4;
        int cz0 = (pos.getZ() - r) >> 4, cz1 = (pos.getZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockPos be : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                    if (be.distManhattan(pos) <= r * 2 && Math.abs(be.getY() - pos.getY()) < r + 4) return true;
                }
            }
        }
        return false;
    }

    private void afterEventImpact(ServerLevel level, BlockPos floor, CraterBuilder.Loot loot) {
        if (loot == CraterBuilder.Loot.LARGE || loot == CraterBuilder.Loot.EGG) {
            int wisps = 1 + random.nextInt(3);
            for (int i = 0; i < wisps; i++) {
                StarWispEntity wisp = ModEntities.STAR_WISP.get().create(level);
                if (wisp != null) {
                    wisp.moveTo(floor.getX() + 0.5 + random.nextGaussian(), floor.getY() + 2.5, floor.getZ() + 0.5 + random.nextGaussian(), random.nextFloat() * 360, 0);
                    wisp.finalizeSpawn(level, level.getCurrentDifficultyAt(floor), MobSpawnType.EVENT, null, null);
                    level.addFreshEntity(wisp);
                }
            }
            if (random.nextFloat() < 0.35F) {
                MeteorGolemEntity golem = ModEntities.METEOR_GOLEM.get().create(level);
                if (golem != null) {
                    golem.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, random.nextFloat() * 360, 0);
                    golem.finalizeSpawn(level, level.getCurrentDifficultyAt(floor), MobSpawnType.EVENT, null, null);
                    golem.emerge();
                    level.addFreshEntity(golem);
                }
            }
        }
        for (ServerPlayer p : level.getPlayers(pl -> pl.blockPosition().closerThan(floor, 96))) {
            SFUtil.award(p, "look_up");
            if (loot == CraterBuilder.Loot.GOLDEN) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.starfallen.golden_meteor")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 512 * 512;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Meteors are transient; they are not saved.
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
