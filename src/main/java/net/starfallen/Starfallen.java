package net.starfallen;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.starfallen.config.SFConfig;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.*;
import org.slf4j.Logger;

/**
 * Starfallen - the sky is falling, and something came with it.
 */
@Mod(Starfallen.MODID)
public class Starfallen {
    public static final String MODID = "starfallen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Starfallen(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModTiers.init();
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModWorldgen.FEATURES.register(modBus);
        ModWorldgen.STRUCTURE_TYPES.register(modBus);
        ModWorldgen.STRUCTURE_PIECES.register(modBus);
        ModLootModifiers.SERIALIZERS.register(modBus);

        context.registerConfig(ModConfig.Type.COMMON, SFConfig.SPEC, "starfallen-common.toml");

        SFNetwork.register();
        modBus.addListener(this::commonSetup);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModEntities::registerSpawnPlacements);

        MinecraftForge.EVENT_BUS.register(net.starfallen.event.CommonEvents.class);
        MinecraftForge.EVENT_BUS.register(net.starfallen.event.StarfallManager.class);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.starfallen.client.ClientSetup.init(modBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModItems::registerDispenserAndCompostables);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
