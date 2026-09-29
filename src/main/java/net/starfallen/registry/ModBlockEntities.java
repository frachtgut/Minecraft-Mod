package net.starfallen.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;
import net.starfallen.blockentity.*;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Starfallen.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FlameJetBlockEntity>> FLAME_JET = BLOCK_ENTITIES.register("flame_jet",
            () -> BlockEntityType.Builder.of(FlameJetBlockEntity::new, ModBlocks.FLAME_JET.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SentinelEyeBlockEntity>> SENTINEL_EYE = BLOCK_ENTITIES.register("sentinel_eye",
            () -> BlockEntityType.Builder.of(SentinelEyeBlockEntity::new, ModBlocks.SENTINEL_EYE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SealKeystoneBlockEntity>> SEAL_KEYSTONE = BLOCK_ENTITIES.register("seal_keystone",
            () -> BlockEntityType.Builder.of(SealKeystoneBlockEntity::new, ModBlocks.SEAL_KEYSTONE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AstralTelescopeBlockEntity>> ASTRAL_TELESCOPE = BLOCK_ENTITIES.register("astral_telescope",
            () -> BlockEntityType.Builder.of(AstralTelescopeBlockEntity::new, ModBlocks.ASTRAL_TELESCOPE.get()).build(null));

    private ModBlockEntities() {}
}
