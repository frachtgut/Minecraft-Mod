package net.starfallen.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.starfallen.registry.ModWorldgen;

/**
 * The single structure piece type used by all Starfallen structures. It stores only the
 * recipe (kind, origin, rotation, seed, parameter) and rebuilds the blueprint on demand.
 */
public class BlueprintPiece extends StructurePiece {
    private final String kind;
    private final BlockPos origin;
    private final Rotation rotation;
    private final long seed;
    private final int param;

    public BlueprintPiece(String kind, BlockPos origin, Rotation rotation, long seed, int param, BoundingBox box) {
        super(ModWorldgen.BLUEPRINT_PIECE.get(), 0, box);
        this.kind = kind;
        this.origin = origin;
        this.rotation = rotation;
        this.seed = seed;
        this.param = param;
        setOrientation(null);
    }

    /** Where a visitor should arrive: the obelisk for the Sanctum, the tower for anything else. */
    public BlockPos entrance() {
        if ("sanctum".equals(kind)) {
            BlockPos p = new Blueprint(origin, rotation, 0L).world(0, 0, SanctumBuilder.SHAFT_Z);
            return new BlockPos(p.getX(), origin.getY() + param, p.getZ());
        }
        return origin;
    }

    public BlueprintPiece(CompoundTag tag) {
        super(ModWorldgen.BLUEPRINT_PIECE.get(), tag);
        this.kind = tag.getString("Kind");
        this.origin = NbtUtils.readBlockPos(tag.getCompound("Origin"));
        this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rot"), Rotation.values().length)];
        this.seed = tag.getLong("Seed");
        this.param = tag.getInt("Param");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putString("Kind", kind);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putInt("Rot", rotation.ordinal());
        tag.putLong("Seed", seed);
        tag.putInt("Param", param);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        Blueprint bp = BlueprintCache.get(kind, origin, rotation, seed, param);
        if (bp != null) bp.place(level, chunkBox, random);
    }
}
