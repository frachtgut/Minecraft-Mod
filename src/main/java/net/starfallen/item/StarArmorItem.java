package net.starfallen.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.starfallen.registry.ModTiers;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Armor pieces that describe the set bonus of their material in the tooltip. */
public class StarArmorItem extends ArmorItem {
    public StarArmorItem(ArmorMaterial material, Type type, Properties props) {
        super(material, type, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (material instanceof ModTiers.Armor armor) {
            String key = armor.name().toLowerCase(java.util.Locale.ROOT);
            tooltip.add(Component.translatable("tooltip.starfallen.set_bonus", Component.translatable("tooltip.starfallen.set." + key))
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.starfallen.set." + key + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
