package net.starfallen.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.entity.projectile.StarBoltEntity;
import net.starfallen.registry.ModBlockEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

/**
 * Turret logic. Runs identically on both sides so the client can animate the eye (tracking
 * and charge glow) without extra packets; only the server fires.
 */
public class SentinelEyeBlockEntity extends BlockEntity {
    public static final double RANGE = 14.0;
    public static final int CHARGE_TIME = 34;
    public int charge;
    public float yaw, prevYaw, pitch, prevPitch;
    public int idleTicks;

    public SentinelEyeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SENTINEL_EYE.get(), pos, state);
    }

    @Nullable
    private Player findTarget(Level level) {
        Vec3 eye = Vec3.atCenterOf(worldPosition);
        Player best = null;
        double bestDist = RANGE * RANGE;
        for (Player p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive() || p.isInvisible()) continue;
            double d = p.distanceToSqr(eye);
            if (d < bestDist && canSee(level, eye, p.getEyePosition())) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean canSee(Level level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        Vec3 start = from.add(dir.scale(0.75));
        BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void track(Level level, @Nullable Player target) {
        prevYaw = yaw;
        prevPitch = pitch;
        if (target != null) {
            Vec3 d = target.getEyePosition().subtract(Vec3.atCenterOf(worldPosition));
            float ty = (float) (Math.atan2(d.x, d.z));
            float tp = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            yaw = approachAngle(yaw, ty, 0.25F);
            pitch += (tp - pitch) * 0.25F;
            idleTicks = 0;
        } else {
            idleTicks++;
            yaw = approachAngle(yaw, (float) Math.sin(idleTicks * 0.02F) * 1.2F, 0.05F);
            pitch += (0.2F * (float) Math.sin(idleTicks * 0.031F) - pitch) * 0.05F;
        }
    }

    private static float approachAngle(float cur, float target, float f) {
        float diff = (float) Math.atan2(Math.sin(target - cur), Math.cos(target - cur));
        return cur + diff * f;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SentinelEyeBlockEntity be) {
        Player target = level.getGameTime() % 2 == 0 ? be.findTarget(level) : null;
        if (target == null && level.getGameTime() % 2 == 1) return;
        if (target != null) {
            be.charge++;
            if (be.charge == 1) {
                level.playSound(null, pos, ModSounds.ASTRAEON_BEAM_CHARGE.get(), SoundSource.HOSTILE, 0.4F, 1.9F);
            }
            if (be.charge >= CHARGE_TIME / 2) {
                be.charge = 0;
                Vec3 from = Vec3.atCenterOf(pos);
                Vec3 dir = target.getEyePosition().subtract(0, 0.3, 0).subtract(from).normalize();
                StarBoltEntity bolt = new StarBoltEntity(level, from.add(dir.scale(0.8)), dir.scale(0.9), null, 5.0F);
                bolt.setHoming(target, 0.03);
                level.addFreshEntity(bolt);
                level.playSound(null, pos, ModSounds.STAR_BOLT.get(), SoundSource.HOSTILE, 1.0F, 1.2F);
            }
        } else {
            be.charge = Math.max(0, be.charge - 1);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SentinelEyeBlockEntity be) {
        Player target = be.findTarget(level);
        be.track(level, target);
        if (target != null) {
            be.charge = Math.min(CHARGE_TIME, be.charge + 1);
            if (be.charge > CHARGE_TIME / 2 && level.random.nextInt(2) == 0) {
                Vec3 c = Vec3.atCenterOf(pos);
                level.addParticle(ModParticles.VOID_SPARK.get(), c.x + (level.random.nextDouble() - 0.5) * 1.2,
                        c.y + (level.random.nextDouble() - 0.5) * 1.2, c.z + (level.random.nextDouble() - 0.5) * 1.2, 0, 0, 0);
            }
            if (be.charge >= CHARGE_TIME) be.charge = 0;
        } else {
            be.charge = Math.max(0, be.charge - 1);
        }
    }
}
