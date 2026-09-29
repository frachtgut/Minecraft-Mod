package net.starfallen.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;
import net.starfallen.entity.*;
import net.starfallen.entity.boss.AstraeonEntity;
import net.starfallen.entity.projectile.*;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Starfallen.MODID);

    // ---- Creatures ----
    public static final RegistryObject<EntityType<StarWispEntity>> STAR_WISP = ENTITIES.register("star_wisp",
            () -> EntityType.Builder.of(StarWispEntity::new, MobCategory.AMBIENT).sized(0.5F, 0.55F).clientTrackingRange(8).build("star_wisp"));
    public static final RegistryObject<EntityType<MeteorGolemEntity>> METEOR_GOLEM = ENTITIES.register("meteor_golem",
            () -> EntityType.Builder.of(MeteorGolemEntity::new, MobCategory.MONSTER).sized(1.6F, 2.7F).fireImmune().clientTrackingRange(10).build("meteor_golem"));
    public static final RegistryObject<EntityType<VoidStalkerEntity>> VOID_STALKER = ENTITIES.register("void_stalker",
            () -> EntityType.Builder.of(VoidStalkerEntity::new, MobCategory.MONSTER).sized(0.7F, 2.9F).clientTrackingRange(10).build("void_stalker"));
    public static final RegistryObject<EntityType<NebulaJellyEntity>> NEBULA_JELLY = ENTITIES.register("nebula_jelly",
            () -> EntityType.Builder.of(NebulaJellyEntity::new, MobCategory.AMBIENT).sized(1.3F, 1.5F).clientTrackingRange(10).build("nebula_jelly"));
    public static final RegistryObject<EntityType<StarseerEntity>> STARSEER = ENTITIES.register("starseer",
            () -> EntityType.Builder.of(StarseerEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8).build("starseer"));
    public static final RegistryObject<EntityType<AstralMimicEntity>> ASTRAL_MIMIC = ENTITIES.register("astral_mimic",
            () -> EntityType.Builder.of(AstralMimicEntity::new, MobCategory.MONSTER).sized(0.95F, 0.95F).clientTrackingRange(8).build("astral_mimic"));
    public static final RegistryObject<EntityType<CometRayEntity>> COMET_RAY = ENTITIES.register("comet_ray",
            () -> EntityType.Builder.of(CometRayEntity::new, MobCategory.CREATURE).sized(2.4F, 0.7F).clientTrackingRange(10).build("comet_ray"));
    public static final RegistryObject<EntityType<AstraeonEntity>> ASTRAEON = ENTITIES.register("astraeon",
            () -> EntityType.Builder.of(AstraeonEntity::new, MobCategory.MONSTER).sized(3.2F, 4.6F).fireImmune().clientTrackingRange(12).build("astraeon"));

    // ---- Projectiles & effects ----
    public static final RegistryObject<EntityType<MeteorEntity>> METEOR = ENTITIES.register("meteor",
            () -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC).sized(1.5F, 1.5F).fireImmune()
                    .clientTrackingRange(16).updateInterval(1).build("meteor"));
    public static final RegistryObject<EntityType<StarBoltEntity>> STAR_BOLT = ENTITIES.register("star_bolt",
            () -> EntityType.Builder.<StarBoltEntity>of(StarBoltEntity::new, MobCategory.MISC).sized(0.4F, 0.4F).fireImmune()
                    .clientTrackingRange(8).updateInterval(2).build("star_bolt"));
    public static final RegistryObject<EntityType<StarArrowEntity>> STAR_ARROW = ENTITIES.register("star_arrow",
            () -> EntityType.Builder.<StarArrowEntity>of(StarArrowEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(2).build("star_arrow"));
    public static final RegistryObject<EntityType<SingularityEntity>> SINGULARITY = ENTITIES.register("singularity",
            () -> EntityType.Builder.<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build("singularity"));
    public static final RegistryObject<EntityType<ThrownScytheEntity>> THROWN_SCYTHE = ENTITIES.register("thrown_scythe",
            () -> EntityType.Builder.<ThrownScytheEntity>of(ThrownScytheEntity::new, MobCategory.MISC).sized(1.4F, 0.5F).fireImmune()
                    .clientTrackingRange(8).updateInterval(1).build("thrown_scythe"));
    public static final RegistryObject<EntityType<TetherHookEntity>> TETHER_HOOK = ENTITIES.register("tether_hook",
            () -> EntityType.Builder.<TetherHookEntity>of(TetherHookEntity::new, MobCategory.MISC).sized(0.35F, 0.35F)
                    .clientTrackingRange(8).updateInterval(1).build("tether_hook"));
    public static final RegistryObject<EntityType<MoltenRockEntity>> MOLTEN_ROCK = ENTITIES.register("molten_rock",
            () -> EntityType.Builder.<MoltenRockEntity>of(MoltenRockEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).fireImmune()
                    .clientTrackingRange(8).updateInterval(2).build("molten_rock"));
    public static final RegistryObject<EntityType<ShockwaveEntity>> SHOCKWAVE = ENTITIES.register("shockwave",
            () -> EntityType.Builder.<ShockwaveEntity>of(ShockwaveEntity::new, MobCategory.MISC).sized(0.5F, 0.25F).fireImmune()
                    .clientTrackingRange(8).updateInterval(20).build("shockwave"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(STAR_WISP.get(), StarWispEntity.createAttributes().build());
        event.put(METEOR_GOLEM.get(), MeteorGolemEntity.createAttributes().build());
        event.put(VOID_STALKER.get(), VoidStalkerEntity.createAttributes().build());
        event.put(NEBULA_JELLY.get(), NebulaJellyEntity.createAttributes().build());
        event.put(STARSEER.get(), StarseerEntity.createAttributes().build());
        event.put(ASTRAL_MIMIC.get(), AstralMimicEntity.createAttributes().build());
        event.put(COMET_RAY.get(), CometRayEntity.createAttributes().build());
        event.put(ASTRAEON.get(), AstraeonEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(STAR_WISP.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::nightSkySpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(NEBULA_JELLY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::nightSkySpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(VOID_STALKER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::stalkerSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(COMET_RAY.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEntities::nightSkySpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(STARSEER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(METEOR_GOLEM.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ASTRAL_MIMIC.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /** Natural spawns for sky creatures: open sky, at night. */
    private static boolean nightSkySpawn(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWNER || reason == MobSpawnType.STRUCTURE) return true;
        long time = level.getLevel().getDayTime() % 24000L;
        boolean night = time > 13000L && time < 23000L;
        return night && level.canSeeSky(pos) && level.getBrightness(LightLayer.BLOCK, pos) < 8 && random.nextInt(3) == 0;
    }

    private static boolean stalkerSpawn(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.SPAWNER || reason == MobSpawnType.STRUCTURE) return true;
        return level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(4) == 0
                && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    private ModEntities() {}
}
