package net.starfallen.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.starfallen.registry.ModTiers;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Halo of the Fallen Star - crafted from Astraeon's heart. A crown of living starlight that
 * floats above its wearer: grants flight, starsight, and reveals every hostile creature nearby.
 */
public class HaloItem extends ArmorItem {
    public HaloItem(Properties props) {
        super(ModTiers.Armor.CELESTIAL, Type.HELMET, props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }
}
