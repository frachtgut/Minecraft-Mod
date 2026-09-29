package net.starfallen.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.starfallen.util.SFUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Plain item with rarity, optional enchantment glint and translated lore lines. */
public class LoreItem extends Item {
    private final boolean foil;

    public LoreItem(Properties props, Rarity rarity, boolean foil) {
        super(props.rarity(rarity));
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SFUtil.addTooltip(stack, tooltip);
    }
}
