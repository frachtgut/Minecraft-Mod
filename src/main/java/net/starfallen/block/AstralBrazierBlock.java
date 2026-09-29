package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.starfallen.registry.ModItems;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;

/**
 * Ancient star brazier. Kindle it with Stardust (or flint and steel). The Sanctum's seal
 * only breaks once every brazier of the dungeon burns.
 */
public class AstralBrazierBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 3, 12), Block.box(6, 3, 6, 10, 9, 10), Block.box(2, 9, 2, 14, 14, 14));

    public AstralBrazierBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(LIT)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        boolean stardust = stack.is(ModItems.STARDUST.get());
        if (!stardust && !stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.starfallen.brazier_hint"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(LIT, true), 3);
            level.playSound(null, pos, ModSounds.BRAZIER_IGNITE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            ((ServerLevel) level).sendParticles(ModParticles.ASTRAL_SPARK.get(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.08);
            ((ServerLevel) level).sendParticles(ModParticles.SHOCKWAVE.get(), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            if (!player.getAbilities().instabuild) {
                if (stardust) stack.shrink(1);
                else if (stack.is(Items.FIRE_CHARGE)) stack.shrink(1);
                else stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            if (random.nextInt(8) == 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.9, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
            }
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.95,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0.03 + random.nextDouble() * 0.03, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.ASTRAL_SPARK.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.1 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.4F, 1.0F, false);
        }
    }
}
