package net.starfallen.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.starfallen.entity.projectile.ThrownScytheEntity;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Eclipse Scythe - Astraeon's own blade. Every swing reaps a wide arc and drinks the life of
 * the fallen. Right-click to hurl it: it carves through everything and returns to your hand.
 */
public class EclipseScytheItem extends SwordItem {
    public EclipseScytheItem(Tier tier, int damage, float speed, Properties props) {
        super(tier, damage, speed, props);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel server && attacker instanceof Player player) {
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F;
            Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
            float healed = 1.5F;
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.8, 1.0, 3.8),
                    e -> e != target && SFUtil.isValidTarget(player, e))) {
                Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
                if (to.length() > 4.0 || to.normalize().dot(look) < 0.2) continue;
                if (e.hurt(player.damageSources().playerAttack(player), damage)) {
                    healed += 1.0F;
                    server.sendParticles(ModParticles.VOID_SPARK.get(), e.getX(), e.getY(0.5), e.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
                }
            }
            player.heal(healed);
            Vec3 c = player.position().add(look.scale(2.0)).add(0, player.getBbHeight() * 0.55, 0);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 3, 1.0, 0.1, 1.0, 0);
            server.sendParticles(ModParticles.VOID_SPARK.get(), c.x, c.y, c.z, 12, 1.2, 0.2, 1.2, 0.02);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            ThrownScytheEntity thrown = new ThrownScytheEntity(level, player, stack.copy());
            level.addFreshEntity(thrown);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SCYTHE_THROW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(this, ThrownScytheEntity.MAX_FLIGHT + 10);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(net.starfallen.client.item.WeaponAnimations.SCYTHE);
    }
}
