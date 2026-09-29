package net.starfallen.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.starfallen.entity.projectile.MeteorEntity;
import net.starfallen.entity.projectile.StarBoltEntity;
import net.starfallen.network.Packets;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Starseer: a robed cultist of the Eclipse who reads death in the stars. Keeps its distance,
 * fires homing star bolts, marks the ground for falling meteors and blinks away when cornered.
 * The High Starseer, master of the Observatory, fights as a mini-boss.
 */
public class StarseerEntity extends Monster {
    public static final int SPELL_NONE = 0, SPELL_VOLLEY = 1, SPELL_METEOR = 2, SPELL_WARD = 3;
    private static final EntityDataAccessor<Integer> SPELL = SynchedEntityData.defineId(StarseerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ELITE = SynchedEntityData.defineId(StarseerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WARDED = SynchedEntityData.defineId(StarseerEntity.class, EntityDataSerializers.BOOLEAN);

    private int castTicks;
    private int volleyCooldown = 40;
    private int meteorCooldown = 90;
    private int blinkCooldown;
    private int wardTicks;
    private boolean wardUsed;
    private boolean introduced;
    @Nullable private ServerBossEvent bossEvent;

    public StarseerEntity(EntityType<? extends StarseerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 32.0D).add(Attributes.ARMOR, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D).add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CastGoal(this));
        goalSelector.addGoal(2, new KeepDistanceGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, StarseerEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SPELL, SPELL_NONE);
        entityData.define(ELITE, false);
        entityData.define(WARDED, false);
    }

    public int getSpell() {
        return entityData.get(SPELL);
    }

    public boolean isElite() {
        return entityData.get(ELITE);
    }

    public boolean isWarded() {
        return entityData.get(WARDED);
    }

    /** Turns this Starseer into the High Starseer mini-boss. */
    public void makeElite() {
        entityData.set(ELITE, true);
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(110.0D);
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(6.0D);
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.starfallen.high_starseer").withStyle(ChatFormatting.GOLD));
        xpReward = 80;
        setPersistenceRequired();
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isElite() ? super.getDimensions(pose).scale(1.25F) : super.getDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (ELITE.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int spell = getSpell();
            if (spell != SPELL_NONE) {
                var p = spell == SPELL_METEOR ? ModParticles.EMBER.get() : spell == SPELL_WARD ? ModParticles.ASTRAL_SPARK.get() : ModParticles.STAR_SPARK.get();
                float yaw = yBodyRot * ((float) Math.PI / 180F);
                for (int side = -1; side <= 1; side += 2) {
                    double hx = getX() - Math.cos(yaw) * 0.6 * side;
                    double hz = getZ() - Math.sin(yaw) * 0.6 * side;
                    level().addParticle(p, hx, getY() + getBbHeight() * 1.05, hz, 0, 0.03, 0);
                }
                if (tickCount % 3 == 0) {
                    double a = tickCount * 0.3;
                    level().addParticle(ModParticles.RUNE.get(), getX() + Math.cos(a) * 1.2, getY() + 0.1, getZ() + Math.sin(a) * 1.2, 0, 0.02, 0);
                }
            }
            if (isWarded()) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(ModParticles.ASTRAL_SPARK.get(), getX() + Math.cos(a) * 0.9, getY() + random.nextDouble() * getBbHeight(),
                        getZ() + Math.sin(a) * 0.9, 0, 0, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (volleyCooldown > 0) volleyCooldown--;
        if (meteorCooldown > 0) meteorCooldown--;
        if (blinkCooldown > 0) blinkCooldown--;
        if (wardTicks > 0 && --wardTicks == 0) entityData.set(WARDED, false);
        if (bossEvent != null) bossEvent.setProgress(getHealth() / getMaxHealth());

        LivingEntity target = getTarget();
        if (isElite() && target instanceof ServerPlayer && !introduced) {
            introduced = true;
            for (ServerPlayer p : ((ServerLevel) level()).getPlayers(pl -> pl.distanceToSqr(this) < 40 * 40)) {
                SFNetwork.cinematic(p, Packets.Cinematic.STARSEER_INTRO, getId(), 70);
            }
            playSound(ModSounds.STARSEER_CAST.get(), 2.0F, 0.6F);
        }
        if (target != null && blinkCooldown <= 0 && distanceToSqr(target) < 3.5 * 3.5) {
            blinkAway(target);
        }
        if (isElite() && !wardUsed && getHealth() < getMaxHealth() * 0.5F) {
            wardUsed = true;
            entityData.set(WARDED, true);
            wardTicks = 120;
            playSound(ModSounds.STARSEER_CAST.get(), 2.0F, 1.4F);
            ((ServerLevel) level()).sendParticles(ModParticles.NOVA_FLASH.get(), getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            // Starfall spell: a ring of meteors around the challenger
            if (target != null) {
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    Vec3 t = target.position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
                    MeteorEntity.strike((ServerLevel) level(), this, t, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.8F, MeteorEntity.Kind.SEER, 9.0F);
                }
            }
        }
    }

    private void blinkAway(LivingEntity threat) {
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 7 + random.nextDouble() * 5;
            double x = getX() + Math.cos(a) * d;
            double z = getZ() + Math.sin(a) * d;
            Vec3 old = position();
            if (randomTeleport(x, getY() + random.nextInt(5) - 2, z, false) && distanceToSqr(threat) > 36) {
                blinkCooldown = isElite() ? 60 : 100;
                ServerLevel level = (ServerLevel) level();
                level.sendParticles(ModParticles.VOID_SPARK.get(), old.x, old.y + 1, old.z, 25, 0.3, 0.6, 0.3, 0.08);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 25, 0.3, 0.6, 0.3, 0.1);
                level.playSound(null, old.x, old.y, old.z, ModSounds.RIFT_BLINK.get(), SoundSource.HOSTILE, 1.0F, 1.2F);
                return;
            }
        }
        blinkCooldown = 20;
    }

    void performSpell(int spell, LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        switch (spell) {
            case SPELL_VOLLEY -> {
                int count = isElite() ? 5 : 3;
                for (int i = 0; i < count; i++) {
                    Vec3 from = position().add(0, getBbHeight() * 0.95, 0);
                    Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize()
                            .yRot((float) ((i - (count - 1) / 2.0) * 0.25)).add(0, 0.15, 0).normalize();
                    StarBoltEntity bolt = new StarBoltEntity(level, from.add(dir.scale(0.6)), dir.scale(0.55), this, isElite() ? 6.0F : 4.5F);
                    bolt.setVariant(1);
                    bolt.setHoming(target, 0.045);
                    level.addFreshEntity(bolt);
                }
                playSound(ModSounds.STAR_BOLT.get(), 1.2F, 0.9F);
                volleyCooldown = isElite() ? 45 : 70;
            }
            case SPELL_METEOR -> {
                int count = isElite() ? 3 : 1;
                for (int i = 0; i < count; i++) {
                    Vec3 lead = target.getDeltaMovement().multiply(12, 0, 12);
                    Vec3 t = target.position().add(lead).add(i == 0 ? 0 : (random.nextDouble() - 0.5) * 6, 0, i == 0 ? 0 : (random.nextDouble() - 0.5) * 6);
                    Vec3 approach = t.subtract(position()).multiply(1, 0, 1);
                    MeteorEntity.strike(level, this, t, approach, 0.8F, MeteorEntity.Kind.SEER, isElite() ? 12.0F : 9.0F);
                }
                meteorCooldown = isElite() ? 90 : 140;
            }
            default -> {}
        }
        playSound(ModSounds.STARSEER_CAST.get(), 1.0F, 1.0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isWarded() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) amount *= 0.3F;
        return super.hurt(source, amount);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (isElite()) {
            if (bossEvent == null) {
                bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
            }
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (bossEvent != null) bossEvent.removeAllPlayers();
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !isElite() && super.removeWhenFarAway(dist);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.STARSEER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STARSEER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.STARSEER_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Elite", isElite());
        tag.putBoolean("WardUsed", wardUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Elite")) entityData.set(ELITE, true);
        wardUsed = tag.getBoolean("WardUsed");
        if (hasCustomName() && bossEvent != null) bossEvent.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (bossEvent != null) bossEvent.setName(getDisplayName());
    }

    // ------------------------------------------------------------------ goals

    /** Casts spells with a visible wind-up. */
    static class CastGoal extends Goal {
        private final StarseerEntity seer;
        private int spell;

        CastGoal(StarseerEntity seer) {
            this.seer = seer;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = seer.getTarget();
            if (t == null || !t.isAlive() || !seer.hasLineOfSight(t)) return false;
            if (seer.meteorCooldown <= 0 && seer.distanceToSqr(t) > 16) {
                spell = SPELL_METEOR;
                return true;
            }
            if (seer.volleyCooldown <= 0) {
                spell = SPELL_VOLLEY;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            seer.castTicks = spell == SPELL_METEOR ? 26 : 16;
            seer.entityData.set(SPELL, spell);
            seer.getNavigation().stop();
            seer.playSound(ModSounds.STARSEER_AMBIENT.get(), 1.0F, 1.3F);
        }

        @Override
        public boolean canContinueToUse() {
            return seer.castTicks > 0 && seer.getTarget() != null && seer.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = seer.getTarget();
            if (t != null) seer.getLookControl().setLookAt(t, 30, 30);
            if (--seer.castTicks <= 0 && t != null) {
                seer.performSpell(spell, t);
            }
        }

        @Override
        public void stop() {
            seer.entityData.set(SPELL, SPELL_NONE);
            seer.castTicks = 0;
            if (spell == SPELL_METEOR && seer.meteorCooldown <= 0) seer.meteorCooldown = 40;
            if (spell == SPELL_VOLLEY && seer.volleyCooldown <= 0) seer.volleyCooldown = 30;
        }
    }

    /** Stays at casting range: backs off when approached, closes in when too far. */
    static class KeepDistanceGoal extends Goal {
        private final StarseerEntity seer;
        private int repath;

        KeepDistanceGoal(StarseerEntity seer) {
            this.seer = seer;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return seer.getTarget() != null && seer.getTarget().isAlive();
        }

        @Override
        public void tick() {
            LivingEntity t = seer.getTarget();
            if (t == null || --repath > 0) return;
            repath = 10;
            double d = seer.distanceTo(t);
            if (d < 6) {
                Vec3 away = seer.position().subtract(t.position()).normalize().scale(6);
                seer.getNavigation().moveTo(seer.getX() + away.x, seer.getY(), seer.getZ() + away.z, 1.15D);
            } else if (d > 14 || !seer.hasLineOfSight(t)) {
                seer.getNavigation().moveTo(t, 1.0D);
            } else {
                // Strafe sideways
                Vec3 side = t.position().subtract(seer.position()).normalize().yRot((float) Math.PI / 2 * (seer.random.nextBoolean() ? 1 : -1)).scale(3);
                seer.getNavigation().moveTo(seer.getX() + side.x, seer.getY(), seer.getZ() + side.z, 0.9D);
            }
        }
    }

    public static boolean checkSpawnRules(EntityType<StarseerEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, net.minecraft.util.RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }
}
