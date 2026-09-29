package net.starfallen.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.starfallen.entity.projectile.StarArrowEntity;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Constellation Bow. Needs no arrows: it looses bolts of starlight that home in on foes.
 * Sneak while firing a full draw to release a five-star Constellation Volley.
 */
public class ConstellationBowItem extends BowItem {
    public ConstellationBowItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        int charge = getUseDuration(stack) - timeLeft;
        float power = getPowerForTime(charge);
        if (power < 0.1F) return;
        if (!level.isClientSide) {
            boolean volley = power >= 1.0F && player.isShiftKeyDown();
            float[] spreads = volley ? new float[]{-14F, -7F, 0F, 7F, 14F} : new float[]{0F};
            int powerLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
            int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
            boolean flame = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0;
            for (float spread : spreads) {
                StarArrowEntity arrow = new StarArrowEntity(level, player);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + spread, 0.0F, power * 3.2F, 0.4F);
                arrow.setBaseDamage(3.0D + (powerLvl > 0 ? powerLvl * 0.5D + 0.5D : 0));
                arrow.setCritArrow(power >= 1.0F);
                arrow.setHomingStrength(power >= 1.0F ? 0.14 : 0.05);
                if (punch > 0) arrow.setKnockback(punch);
                if (flame) arrow.setSecondsOnFire(100);
                if (volley) arrow.setBaseDamage(arrow.getBaseDamage() * 0.7D);
                level.addFreshEntity(arrow);
            }
            stack.hurtAndBreak(volley ? 3 : 1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.STAR_BOLT.get(), SoundSource.PLAYERS, 1.0F, 1.2F + power * 0.3F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6F, 1.6F);
            if (volley) player.getCooldowns().addCooldown(this, 30);
        }
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }
}
