package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.starfallen.registry.ModParticles;

/** Glowing crystal cluster that grows where stars have fallen. Drops Stardust. */
public class StarlitCrystalBlock extends AmethystClusterBlock {
    public StarlitCrystalBlock(Properties props) {
        super(7, 3, props);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addParticle(ModParticles.ASTRAL_SPARK.get(),
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.2 + random.nextDouble() * 0.6, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.01, 0.0);
        }
    }
}
