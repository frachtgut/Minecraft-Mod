package net.starfallen.worldgen.structure;

import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.starfallen.Starfallen;
import net.starfallen.block.AstralBrazierBlock;
import net.starfallen.entity.AstralMimicEntity;
import net.starfallen.entity.StarseerEntity;
import net.starfallen.registry.ModBlocks;
import net.starfallen.registry.ModEntities;

/** Decoration helpers shared by the structure builders. */
final class BuildKit {
    private BuildKit() {}

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    static BlockState stairs(Block b, Direction facing, boolean top) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState facing(Block b, Direction d) {
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return st.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        if (st.hasProperty(BlockStateProperties.FACING)) return st.setValue(BlockStateProperties.FACING, d);
        return st;
    }

    static void chest(Blueprint bp, int x, int y, int z, Direction facing, String table) {
        bp.set(x, y, z, facing(Blocks.CHEST, facing), (be, r) -> {
            if (be instanceof RandomizableContainerBlockEntity c) c.setLootTable(Starfallen.id("chests/" + table), r.nextLong());
        });
    }

    static void barrel(Blueprint bp, int x, int y, int z, Direction facing, String table) {
        bp.set(x, y, z, facing(Blocks.BARREL, facing), (be, r) -> {
            if (be instanceof RandomizableContainerBlockEntity c) c.setLootTable(Starfallen.id("chests/" + table), r.nextLong());
        });
    }

    static void dispenser(Blueprint bp, int x, int y, int z, Direction facing, String table) {
        bp.set(x, y, z, facing(Blocks.DISPENSER, facing), (be, r) -> {
            if (be instanceof RandomizableContainerBlockEntity c) c.setLootTable(Starfallen.id(table), r.nextLong());
        });
    }

    static void spawner(Blueprint bp, int x, int y, int z, EntityType<?> type) {
        bp.set(x, y, z, s(Blocks.SPAWNER), (be, r) -> {
            if (be instanceof SpawnerBlockEntity sp) sp.setEntityId(type, r);
        });
    }

    /** Chain from the ceiling with a hanging lantern at the bottom. */
    static void hangingLantern(Blueprint bp, int x, int ceilingY, int z, int chain, boolean soul) {
        for (int i = 1; i <= chain; i++) bp.set(x, ceilingY - i, z, s(Blocks.CHAIN));
        bp.set(x, ceilingY - chain - 1, z, (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    static void lantern(Blueprint bp, int x, int y, int z, boolean soul) {
        bp.set(x, y, z, s(soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
    }

    static void candles(Blueprint bp, int x, int y, int z, int count, boolean lit) {
        bp.set(x, y, z, Blocks.PURPLE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, count))).setValue(CandleBlock.LIT, lit));
    }

    static void brazier(Blueprint bp, int x, int y, int z, boolean lit) {
        bp.set(x, y, z, ModBlocks.ASTRAL_BRAZIER.get().defaultBlockState().setValue(AstralBrazierBlock.LIT, lit));
    }

    static void sign(Blueprint bp, int x, int y, int z, Direction facing, String... lines) {
        bp.set(x, y, z, facing(Blocks.DARK_OAK_WALL_SIGN, facing), (be, r) -> {
            if (be instanceof SignBlockEntity sign) {
                SignText text = new SignText().setColor(DyeColor.LIGHT_BLUE).setHasGlowingText(true);
                for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.translatable(lines[i]));
                sign.setText(text, true);
            }
        });
    }

    /** A lectern holding a readable written book (pages are translation keys). */
    static void lecternBook(Blueprint bp, int x, int y, int z, Direction facing, String titleKey, String... pageKeys) {
        bp.set(x, y, z, facing(Blocks.LECTERN, facing).setValue(LecternBlock.HAS_BOOK, true), (be, r) -> {
            if (be instanceof LecternBlockEntity lectern) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                var tag = book.getOrCreateTag();
                tag.putString("title", Component.translatable(titleKey).getString());
                tag.putString("author", "The Astral Order");
                ListTag pages = new ListTag();
                for (String key : pageKeys) {
                    pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable(key))));
                }
                tag.put("pages", pages);
                tag.putBoolean("resolved", false);
                lectern.setBook(book);
            }
        });
    }

    static void seer(Blueprint bp, double x, double y, double z, float yaw, boolean elite) {
        bp.spawn(x, y, z, yaw, (level, r) -> {
            StarseerEntity seer = ModEntities.STARSEER.get().create(level);
            if (seer != null && elite) seer.makeElite();
            return seer;
        });
    }

    static void mob(Blueprint bp, double x, double y, double z, float yaw, EntityType<?> type) {
        bp.spawn(x, y, z, yaw, (level, r) -> type.create(level));
    }

    /** A mimic disguised as a chest, facing like one. */
    static void mimic(Blueprint bp, int x, int y, int z, float yaw) {
        bp.spawn(x + 0.5, y, z + 0.5, yaw, (level, r) -> {
            AstralMimicEntity mimic = ModEntities.ASTRAL_MIMIC.get().create(level);
            if (mimic != null) mimic.setPersistenceRequired();
            return mimic;
        });
    }

    static boolean chance(Blueprint bp, float p) {
        return bp.random.nextFloat() < p;
    }
}
