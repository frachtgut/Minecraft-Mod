package net.starfallen.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.starfallen.entity.boss.AstraeonEntity;
import net.starfallen.registry.ModItems;
import net.starfallen.registry.ModParticles;

/** The Star Altar. Offer it a Sigil of the Fallen Star to awaken Astraeon. */
public class StarAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(3, 4, 3, 13, 12, 13), Block.box(1, 12, 1, 15, 16, 15));

    public StarAltarBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.SIGIL_OF_THE_FALLEN_STAR.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.starfallen.altar_hint").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel server = (ServerLevel) level;
        if (!server.getEntitiesOfClass(AstraeonEntity.class, new AABB(pos).inflate(96)).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.starfallen.altar_busy").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        AstraeonEntity.summon(server, pos.above(), player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double a = (level.getGameTime() * 0.08 + random.nextDouble() * 0.3) % (Math.PI * 2);
        for (int i = 0; i < 4; i++) {
            double ang = a + i * Math.PI / 2;
            level.addParticle(ModParticles.RUNE.get(), pos.getX() + 0.5 + Math.cos(ang) * 1.3, pos.getY() + 0.4, pos.getZ() + 0.5 + Math.sin(ang) * 1.3,
                    0, 0.04, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.VOID_SPARK.get(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.05, 0.05, (random.nextDouble() - 0.5) * 0.05);
        }
    }
}
