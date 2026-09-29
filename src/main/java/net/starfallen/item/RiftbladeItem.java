package net.starfallen.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.Effects;
import net.starfallen.util.SFUtil;
import net.starfallen.util.Targeting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Riftblade. Right-click a foe to tear through space and strike it from behind for heavy
 * damage. A killing blow resets the cooldown - chain rifts from enemy to enemy.
 */
public class RiftbladeItem extends SwordItem {
    public RiftbladeItem(Tier tier, int damage, float speed, Properties props) {
        super(tier, damage, speed, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        LivingEntity target = Targeting.findLookTarget(player, 20.0, 9.0);
        if (target == null) {
            if (level.isClientSide) player.playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.3F, 0.5F);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        ServerLevel server = (ServerLevel) level;
        ServerPlayer sp = (ServerPlayer) player;

        Vec3 dest = findBehind(server, sp, target);
        if (dest == null) return InteractionResultHolder.fail(stack);
        Vec3 from = sp.position();
        Vec3 toTarget = target.position().subtract(dest);
        float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180F / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(target.getEyeY() - (dest.y + sp.getEyeHeight()), toTarget.horizontalDistance()) * (180F / Math.PI));

        server.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 50, 0.3, 0.7, 0.3, 0.25);
        server.sendParticles(ModParticles.VOID_SPARK.get(), from.x, from.y + 1, from.z, 20, 0.3, 0.7, 0.3, 0.1);
        Effects.line(server, from.add(0, 1, 0), dest.add(0, 1, 0), ModParticles.VOID_SPARK.get(), 0.8);
        sp.teleportTo(server, dest.x, dest.y, dest.z, yaw, pitch);
        sp.fallDistance = 0;
        server.playSound(null, dest.x, dest.y, dest.z, ModSounds.RIFT_BLINK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        float base = (float) sp.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float bonus = EnchantmentHelper.getDamageBonus(stack, target.getMobType());
        float damage = base * 1.8F + bonus;
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(sp.damageSources().playerAttack(sp), damage);
        if (hurt) {
            sp.crit(target);
            sp.magicCrit(target);
            Vec3 push = target.position().subtract(dest).multiply(1, 0, 1).normalize().scale(0.6);
            target.push(push.x, 0.25, push.z);
            int fire = EnchantmentHelper.getFireAspect(sp);
            if (fire > 0) target.setSecondsOnFire(fire * 4);
            server.sendParticles(ModParticles.VOID_SHOCKWAVE.get(), target.getX(), target.getY() + 0.1, target.getZ(), 1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
        }
        stack.hurtAndBreak(1, sp, p -> p.broadcastBreakEvent(hand));
        sp.resetAttackStrengthTicker();
        if (!target.isAlive()) {
            // Killing blow: the rift stays open.
            sp.getCooldowns().removeCooldown(this);
            sp.getCooldowns().addCooldown(this, 6);
            sp.displayClientMessage(Component.translatable("message.starfallen.rift_chain").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            server.playSound(null, dest.x, dest.y, dest.z, ModSounds.STAR_CHIME.get(), SoundSource.PLAYERS, 1.0F, 1.6F);
        } else {
            sp.getCooldowns().addCooldown(this, 45);
        }
        return InteractionResultHolder.success(stack);
    }

    @Nullable
    private static Vec3 findBehind(ServerLevel level, ServerPlayer player, LivingEntity target) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        double back = target.getBbWidth() / 2 + 0.9;
        Vec3[] candidates = {
                target.position().subtract(facing.scale(back)),
                target.position().add(facing.yRot((float) Math.PI / 2).scale(back)),
                target.position().add(facing.yRot((float) -Math.PI / 2).scale(back)),
                target.position().add(target.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(back)),
                target.position().add(player.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(back))
        };
        for (Vec3 c : candidates) {
            for (double dy : new double[]{0, 1, -1}) {
                Vec3 p = c.add(0, dy, 0);
                AABB box = player.getDimensions(player.getPose()).makeBoundingBox(p);
                if (level.noCollision(player, box)) return p;
            }
        }
        return null;
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
