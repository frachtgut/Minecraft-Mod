package net.starfallen.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.starfallen.Starfallen;
import net.starfallen.block.MoltenMeteoriteBlock;
import net.starfallen.registry.ModBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Carves meteor craters, both during world generation (ancient, cooled craters) and live,
 * when a meteor slams into the world (fresh, molten craters).
 */
public final class CraterBuilder {
    /** Only natural terrain gets carved - player builds are left alone. */
    public static final TagKey<Block> CARVABLE = BlockTags.create(Starfallen.id("meteor_carvable"));

    public enum Loot { NONE, NORMAL, LARGE, GOLDEN, EGG, ANCIENT }

    private CraterBuilder() {}

    public static boolean carvable(BlockState state) {
        return state.is(CARVABLE) || state.isAir() || state.canBeReplaced();
    }

    /**
     * @param center the block at the point of impact (top of the ground)
     * @param radius crater radius in blocks
     * @param fresh  molten, smoking crater (live impact) vs cooled ancient crater
     * @param flags  setBlock flags (2 for world-gen, 3 for live)
     * @return the position of the crater floor centre
     */
    public static BlockPos carve(LevelAccessor level, BlockPos center, int radius, RandomSource random, boolean fresh, Loot loot, int flags) {
        double depthMax = Math.max(1.5, radius * 0.6);
        int clearAbove = Math.max(3, radius / 2 + 2);
        List<BlockPos> floor = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -radius - 3; dx <= radius + 3; dx++) {
            for (int dz = -radius - 3; dz <= radius + 3; dz++) {
                double noise = (random.nextDouble() - 0.5) * 0.9;
                double dist = Math.sqrt(dx * dx + dz * dz) + noise;
                if (dist <= radius) {
                    double t = dist / radius;
                    int depth = (int) Math.round(depthMax * (1.0 - t * t));
                    int bottom = center.getY() - depth;
                    // Hollow the bowl
                    for (int y = bottom + 1; y <= center.getY() + clearAbove; y++) {
                        BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                        if (level.getBlockEntity(p) != null || !carvable(s)) continue;
                        level.setBlock(p, air, flags);
                    }
                    // Line the bowl
                    for (int y = bottom; y >= bottom - 1; y--) {
                        BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !s.getFluidState().isEmpty() || level.getBlockEntity(p) != null || !carvable(s)) continue;
                        level.setBlock(p, liningFor(t, random, fresh, loot, y == bottom), flags);
                        if (y == bottom) floor.add(p);
                    }
                } else if (dist <= radius + 3) {
                    // Ejecta: scatter debris around the rim
                    if (random.nextFloat() < (fresh ? 0.28F : 0.18F) * (1.0 - (dist - radius) / 3.0)) {
                        BlockPos top = surfaceAt(level, center.getX() + dx, center.getZ() + dz, center.getY() + clearAbove + 2, center.getY() - 6);
                        if (top != null) {
                            BlockState s = level.getBlockState(top);
                            if (carvable(s) && level.getBlockEntity(top) == null && s.getFluidState().isEmpty()) {
                                BlockState debris = fresh && random.nextFloat() < 0.35F
                                        ? ModBlocks.MOLTEN_METEORITE.get().defaultBlockState().setValue(MoltenMeteoriteBlock.PERMANENT, false)
                                        : random.nextFloat() < 0.5F ? ModBlocks.METEORITE.get().defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                                level.setBlock(top, debris, flags);
                                BlockPos above = top.above();
                                if (fresh && random.nextFloat() < 0.25F && level.getBlockState(above).isAir()) {
                                    level.setBlock(above, Blocks.FIRE.defaultBlockState(), flags);
                                }
                            }
                        }
                    }
                }
            }
        }

        BlockPos floorCenter = center.below((int) Math.round(depthMax));
        decorate(level, floorCenter, floor, radius, random, fresh, loot, flags);
        return floorCenter;
    }

    private static BlockState liningFor(double t, RandomSource random, boolean fresh, Loot loot, boolean top) {
        float r = random.nextFloat();
        if (loot == Loot.GOLDEN && t < 0.7) {
            if (r < 0.18F) return Blocks.RAW_GOLD_BLOCK.defaultBlockState();
            if (r < 0.40F) return Blocks.GOLD_ORE.defaultBlockState();
            if (r < 0.48F) return Blocks.DIAMOND_ORE.defaultBlockState();
            if (r < 0.52F) return Blocks.EMERALD_ORE.defaultBlockState();
        }
        if (fresh && top && t < 0.75 && r < 0.45F) {
            return ModBlocks.MOLTEN_METEORITE.get().defaultBlockState().setValue(MoltenMeteoriteBlock.PERMANENT, false);
        }
        if (r < (t < 0.5 ? 0.22F : 0.08F)) return ModBlocks.METEORIC_IRON_ORE.get().defaultBlockState();
        if (!fresh && r > 0.9F) return Blocks.TUFF.defaultBlockState();
        return ModBlocks.METEORITE.get().defaultBlockState();
    }

    private static void decorate(LevelAccessor level, BlockPos floorCenter, List<BlockPos> floor, int radius, RandomSource random,
                                 boolean fresh, Loot loot, int flags) {
        // Starlit crystals sprout from the crater floor
        int crystals = loot == Loot.NONE ? 0 : loot == Loot.LARGE || loot == Loot.EGG ? 6 + random.nextInt(5) : loot == Loot.ANCIENT ? 3 + random.nextInt(4) : random.nextInt(3);
        for (int i = 0; i < crystals && !floor.isEmpty(); i++) {
            BlockPos p = floor.get(random.nextInt(floor.size())).above();
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                level.setBlock(p, ModBlocks.STARLIT_CRYSTAL.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), flags);
            }
        }
        // The core
        BlockPos core = findFloor(level, floorCenter);
        if (core == null) return;
        BlockPos above = core.above();
        switch (loot) {
            case LARGE, EGG -> {
                level.setBlock(above, ModBlocks.FALLEN_STAR.get().defaultBlockState(), flags);
                if (loot == Loot.EGG) {
                    BlockPos eggPos = above.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
                    if (level.getBlockState(eggPos).isAir()) {
                        level.setBlock(eggPos.below(), ModBlocks.METEORITE.get().defaultBlockState(), flags);
                        level.setBlock(eggPos, ModBlocks.STELLAR_EGG.get().defaultBlockState(), flags);
                    }
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (random.nextFloat() < 0.6F) level.setBlock(core.relative(d), ModBlocks.METEORIC_IRON_ORE.get().defaultBlockState(), flags);
                }
            }
            case ANCIENT -> {
                if (random.nextFloat() < 0.3F) level.setBlock(above, ModBlocks.FALLEN_STAR.get().defaultBlockState(), flags);
                else level.setBlock(core, ModBlocks.METEORIC_IRON_ORE.get().defaultBlockState(), flags);
            }
            case GOLDEN -> {
                level.setBlock(above, Blocks.RAW_GOLD_BLOCK.defaultBlockState(), flags);
                level.setBlock(core, Blocks.DIAMOND_ORE.defaultBlockState(), flags);
            }
            case NORMAL -> level.setBlock(core, ModBlocks.METEORIC_IRON_ORE.get().defaultBlockState(), flags);
            default -> {}
        }
    }

    public static BlockPos findFloor(LevelAccessor level, BlockPos from) {
        BlockPos.MutableBlockPos p = from.mutable().move(0, 3, 0);
        for (int i = 0; i < 10; i++) {
            if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p.immutable();
            p.move(0, -1, 0);
        }
        return null;
    }

    private static BlockPos surfaceAt(LevelAccessor level, int x, int z, int fromY, int toY) {
        for (int y = fromY; y >= toY; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.canBeReplaced()) {
                return level.getBlockState(p.above()).isAir() || level.getBlockState(p.above()).canBeReplaced() ? p : null;
            }
        }
        return null;
    }
}
