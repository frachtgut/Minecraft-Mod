package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.starfallen.blockentity.SentinelEyeBlockEntity;
import net.starfallen.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

/** An ancient caged eye that tracks intruders and spits star bolts at them. */
public class SentinelEyeBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 1, 1, 15, 15, 15);

    public SentinelEyeBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SentinelEyeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.SENTINEL_EYE.get(),
                level.isClientSide ? SentinelEyeBlockEntity::clientTick : SentinelEyeBlockEntity::serverTick);
    }
}
