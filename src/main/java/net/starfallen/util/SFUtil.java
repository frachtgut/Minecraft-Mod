package net.starfallen.util;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.starfallen.Starfallen;

import java.util.List;

public final class SFUtil {
    private SFUtil() {}

    /** Grants every remaining criterion of a code-driven ("impossible" trigger) advancement. */
    public static void award(ServerPlayer player, String name) {
        Advancement adv = player.server.getAdvancements().getAdvancement(Starfallen.id(name));
        if (adv == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    /**
     * Appends tooltip lines "tooltip.starfallen.&lt;item&gt;.0..n" that exist in the language file.
     * Lines starting with '!' are rendered as ability headers.
     */
    public static void addTooltip(ItemStack stack, List<Component> tooltip) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return;
        Language lang = Language.getInstance();
        for (int i = 0; i < 12; i++) {
            String k = "tooltip." + key.getNamespace() + "." + key.getPath() + "." + i;
            if (!lang.has(k)) break;
            String raw = lang.getOrDefault(k);
            if (raw.startsWith("!")) {
                tooltip.add(Component.literal(raw.substring(1)).withStyle(ChatFormatting.GOLD));
            } else if (raw.startsWith("~")) {
                tooltip.add(Component.literal(raw.substring(1)).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            } else {
                tooltip.add(Component.literal(raw).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** True if the entity is a sensible target for area abilities used by {@code user}. */
    public static boolean isValidTarget(Entity user, Entity e) {
        if (e == user || !(e instanceof LivingEntity living) || !living.isAlive()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (e instanceof TamableAnimal t && t.isTame() && user instanceof LivingEntity u && t.isOwnedBy(u)) return false;
        if (user instanceof TamableAnimal ut && ut.isTame()) {
            LivingEntity owner = ut.getOwner();
            if (e == owner) return false;
            if (owner != null && e instanceof TamableAnimal t2 && t2.isTame() && t2.isOwnedBy(owner)) return false;
            if (owner instanceof Player op && e instanceof Player ep && !op.canHarmPlayer(ep)) return false;
        }
        if (e.isAlliedTo(user)) return false;
        if (user instanceof Player && e instanceof Player && !((Player) user).canHarmPlayer((Player) e)) return false;
        return !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }
}
