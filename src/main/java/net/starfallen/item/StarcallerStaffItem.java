package net.starfallen.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.starfallen.entity.projectile.MeteorEntity;
import net.starfallen.event.SFScheduler;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Starcaller Staff. Point at the ground and call a meteor down from the heavens.
 * Sneak to call a whole Meteor Storm.
 */
public class StarcallerStaffItem extends Item {
    public StarcallerStaffItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            ServerLevel server = (ServerLevel) level;
            Vec3 target = findTarget(server, player);
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.starfallen.staff_no_sky").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            boolean storm = player.isShiftKeyDown();
            Vec3 look = player.getLookAngle();
            Vec3 horiz = new Vec3(look.x, 0, look.z);
            horiz = horiz.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : horiz.normalize();
            if (storm) {
                for (int i = 0; i < 7; i++) {
                    final int n = i;
                    final Vec3 dirFinal = horiz;
                    SFScheduler.schedule(server, 1 + i * 7, lvl -> {
                        double ang = lvl.random.nextDouble() * Math.PI * 2;
                        double r = n == 0 ? 0 : 3 + lvl.random.nextDouble() * 7;
                        Vec3 t = target.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                        int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(t.x), (int) Math.floor(t.z));
                        t = new Vec3(t.x, Math.min(y, target.y + 6), t.z);
                        MeteorEntity.strike(lvl, player, t, dirFinal, 0.9F + lvl.random.nextFloat() * 0.5F, MeteorEntity.Kind.STAFF, 16.0F);
                    });
                }
                player.getCooldowns().addCooldown(this, 400);
                stack.hurtAndBreak(5, player, p -> p.broadcastBreakEvent(hand));
            } else {
                MeteorEntity.strike(server, player, target, horiz, 1.25F, MeteorEntity.Kind.STAFF, 22.0F);
                player.getCooldowns().addCooldown(this, 70);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.STARSEER_CAST.get(), SoundSource.PLAYERS, 1.0F, storm ? 0.7F : 1.0F);
            server.sendParticles(ModParticles.STAR_SPARK.get(), player.getX(), player.getY() + 2.2, player.getZ(), 25, 0.3, 0.3, 0.3, 0.1);
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Nullable
    private static Vec3 findTarget(ServerLevel level, LivingEntity player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(72));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        Vec3 target;
        if (hit.getType() == HitResult.Type.MISS) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(end.x), (int) Math.floor(end.z));
            if (y <= level.getMinBuildHeight()) return null;
            target = new Vec3(end.x, y, end.z);
        } else {
            target = hit.getLocation();
        }
        return target;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(net.starfallen.client.item.WeaponAnimations.STAFF);
    }
}
