package net.starfallen.registry;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;
import net.starfallen.block.*;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Starfallen.MODID);

    // ---- Meteorite materials ----
    public static final RegistryObject<Block> METEORITE = BLOCKS.register("meteorite", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.5F, 8.0F).requiresCorrectToolForDrops().sound(SoundType.BASALT)));
    public static final RegistryObject<Block> MOLTEN_METEORITE = BLOCKS.register("molten_meteorite", () -> new MoltenMeteoriteBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.5F, 8.0F).requiresCorrectToolForDrops().sound(SoundType.BASALT)
                    .lightLevel(s -> 12).emissiveRendering((s, l, p) -> true).isValidSpawn((s, l, p, e) -> e.fireImmune())));
    public static final RegistryObject<Block> METEORIC_IRON_ORE = BLOCKS.register("meteoric_iron_ore", () -> new DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(4.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.BASALT).lightLevel(s -> 3), UniformInt.of(2, 5)));
    public static final RegistryObject<Block> METEORIC_IRON_BLOCK = BLOCKS.register("meteoric_iron_block", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .strength(5.0F, 7.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final RegistryObject<Block> STARLIT_CRYSTAL = BLOCKS.register("starlit_crystal", () -> new StarlitCrystalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion()
                    .strength(1.5F).sound(ModSounds.STARLIT_CRYSTAL_SOUND).lightLevel(s -> 9).pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> FALLEN_STAR = BLOCKS.register("fallen_star", () -> new FallenStarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(4.0F, 12.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 15).emissiveRendering((s, l, p) -> true).noOcclusion()));
    public static final RegistryObject<Block> ASTRAL_ALLOY_BLOCK = BLOCKS.register("astral_alloy_block", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.CHIME)
                    .strength(6.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(s -> 6)));
    public static final RegistryObject<Block> VOIDSTEEL_BLOCK = BLOCKS.register("voidsteel_block", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASS)
                    .strength(8.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)));

    // ---- Astral bricks (Observatory) ----
    public static final RegistryObject<Block> ASTRAL_BRICKS = BLOCKS.register("astral_bricks", () -> new Block(astralBrick()));
    public static final RegistryObject<Block> CRACKED_ASTRAL_BRICKS = BLOCKS.register("cracked_astral_bricks", () -> new Block(astralBrick()));
    public static final RegistryObject<Block> CHISELED_ASTRAL_BRICKS = BLOCKS.register("chiseled_astral_bricks", () -> new Block(astralBrick().lightLevel(s -> 4)));
    public static final RegistryObject<Block> ASTRAL_BRICK_STAIRS = BLOCKS.register("astral_brick_stairs", () -> new StairBlock(() -> ModBlocks.ASTRAL_BRICKS.get().defaultBlockState(), astralBrick()));
    public static final RegistryObject<Block> ASTRAL_BRICK_SLAB = BLOCKS.register("astral_brick_slab", () -> new SlabBlock(astralBrick()));
    public static final RegistryObject<Block> ASTRAL_BRICK_WALL = BLOCKS.register("astral_brick_wall", () -> new WallBlock(astralBrick().forceSolidOn()));
    public static final RegistryObject<Block> ASTRAL_PILLAR = BLOCKS.register("astral_pillar", () -> new RotatedPillarBlock(astralBrick()));

    // ---- Void bricks (Sanctum) ----
    public static final RegistryObject<Block> VOID_BRICKS = BLOCKS.register("void_bricks", () -> new Block(voidBrick()));
    public static final RegistryObject<Block> CRACKED_VOID_BRICKS = BLOCKS.register("cracked_void_bricks", () -> new Block(voidBrick()));
    public static final RegistryObject<Block> CHISELED_VOID_BRICKS = BLOCKS.register("chiseled_void_bricks", () -> new Block(voidBrick().lightLevel(s -> 5)));
    public static final RegistryObject<Block> VOID_BRICK_STAIRS = BLOCKS.register("void_brick_stairs", () -> new StairBlock(() -> ModBlocks.VOID_BRICKS.get().defaultBlockState(), voidBrick()));
    public static final RegistryObject<Block> VOID_BRICK_SLAB = BLOCKS.register("void_brick_slab", () -> new SlabBlock(voidBrick()));
    public static final RegistryObject<Block> VOID_BRICK_WALL = BLOCKS.register("void_brick_wall", () -> new WallBlock(voidBrick().forceSolidOn()));
    public static final RegistryObject<Block> VOID_PILLAR = BLOCKS.register("void_pillar", () -> new RotatedPillarBlock(voidBrick()));
    public static final RegistryObject<Block> STARFIELD_TILES = BLOCKS.register("starfield_tiles", () -> new Block(voidBrick().lightLevel(s -> 3)));
    public static final RegistryObject<Block> STARLIGHT_LAMP = BLOCKS.register("starlight_lamp", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8F).sound(SoundType.GLASS)
                    .lightLevel(s -> 15).isValidSpawn((s, l, p, e) -> false)));

    // ---- Traps & dungeon mechanisms ----
    public static final RegistryObject<Block> SPIKE_TRAP = BLOCKS.register("spike_trap", () -> new SpikeTrapBlock(voidBrick()));
    public static final RegistryObject<Block> FLAME_JET = BLOCKS.register("flame_jet", () -> new FlameJetBlock(voidBrick().lightLevel(s -> 3)));
    public static final RegistryObject<Block> SENTINEL_EYE = BLOCKS.register("sentinel_eye", () -> new SentinelEyeBlock(
            voidBrick().noOcclusion().lightLevel(s -> 7)));
    public static final RegistryObject<Block> CRUMBLING_VOID_BRICKS = BLOCKS.register("crumbling_void_bricks", () -> new CrumblingBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F).sound(SoundType.DEEPSLATE_BRICKS)));
    public static final RegistryObject<Block> ASTRAL_BRAZIER = BLOCKS.register("astral_brazier", () -> new AstralBrazierBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> s.getValue(AstralBrazierBlock.LIT) ? 15 : 2)));
    public static final RegistryObject<Block> SANCTUM_SEAL = BLOCKS.register("sanctum_seal", () -> new SanctumSealBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noLootTable()
                    .sound(SoundType.GLASS).lightLevel(s -> 10).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> SEAL_KEYSTONE = BLOCKS.register("seal_keystone", () -> new SealKeystoneBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable()
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 10).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> STAR_ALTAR = BLOCKS.register("star_altar", () -> new StarAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 12).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> ASTRAL_TELESCOPE = BLOCKS.register("astral_telescope", () -> new AstralTelescopeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<Block> STELLAR_EGG = BLOCKS.register("stellar_egg", () -> new StellarEggBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6F).sound(SoundType.METAL)
                    .lightLevel(s -> 8).noOcclusion().randomTicks()));

    private static BlockBehaviour.Properties astralBrick() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties voidBrick() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3.0F, 8.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS);
    }

    @SuppressWarnings("unused")
    private static <T extends Block> RegistryObject<T> reg(String name, Supplier<T> sup) {
        return BLOCKS.register(name, sup);
    }

    private ModBlocks() {}
}
