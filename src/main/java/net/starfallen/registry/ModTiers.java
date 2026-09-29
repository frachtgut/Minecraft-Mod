package net.starfallen.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.starfallen.Starfallen;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/** Tool tiers and armor materials for the Starfallen progression. */
public final class ModTiers {
    public static final TagKey<Block> NEEDS_METEORIC_TOOL = BlockTags.create(Starfallen.id("needs_meteoric_tool"));
    public static final TagKey<Block> NEEDS_ASTRAL_TOOL = BlockTags.create(Starfallen.id("needs_astral_tool"));
    public static final TagKey<Block> NEEDS_VOIDSTEEL_TOOL = BlockTags.create(Starfallen.id("needs_voidsteel_tool"));
    public static final TagKey<Block> NEEDS_CELESTIAL_TOOL = BlockTags.create(Starfallen.id("needs_celestial_tool"));

    /** Forged from meteoric iron: a notch above iron. */
    public static final Tier METEORIC = TierSortingRegistry.registerTier(
            new ForgeTier(2, 900, 7.0F, 2.5F, 16, NEEDS_METEORIC_TOOL, () -> Ingredient.of(ModItems.METEORIC_IRON_INGOT.get())),
            Starfallen.id("meteoric"), List.of(Tiers.IRON), List.of(Tiers.DIAMOND));
    /** Astral alloy: between diamond and netherite. */
    public static final Tier ASTRAL = TierSortingRegistry.registerTier(
            new ForgeTier(3, 1800, 9.0F, 3.5F, 18, NEEDS_ASTRAL_TOOL, () -> Ingredient.of(ModItems.ASTRAL_ALLOY_INGOT.get())),
            Starfallen.id("astral"), List.of(Tiers.DIAMOND), List.of(Tiers.NETHERITE));
    /** Voidsteel: beyond netherite. */
    public static final Tier VOIDSTEEL = TierSortingRegistry.registerTier(
            new ForgeTier(4, 2600, 10.0F, 5.0F, 20, NEEDS_VOIDSTEEL_TOOL, () -> Ingredient.of(ModItems.VOIDSTEEL_INGOT.get())),
            Starfallen.id("voidsteel"), List.of(Tiers.NETHERITE), List.of());
    /** Celestial: boss-forged. */
    public static final Tier CELESTIAL = TierSortingRegistry.registerTier(
            new ForgeTier(5, 3400, 12.0F, 6.0F, 25, NEEDS_CELESTIAL_TOOL, () -> Ingredient.of(ModItems.STAR_FRAGMENT.get())),
            Starfallen.id("celestial"), List.of(Starfallen.id("voidsteel")), List.of());

    public static void init() {
        // Forces class loading so the tiers are registered with the sorting registry during mod construction.
    }

    public enum Armor implements ArmorMaterial {
        METEORIC("meteoric", 25, 2, 6, 5, 2, 12, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F, () -> Ingredient.of(ModItems.METEORIC_IRON_INGOT.get())),
        STARFORGED("starforged", 35, 3, 8, 6, 3, 18, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.5F, 0.05F, () -> Ingredient.of(ModItems.ASTRAL_ALLOY_INGOT.get())),
        VOIDWALKER("voidwalker", 40, 3, 8, 6, 3, 20, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.1F, () -> Ingredient.of(ModItems.VOIDSTEEL_INGOT.get())),
        CELESTIAL("celestial", 45, 4, 9, 7, 4, 25, SoundEvents.ARMOR_EQUIP_GOLD, 4.0F, 0.15F, () -> Ingredient.of(ModItems.STAR_FRAGMENT.get()));

        private static final EnumMap<ArmorItem.Type, Integer> HEALTH = new EnumMap<>(ArmorItem.Type.class);
        static {
            HEALTH.put(ArmorItem.Type.BOOTS, 13);
            HEALTH.put(ArmorItem.Type.LEGGINGS, 15);
            HEALTH.put(ArmorItem.Type.CHESTPLATE, 16);
            HEALTH.put(ArmorItem.Type.HELMET, 11);
        }

        private final String name;
        private final int durabilityMultiplier;
        private final EnumMap<ArmorItem.Type, Integer> protection = new EnumMap<>(ArmorItem.Type.class);
        private final int enchantability;
        private final SoundEvent sound;
        private final float toughness;
        private final float knockbackResistance;
        private final Supplier<Ingredient> repair;

        Armor(String name, int durabilityMultiplier, int helmet, int chest, int legs, int boots, int enchantability,
              SoundEvent sound, float toughness, float knockbackResistance, Supplier<Ingredient> repair) {
            this.name = name;
            this.durabilityMultiplier = durabilityMultiplier;
            this.protection.put(ArmorItem.Type.HELMET, helmet);
            this.protection.put(ArmorItem.Type.CHESTPLATE, chest);
            this.protection.put(ArmorItem.Type.LEGGINGS, legs);
            this.protection.put(ArmorItem.Type.BOOTS, boots);
            this.enchantability = enchantability;
            this.sound = sound;
            this.toughness = toughness;
            this.knockbackResistance = knockbackResistance;
            this.repair = repair;
        }

        @Override public int getDurabilityForType(ArmorItem.Type type) { return HEALTH.get(type) * durabilityMultiplier; }
        @Override public int getDefenseForType(ArmorItem.Type type) { return protection.get(type); }
        @Override public int getEnchantmentValue() { return enchantability; }
        @Override public SoundEvent getEquipSound() { return sound; }
        @Override public Ingredient getRepairIngredient() { return repair.get(); }
        @Override public String getName() { return ResourceLocation.fromNamespaceAndPath(Starfallen.MODID, name).toString(); }
        @Override public float getToughness() { return toughness; }
        @Override public float getKnockbackResistance() { return knockbackResistance; }
    }

    private ModTiers() {}
}
