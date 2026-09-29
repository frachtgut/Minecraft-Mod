package net.starfallen.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;
import net.starfallen.worldgen.CraterFeature;
import net.starfallen.worldgen.structure.BlueprintPiece;
import net.starfallen.worldgen.structure.ObservatoryStructure;
import net.starfallen.worldgen.structure.SanctumStructure;

public final class ModWorldgen {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Starfallen.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Starfallen.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Starfallen.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CRATER = FEATURES.register("crater",
            () -> new CraterFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<StructureType<ObservatoryStructure>> OBSERVATORY = STRUCTURE_TYPES.register("astral_observatory",
            () -> () -> ObservatoryStructure.CODEC);
    public static final RegistryObject<StructureType<SanctumStructure>> SANCTUM = STRUCTURE_TYPES.register("sanctum",
            () -> () -> SanctumStructure.CODEC);

    public static final RegistryObject<StructurePieceType> BLUEPRINT_PIECE = STRUCTURE_PIECES.register("blueprint",
            () -> (StructurePieceType.ContextlessType) BlueprintPiece::new);

    /** Structures the Grand Telescope can chart. */
    public static final TagKey<Structure> ON_SANCTUM_MAPS = TagKey.create(Registries.STRUCTURE, Starfallen.id("on_sanctum_maps"));

    private ModWorldgen() {}
}
