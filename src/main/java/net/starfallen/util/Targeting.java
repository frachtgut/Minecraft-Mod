package net.starfallen.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Targeting {
    private Targeting() {}

    /**
     * Finds the living entity the user is aiming at: a direct ray hit first, otherwise the
     * closest valid target inside a narrow cone (gentle aim assist).
     */
    @Nullable
    public static LivingEntity findLookTarget(LivingEntity user, double range, double coneDegrees) {
        Level level = user.level();
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        double maxDist = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye);
        Vec3 clipped = eye.add(look.scale(maxDist));
        AABB search = user.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.5);
        EntityHitResult direct = ProjectileUtil.getEntityHitResult(user, eye, clipped, search,
                e -> e instanceof LivingEntity && SFUtil.isValidTarget(user, e), maxDist * maxDist);
        if (direct != null && direct.getEntity() instanceof LivingEntity le) return le;

        double cos = Math.cos(Math.toRadians(coneDegrees));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(range),
                e -> SFUtil.isValidTarget(user, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.5) continue;
            double dot = to.normalize().dot(look);
            if (dot < cos) continue;
            if (!hasLineOfSight(level, user, eye, e)) continue;
            double score = dist * (2.0 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    public static boolean hasLineOfSight(Level level, Entity user, Vec3 from, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)).getType() == HitResult.Type.MISS;
    }
}
