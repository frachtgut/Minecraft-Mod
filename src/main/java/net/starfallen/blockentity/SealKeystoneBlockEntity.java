package net.starfallen.blockentity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.starfallen.block.AstralBrazierBlock;
import net.starfallen.block.SanctumSealBlock;
import net.starfallen.block.SealKeystoneBlock;
import net.starfallen.network.SFNetwork;
import net.starfallen.registry.ModBlockEntities;
import net.starfallen.registry.ModParticles;
import net.starfallen.registry.ModSounds;
import net.starfallen.util.SFUtil;

import java.util.*;

public class SealKeystoneBlockEntity extends BlockEntity {
    private final List<BlockPos> braziers = new ArrayList<>();
    private int lastLit = -1;
    /** Seal blocks still waiting to dissolve (dissolve animation). */
    private final Deque<BlockPos> dissolving = new ArrayDeque<>();
    private boolean broken;

    public SealKeystoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEAL_KEYSTONE.get(), pos, state);
    }

    public void setBraziers(Collection<BlockPos> positions) {
        braziers.clear();
        braziers.addAll(positions);
        setChanged();
    }

    public int getBrazierCount() {
        return braziers.size();
    }

    public int countLit(Level level) {
        int lit = 0;
        for (BlockPos p : braziers) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            // A destroyed brazier counts as burning so the dungeon can never soft-lock.
            if (!(s.getBlock() instanceof AstralBrazierBlock) || s.getValue(AstralBrazierBlock.LIT)) lit++;
        }
        return lit;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SealKeystoneBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (!be.dissolving.isEmpty()) {
            be.dissolveStep(server);
            return;
        }
        if (be.broken || !state.getValue(SealKeystoneBlock.ACTIVE) || level.getGameTime() % 20 != 7) return;
        int total = be.braziers.size();
        if (total == 0) return;
        int lit = be.countLit(level);
        if (be.lastLit >= 0 && lit > be.lastLit && lit < total) {
            for (ServerPlayer p : server.getPlayers(pl -> pl.blockPosition().closerThan(pos, 64))) {
                p.displayClientMessage(Component.translatable("message.starfallen.seal_weakens", lit, total).withStyle(ChatFormatting.AQUA), false);
            }
            server.playSound(null, pos, ModSounds.SEAL_BREAK.get(), SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        be.lastLit = lit;
        be.setChanged();
        if (lit >= total) {
            be.breakSeal(server, pos, state);
        }
    }

    private void breakSeal(ServerLevel level, BlockPos pos, BlockState state) {
        broken = true;
        level.setBlock(pos, state.setValue(SealKeystoneBlock.ACTIVE, false), 3);
        // Flood-fill every connected seal block.
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) queue.add(pos.relative(d));
        List<BlockPos> found = new ArrayList<>();
        while (!queue.isEmpty() && found.size() < 1024) {
            BlockPos p = queue.poll();
            if (!seen.add(p)) continue;
            if (!(level.getBlockState(p).getBlock() instanceof SanctumSealBlock)) continue;
            found.add(p);
            for (Direction d : Direction.values()) queue.add(p.relative(d));
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(pos)));
        dissolving.addAll(found);
        level.playSound(null, pos, ModSounds.SEAL_BREAK.get(), SoundSource.BLOCKS, 2.0F, 0.8F);
        for (ServerPlayer p : level.getPlayers(pl -> pl.blockPosition().closerThan(pos, 64))) {
            SFNetwork.sendTitle(p, Component.translatable("title.starfallen.seal_broken").withStyle(ChatFormatting.AQUA),
                    Component.translatable("title.starfallen.seal_broken.sub").withStyle(ChatFormatting.GRAY));
            SFNetwork.shake(p, 0.5F, 30);
            SFUtil.award(p, "seal_breaker");
        }
        setChanged();
    }

    private void dissolveStep(ServerLevel level) {
        for (int i = 0; i < 6 && !dissolving.isEmpty(); i++) {
            BlockPos p = dissolving.poll();
            if (level.getBlockState(p).getBlock() instanceof SanctumSealBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ModParticles.ASTRAL_SPARK.get(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                level.sendParticles(ModParticles.RUNE.get(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (dissolving.isEmpty()) setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        braziers.clear();
        ListTag list = tag.getList("Braziers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) braziers.add(NbtUtils.readBlockPos(list.getCompound(i)));
        lastLit = tag.getInt("LastLit");
        broken = tag.getBoolean("Broken");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (BlockPos p : braziers) list.add(NbtUtils.writeBlockPos(p));
        tag.put("Braziers", list);
        tag.putInt("LastLit", lastLit);
        tag.putBoolean("Broken", broken);
    }
}
