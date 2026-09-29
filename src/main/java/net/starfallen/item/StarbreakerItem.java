package net.starfallen.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Starbreaker: an astral pickaxe that shatters a 3x3 wall of stone. Sneak to mine single blocks. */
public class StarbreakerItem extends PickaxeItem {
    private static boolean breaking;

    public StarbreakerItem(Tier tier, int damage, float speed, Properties props) {
        super(tier, damage, speed, props);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        boolean result = super.mineBlock(stack, level, state, pos, miner);
        if (!breaking && !level.isClientSide && miner instanceof ServerPlayer player && !player.isShiftKeyDown()
                && isCorrectToolForDrops(stack, state)) {
            Direction face = hitFace(player);
            if (face != null) {
                breaking = true;
                try {
                    for (int a = -1; a <= 1; a++) {
                        for (int b = -1; b <= 1; b++) {
                            if (a == 0 && b == 0) continue;
                            BlockPos p = offset(pos, face, a, b);
                            BlockState s = level.getBlockState(p);
                            if (s.isAir() || s.getDestroySpeed(level, p) < 0 || !isCorrectToolForDrops(stack, s)) continue;
                            if (s.getDestroySpeed(level, p) > state.getDestroySpeed(level, pos) * 2.5F + 1.0F) continue;
                            if (stack.isEmpty()) return result;
                            player.gameMode.destroyBlock(p);
                        }
                    }
                } finally {
                    breaking = false;
                }
            }
        }
        return result;
    }

    @Nullable
    private static Direction hitFace(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(6.0));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getDirection() : null;
    }

    private static BlockPos offset(BlockPos pos, Direction face, int a, int b) {
        return switch (face.getAxis()) {
            case Y -> pos.offset(a, 0, b);
            case X -> pos.offset(0, a, b);
            case Z -> pos.offset(a, b, 0);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }
}
