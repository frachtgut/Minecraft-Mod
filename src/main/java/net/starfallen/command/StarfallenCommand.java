package net.starfallen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.entity.AstralMimicEntity;
import net.starfallen.entity.StarseerEntity;
import net.starfallen.entity.boss.AstraeonEntity;
import net.starfallen.event.StarfallManager;
import net.starfallen.registry.ModEntities;
import net.starfallen.registry.ModItems;
import net.starfallen.world.CraterBuilder;

import java.util.List;
import java.util.Locale;

/**
 * /starfallen - showcase and admin commands.
 * <pre>
 *  /starfallen starfall start|stop|status
 *  /starfallen meteor [normal|large|golden|egg]
 *  /starfallen boss
 *  /starfallen highseer
 *  /starfallen mimic
 *  /starfallen kit weapons|armor|materials|gadgets|blocks|all
 * </pre>
 */
public final class StarfallenCommand {
    private static final List<String> METEORS = List.of("normal", "large", "golden", "egg");
    private static final List<String> KITS = List.of("weapons", "armor", "materials", "gadgets", "blocks", "all");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("starfallen").requires(s -> s.hasPermission(2))
                .then(Commands.literal("starfall")
                        .then(Commands.literal("start").executes(ctx -> startStarfall(ctx, 0))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(200, 72000)).executes(ctx -> startStarfall(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))
                        .then(Commands.literal("stop").executes(StarfallenCommand::stopStarfall))
                        .then(Commands.literal("status").executes(ctx -> {
                            boolean active = StarfallManager.isActive(ctx.getSource().getLevel());
                            ctx.getSource().sendSuccess(() -> Component.translatable(active ? "command.starfallen.status_on" : "command.starfallen.status_off"), false);
                            return active ? 1 : 0;
                        })))
                .then(Commands.literal("meteor").executes(ctx -> meteor(ctx, "large"))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(METEORS, b))
                                .executes(ctx -> meteor(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("boss").executes(StarfallenCommand::boss))
                .then(Commands.literal("highseer").executes(StarfallenCommand::highSeer))
                .then(Commands.literal("mimic").executes(StarfallenCommand::mimic))
                .then(Commands.literal("kit").executes(ctx -> kit(ctx, "all"))
                        .then(Commands.argument("kit", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(KITS, b))
                                .executes(ctx -> kit(ctx, StringArgumentType.getString(ctx, "kit"))))));
    }

    private static int startStarfall(CommandContext<CommandSourceStack> ctx, int ticks) {
        StarfallManager.start(ctx.getSource().getLevel(), ticks);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.starfallen.started").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int stopStarfall(CommandContext<CommandSourceStack> ctx) {
        StarfallManager.stop(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.starfallen.stopped"), true);
        return 1;
    }

    private static int meteor(CommandContext<CommandSourceStack> ctx, String type) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        CraterBuilder.Loot loot = switch (type.toLowerCase(Locale.ROOT)) {
            case "golden" -> CraterBuilder.Loot.GOLDEN;
            case "egg" -> CraterBuilder.Loot.EGG;
            case "normal" -> CraterBuilder.Loot.NORMAL;
            default -> CraterBuilder.Loot.LARGE;
        };
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 t = player.position().add(look.scale(28));
        StarfallManager.launch(level, t.x, t.z, loot);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.starfallen.meteor", type).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos pos = BlockPos.containing(player.position().add(look.scale(10)));
        AstraeonEntity.summon(ctx.getSource().getLevel(), pos, player);
        return 1;
    }

    private static int highSeer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        StarseerEntity seer = ModEntities.STARSEER.get().create(level);
        if (seer == null) return 0;
        Vec3 p = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(8));
        seer.moveTo(p.x, p.y, p.z, player.getYRot() + 180, 0);
        seer.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.COMMAND, null, null);
        seer.makeElite();
        level.addFreshEntity(seer);
        return 1;
    }

    private static int mimic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        AstralMimicEntity mimic = ModEntities.ASTRAL_MIMIC.get().create(level);
        if (mimic == null) return 0;
        BlockPos pos = BlockPos.containing(player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(3)));
        mimic.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
        mimic.settle(pos, player.getYRot() + 180);
        level.addFreshEntity(mimic);
        return 1;
    }

    private static int kit(CommandContext<CommandSourceStack> ctx, String kit) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String k = kit.toLowerCase(Locale.ROOT);
        boolean all = k.equals("all");
        if (all || k.equals("weapons")) {
            give(player, ModItems.METEOR_HAMMER, ModItems.NEBULA_BLADE, ModItems.CONSTELLATION_BOW, ModItems.RIFTBLADE,
                    ModItems.SINGULARITY_GAUNTLET, ModItems.STARCALLER_STAFF, ModItems.ECLIPSE_SCYTHE, ModItems.STARBREAKER, ModItems.METEORIC_SWORD);
        }
        if (all || k.equals("armor")) {
            give(player, ModItems.METEORIC_HELMET, ModItems.METEORIC_CHESTPLATE, ModItems.METEORIC_LEGGINGS, ModItems.METEORIC_BOOTS,
                    ModItems.STARFORGED_HELMET, ModItems.STARFORGED_CHESTPLATE, ModItems.STARFORGED_LEGGINGS, ModItems.STARFORGED_BOOTS,
                    ModItems.VOIDWALKER_HELMET, ModItems.VOIDWALKER_CHESTPLATE, ModItems.VOIDWALKER_LEGGINGS, ModItems.VOIDWALKER_BOOTS,
                    ModItems.HALO_OF_THE_FALLEN_STAR);
        }
        if (all || k.equals("gadgets")) {
            give(player, ModItems.STAR_TETHER, ModItems.TOTEM_OF_THE_FALLEN_STAR, ModItems.STARFALL_BEACON, ModItems.SIGIL_OF_THE_FALLEN_STAR,
                    ModItems.STELLAR_EGG, ModItems.STARSEER_JOURNAL);
        }
        if (all || k.equals("materials")) {
            giveStack(player, new ItemStack(ModItems.STARDUST.get(), 32));
            giveStack(player, new ItemStack(ModItems.STAR_FRAGMENT.get(), 8));
            giveStack(player, new ItemStack(ModItems.METEORIC_IRON_INGOT.get(), 16));
            giveStack(player, new ItemStack(ModItems.ASTRAL_ALLOY_INGOT.get(), 8));
            giveStack(player, new ItemStack(ModItems.VOIDSTEEL_INGOT.get(), 8));
            giveStack(player, new ItemStack(ModItems.VOID_ESSENCE.get(), 8));
            giveStack(player, new ItemStack(ModItems.ECLIPSE_SHARD.get(), 8));
            giveStack(player, new ItemStack(ModItems.MOLTEN_CORE.get(), 2));
            giveStack(player, new ItemStack(ModItems.NEBULA_GEL.get(), 8));
        }
        if (k.equals("blocks")) {
            give(player, ModItems.STAR_ALTAR, ModItems.ASTRAL_BRAZIER, ModItems.SPIKE_TRAP, ModItems.FLAME_JET, ModItems.SENTINEL_EYE,
                    ModItems.CRUMBLING_VOID_BRICKS, ModItems.ASTRAL_TELESCOPE, ModItems.FALLEN_STAR, ModItems.STARLIGHT_LAMP);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.starfallen.kit", kit).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    @SafeVarargs
    private static void give(ServerPlayer player, RegistryObject<? extends Item>... items) {
        for (RegistryObject<? extends Item> item : items) giveStack(player, new ItemStack(item.get()));
    }

    private static void giveStack(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private StarfallenCommand() {}
}
