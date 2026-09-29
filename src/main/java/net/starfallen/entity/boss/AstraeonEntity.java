package net.starfallen.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.config.SFConfig;
import net.starfallen.entity.VoidStalkerEntity;
import net.starfallen.entity.projectile.MeteorEntity;
import net.starfallen.entity.projectile.ShockwaveEntity;
import net.starfallen.entity.projectile.SingularityEntity;
import net.starfallen.entity.projectile.StarBoltEntity;
import net.starfallen.network.Packets;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.*;
import net.starfallen.util.Effects;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ASTRAEON, THE DEVOURING STAR.
 * <p>
 * A star that fell, and hungered. Rises from the Star Altar in a cinematic awakening, then
 * fights in two phases: star volleys, falling-hand slams with jumpable shockwaves, meteor rain
 * and charges - and once its shell shatters, a sweeping void beam, a black hole and a
 * world-ending nova you must hide from.
 */
public class AstraeonEntity extends Monster {
    public static final int INTRO = 0, IDLE = 1, VOLLEY = 2, SLAM = 3, METEOR_RAIN = 4, CHARGE = 5, PHASE_SHIFT = 6,
            VOID_BEAM = 7, SINGULARITY = 8, NOVA = 9, DYING = 10;
    public static final int INTRO_TIME = 170, ROAR_AT = 130, PHASE_SHIFT_TIME = 100, DEATH_TIME = 150;
    public static final int BEAM_CHARGE = 35, BEAM_FIRE = 60;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(AstraeonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(AstraeonEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("entity.starfallen.astraeon.title").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true).setCreateWorldFog(true);

    /** Ticks in the current state (tracked on both sides; the client resets it when the state changes). */
    public int stateTicks;
    @Nullable private BlockPos home;
    private int attackCooldown = 40;
    private int lastAttack = -1;
    private int novaCooldown = 400;
    private int idleNoPlayers;
    private float beamYawStart, beamYawEnd;
    @Nullable private Vec3 slamTarget;
    @Nullable private Vec3 chargeDir;
    @Nullable private DamageSource deathSource;
    private boolean lootDropped;
    private boolean killedByPlayer;

    public AstraeonEntity(EntityType<? extends AstraeonEntity> type, Level level) {
        super(type, level);
        this.xpReward = 600;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 600.0D).add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D).add(Attributes.ATTACK_DAMAGE, 12.0D).add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FLYING_SPEED, 0.4D);
    }

    /** Awakens Astraeon above the given position with the full cinematic. */
    public static AstraeonEntity summon(ServerLevel level, BlockPos pos, @Nullable Player summoner) {
        AstraeonEntity boss = ModEntities.ASTRAEON.get().create(level);
        if (boss == null) throw new IllegalStateException("Astraeon failed to spawn");
        boss.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, summoner == null ? 0 : summoner.getYRot() + 180, 0);
        boss.home = pos;
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(600.0D * SFConfig.BOSS_HEALTH_MULTIPLIER.get());
        boss.setHealth(boss.getMaxHealth());
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        boss.setState(INTRO);
        level.addFreshEntity(boss);
        for (ServerPlayer p : level.getPlayers(pl -> pl.blockPosition().closerThan(pos, 80))) {
            SFNetwork.cinematic(p, Packets.Cinematic.BOSS_INTRO, boss.getId(), INTRO_TIME);
        }
        level.playSound(null, pos, ModSounds.ASTRAEON_AWAKEN.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
        return boss;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, INTRO);
        entityData.define(ENRAGED, false);
    }

    public int getState() {
        return entityData.get(STATE);
    }

    public boolean isEnraged() {
        return entityData.get(ENRAGED);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && level().isClientSide) stateTicks = 0;
    }

    /** 0..1 materialisation during the intro (used by the renderer). */
    public float introProgress(float partial) {
        if (getState() != INTRO) return 1.0F;
        float t = stateTicks + partial;
        return Mth.clamp((t - 55.0F) / 70.0F, 0.0F, 1.0F);
    }

    @Override
    protected void registerGoals() {
        // Fully scripted in customServerAiStep.
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (level().isClientSide) clientEffects();
    }

    private void clientEffects() {
        int s = getState();
        Vec3 core = position().add(0, getBbHeight() * 0.55, 0);
        if (s == INTRO && stateTicks < 125) {
            // Starlight converging on the altar
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 6 + random.nextDouble() * 8;
                double y = random.nextDouble() * 10 - 2;
                Vec3 p = core.add(Math.cos(a) * r, y, Math.sin(a) * r);
                Vec3 v = core.subtract(p).scale(0.06);
                level().addParticle(i % 2 == 0 ? ModParticles.STAR_SPARK.get() : ModParticles.VOID_SPARK.get(), p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
        if (s == VOID_BEAM && stateTicks < BEAM_CHARGE) {
            Vec3 eye = getEyePosition();
            for (int i = 0; i < 4; i++) {
                Vec3 p = eye.add((random.nextDouble() - 0.5) * 6, (random.nextDouble() - 0.5) * 6, (random.nextDouble() - 0.5) * 6);
                Vec3 v = eye.subtract(p).scale(0.1);
                level().addParticle(ModParticles.VOID_SPARK.get(), p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
        if (s == NOVA) {
            double r = 1.5 + stateTicks * 0.05;
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, b = random.nextDouble() * Math.PI - Math.PI / 2;
                level().addParticle(ModParticles.STAR_SPARK.get(), core.x + Math.cos(a) * Math.cos(b) * r, core.y + Math.sin(b) * r,
                        core.z + Math.sin(a) * Math.cos(b) * r, 0, 0, 0);
            }
        }
        if (s != INTRO && random.nextInt(2) == 0) {
            level().addParticle(isEnraged() ? ModParticles.VOID_SPARK.get() : ModParticles.STAR_SPARK.get(),
                    getRandomX(1.2), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.2), 0, -0.02, 0);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        ServerLevel level = (ServerLevel) level();
        if (home == null) home = blockPosition();
        setDeltaMovement(getDeltaMovement().scale(0.8));
        Player target = findTarget();
        if (target != null) setTarget(target);

        switch (getState()) {
            case INTRO -> tickIntro(level);
            case IDLE -> tickIdle(level, target);
            case VOLLEY -> tickVolley(level, target);
            case SLAM -> tickSlam(level, target);
            case METEOR_RAIN -> tickMeteorRain(level, target);
            case CHARGE -> tickCharge(level, target);
            case PHASE_SHIFT -> tickPhaseShift(level);
            case VOID_BEAM -> tickBeam(level, target);
            case SINGULARITY -> tickSingularity(level, target);
            case NOVA -> tickNova(level, target);
            default -> {}
        }
        move(MoverType.SELF, getDeltaMovement());

        // Never stray too far from the altar
        if (home != null && getState() != DYING && position().distanceToSqr(Vec3.atCenterOf(home)) > 36 * 36) {
            Vec3 back = Vec3.atCenterOf(home).add(0, 6, 0).subtract(position()).normalize().scale(0.25);
            setDeltaMovement(getDeltaMovement().add(back));
        }
        // Reset if everyone left
        if (target == null && getState() != INTRO && getState() != DYING) {
            if (++idleNoPlayers > 1200) {
                setHealth(getMaxHealth());
                idleNoPlayers = 0;
            }
        } else {
            idleNoPlayers = 0;
        }
    }

    @Nullable
    private Player findTarget() {
        Player best = null;
        double bestD = 48 * 48;
        for (Player p : level().players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel level, double r) {
        return level.getPlayers(p -> p.distanceToSqr(this) < r * r);
    }

    private void faceTowards(Vec3 pos, float maxTurn) {
        Vec3 d = pos.subtract(position());
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxTurn));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private void hoverTowards(Vec3 dest, double speed) {
        Vec3 to = dest.subtract(position());
        double d = to.length();
        if (d > 0.2) setDeltaMovement(getDeltaMovement().add(to.normalize().scale(Math.min(speed, d * 0.05))));
    }

    // ------------------------------------------------------------------ states

    private void tickIntro(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (stateTicks > 55 && stateTicks < 125) {
            setDeltaMovement(0, 0.06, 0);
        }
        if (stateTicks % 20 == 0) SFNetwork.shakeAround(level, position(), 48, 0.25F + stateTicks / 400F, 20);
        if (stateTicks == 60) {
            level.sendParticles(ModParticles.NOVA_FLASH.get(), getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        }
        if (stateTicks == ROAR_AT) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_ROAR.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
            SFNetwork.shakeAround(level, position(), 64, 1.8F, 40);
            SFNetwork.flashAround(level, position(), 64, 0xA0E6C9FF, 14);
            level.sendParticles(ModParticles.VOID_SHOCKWAVE.get(), getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ModParticles.SHOCKWAVE.get(), getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
            for (ServerPlayer p : nearbyPlayers(level, 20)) {
                Vec3 away = p.position().subtract(position()).multiply(1, 0, 1).normalize();
                p.setDeltaMovement(away.x * 1.2, 0.5, away.z * 1.2);
                p.hurtMarked = true;
            }
        }
        if (stateTicks >= INTRO_TIME) {
            setState(IDLE);
            attackCooldown = 30;
        }
    }

    private void tickIdle(ServerLevel level, @Nullable Player target) {
        if (target == null) {
            if (home != null) hoverTowards(Vec3.atCenterOf(home).add(0, 5, 0), 0.05);
            return;
        }
        faceTowards(target.position(), 10);
        // Hover at a menacing distance, bobbing
        Vec3 fromTarget = position().subtract(target.position()).multiply(1, 0, 1);
        if (fromTarget.lengthSqr() < 1.0E-3) fromTarget = new Vec3(1, 0, 0);
        double wanted = isEnraged() ? 8.0 : 10.0;
        Vec3 dest = target.position().add(fromTarget.normalize().scale(wanted)).add(0, 4.5 + Math.sin(tickCount * 0.05) * 1.2, 0);
        hoverTowards(dest, 0.045);
        if (--attackCooldown <= 0) chooseAttack(level, target);
    }

    private void chooseAttack(ServerLevel level, Player target) {
        int next;
        boolean enraged = isEnraged();
        if (enraged && novaCooldown <= 0 && getHealth() < getMaxHealth() * 0.3F) {
            next = NOVA;
            novaCooldown = 900;
        } else {
            int[] pool = enraged ? new int[]{VOLLEY, SLAM, METEOR_RAIN, CHARGE, VOID_BEAM, VOID_BEAM, SINGULARITY, SLAM}
                    : new int[]{VOLLEY, VOLLEY, SLAM, SLAM, METEOR_RAIN, CHARGE};
            do {
                next = pool[random.nextInt(pool.length)];
            } while (next == lastAttack && random.nextInt(4) != 0);
        }
        lastAttack = next;
        setState(next);
        if (next == SLAM) slamTarget = null;
        if (next == VOID_BEAM) {
            Vec3 d = target.position().subtract(position());
            float center = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90.0F;
            boolean cw = random.nextBoolean();
            beamYawStart = center + (cw ? -75 : 75);
            beamYawEnd = center + (cw ? 75 : -75);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_BEAM_CHARGE.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
        if (next == NOVA) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_BEAM_CHARGE.get(), SoundSource.HOSTILE, 6.0F, 0.5F);
            for (ServerPlayer p : nearbyPlayers(level, 48)) {
                p.displayClientMessage(Component.translatable("message.starfallen.nova_warning").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
    }

    private void endAttack(int cooldown) {
        setState(IDLE);
        attackCooldown = isEnraged() ? (int) (cooldown * 0.65F) : cooldown;
        if (novaCooldown > 0) novaCooldown -= cooldown;
    }

    private void tickVolley(ServerLevel level, @Nullable Player target) {
        if (target == null) {
            endAttack(20);
            return;
        }
        faceTowards(target.position(), 15);
        if (stateTicks == 15 || stateTicks == 25 || stateTicks == 35 || (isEnraged() && stateTicks == 45)) {
            int n = isEnraged() ? 5 : 3;
            for (int i = 0; i < n; i++) {
                Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
                Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize()
                        .yRot((float) ((i - (n - 1) / 2.0) * 0.35)).add(0, 0.25, 0).normalize();
                StarBoltEntity bolt = new StarBoltEntity(level, from.add(dir.scale(2.0)), dir.scale(0.6), this, 7.0F);
                bolt.setVariant(1);
                bolt.setHoming(target, 0.04);
                level.addFreshEntity(bolt);
            }
            level.playSound(null, getX(), getY(), getZ(), ModSounds.STAR_BOLT.get(), SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (stateTicks >= 55) endAttack(45);
    }

    private void tickSlam(ServerLevel level, @Nullable Player target) {
        if (stateTicks < 28) {
            if (target != null) slamTarget = target.position();
            if (slamTarget != null) {
                hoverTowards(slamTarget.add(0, 7, 0), 0.12);
                if (stateTicks % 3 == 0) {
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8;
                        level.sendParticles(ModParticles.VOID_SPARK.get(), slamTarget.x + Math.cos(a) * 4, slamTarget.y + 0.2, slamTarget.z + Math.sin(a) * 4, 1, 0, 0, 0, 0);
                    }
                }
            }
        } else if (stateTicks < 41) {
            // Plunge
            setDeltaMovement(new Vec3(0, -1.6, 0));
            if (onGround() || verticalCollisionBelow || stateTicks == 40) {
                slamImpact(level);
                stateTicks = 41;
            }
        } else if (stateTicks > 76) {
            endAttack(40);
        } else {
            // Stunned after the slam: open window to punish
            setDeltaMovement(getDeltaMovement().add(0, 0.02, 0));
        }
    }

    private void slamImpact(ServerLevel level) {
        Vec3 c = position();
        Effects.slam(level, this, c, 5.5, 15.0F, 0.9, damageSources().mobAttack(this), ModSounds.ASTRAEON_SLAM.get());
        ShockwaveEntity.spawn(level, c.add(0, 0.1, 0), this, 18.0F, 0.5F, 10.0F, true);
        if (isEnraged()) {
            net.starfallen.event.SFScheduler.schedule(level, 14, lvl -> ShockwaveEntity.spawn(lvl, c.add(0, 0.1, 0), this, 18.0F, 0.55F, 10.0F, true));
        }
        level.sendParticles(ModParticles.NOVA_FLASH.get(), c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        SFNetwork.shakeAround(level, c, 48, 1.4F, 20);
    }

    private void tickMeteorRain(ServerLevel level, @Nullable Player target) {
        if (home != null) hoverTowards(Vec3.atCenterOf(home).add(0, 10, 0), 0.08);
        if (stateTicks == 5) level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_ROAR.get(), SoundSource.HOSTILE, 4.0F, 1.3F);
        int count = isEnraged() ? 14 : 10;
        if (stateTicks >= 15 && stateTicks < 15 + count * 5 && (stateTicks - 15) % 5 == 0) {
            List<ServerPlayer> players = nearbyPlayers(level, 48);
            Vec3 t;
            if (!players.isEmpty() && random.nextInt(3) != 0) {
                ServerPlayer p = players.get(random.nextInt(players.size()));
                t = p.position().add(p.getDeltaMovement().scale(10));
            } else {
                Vec3 h = home == null ? position() : Vec3.atCenterOf(home);
                t = h.add((random.nextDouble() - 0.5) * 30, 0, (random.nextDouble() - 0.5) * 30);
            }
            t = groundBelow(level, t);
            MeteorEntity.strike(level, this, t, new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5), 1.0F, MeteorEntity.Kind.BOSS, 12.0F);
        }
        if (stateTicks > 15 + count * 5 + 30) endAttack(60);
    }

    private Vec3 groundBelow(ServerLevel level, Vec3 t) {
        BlockHitResult hit = level.clip(new ClipContext(t.add(0, 6, 0), t.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? t : hit.getLocation();
    }

    private void tickCharge(ServerLevel level, @Nullable Player target) {
        if (stateTicks < 18) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
            if (target != null) {
                faceTowards(target.position(), 20);
                chargeDir = target.position().add(0, 1, 0).subtract(position().add(0, getBbHeight() * 0.4, 0)).normalize();
            }
            if (stateTicks == 1) level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_ROAR.get(), SoundSource.HOSTILE, 3.0F, 1.6F);
        } else if (stateTicks < 34 && chargeDir != null) {
            setDeltaMovement(chargeDir.scale(1.35));
            level.sendParticles(ModParticles.VOID_SPARK.get(), getX(), getY() + 2, getZ(), 8, 1, 1, 1, 0.05);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), e -> e != this && SFUtil.isValidTarget(this, e))) {
                if (e.hurt(damageSources().mobAttack(this), 14.0F)) {
                    e.setDeltaMovement(chargeDir.x * 1.6, 0.7, chargeDir.z * 1.6);
                    e.hurtMarked = true;
                }
            }
            if (horizontalCollision) {
                SFNetwork.shakeAround(level, position(), 32, 1.0F, 12);
                level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_SLAM.get(), SoundSource.HOSTILE, 3.0F, 1.2F);
                stateTicks = 34;
            }
        } else if (stateTicks > 46) {
            endAttack(45);
        }
    }

    private void tickPhaseShift(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (home != null) hoverTowards(Vec3.atCenterOf(home).add(0, 6, 0), 0.1);
        if (stateTicks == 1) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_ROAR.get(), SoundSource.HOSTILE, 8.0F, 0.7F);
            for (ServerPlayer p : nearbyPlayers(level, 64)) SFNetwork.cinematic(p, Packets.Cinematic.BOSS_PHASE, getId(), PHASE_SHIFT_TIME);
        }
        if (stateTicks == 40) {
            entityData.set(ENRAGED, true);
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            bossEvent.setName(Component.translatable("entity.starfallen.astraeon.title_unbound").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            SFNetwork.shakeAround(level, position(), 64, 2.0F, 30);
            SFNetwork.flashAround(level, position(), 64, 0xC0FFFFFF, 16);
            level.sendParticles(ModParticles.NOVA_FLASH.get(), getX(), getY() + 2, getZ(), 3, 0.5, 0.5, 0.5, 0);
            level.sendParticles(ModParticles.VOID_SHOCKWAVE.get(), getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.SEAL_BREAK.get(), SoundSource.HOSTILE, 6.0F, 0.5F);
            // Minions: stalkers that creep up while you watch the star
            for (int i = 0; i < 2; i++) {
                VoidStalkerEntity s = ModEntities.VOID_STALKER.get().create(level);
                if (s == null) continue;
                Vec3 h = home == null ? position() : Vec3.atCenterOf(home);
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 p = groundBelow(level, h.add(Math.cos(a) * 12, 4, Math.sin(a) * 12));
                s.moveTo(p.x, p.y, p.z, random.nextFloat() * 360, 0);
                s.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.MOB_SUMMONED, null, null);
                level.addFreshEntity(s);
                level.sendParticles(ModParticles.VOID_SPARK.get(), p.x, p.y + 1.5, p.z, 30, 0.4, 1, 0.4, 0.05);
            }
        }
        if (stateTicks >= PHASE_SHIFT_TIME) endAttack(30);
    }

    private void tickBeam(ServerLevel level, @Nullable Player target) {
        setDeltaMovement(getDeltaMovement().scale(0.3));
        if (stateTicks < BEAM_CHARGE) {
            float yaw = beamYawStart;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            if (target != null) setXRot(Mth.clamp(pitchTo(target.position().add(0, 1, 0)), -10, 50));
            return;
        }
        int t = stateTicks - BEAM_CHARGE;
        if (t == 0) level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_BEAM.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
        if (t < BEAM_FIRE) {
            float yaw = Mth.rotLerp(t / (float) BEAM_FIRE, beamYawStart, beamYawEnd);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            Vec3 eye = getEyePosition();
            Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot());
            Vec3 end = beamEnd(eye, dir);
            if (t % 2 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5), e -> e != this && SFUtil.isValidTarget(this, e))) {
                    if (distanceToSegment(e.getBoundingBox().getCenter(), eye, end) < 1.3 + e.getBbWidth() * 0.5) {
                        e.invulnerableTime = 0;
                        if (e.hurt(ModDamageTypes.source(level, ModDamageTypes.ASTRAL_BEAM, this, this), 5.0F)) e.setSecondsOnFire(3);
                    }
                }
            }
            if (t % 3 == 0) {
                level.sendParticles(ModParticles.VOID_SPARK.get(), end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.12);
                level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 1, 0.2, 0.2, 0.2, 0);
            }
            if (t % 10 == 0) SFNetwork.shakeAround(level, position(), 40, 0.5F, 10);
        } else if (t > BEAM_FIRE + 15) {
            endAttack(50);
        }
    }

    /** Where the void beam currently ends (blocks stop it: hide behind the pillars!). */
    public Vec3 beamEnd(Vec3 eye, Vec3 dir) {
        Vec3 far = eye.add(dir.scale(48));
        BlockHitResult hit = level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
    }

    public boolean isBeamFiring() {
        return getState() == VOID_BEAM && stateTicks >= BEAM_CHARGE && stateTicks < BEAM_CHARGE + BEAM_FIRE;
    }

    private float pitchTo(Vec3 p) {
        Vec3 d = p.subtract(getEyePosition());
        return (float) -(Mth.atan2(d.y, d.horizontalDistance()) * (180F / Math.PI));
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private void tickSingularity(ServerLevel level, @Nullable Player target) {
        if (target != null) faceTowards(target.position(), 12);
        if (stateTicks == 10 && target != null) {
            Vec3 at = position().add(0, getBbHeight() * 0.5, 0).lerp(target.position().add(0, 1.5, 0), 0.55);
            SingularityEntity s = new SingularityEntity(level, this, at, Vec3.ZERO, true);
            level.addFreshEntity(s);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.SINGULARITY_HUM.get(), SoundSource.HOSTILE, 4.0F, 0.5F);
        }
        if (target != null && stateTicks > 30 && stateTicks % 14 == 0) {
            Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
            Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize();
            StarBoltEntity bolt = new StarBoltEntity(level, from.add(dir.scale(2)), dir.scale(0.7), this, 6.0F);
            bolt.setVariant(1);
            level.addFreshEntity(bolt);
        }
        if (stateTicks > 110) endAttack(50);
    }

    private void tickNova(ServerLevel level, @Nullable Player target) {
        if (home != null) hoverTowards(Vec3.atCenterOf(home).add(0, 7, 0), 0.1);
        if (stateTicks % 10 == 0) SFNetwork.shakeAround(level, position(), 48, 0.3F + stateTicks / 120F, 12);
        if (stateTicks == 80) {
            Vec3 core = position().add(0, getBbHeight() * 0.55, 0);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_DEATH.get(), SoundSource.HOSTILE, 8.0F, 1.4F);
            SFNetwork.flashAround(level, core, 64, 0xE0FFFFFF, 20);
            SFNetwork.shakeAround(level, core, 64, 2.2F, 30);
            level.sendParticles(ModParticles.NOVA_FLASH.get(), core.x, core.y, core.z, 4, 1, 1, 1, 0);
            level.sendParticles(ModParticles.STAR_SPARK.get(), core.x, core.y, core.z, 200, 1, 1, 1, 1.2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(core, core).inflate(22), e -> e != this && SFUtil.isValidTarget(this, e))) {
                Vec3 ec = e.getBoundingBox().getCenter();
                if (ec.distanceTo(core) > 22) continue;
                // Pillars shelter you from the nova.
                BlockHitResult cover = level.clip(new ClipContext(core, ec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (cover.getType() != HitResult.Type.MISS) continue;
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.source(level, ModDamageTypes.ASTRAL_BEAM, this, this), 26.0F);
                e.addEffect(new MobEffectInstance(ModEffects.STARSTRUCK.get(), 100, 0), this);
            }
        }
        if (stateTicks > 110) endAttack(60);
    }

    // ------------------------------------------------------------------ damage & death

    @Override
    public boolean hurt(DamageSource source, float amount) {
        int s = getState();
        if (s == INTRO || s == PHASE_SHIFT || s == DYING) {
            if (source.getEntity() instanceof Player p && !level().isClientSide) {
                p.displayClientMessage(Component.translatable("message.starfallen.boss_immune").withStyle(ChatFormatting.GRAY), true);
            }
            return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
        }
        if (source.getEntity() == this || source.getDirectEntity() instanceof MeteorEntity) return false;
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypeTags.IS_FIRE)) return false;
        // Stunned after a slam: takes extra damage
        if (s == SLAM && stateTicks > 40) amount *= 1.3F;
        // No single blow can deal more than 40 - except commands like /kill.
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) amount = Math.min(amount, 40.0F);
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && !isEnraged() && s != PHASE_SHIFT && getHealth() <= getMaxHealth() * 0.5F && getHealth() > 0) {
            setState(PHASE_SHIFT);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (net.minecraftforge.common.ForgeHooks.onLivingDeath(this, source)) return;
        if (isRemoved() || dead) return;
        dead = true;
        deathSource = source;
        killedByPlayer = lastHurtByPlayerTime > 0 || source.getEntity() instanceof Player;
        setState(DYING);
        setHealth(0.0F);
        bossEvent.setProgress(0.0F);
        if (level() instanceof ServerLevel server) {
            for (ServerPlayer p : nearbyPlayers(server, 80)) SFNetwork.cinematic(p, Packets.Cinematic.BOSS_DEATH, getId(), DEATH_TIME);
            server.playSound(null, getX(), getY(), getZ(), ModSounds.ASTRAEON_DEATH.get(), SoundSource.HOSTILE, 8.0F, 0.8F);
        }
        if (source.getEntity() instanceof LivingEntity killer) {
            killer.awardKillScore(this, deathScore, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        setDeltaMovement(0, 0.012, 0);
        move(MoverType.SELF, getDeltaMovement());
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 c = position().add(0, getBbHeight() * 0.55, 0);
        if (deathTime % 12 == 0) {
            server.sendParticles(ParticleTypes.EXPLOSION, c.x + (random.nextDouble() - 0.5) * 3, c.y + (random.nextDouble() - 0.5) * 3,
                    c.z + (random.nextDouble() - 0.5) * 3, 1, 0, 0, 0, 0);
            server.playSound(null, c.x, c.y, c.z, ModSounds.METEOR_IMPACT.get(), SoundSource.HOSTILE, 2.0F, 1.5F);
            SFNetwork.shakeAround(server, c, 48, 0.4F + deathTime / 200F, 12);
        }
        server.sendParticles(ModParticles.STAR_SPARK.get(), c.x, c.y, c.z, 6, 1.2, 1.2, 1.2, 0.3);
        if (deathTime >= DEATH_TIME && !isRemoved()) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1, 1, 1, 0);
            server.sendParticles(ModParticles.NOVA_FLASH.get(), c.x, c.y, c.z, 5, 1, 1, 1, 0);
            server.sendParticles(ModParticles.STAR_SPARK.get(), c.x, c.y, c.z, 300, 2, 2, 2, 1.5);
            server.playSound(null, c.x, c.y, c.z, ModSounds.METEOR_IMPACT.get(), SoundSource.HOSTILE, 8.0F, 0.5F);
            SFNetwork.flashAround(server, c, 80, 0xF0FFFFFF, 24);
            SFNetwork.shakeAround(server, c, 80, 2.5F, 40);
            if (!lootDropped) {
                lootDropped = true;
                DamageSource src = deathSource == null ? damageSources().generic() : deathSource;
                if (killedByPlayer) lastHurtByPlayerTime = 100;
                super.dropAllDeathLoot(src);
            }
            for (ServerPlayer p : nearbyPlayers(server, 96)) {
                SFUtil.award(p, "starfallen");
                p.displayClientMessage(Component.translatable("message.starfallen.boss_defeated").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
            bossEvent.removeAllPlayers();
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        // Deferred until the death sequence ends (see tickDeath).
    }

    @Override
    public boolean isAlwaysExperienceDropper() {
        return true;
    }

    // ------------------------------------------------------------------ misc

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return dims.height * 0.72F;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 256 * 256;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return getState() == INTRO ? null : ModSounds.ASTRAEON_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ASTRAEON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putBoolean("Enraged", isEnraged());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        if (isEnraged()) {
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            bossEvent.setName(Component.translatable("entity.starfallen.astraeon.title_unbound").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        setState(IDLE);
    }
}
