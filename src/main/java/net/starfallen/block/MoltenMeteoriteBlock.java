package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.starfallen.registry.ModBlocks;
import net.starfallen.registry.ModParticles;

/**
 * Freshly fallen meteorite rock. Scorching hot: burns anything that walks on it, glows,
 * smokes and slowly cools back into ordinary meteorite. Placed "permanent" (by players or
 * structures) it never cools.
 */
public class MoltenMeteoriteBlock extends Block {
    public static final BooleanProperty PERMANENT = BooleanProperty.create("permanent");

    public MoltenMeteoriteBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PERMANENT, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PERMANENT);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(PERMANENT);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PERMANENT) && random.nextInt(3) == 0) {
            level.setBlockAndUpdate(pos, ModBlocks.METEORITE.get().defaultBlockState());
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F + random.nextFloat() * 0.4F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity living && !EnchantmentHelper.hasFrostWalker(living)
                && !living.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), 2.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        if (random.nextInt(4) == 0) {
            level.addParticle(ModParticles.EMBER.get(), pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.02, 0.04 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.02);
        }
        if (!state.getValue(PERMANENT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(),
                    0.0, 0.03 + random.nextDouble() * 0.03, 0.0);
        }
    }
}
