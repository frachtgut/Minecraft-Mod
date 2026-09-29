package net.starfallen.worldgen.structure;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A structure described as a sparse map of block placements in world coordinates, built in
 * local coordinates and transformed by an origin and rotation. Built once (deterministically
 * from a seed), then placed chunk by chunk during world generation.
 */
public class Blueprint {
    @FunctionalInterface
    public interface BlockEntityInit {
        void apply(BlockEntity be, RandomSource random);
    }

    @FunctionalInterface
    public interface EntityFactory {
        @Nullable Entity create(ServerLevel level, RandomSource random);
    }

    /** Fill-down columns: foundation pillars that extend until they hit solid ground. */
    public record Column(int x, int z, int topY, int maxDepth, BlockState state) {}

    public record Placement(BlockPos pos, BlockState state, @Nullable BlockEntityInit init) {}

    public record Spawn(Vec3 pos, float yaw, EntityFactory factory) {}

    private final BlockPos origin;
    private final Rotation rotation;
    public final RandomSource random;
    private final Long2ObjectOpenHashMap<Placement> blocks = new Long2ObjectOpenHashMap<>();
    private final List<Spawn> spawns = new ArrayList<>();
    private final List<Column> columns = new ArrayList<>();
    private Long2ObjectMap<List<Placement>> byChunk;
    private BoundingBox bounds;

    public Blueprint(BlockPos origin, Rotation rotation, long seed) {
        this.origin = origin;
        this.rotation = rotation;
        this.random = RandomSource.create(seed);
    }

    // ------------------------------------------------------------------ transforms

    public BlockPos world(int x, int y, int z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(origin.getX() - z, origin.getY() + y, origin.getZ() + x);
            case CLOCKWISE_180 -> new BlockPos(origin.getX() - x, origin.getY() + y, origin.getZ() - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(origin.getX() + z, origin.getY() + y, origin.getZ() - x);
            default -> new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        };
    }

    public Vec3 world(double x, double y, double z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vec3(origin.getX() - z + 1, origin.getY() + y, origin.getZ() + x);
            case CLOCKWISE_180 -> new Vec3(origin.getX() - x + 1, origin.getY() + y, origin.getZ() - z + 1);
            case COUNTERCLOCKWISE_90 -> new Vec3(origin.getX() + z, origin.getY() + y, origin.getZ() - x + 1);
            default -> new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        };
    }

    public Direction rotate(Direction local) {
        return rotation.rotate(local);
    }

    public float rotateYaw(float yaw) {
        return switch (rotation) {
            case CLOCKWISE_90 -> yaw + 90;
            case CLOCKWISE_180 -> yaw + 180;
            case COUNTERCLOCKWISE_90 -> yaw - 90;
            default -> yaw;
        };
    }

    public Rotation rotation() {
        return rotation;
    }

    // ------------------------------------------------------------------ building

    public void set(int x, int y, int z, BlockState state) {
        set(x, y, z, state, null);
    }

    public void set(int x, int y, int z, BlockState state, @Nullable BlockEntityInit init) {
        BlockPos p = world(x, y, z);
        blocks.put(p.asLong(), new Placement(p, state.rotate(rotation), init));
    }

    /** Only places if nothing has been planned at this position yet. */
    public void setIfAbsent(int x, int y, int z, BlockState state) {
        BlockPos p = world(x, y, z);
        blocks.putIfAbsent(p.asLong(), new Placement(p, state.rotate(rotation), null));
    }

    /** Places unless the position is already planned as air (keeps carved spaces open). */
    public void setUnlessAir(int x, int y, int z, BlockState state) {
        BlockPos p = world(x, y, z);
        Placement old = blocks.get(p.asLong());
        if (old != null && old.state.isAir()) return;
        blocks.put(p.asLong(), new Placement(p, state.rotate(rotation), null));
    }

    @Nullable
    public BlockState get(int x, int y, int z) {
        Placement pl = blocks.get(world(x, y, z).asLong());
        return pl == null ? null : pl.state;
    }

    public boolean isPlannedAir(int x, int y, int z) {
        BlockState s = get(x, y, z);
        return s != null && s.isAir();
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, state);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Palette palette) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, palette.pick(random));
    }

    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }

    /** A room: shell of {@code walls} (floor, ceiling, sides) around an air interior. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, Palette walls) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                    if (edge) setUnlessAir(x, y, z, walls.pick(random));
                    else set(x, y, z, Blocks.AIR.defaultBlockState());
                }
    }

    public void columnDown(int x, int y, int z, int maxDepth, BlockState state) {
        BlockPos p = world(x, y, z);
        columns.add(new Column(p.getX(), p.getZ(), p.getY(), maxDepth, state));
    }

    public void spawn(double x, double y, double z, float yaw, EntityFactory factory) {
        spawns.add(new Spawn(world(x, y, z), rotateYaw(yaw), factory));
    }

    // ------------------------------------------------------------------ finishing & placement

    public void finish() {
        byChunk = new Long2ObjectOpenHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Placement pl : blocks.values()) {
            BlockPos p = pl.pos;
            byChunk.computeIfAbsent(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4), k -> new ArrayList<>()).add(pl);
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        for (Column c : columns) {
            minY = Math.min(minY, c.topY - c.maxDepth);
        }
        bounds = blocks.isEmpty() ? new BoundingBox(origin) : new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public int size() {
        return blocks.size();
    }

    public void place(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        ChunkPos cp = new ChunkPos(chunkBox.minX() >> 4, chunkBox.minZ() >> 4);
        // Foundations first
        for (Column c : columns) {
            if (c.x < chunkBox.minX() || c.x > chunkBox.maxX() || c.z < chunkBox.minZ() || c.z > chunkBox.maxZ()) continue;
            for (int y = c.topY; y > c.topY - c.maxDepth && y > level.getMinBuildHeight(); y--) {
                BlockPos p = new BlockPos(c.x, y, c.z);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                level.setBlock(p, c.state, 2);
            }
        }
        List<Placement> list = byChunk.get(cp.toLong());
        if (list != null) {
            List<Placement> withEntities = new ArrayList<>();
            for (Placement pl : list) {
                if (!chunkBox.isInside(pl.pos)) continue;
                BlockState old = level.getBlockState(pl.pos);
                if (old.is(Blocks.BEDROCK) || old.is(Blocks.END_PORTAL_FRAME)) continue;
                level.setBlock(pl.pos, pl.state, 2);
                FluidState fluid = pl.state.getFluidState();
                if (!fluid.isEmpty()) level.scheduleTick(pl.pos, fluid.getType(), 0);
                if (needsShapeUpdate(pl.state)) level.getChunk(pl.pos).markPosForPostprocessing(pl.pos);
                if (pl.init != null) withEntities.add(pl);
            }
            for (Placement pl : withEntities) {
                BlockEntity be = level.getBlockEntity(pl.pos);
                if (be != null) {
                    try {
                        pl.init.apply(be, random);
                    } catch (Exception e) {
                        net.starfallen.Starfallen.LOGGER.warn("Failed to initialise block entity at {}", pl.pos, e);
                    }
                }
            }
        }
        for (Spawn s : spawns) {
            BlockPos bp = BlockPos.containing(s.pos);
            if (!chunkBox.isInside(bp)) continue;
            Entity e = s.factory.create(level.getLevel(), random);
            if (e == null) continue;
            e.moveTo(s.pos.x, s.pos.y, s.pos.z, s.yaw, 0);
            if (e instanceof Mob mob) {
                mob.setYHeadRot(s.yaw);
                mob.yBodyRot = s.yaw;
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(bp), MobSpawnType.STRUCTURE, null, null);
                mob.setPersistenceRequired();
            }
            level.addFreshEntityWithPassengers(e);
        }
    }

    private static boolean needsShapeUpdate(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock || b instanceof ChainBlock
                || b instanceof LanternBlock || b instanceof TorchBlock || b instanceof LadderBlock || b instanceof VineBlock;
    }
}
