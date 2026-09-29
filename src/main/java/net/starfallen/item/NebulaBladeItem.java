package net.starfallen.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
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
 * Nebula Blade. Right-click to dash through your enemies in a streak of starlight;
 * everything you cut through is struck again by a Starlight Echo a moment later.
 */
public class NebulaBladeItem extends SwordItem {
    public NebulaBladeItem(Tier tier, int damage, float speed, Properties props) {
        super(tier, damage, speed, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.3, 0.35), look.z).normalize();
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9F;
            AbilityHandler.startDash((ServerPlayer) player, dir.scale(1.9), damage);
            player.getCooldowns().addCooldown(this, 36);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_DASH.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
            ((ServerLevel) level).sendParticles(ModParticles.SHOCKWAVE.get(), player.getX(), player.getY() + 0.1, player.getZ(), 1, 0, 0, 0, 0);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.STAR_SPARK.get(), target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.08);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(net.starfallen.client.item.WeaponAnimations.BLADE);
    }
}
