package net.starfallen.blockentity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.starfallen.event.StarfallManager;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModBlockEntities;
import net.starfallen.registry.ModSounds;
import net.starfallen.registry.ModWorldgen;
import net.starfallen.util.SFUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AstralTelescopeBlockEntity extends BlockEntity {
    private final Set<UUID> charted = new HashSet<>();
    private long lastStarCall = -100000L;

    public AstralTelescopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASTRAL_TELESCOPE.get(), pos, state);
    }

    public void gaze(ServerLevel level, ServerPlayer player) {
        SFNetwork.telescopeZoom(player);
        level.playSound(null, worldPosition, ModSounds.TELESCOPE_GAZE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        SFUtil.award(player, "stargazer");

        if (!charted.contains(player.getUUID())) {
            BlockPos target = level.findNearestMapStructure(ModWorldgen.ON_SANCTUM_MAPS, worldPosition, 100, false);
            if (target != null) {
                ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
                MapItem.renderBiomePreviewMap(level, map);
                MapItemSavedData.addTargetDecoration(map, target, "+", MapDecoration.Type.TARGET_X);
                map.setHoverName(Component.translatable("item.starfallen.sanctum_chart").withStyle(ChatFormatting.LIGHT_PURPLE));
                if (!player.getInventory().add(map)) player.drop(map, false);
                charted.add(player.getUUID());
                setChanged();
                player.displayClientMessage(Component.translatable("message.starfallen.telescope_chart").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            } else {
                player.displayClientMessage(Component.translatable("message.starfallen.telescope_nothing").withStyle(ChatFormatting.GRAY), false);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.starfallen.telescope_again").withStyle(ChatFormatting.GRAY), true);
        }

        // At night, the stars answer: a single meteor falls somewhere in view.
        long time = level.getDayTime() % 24000L;
        boolean night = time > 13000L && time < 23000L;
        if (night && level.dimensionType().hasSkyLight() && level.getGameTime() - lastStarCall > 1200L) {
            lastStarCall = level.getGameTime();
            setChanged();
            player.displayClientMessage(Component.translatable("message.starfallen.telescope_meteor").withStyle(ChatFormatting.GOLD), false);
            StarfallManager.spawnMeteorInView(level, player, 0.35F);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        charted.clear();
        ListTag list = tag.getList("Charted", Tag.TAG_INT_ARRAY);
        for (Tag t : list) charted.add(NbtUtils.loadUUID(t));
        lastStarCall = tag.getLong("LastStarCall");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (UUID id : charted) list.add(NbtUtils.createUUID(id));
        tag.put("Charted", list);
        tag.putLong("LastStarCall", lastStarCall);
    }
}
