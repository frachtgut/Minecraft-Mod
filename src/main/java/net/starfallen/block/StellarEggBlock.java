package net.starfallen.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.starfallen.entity.CometRayEntity;
import net.starfallen.registry.ModBlocks;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModItems;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;

/** A rare egg carried inside meteors. Keep it warm with stardust and a Comet Ray will hatch. */
public class StellarEggBlock extends Block {
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public StellarEggBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(HATCH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    private static boolean boosted(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(ModBlocks.FALLEN_STAR.get()) || below.is(ModBlocks.STARLIT_CRYSTAL.get()) || below.is(ModBlocks.MOLTEN_METEORITE.get());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) {
            int base = boosted(level, pos) ? 600 : 2400;
            level.scheduleTick(pos, this, base + level.random.nextInt(300));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.STARDUST.get())) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            ((ServerLevel) level).sendParticles(ModParticles.STAR_SPARK.get(), pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
            advance((ServerLevel) level, pos, level.getBlockState(pos), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        advance(level, pos, state, null);
        if (level.getBlockState(pos).is(this)) {
            int base = boosted(level, pos) ? 600 : 2400;
            level.scheduleTick(pos, this, base + random.nextInt(300));
        }
    }

    private void advance(ServerLevel level, BlockPos pos, BlockState state, Player feeder) {
        int hatch = state.getValue(HATCH);
        if (hatch < 2) {
            level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 0.8F, 1.3F + level.random.nextFloat() * 0.2F);
            level.setBlock(pos, state.setValue(HATCH, hatch + 1), 2);
            return;
        }
        level.playSound(null, pos, ModSounds.EGG_HATCH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.destroyBlock(pos, false);
        CometRayEntity ray = ModEntities.COMET_RAY.get().create(level);
        if (ray == null) return;
        Vec3 c = pos.getCenter();
        ray.moveTo(c.x, c.y, c.z, Mth.wrapDegrees(level.random.nextFloat() * 360.0F), 0.0F);
        Player owner = feeder != null ? feeder : level.getNearestPlayer(c.x, c.y, c.z, 24, false);
        if (owner != null) {
            ray.tame(owner);
            owner.displayClientMessage(Component.translatable("message.starfallen.egg_hatched").withStyle(ChatFormatting.AQUA), false);
            if (owner instanceof net.minecraft.server.level.ServerPlayer sp) SFUtil.award(sp, "comet_rider");
        }
        level.addFreshEntity(ray);
        level.sendParticles(ModParticles.NOVA_FLASH.get(), c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.STAR_SPARK.get(), c.x, c.y + 0.5, c.z, 60, 0.6, 0.6, 0.6, 0.2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.ASTRAL_SPARK.get(), pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3 + random.nextDouble() * 0.7,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }
}
