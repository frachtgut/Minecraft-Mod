package net.starfallen.worldgen.structure;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.starfallen.registry.ModWorldgen;

import java.util.Optional;

/**
 * The Sanctum of the Fallen Star: a vast underground dungeon beneath a ruined obelisk. Its
 * heart chamber holds the Star Altar where Astraeon sleeps.
 */
public class SanctumStructure extends Structure {
    public static final Codec<SanctumStructure> CODEC = simpleCodec(SanctumStructure::new);

    public SanctumStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        Rotation rot = Rotation.getRandom(ctx.random());
        long seed = ctx.random().nextLong();
        // The entrance shaft sits 22 blocks "north" of the hall of stars (in local space).
        Blueprint probe = new Blueprint(new BlockPos(x, 0, z), rot, 0L);
        BlockPos entrance = probe.world(0, 0, SanctumBuilder.SHAFT_Z);
        if (!StructureUtil.dry(ctx, entrance.getX(), entrance.getZ())) return Optional.empty();
        int surface = StructureUtil.surface(ctx, entrance.getX(), entrance.getZ());
        if (surface < ctx.chunkGenerator().getSeaLevel() - 2) return Optional.empty();
        if (!StructureUtil.validBiome(ctx, entrance.getX(), surface, entrance.getZ())) return Optional.empty();
        int minY = ctx.heightAccessor().getMinBuildHeight();
        int floorY = Mth.clamp(surface - 42, minY + 14, 36);
        int surfaceRel = surface - floorY;
        BlockPos origin = new BlockPos(x, floorY, z);
        BoundingBox box = StructureUtil.worldBox(origin, rot, SanctumBuilder.MIN_X, -8, SanctumBuilder.MIN_Z,
                SanctumBuilder.MAX_X, Math.max(SanctumBuilder.DOME_TOP + 2, surfaceRel + 16), SanctumBuilder.MAX_Z);
        return Optional.of(new GenerationStub(new BlockPos(entrance.getX(), surface, entrance.getZ()), builder ->
                builder.addPiece(new BlueprintPiece("sanctum", origin, rot, seed, surfaceRel, box))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.SANCTUM.get();
    }
}
