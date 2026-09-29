package net.starfallen.registry;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;
import net.starfallen.item.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Starfallen.MODID);
    /** Creative tab ordering. */
    public static final List<RegistryObject<? extends Item>> TAB_ORDER = new ArrayList<>();

    // ---- Materials ----
    public static final RegistryObject<Item> RAW_METEORIC_IRON = simple("raw_meteoric_iron", Rarity.COMMON);
    public static final RegistryObject<Item> METEORIC_IRON_INGOT = simple("meteoric_iron_ingot", Rarity.COMMON);
    public static final RegistryObject<Item> METEORIC_IRON_NUGGET = simple("meteoric_iron_nugget", Rarity.COMMON);
    public static final RegistryObject<Item> STARDUST = reg("stardust", () -> new LoreItem(new Item.Properties(), Rarity.UNCOMMON, false));
    public static final RegistryObject<Item> STAR_FRAGMENT = reg("star_fragment", () -> new LoreItem(new Item.Properties(), Rarity.RARE, false));
    public static final RegistryObject<Item> ASTRAL_ALLOY_INGOT = reg("astral_alloy_ingot", () -> new LoreItem(new Item.Properties(), Rarity.UNCOMMON, false));
    public static final RegistryObject<Item> MOLTEN_CORE = reg("molten_core", () -> new LoreItem(new Item.Properties().fireResistant(), Rarity.UNCOMMON, false));
    public static final RegistryObject<Item> NEBULA_GEL = reg("nebula_gel", () -> new LoreItem(new Item.Properties(), Rarity.UNCOMMON, false));
    public static final RegistryObject<Item> VOID_ESSENCE = reg("void_essence", () -> new LoreItem(new Item.Properties(), Rarity.UNCOMMON, false));
    public static final RegistryObject<Item> ECLIPSE_SHARD = reg("eclipse_shard", () -> new LoreItem(new Item.Properties(), Rarity.RARE, false));
    public static final RegistryObject<Item> VOIDSTEEL_INGOT = reg("voidsteel_ingot", () -> new LoreItem(new Item.Properties().fireResistant(), Rarity.RARE, false));
    public static final RegistryObject<Item> HEART_OF_ASTRAEON = reg("heart_of_astraeon", () -> new LoreItem(new Item.Properties().fireResistant().stacksTo(16), Rarity.EPIC, true));

    // ---- Key items & gadgets ----
    public static final RegistryObject<Item> SIGIL_OF_THE_FALLEN_STAR = reg("sigil_of_the_fallen_star", () -> new LoreItem(new Item.Properties().stacksTo(16).fireResistant(), Rarity.EPIC, true));
    public static final RegistryObject<Item> STARFALL_BEACON = reg("starfall_beacon", () -> new StarfallBeaconItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TOTEM_OF_THE_FALLEN_STAR = reg("totem_of_the_fallen_star", () -> new LoreItem(new Item.Properties().stacksTo(1), Rarity.EPIC, false));
    public static final RegistryObject<Item> STAR_TETHER = reg("star_tether", () -> new StarTetherItem(new Item.Properties().durability(512).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> STARSEER_JOURNAL = reg("starseer_journal", () -> new StarseerJournalItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---- Meteoric tools ----
    public static final RegistryObject<Item> METEORIC_SWORD = reg("meteoric_sword", () -> new MeteoricSwordItem(ModTiers.METEORIC, 3, -2.4F, new Item.Properties()));
    public static final RegistryObject<Item> METEORIC_PICKAXE = reg("meteoric_pickaxe", () -> new PickaxeItem(ModTiers.METEORIC, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> METEORIC_AXE = reg("meteoric_axe", () -> new AxeItem(ModTiers.METEORIC, 5.5F, -3.1F, new Item.Properties()));
    public static final RegistryObject<Item> METEORIC_SHOVEL = reg("meteoric_shovel", () -> new ShovelItem(ModTiers.METEORIC, 1.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> METEORIC_HOE = reg("meteoric_hoe", () -> new HoeItem(ModTiers.METEORIC, -2, -1.0F, new Item.Properties()));
    public static final RegistryObject<Item> STARBREAKER = reg("starbreaker", () -> new StarbreakerItem(ModTiers.ASTRAL, 1, -2.9F, new Item.Properties().rarity(Rarity.RARE)));

    // ---- Signature weapons ----
    public static final RegistryObject<Item> METEOR_HAMMER = reg("meteor_hammer", () -> new MeteorHammerItem(ModTiers.METEORIC, 6, -3.3F, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> NEBULA_BLADE = reg("nebula_blade", () -> new NebulaBladeItem(ModTiers.ASTRAL, 3, -2.2F, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CONSTELLATION_BOW = reg("constellation_bow", () -> new ConstellationBowItem(new Item.Properties().durability(900).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> RIFTBLADE = reg("riftblade", () -> new RiftbladeItem(ModTiers.VOIDSTEEL, 3, -2.0F, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> SINGULARITY_GAUNTLET = reg("singularity_gauntlet", () -> new SingularityGauntletItem(new Item.Properties().durability(400).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> STARCALLER_STAFF = reg("starcaller_staff", () -> new StarcallerStaffItem(new Item.Properties().durability(350).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> ECLIPSE_SCYTHE = reg("eclipse_scythe", () -> new EclipseScytheItem(ModTiers.CELESTIAL, 6, -2.8F, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    // ---- Armor ----
    public static final RegistryObject<Item> METEORIC_HELMET = armor("meteoric_helmet", ModTiers.Armor.METEORIC, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> METEORIC_CHESTPLATE = armor("meteoric_chestplate", ModTiers.Armor.METEORIC, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> METEORIC_LEGGINGS = armor("meteoric_leggings", ModTiers.Armor.METEORIC, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> METEORIC_BOOTS = armor("meteoric_boots", ModTiers.Armor.METEORIC, ArmorItem.Type.BOOTS);
    public static final RegistryObject<Item> STARFORGED_HELMET = armor("starforged_helmet", ModTiers.Armor.STARFORGED, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> STARFORGED_CHESTPLATE = armor("starforged_chestplate", ModTiers.Armor.STARFORGED, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> STARFORGED_LEGGINGS = armor("starforged_leggings", ModTiers.Armor.STARFORGED, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> STARFORGED_BOOTS = armor("starforged_boots", ModTiers.Armor.STARFORGED, ArmorItem.Type.BOOTS);
    public static final RegistryObject<Item> VOIDWALKER_HELMET = armor("voidwalker_helmet", ModTiers.Armor.VOIDWALKER, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> VOIDWALKER_CHESTPLATE = armor("voidwalker_chestplate", ModTiers.Armor.VOIDWALKER, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> VOIDWALKER_LEGGINGS = armor("voidwalker_leggings", ModTiers.Armor.VOIDWALKER, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> VOIDWALKER_BOOTS = armor("voidwalker_boots", ModTiers.Armor.VOIDWALKER, ArmorItem.Type.BOOTS);
    public static final RegistryObject<Item> HALO_OF_THE_FALLEN_STAR = reg("halo_of_the_fallen_star", () -> new HaloItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    // ---- Block items ----
    public static final RegistryObject<Item> METEORITE = block(ModBlocks.METEORITE);
    public static final RegistryObject<Item> MOLTEN_METEORITE = block(ModBlocks.MOLTEN_METEORITE);
    public static final RegistryObject<Item> METEORIC_IRON_ORE = block(ModBlocks.METEORIC_IRON_ORE);
    public static final RegistryObject<Item> METEORIC_IRON_BLOCK = block(ModBlocks.METEORIC_IRON_BLOCK);
    public static final RegistryObject<Item> STARLIT_CRYSTAL = block(ModBlocks.STARLIT_CRYSTAL);
    public static final RegistryObject<Item> FALLEN_STAR = reg("fallen_star", () -> new BlockItem(ModBlocks.FALLEN_STAR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ASTRAL_ALLOY_BLOCK = block(ModBlocks.ASTRAL_ALLOY_BLOCK);
    public static final RegistryObject<Item> VOIDSTEEL_BLOCK = reg("voidsteel_block", () -> new BlockItem(ModBlocks.VOIDSTEEL_BLOCK.get(), new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> STELLAR_EGG = reg("stellar_egg", () -> new BlockItem(ModBlocks.STELLAR_EGG.get(), new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)));
    public static final RegistryObject<Item> ASTRAL_BRICKS = block(ModBlocks.ASTRAL_BRICKS);
    public static final RegistryObject<Item> CRACKED_ASTRAL_BRICKS = block(ModBlocks.CRACKED_ASTRAL_BRICKS);
    public static final RegistryObject<Item> CHISELED_ASTRAL_BRICKS = block(ModBlocks.CHISELED_ASTRAL_BRICKS);
    public static final RegistryObject<Item> ASTRAL_BRICK_STAIRS = block(ModBlocks.ASTRAL_BRICK_STAIRS);
    public static final RegistryObject<Item> ASTRAL_BRICK_SLAB = block(ModBlocks.ASTRAL_BRICK_SLAB);
    public static final RegistryObject<Item> ASTRAL_BRICK_WALL = block(ModBlocks.ASTRAL_BRICK_WALL);
    public static final RegistryObject<Item> ASTRAL_PILLAR = block(ModBlocks.ASTRAL_PILLAR);
    public static final RegistryObject<Item> VOID_BRICKS = block(ModBlocks.VOID_BRICKS);
    public static final RegistryObject<Item> CRACKED_VOID_BRICKS = block(ModBlocks.CRACKED_VOID_BRICKS);
    public static final RegistryObject<Item> CHISELED_VOID_BRICKS = block(ModBlocks.CHISELED_VOID_BRICKS);
    public static final RegistryObject<Item> VOID_BRICK_STAIRS = block(ModBlocks.VOID_BRICK_STAIRS);
    public static final RegistryObject<Item> VOID_BRICK_SLAB = block(ModBlocks.VOID_BRICK_SLAB);
    public static final RegistryObject<Item> VOID_BRICK_WALL = block(ModBlocks.VOID_BRICK_WALL);
    public static final RegistryObject<Item> VOID_PILLAR = block(ModBlocks.VOID_PILLAR);
    public static final RegistryObject<Item> STARFIELD_TILES = block(ModBlocks.STARFIELD_TILES);
    public static final RegistryObject<Item> STARLIGHT_LAMP = block(ModBlocks.STARLIGHT_LAMP);
    public static final RegistryObject<Item> ASTRAL_BRAZIER = block(ModBlocks.ASTRAL_BRAZIER);
    public static final RegistryObject<Item> SPIKE_TRAP = block(ModBlocks.SPIKE_TRAP);
    public static final RegistryObject<Item> FLAME_JET = block(ModBlocks.FLAME_JET);
    public static final RegistryObject<Item> SENTINEL_EYE = block(ModBlocks.SENTINEL_EYE);
    public static final RegistryObject<Item> CRUMBLING_VOID_BRICKS = block(ModBlocks.CRUMBLING_VOID_BRICKS);
    public static final RegistryObject<Item> ASTRAL_TELESCOPE = block(ModBlocks.ASTRAL_TELESCOPE);
    public static final RegistryObject<Item> STAR_ALTAR = reg("star_altar", () -> new BlockItem(ModBlocks.STAR_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SEAL_KEYSTONE = block(ModBlocks.SEAL_KEYSTONE);
    public static final RegistryObject<Item> SANCTUM_SEAL = block(ModBlocks.SANCTUM_SEAL);

    // ---- Spawn eggs ----
    public static final RegistryObject<Item> STAR_WISP_SPAWN_EGG = egg("star_wisp_spawn_egg", ModEntities.STAR_WISP, 0xFFE38A, 0x6EF3FF);
    public static final RegistryObject<Item> METEOR_GOLEM_SPAWN_EGG = egg("meteor_golem_spawn_egg", ModEntities.METEOR_GOLEM, 0x2B211E, 0xFF6A1A);
    public static final RegistryObject<Item> VOID_STALKER_SPAWN_EGG = egg("void_stalker_spawn_egg", ModEntities.VOID_STALKER, 0x0B0620, 0xC04DFF);
    public static final RegistryObject<Item> NEBULA_JELLY_SPAWN_EGG = egg("nebula_jelly_spawn_egg", ModEntities.NEBULA_JELLY, 0x7A4DD6, 0xFF8AD8);
    public static final RegistryObject<Item> STARSEER_SPAWN_EGG = egg("starseer_spawn_egg", ModEntities.STARSEER, 0x1A0F3D, 0xFFC94A);
    public static final RegistryObject<Item> ASTRAL_MIMIC_SPAWN_EGG = egg("astral_mimic_spawn_egg", ModEntities.ASTRAL_MIMIC, 0x4B2C9E, 0xFFD34A);
    public static final RegistryObject<Item> COMET_RAY_SPAWN_EGG = egg("comet_ray_spawn_egg", ModEntities.COMET_RAY, 0x1B2F6E, 0x9FF7FF);
    public static final RegistryObject<Item> ASTRAEON_SPAWN_EGG = egg("astraeon_spawn_egg", ModEntities.ASTRAEON, 0x05030F, 0xFFF6D5);

    private static <T extends Item> RegistryObject<T> reg(String name, Supplier<T> sup) {
        RegistryObject<T> obj = ITEMS.register(name, sup);
        TAB_ORDER.add(obj);
        return obj;
    }

    private static RegistryObject<Item> simple(String name, Rarity rarity) {
        return reg(name, () -> new Item(new Item.Properties().rarity(rarity)));
    }

    private static RegistryObject<Item> armor(String name, ModTiers.Armor material, ArmorItem.Type type) {
        return reg(name, () -> new StarArmorItem(material, type, new Item.Properties()));
    }

    private static RegistryObject<Item> block(RegistryObject<? extends Block> block) {
        return reg(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<Item> egg(String name, Supplier<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> type, int bg, int fg) {
        return reg(name, () -> new ForgeSpawnEggItem(type, bg, fg, new Item.Properties()));
    }

    public static void registerDispenserAndCompostables() {
        ComposterBlock.COMPOSTABLES.put(NEBULA_GEL.get(), 0.5F);
    }

    private ModItems() {}
}
