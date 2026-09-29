package net.starfallen.worldgen.structure;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.starfallen.registry.ModWorldgen;

import java.util.Optional;

/** The Astral Observatory: a star-watchers' tower crowned by a great telescope. */
public class ObservatoryStructure extends Structure {
    public static final Codec<ObservatoryStructure> CODEC = simpleCodec(ObservatoryStructure::new);

    public ObservatoryStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -14; dx <= 14; dx += 14) {
            for (int dz = -14; dz <= 14; dz += 14) {
                if (!StructureUtil.dry(ctx, x + dx, z + dz)) return Optional.empty();
                int h = StructureUtil.surface(ctx, x + dx, z + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        if (max - min > 7) return Optional.empty();
        int y = StructureUtil.surface(ctx, x, z);
        if (y < ctx.chunkGenerator().getSeaLevel()) return Optional.empty();
        if (!StructureUtil.validBiome(ctx, x, y, z)) return Optional.empty();
        Rotation rot = Rotation.getRandom(ctx.random());
        long seed = ctx.random().nextLong();
        BlockPos origin = new BlockPos(x, y, z);
        BoundingBox box = StructureUtil.worldBox(origin, rot, -18, -22, -18, 18, 38, 18);
        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new BlueprintPiece("observatory", origin, rot, seed, 0, box))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.OBSERVATORY.get();
    }
}
