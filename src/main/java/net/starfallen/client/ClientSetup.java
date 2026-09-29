package net.starfallen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.starfallen.client.fx.ClientFx;
import net.starfallen.client.model.*;
import net.starfallen.client.particle.SFParticles;
import net.starfallen.client.render.*;
import net.starfallen.network.Packets;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.*;
import net.starfallen.util.ClientInput;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    public static final KeyMapping BLINK_KEY = new KeyMapping("key.starfallen.blink", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.starfallen");

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::clientSetup);
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerLayers);
        modBus.addListener(ClientSetup::addLayers);
        modBus.addListener(ClientSetup::registerParticles);
        modBus.addListener(ClientSetup::registerKeys);
        modBus.addListener(ClientSetup::registerOverlays);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.CONSTELLATION_BOW.get(), ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                    entity == null || entity.getUseItem() != stack ? 0.0F : (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0F);
            ItemProperties.register(ModItems.CONSTELLATION_BOW.get(), ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.METEOR_HAMMER.get(), ResourceLocation.withDefaultNamespace("charging"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.ECLIPSE_SCYTHE.get(), ResourceLocation.withDefaultNamespace("thrown"), (stack, level, entity, seed) ->
                    entity instanceof net.minecraft.world.entity.player.Player p && p.getCooldowns().isOnCooldown(stack.getItem()) ? 1.0F : 0.0F);
        });
        ClientInput.JUMP = () -> Minecraft.getInstance().options.keyJump.isDown();
        ClientInput.SPRINT = () -> Minecraft.getInstance().options.keySprint.isDown();
        ClientInput.RAY_BOOST = ray -> SFNetwork.CHANNEL.sendToServer(new Packets.Ability(Packets.Ability.RAY_BOOST));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STAR_WISP.get(), StarWispRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR_GOLEM.get(), MeteorGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_STALKER.get(), VoidStalkerRenderer::new);
        event.registerEntityRenderer(ModEntities.NEBULA_JELLY.get(), NebulaJellyRenderer::new);
        event.registerEntityRenderer(ModEntities.STARSEER.get(), StarseerRenderer::new);
        event.registerEntityRenderer(ModEntities.ASTRAL_MIMIC.get(), AstralMimicRenderer::new);
        event.registerEntityRenderer(ModEntities.COMET_RAY.get(), CometRayRenderer::new);
        event.registerEntityRenderer(ModEntities.ASTRAEON.get(), AstraeonRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.STAR_BOLT.get(), StarBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.STAR_ARROW.get(), StarArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.SINGULARITY.get(), SingularityRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_SCYTHE.get(), ThrownScytheRenderer::new);
        event.registerEntityRenderer(ModEntities.TETHER_HOOK.get(), TetherHookRenderer::new);
        event.registerEntityRenderer(ModEntities.MOLTEN_ROCK.get(), MoltenRockRenderer::new);
        event.registerEntityRenderer(ModEntities.SHOCKWAVE.get(), ShockwaveRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SENTINEL_EYE.get(), SentinelEyeRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SFModelLayers.STAR_WISP, StarWispModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.METEOR_GOLEM, MeteorGolemModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.VOID_STALKER, VoidStalkerModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.NEBULA_JELLY, NebulaJellyModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.STARSEER, StarseerModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.MIMIC_CHEST, AstralMimicModel::createChestLayer);
        event.registerLayerDefinition(SFModelLayers.MIMIC_INNARDS, AstralMimicModel::createInnardsLayer);
        event.registerLayerDefinition(SFModelLayers.COMET_RAY, CometRayModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.ASTRAEON, AstraeonModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.METEOR, MeteorModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.MOLTEN_ROCK, MeteorModel::createSmallLayer);
        event.registerLayerDefinition(SFModelLayers.TETHER_HOOK, TetherHookModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.SINGULARITY, SingularityModel::createBodyLayer);
        event.registerLayerDefinition(SFModelLayers.SENTINEL_EYE, SentinelEyeRenderer::createEyeLayer);
        event.registerLayerDefinition(SFModelLayers.HALO, HaloLayer::createHaloLayer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new HaloLayer(renderer, event.getEntityModels()));
        }
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STAR_SPARK.get(), s -> new SFParticles.SparkProvider(s, 1.0F, 0.9F, 0.55F));
        event.registerSpriteSet(ModParticles.ASTRAL_SPARK.get(), s -> new SFParticles.SparkProvider(s, 0.55F, 0.95F, 1.0F));
        event.registerSpriteSet(ModParticles.VOID_SPARK.get(), s -> new SFParticles.SparkProvider(s, 0.78F, 0.4F, 1.0F));
        event.registerSpriteSet(ModParticles.EMBER.get(), SFParticles.EmberProvider::new);
        event.registerSpriteSet(ModParticles.METEOR_SMOKE.get(), SFParticles.SmokeProvider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), s -> new SFParticles.RingProvider(s, 1.0F, 0.85F, 0.55F, 4.5F, 14));
        event.registerSpriteSet(ModParticles.VOID_SHOCKWAVE.get(), s -> new SFParticles.RingProvider(s, 0.75F, 0.45F, 1.0F, 7.0F, 18));
        event.registerSpriteSet(ModParticles.NOVA_FLASH.get(), SFParticles.FlashProvider::new);
        event.registerSpriteSet(ModParticles.RUNE.get(), SFParticles.RuneProvider::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(BLINK_KEY);
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("starfallen_fx", ClientFx::renderOverlay);
    }

    private ClientSetup() {}

    @SuppressWarnings("unused")
    private static boolean usingItem(LivingEntity e, ItemStack s) {
        return e != null && e.isUsingItem() && e.getUseItem() == s;
    }

    @SuppressWarnings("unused")
    private static void noop() {
        EntityRenderers.class.getName();
    }
}
