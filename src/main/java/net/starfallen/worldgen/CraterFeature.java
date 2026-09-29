package net.starfallen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.starfallen.world.CraterBuilder;

/** Ancient meteor craters scattered across the Overworld: cooled meteorite, ore and the odd fallen star. */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {
    public CraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = ctx.origin();
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ()) - 1;
        BlockPos center = new BlockPos(origin.getX(), y, origin.getZ());
        if (!level.getFluidState(center.above()).isEmpty()) return false;
        if (!CraterBuilder.carvable(level.getBlockState(center))) return false;
        int radius = 4 + random.nextInt(4);
        CraterBuilder.carve(level, center, radius, random, false, CraterBuilder.Loot.ANCIENT, 2);
        return true;
    }
}
