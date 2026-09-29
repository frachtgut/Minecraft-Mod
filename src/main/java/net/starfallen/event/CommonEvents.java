package net.starfallen.event;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.starfallen.command.StarfallenCommand;
import net.starfallen.config.SFConfig;
import net.starfallen.item.HaloItem;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.*;
import net.starfallen.util.Effects;
import net.starfallen.util.SFUtil;

/** Game-wide Forge event handlers (registered on the Forge bus). */
public final class CommonEvents {
    private static final String HALO_FLIGHT = "starfallen:halo_flight";
    private static final String GOT_JOURNAL = "starfallen:got_journal";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SFScheduler.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SFScheduler.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StarfallenCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        AbilityHandler.tickPlayer(player);
        tickHalo(player);
    }

    private static void tickHalo(ServerPlayer player) {
        boolean wearing = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HaloItem;
        CompoundTag data = player.getPersistentData();
        boolean flightGranted = data.getBoolean(HALO_FLIGHT);
        if (wearing) {
            if (SFConfig.HALO_FLIGHT.get() && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                data.putBoolean(HALO_FLIGHT, true);
            }
            if (player.tickCount % 80 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
                // Starsight: reveal every hostile creature nearby (including disguised mimics)
                ServerLevel level = player.serverLevel();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20), e -> e instanceof Enemy)) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false), player);
                }
            }
            if (player.getAbilities().flying && player.tickCount % 3 == 0) {
                player.serverLevel().sendParticles(ModParticles.STAR_SPARK.get(), player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.2, 0.05, 0.2, 0.0);
            }
        } else if (flightGranted) {
            data.remove(HALO_FLIGHT);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.fallDistance = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (AbilityHandler.onFall(player, event.getDistance())) {
                event.setDistance(0);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof Player player)) return;
        var src = event.getSource();
        if (AbilityHandler.hasFullSet(player, ModTiers.Armor.METEORIC)) {
            if (src.is(DamageTypes.HOT_FLOOR)) event.setCanceled(true);
            else if (src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) event.setAmount(event.getAmount() * 0.5F);
        }
        if (AbilityHandler.hasFullSet(player, ModTiers.Armor.VOIDWALKER) && (src.is(ModDamageTypes.VOID_REND) || src.is(ModDamageTypes.STAR_BOLT))) {
            event.setAmount(event.getAmount() * 0.5F);
        }
        if (AbilityHandler.hasFullSet(player, ModTiers.Armor.STARFORGED) && src.is(ModDamageTypes.METEOR)) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    /** Totem of the Fallen Star: cheat death in a blinding supernova. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ItemStack totem = ItemStack.EMPTY;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.is(ModItems.TOTEM_OF_THE_FALLEN_STAR.get())) {
                totem = s;
                break;
            }
        }
        if (totem.isEmpty()) {
            for (ItemStack s : player.getInventory().items) {
                if (s.is(ModItems.TOTEM_OF_THE_FALLEN_STAR.get())) {
                    totem = s;
                    break;
                }
            }
        }
        if (totem.isEmpty()) return;
        ItemStack shown = totem.copy();
        totem.shrink(1);
        event.setCanceled(true);
        player.setHealth(10.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2));
        player.awardStat(Stats.ITEM_USED.get(ModItems.TOTEM_OF_THE_FALLEN_STAR.get()));
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position().add(0, 1, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(9), e -> SFUtil.isValidTarget(player, e))) {
            e.hurt(ModDamageTypes.source(level, ModDamageTypes.STAR_BOLT, player, player), 14.0F);
            Vec3 away = e.position().subtract(c).normalize();
            e.setDeltaMovement(away.x * 1.8, 0.8, away.z * 1.8);
            e.hurtMarked = true;
        }
        level.playSound(null, c.x, c.y, c.z, ModSounds.TOTEM_NOVA.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        level.sendParticles(ModParticles.NOVA_FLASH.get(), c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
        level.sendParticles(ModParticles.STAR_SPARK.get(), c.x, c.y, c.z, 120, 0.5, 0.5, 0.5, 0.8);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 60, 0.5, 0.8, 0.5, 0.5);
        Effects.ring(level, player.position().add(0, 0.1, 0), ModParticles.SHOCKWAVE.get());
        SFNetwork.totemPop(player, shown);
        SFNetwork.shakeAround(level, c, 24, 1.0F, 20);
        SFUtil.award(player, "second_sunrise");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
            if (!persisted.getBoolean(GOT_JOURNAL)) {
                persisted.putBoolean(GOT_JOURNAL, true);
                data.put(Player.PERSISTED_NBT_TAG, persisted);
                ItemStack journal = new ItemStack(ModItems.STARSEER_JOURNAL.get());
                if (!player.getInventory().add(journal)) player.drop(journal, false);
                player.displayClientMessage(Component.translatable("message.starfallen.welcome").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AbilityHandler.clear(event.getEntity().getUUID());
    }

    private CommonEvents() {}
}
