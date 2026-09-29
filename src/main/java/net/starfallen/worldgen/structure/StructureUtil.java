package net.starfallen.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class StructureUtil {
    private StructureUtil() {}

    /** World-space box for a local box under the given origin/rotation. */
    public static BoundingBox worldBox(BlockPos origin, Rotation rotation, int x1, int y1, int z1, int x2, int y2, int z2) {
        Blueprint probe = new Blueprint(origin, rotation, 0L);
        BlockPos a = probe.world(x1, y1, z1);
        BlockPos b = probe.world(x2, y2, z2);
        return new BoundingBox(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static boolean validBiome(Structure.GenerationContext ctx, int x, int y, int z) {
        return ctx.validBiome().test(ctx.chunkGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), ctx.randomState().sampler()));
    }

    public static int surface(Structure.GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }

    public static int oceanFloor(Structure.GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
    }

    /** True when the column at x/z is dry land (no water above the ground). */
    public static boolean dry(Structure.GenerationContext ctx, int x, int z) {
        return surface(ctx, x, z) - oceanFloor(ctx, x, z) <= 0;
    }
}
