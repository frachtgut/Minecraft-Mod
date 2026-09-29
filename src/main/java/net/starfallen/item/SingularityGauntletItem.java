package net.starfallen.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.starfallen.entity.projectile.SingularityEntity;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Singularity Gauntlet. Hurls a seed of collapsed starlight that blooms into a black hole,
 * dragging enemies (and loot) into its heart before imploding in a violent burst.
 */
public class SingularityGauntletItem extends Item {
    public SingularityGauntletItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            SingularityEntity s = new SingularityEntity(level, player, player.getEyePosition().add(look.scale(1.2)), look.scale(0.75), false);
            level.addFreshEntity(s);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SINGULARITY_HUM.get(), SoundSource.PLAYERS, 1.0F, 1.4F);
            player.getCooldowns().addCooldown(this, 200);
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
        consumer.accept(net.starfallen.client.item.WeaponAnimations.GAUNTLET);
    }
}
