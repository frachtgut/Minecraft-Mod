package net.starfallen.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starfallen.block.FlameJetBlock;
import net.starfallen.registry.ModBlockEntities;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;

/**
 * Flame jets fire on a fixed rhythm derived from the world time, so client and server agree
 * without any syncing, and neighbouring jets ripple in sequence.
 */
public class FlameJetBlockEntity extends BlockEntity {
    public static final int CYCLE = 90;
    public static final int ACTIVE = 28;
    public static final int WARN = 16;
    public static final int RANGE = 4;
    private int offset = -1;

    public FlameJetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLAME_JET.get(), pos, state);
    }

    private int phase(Level level) {
        if (offset < 0) {
            // Ripple pattern: jets along a corridor fire one after another.
            offset = Math.floorMod((worldPosition.getX() + worldPosition.getZ()) * 11, CYCLE);
        }
        return (int) Math.floorMod(level.getGameTime() + offset, (long) CYCLE);
    }

    public void setOffset(int offset) {
        this.offset = Mth.clamp(offset, 0, CYCLE - 1);
        setChanged();
    }

    private int reach(Level level, BlockPos pos, Direction dir) {
        int len = 0;
        for (int i = 1; i <= RANGE; i++) {
            BlockPos p = pos.relative(dir, i);
            if (level.getBlockState(p).isSolidRender(level, p)) break;
            len = i;
        }
        return len;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlameJetBlockEntity be) {
        int phase = be.phase(level);
        Direction dir = state.getValue(FlameJetBlock.FACING);
        if (phase == 0) {
            level.playSound(null, pos, ModSounds.FLAME_JET.get(), SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        }
        if (phase < ACTIVE && level.getGameTime() % 4 == 0) {
            int len = be.reach(level, pos, dir);
            if (len <= 0) return;
            Vec3 start = Vec3.atCenterOf(pos.relative(dir));
            Vec3 end = Vec3.atCenterOf(pos.relative(dir, len));
            AABB box = new AABB(start, end).inflate(0.55);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                if (e.hurt(ModDamageTypes.source(level, ModDamageTypes.STARFIRE), 4.0F)) {
                    e.setSecondsOnFire(4);
                }
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FlameJetBlockEntity be) {
        int phase = be.phase(level);
        Direction dir = state.getValue(FlameJetBlock.FACING);
        RandomSource r = level.random;
        double ox = pos.getX() + 0.5 + dir.getStepX() * 0.55;
        double oy = pos.getY() + 0.5 + dir.getStepY() * 0.55;
        double oz = pos.getZ() + 0.5 + dir.getStepZ() * 0.55;
        if (phase >= CYCLE - WARN) {
            // Warning sputter just before the blast.
            if (r.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SMOKE, ox, oy, oz, dir.getStepX() * 0.05, dir.getStepY() * 0.05, dir.getStepZ() * 0.05);
                level.addParticle(ModParticles.EMBER.get(), ox, oy, oz, dir.getStepX() * 0.08 + (r.nextDouble() - 0.5) * 0.05,
                        dir.getStepY() * 0.08 + (r.nextDouble() - 0.5) * 0.05, dir.getStepZ() * 0.08 + (r.nextDouble() - 0.5) * 0.05);
            }
        } else if (phase < ACTIVE) {
            int len = be.reach(level, pos, dir);
            double speed = 0.16 + len * 0.06;
            for (int i = 0; i < 5; i++) {
                double sx = (r.nextDouble() - 0.5) * 0.12;
                double sy = (r.nextDouble() - 0.5) * 0.12;
                double sz = (r.nextDouble() - 0.5) * 0.12;
                level.addParticle(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, ox, oy, oz,
                        dir.getStepX() * speed + sx, dir.getStepY() * speed + sy, dir.getStepZ() * speed + sz);
            }
            if (r.nextInt(3) == 0) {
                level.addParticle(ModParticles.ASTRAL_SPARK.get(), ox, oy, oz, dir.getStepX() * speed * 1.4, dir.getStepY() * speed * 1.4, dir.getStepZ() * speed * 1.4);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Offset")) offset = tag.getInt("Offset");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (offset >= 0) tag.putInt("Offset", offset);
    }
}
