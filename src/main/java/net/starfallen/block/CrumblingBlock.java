package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Looks almost exactly like cracked void bricks - until you stand on it. Shortly after,
 * it shatters, and so does the floor around it.
 */
public class CrumblingBlock extends Block {
    public static final BooleanProperty TRIGGERED = BooleanProperty.create("triggered");

    public CrumblingBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && !state.getValue(TRIGGERED) && !(entity instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            trigger(level, pos, state);
        }
        super.stepOn(level, pos, state, entity);
    }

    private void trigger(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(TRIGGERED, true), 3);
        level.scheduleTick(pos, this, 14);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(TRIGGERED)) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30, 0.35, 0.35, 0.35, 0.05);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.2F, 0.5F);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        // Chain reaction through neighbouring crumbling blocks.
        for (BlockPos n : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof CrumblingBlock && !ns.getValue(TRIGGERED)) {
                level.setBlock(n, ns.setValue(TRIGGERED, true), 3);
                level.scheduleTick(n, ns.getBlock(), 3 + random.nextInt(4));
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(TRIGGERED)) {
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), pos.getX() + random.nextDouble(), pos.getY() - 0.05,
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
