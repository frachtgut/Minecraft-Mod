package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;

/** The still-burning heart of a fallen star. Mine it for Star Fragments. */
public class FallenStarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public FallenStarBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.4 + random.nextDouble() * 0.5;
            level.addParticle(random.nextBoolean() ? ModParticles.STAR_SPARK.get() : ModParticles.ASTRAL_SPARK.get(),
                    pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.3 + random.nextDouble() * 0.9, pos.getZ() + 0.5 + Math.sin(a) * r,
                    0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.STAR_CHIME.get(), SoundSource.BLOCKS,
                    0.6F, 0.8F + random.nextFloat() * 0.5F, false);
        }
    }
}
