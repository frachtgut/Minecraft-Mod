package net.starfallen.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModItems;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;

import java.util.HashSet;
import java.util.Set;

/** The Eclipse Scythe in flight: spins out, reaps everything in its path, and returns. */
public class ThrownScytheEntity extends Projectile {
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(ThrownScytheEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(ThrownScytheEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int OUT_TIME = 14;
    public static final int MAX_FLIGHT = 60;
    private final Set<Integer> hit = new HashSet<>();
    private int age;

    public ThrownScytheEntity(EntityType<? extends ThrownScytheEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public ThrownScytheEntity(Level level, LivingEntity owner, ItemStack stack) {
        this(ModEntities.THROWN_SCYTHE.get(), level);
        setOwner(owner);
        entityData.set(STACK, stack);
        Vec3 look = owner.getLookAngle();
        setPos(owner.getX(), owner.getEyeY() - 0.3, owner.getZ());
        setDeltaMovement(look.scale(1.5));
    }

    public ItemStack getStack() {
        ItemStack s = entityData.get(STACK);
        return s.isEmpty() ? new ItemStack(ModItems.ECLIPSE_SCYTHE.get()) : s;
    }

    public boolean isReturning() {
        return entityData.get(RETURNING);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STACK, ItemStack.EMPTY);
        entityData.define(RETURNING, false);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        Entity owner = getOwner();
        Vec3 vel = getDeltaMovement();
        if (!level().isClientSide) {
            if (owner == null || !owner.isAlive() || age > MAX_FLIGHT + 40) {
                discard();
                return;
            }
            if (!isReturning()) {
                Vec3 to = position().add(vel);
                if (age >= OUT_TIME || level().clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
                    entityData.set(RETURNING, true);
                    hit.clear();
                }
            }
            if (isReturning()) {
                noPhysics = true;
                Vec3 target = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
                Vec3 to = target.subtract(position());
                if (to.length() < 1.6) {
                    level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), net.minecraft.sounds.SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.3F);
                    if (owner instanceof Player p) p.getCooldowns().removeCooldown(ModItems.ECLIPSE_SCYTHE.get());
                    discard();
                    return;
                }
                double speed = Math.min(1.9, 0.9 + age * 0.03);
                vel = vel.scale(0.6).add(to.normalize().scale(speed * 0.4)).normalize().scale(speed);
                setDeltaMovement(vel);
            }
            // Reap
            float damage = 11.0F + EnchantmentHelper.getDamageBonus(getStack(), net.minecraft.world.entity.MobType.UNDEFINED);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8).expandTowards(vel),
                    e -> owner != e && SFUtil.isValidTarget(owner, e) && !hit.contains(e.getId()))) {
                hit.add(e.getId());
                e.invulnerableTime = 0;
                boolean ok = owner instanceof Player p ? e.hurt(p.damageSources().playerAttack(p), damage)
                        : e.hurt(damageSources().mobProjectile(this, (LivingEntity) owner), damage);
                if (ok) {
                    if (owner instanceof LivingEntity lo) lo.heal(1.5F);
                    ((ServerLevel) level()).sendParticles(ModParticles.VOID_SPARK.get(), e.getX(), e.getY(0.5), e.getZ(), 10, 0.2, 0.3, 0.2, 0.08);
                    ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY(0.6), e.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                }
            }
            if (age % 6 == 0) {
                level().playSound(null, getX(), getY(), getZ(), ModSounds.SCYTHE_THROW.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        } else {
            level().addParticle(ModParticles.VOID_SPARK.get(), getX() + (random.nextDouble() - 0.5), getY(), getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
        }
        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
    }

    @Override
    public boolean isPickable() {
        return false;
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
