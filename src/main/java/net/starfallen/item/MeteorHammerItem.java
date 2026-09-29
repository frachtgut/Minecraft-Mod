package net.starfallen.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.starfallen.event.AbilityHandler;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Cratermaker. Hold right-click to wind up, release to leap - and come down like a meteor.
 * Used mid-air it dives straight down instead. Damage grows with charge and fall height.
 */
public class MeteorHammerItem extends SwordItem {
    public static final int FULL_CHARGE = 20;

    public MeteorHammerItem(Tier tier, int damage, float speed, Properties props) {
        super(tier, damage, speed, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!player.onGround() && !player.isInWater() && player.fallDistance > 0.5F) {
            // Mid-air dive
            if (!level.isClientSide) {
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, -1.6, 0));
                player.hurtMarked = true;
                AbilityHandler.startLeap((ServerPlayer) player, 0.5F, true);
                player.getCooldowns().addCooldown(this, 50);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_DASH.get(), SoundSource.PLAYERS, 1.0F, 0.6F);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (level.isClientSide) {
            if (used < FULL_CHARGE && level.random.nextInt(2) == 0) {
                level.addParticle(ModParticles.EMBER.get(), entity.getX() + (level.random.nextDouble() - 0.5), entity.getY() + 1.8,
                        entity.getZ() + (level.random.nextDouble() - 0.5), 0, 0.05, 0);
            } else if (used >= FULL_CHARGE) {
                level.addParticle(ModParticles.STAR_SPARK.get(), entity.getX() + (level.random.nextDouble() - 0.5) * 1.2, entity.getY() + 2.0,
                        entity.getZ() + (level.random.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
            }
        } else if (used == FULL_CHARGE) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        if (used < 6 || !(entity instanceof ServerPlayer player)) return;
        float charge = Math.min(used, FULL_CHARGE) / (float) FULL_CHARGE;
        Vec3 look = player.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0, look.z);
        horiz = horiz.lengthSqr() < 1.0E-4 ? Vec3.ZERO : horiz.normalize();
        double forward = 0.55 + charge * 0.85;
        player.setDeltaMovement(horiz.x * forward, 0.72 + charge * 0.5, horiz.z * forward);
        player.hurtMarked = true;
        AbilityHandler.startLeap(player, charge, false);
        player.getCooldowns().addCooldown(this, 50);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_DASH.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(net.starfallen.client.item.WeaponAnimations.HAMMER);
    }
}
