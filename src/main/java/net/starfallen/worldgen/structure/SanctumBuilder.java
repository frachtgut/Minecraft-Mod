package net.starfallen.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.starfallen.block.FlameJetBlock;
import net.starfallen.blockentity.FlameJetBlockEntity;
import net.starfallen.blockentity.SealKeystoneBlockEntity;
import net.starfallen.registry.ModBlocks;
import net.starfallen.registry.ModEntities;

import java.util.ArrayList;
import java.util.List;

import static net.starfallen.worldgen.structure.BuildKit.*;

/**
 * The Sanctum of the Fallen Star (local coordinates; y = 0 is the dungeon floor, the Hall of
 * Stars is centred on x = z = 0, north is -z):
 * <pre>
 *                      [obelisk + spiral shaft to the surface]  z -24..-20
 *                                   |
 *  [Vault]==[secret]==[Library]==[Hall of Stars]==[Trap Gallery]=====+
 *                                   |                                |
 *                           [Hall of Watchers]====[Crystal Cavern]===+
 *                                   |
 *                             [Sanctum Seal]  (4 braziers break it)
 *                                   |
 *                            [Heart Chamber] - the Star Altar
 * </pre>
 */
final class SanctumBuilder {
    static final int SHAFT_Z = -22;
    static final int MIN_X = -58, MAX_X = 68, MIN_Z = -30, MAX_Z = 96;
    static final int DOME_TOP = 30;
    private static final int ARENA_Z = 77, ARENA_R = 17;

    private final Blueprint bp;
    private final Palette walls, floor, ceiling;
    private final BlockState air = Blocks.AIR.defaultBlockState();
    private final List<int[]> braziers = new ArrayList<>();
    private final long noiseSeed;

    private SanctumBuilder(Blueprint bp) {
        this.bp = bp;
        this.walls = new Palette().add(s(ModBlocks.VOID_BRICKS.get()), 60).add(s(ModBlocks.CRACKED_VOID_BRICKS.get()), 24)
                .add(s(Blocks.DEEPSLATE_BRICKS), 8).add(s(Blocks.CRACKED_DEEPSLATE_BRICKS), 5).add(s(ModBlocks.CHISELED_VOID_BRICKS.get()), 3);
        this.floor = new Palette().add(s(ModBlocks.VOID_BRICKS.get()), 55).add(s(ModBlocks.CRACKED_VOID_BRICKS.get()), 20)
                .add(s(Blocks.DEEPSLATE_TILES), 15).add(s(ModBlocks.STARFIELD_TILES.get()), 10);
        this.ceiling = new Palette().add(s(ModBlocks.VOID_BRICKS.get()), 65).add(s(ModBlocks.STARFIELD_TILES.get()), 35);
        this.noiseSeed = bp.random.nextLong();
    }

    static void build(Blueprint bp, int surfaceRel) {
        new SanctumBuilder(bp).run(Math.max(12, surfaceRel));
    }

    private void run(int surface) {
        // Rooms first, corridors punch through their shells afterwards.
        hallOfStars();
        library();
        vault();
        trapGallery();
        crystalCavern();
        hallOfWatchers();
        heartChamber();

        corridor(-2, SHAFT_Z + 3, 2, -11, 5);          // shaft -> hall of stars
        corridor(-18, -2, -11, 2, 5);                   // hall -> library
        corridor(-46, 0, -35, 0, 2);                    // secret passage -> vault
        corridor(11, -2, 15, 2, 5);                     // hall -> trap gallery
        corridor(49, 3, 51, 23, 4);                     // gallery -> cavern
        corridor(-2, 11, 2, 17, 5);                     // hall -> watchers
        corridor(7, 25, 43, 27, 4);                     // watchers -> cavern
        corridor(-2, 46, 2, 51, 6);                     // watchers -> seal
        corridor(-2, 53, 2, ARENA_Z - ARENA_R + 2, 5);  // seal -> heart chamber
        passageDetails();
        seal();
        shaft(surface);
        shrine(surface);
    }

    // ------------------------------------------------------------------ primitives

    private void room(int x1, int y1, int z1, int x2, int y2, int z2) {
        bp.room(x1, y1, z1, x2, y2, z2, walls);
        for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) {
            bp.set(x, y1, z, floor.pick(bp.random));
            bp.set(x, y2, z, ceiling.pick(bp.random));
        }
    }

    /** Axis-aligned corridor with open ends (interior bounds inclusive). */
    private void corridor(int x1, int z1, int x2, int z2, int h) {
        boolean alongX = (x2 - x1) >= (z2 - z1);
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean inX = x >= x1 && x <= x2, inZ = z >= z1 && z <= z2;
                if (inX && inZ) {
                    bp.set(x, 0, z, floor.pick(bp.random));
                    for (int y = 1; y <= h; y++) bp.set(x, y, z, air);
                    bp.setUnlessAir(x, h + 1, z, ceiling.pick(bp.random));
                } else if ((alongX && inX) || (!alongX && inZ)) {
                    for (int y = 0; y <= h + 1; y++) bp.setUnlessAir(x, y, z, walls.pick(bp.random));
                }
            }
        }
        // Lanterns every 7 blocks along the corridor
        if (h >= 3) {
            if (alongX) {
                for (int x = x1 + 3; x <= x2 - 2; x += 7) bp.set(x, h, (z1 + z2) / 2, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            } else {
                for (int z = z1 + 3; z <= z2 - 2; z += 7) bp.set((x1 + x2) / 2, h, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
    }

    private void brazierSlot(int x, int y, int z) {
        brazier(bp, x, y, z, false);
        braziers.add(new int[]{x, y, z});
    }

    private double noise(double x, double y, double z) {
        // Small deterministic value noise (trilinear) for organic caves.
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        double v = 0;
        for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
            double w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            v += w * hash(xi + dx, yi + dy, zi + dz);
        }
        return v;
    }

    private double hash(int x, int y, int z) {
        long h = noiseSeed ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
        h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2 - 1;
    }

    // ------------------------------------------------------------------ rooms

    private void hallOfStars() {
        room(-11, 0, -11, 11, 10, 11);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 8) bp.set(x, 0, z, s(ModBlocks.STARFIELD_TILES.get()));
                else if (d < 9.5) bp.set(x, 0, z, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
            }
        }
        // A constellation set into the floor
        int[][] stars = {{-5, -2}, {-3, -3}, {-1, -2}, {1, -3}, {3, -1}, {5, -2}, {4, 1}, {0, 3}, {-3, 4}};
        for (int[] st : stars) bp.set(st[0], 0, st[1], s(ModBlocks.STARLIGHT_LAMP.get()));
        // Pillars
        for (int px : new int[]{-6, 6}) {
            for (int pz : new int[]{-6, 6}) {
                for (int x = px - 1; x <= px + 1; x++) for (int z = pz - 1; z <= pz + 1; z++) {
                    for (int y = 1; y <= 9; y++) {
                        boolean cap = y == 1 || y == 9;
                        bp.set(x, y, z, cap ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : s(ModBlocks.VOID_PILLAR.get()));
                    }
                }
                bp.set(px, 5, pz + (pz < 0 ? 1 : -1), s(ModBlocks.STARLIGHT_LAMP.get()));
            }
        }
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {0, 0}}) hangingLantern(bp, c[0], 10, c[1], 1, true);
        bp.set(-10, 7, -10, s(ModBlocks.SENTINEL_EYE.get()));
        bp.set(10, 7, 10, s(ModBlocks.SENTINEL_EYE.get()));
        chest(bp, 9, 1, -9, Direction.WEST, "sanctum_common");
        bp.set(8, 1, -9, s(Blocks.DECORATED_POT));
        bp.set(9, 1, -8, s(Blocks.DECORATED_POT));
        barrel(bp, -9, 1, 9, Direction.UP, "sanctum_common");
        for (int i = 0; i < 6; i++) {
            int x = -9 + bp.random.nextInt(19), z = -9 + bp.random.nextInt(19);
            if (Math.abs(x) > 3 && Math.abs(z) > 3 && bp.isPlannedAir(x, 1, z)) bp.set(x, 1, z, s(Blocks.COBWEB));
        }
        seer(bp, -4.5, 1, 4.5, 180, false);
        seer(bp, 4.5, 1, -3.5, 0, false);
    }

    private void library() {
        room(-35, 0, -9, -18, 8, 9);
        BlockState shelf = s(Blocks.BOOKSHELF);
        for (int y = 1; y <= 5; y++) {
            for (int z = -8; z <= 8; z++) {
                bp.set(-34, y, z, shelf);
                if (Math.abs(z) > 2) bp.set(-19, y, z, shelf);
            }
            for (int x = -34; x <= -19; x++) {
                bp.set(x, y, -8, shelf);
                bp.set(x, y, 8, shelf);
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = -31; x <= -22; x++) {
                if (x == -27 || x == -26) continue;
                bp.set(x, y, -4, shelf);
                bp.set(x, y, 4, shelf);
            }
        }
        // Secret: the bookshelves at the heart of the west wall hide a passage.
        bp.set(-34, 3, 0, s(Blocks.CHISELED_BOOKSHELF));
        brazierSlot(-26, 1, 0);
        for (int x = -28; x <= -24; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x + 26) + Math.abs(z) <= 2) bp.set(x, 0, z, s(ModBlocks.STARFIELD_TILES.get()));
        }
        lecternBook(bp, -21, 1, -6, Direction.WEST, "book.starfallen.sanctum_lore.title",
                "book.starfallen.sanctum_lore.1", "book.starfallen.sanctum_lore.2", "book.starfallen.sanctum_lore.3");
        lecternBook(bp, -30, 1, 1, Direction.WEST, "book.starfallen.hint.title", "book.starfallen.hint.1", "book.starfallen.hint.2");
        for (int[] t : new int[][]{{-22, 6}, {-30, -6}}) {
            bp.set(t[0], 1, t[1], s(Blocks.DARK_OAK_FENCE));
            bp.set(t[0], 2, t[1], slab(Blocks.DARK_OAK_SLAB, false));
            candles(bp, t[0], 3, t[1], 1 + bp.random.nextInt(4), true);
        }
        chest(bp, -33, 1, 7, Direction.EAST, "sanctum_library");
        hangingLantern(bp, -26, 8, -6, 1, true);
        hangingLantern(bp, -26, 8, 6, 1, true);
        seer(bp, -24.5, 1, -6.5, 90, false);
    }

    private void vault() {
        room(-56, 0, -5, -46, 6, 5);
        for (int x = -55; x <= -47; x++) for (int z = -4; z <= 4; z++) {
            bp.set(x, 0, z, ((x + z) & 1) == 0 ? s(Blocks.GOLD_BLOCK) : s(ModBlocks.CHISELED_VOID_BRICKS.get()));
            if (bp.random.nextInt(4) == 0) bp.set(x, 6, z, s(ModBlocks.STARLIGHT_LAMP.get()));
        }
        chest(bp, -54, 1, -3, Direction.EAST, "sanctum_vault_sigil");
        chest(bp, -54, 1, 3, Direction.EAST, "sanctum_vault");
        chest(bp, -50, 1, -4, Direction.SOUTH, "sanctum_vault");
        mimic(bp, -50, 1, 4, Direction.NORTH.toYRot());
        brazier(bp, -48, 1, -3, true);
        brazier(bp, -48, 1, 3, true);
        bp.set(-55, 1, 0, s(Blocks.RAW_GOLD_BLOCK));
        bp.set(-55, 2, 0, s(ModBlocks.FALLEN_STAR.get()));
        bp.set(-55, 1, -1, s(Blocks.GOLD_BLOCK));
        bp.set(-55, 1, 1, s(Blocks.GOLD_BLOCK));
    }

    private void trapGallery() {
        room(15, 0, -3, 53, 7, 3);
        // Spike rows
        for (int x : new int[]{20, 22, 24, 26}) {
            for (int z = -2; z <= 2; z++) bp.set(x, 0, z, s(ModBlocks.SPIKE_TRAP.get()));
        }
        // Flame jets from the walls and the ceiling
        int[][] jets = {{29, -3, 1}, {31, 3, 2}, {33, -3, 2}, {35, 3, 1}};
        int offset = 0;
        for (int[] j : jets) {
            Direction face = j[1] < 0 ? Direction.SOUTH : Direction.NORTH;
            int off = offset;
            bp.set(j[0], j[2], j[1], ModBlocks.FLAME_JET.get().defaultBlockState().setValue(FlameJetBlock.FACING, face),
                    (be, r) -> { if (be instanceof FlameJetBlockEntity f) f.setOffset(off); });
            offset += 22;
        }
        for (int x : new int[]{30, 34}) {
            int off = x == 30 ? 45 : 0;
            bp.set(x, 7, 0, ModBlocks.FLAME_JET.get().defaultBlockState().setValue(FlameJetBlock.FACING, Direction.DOWN),
                    (be, r) -> { if (be instanceof FlameJetBlockEntity f) f.setOffset(off); });
        }
        // The collapsing bridge over a spike pit
        for (int x = 37; x <= 42; x++) {
            for (int z = -2; z <= 2; z++) {
                bp.set(x, 0, z, s(ModBlocks.CRUMBLING_VOID_BRICKS.get()));
                for (int y = -5; y <= -1; y++) bp.set(x, y, z, air);
                bp.set(x, -6, z, s(ModBlocks.SPIKE_TRAP.get()));
            }
        }
        for (int y = -6; y <= -1; y++) {
            for (int x = 36; x <= 43; x++) {
                bp.set(x, y, -3, walls.pick(bp.random));
                bp.set(x, y, 3, walls.pick(bp.random));
            }
            for (int z = -3; z <= 3; z++) {
                bp.set(36, y, z, walls.pick(bp.random));
                bp.set(43, y, z, walls.pick(bp.random));
            }
        }
        for (int y = -5; y <= 0; y++) bp.set(42, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        // Arrow traps: pressure plates beside hidden dispensers
        for (int x = 44; x <= 48; x++) {
            boolean northWall = (x & 1) == 0;
            int wz = northWall ? -3 : 3;
            dispenser(bp, x, 1, wz, northWall ? Direction.SOUTH : Direction.NORTH, "dispensers/sanctum_arrows");
            bp.set(x, 1, northWall ? -2 : 2, s(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE));
        }
        // The prize at the end
        for (int x = 50; x <= 52; x++) for (int z = -1; z <= 1; z++) bp.set(x, 0, z, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
        brazierSlot(52, 1, 0);
        chest(bp, 52, 1, -2, Direction.WEST, "sanctum_trap_reward");
        bp.set(52, 5, 2, s(ModBlocks.SENTINEL_EYE.get()));
        bp.set(52, 5, -2, s(ModBlocks.SENTINEL_EYE.get()));
        for (int x : new int[]{18, 28, 46}) hangingLantern(bp, x, 7, 0, 0, true);
        bp.set(16, 1, 2, s(Blocks.SKELETON_SKULL));
        bp.set(39, -5, 1, s(Blocks.SKELETON_SKULL));
    }

    private void crystalCavern() {
        int cx = 53, cy = 6, cz = 34;
        int rx = 13, ry = 8, rz = 14;
        Palette rock = new Palette().add(s(Blocks.DEEPSLATE), 30).add(s(Blocks.TUFF), 20).add(s(Blocks.CALCITE), 14)
                .add(s(Blocks.SMOOTH_BASALT), 10).add(s(ModBlocks.METEORITE.get()), 16).add(s(Blocks.AMETHYST_BLOCK), 4)
                .add(s(ModBlocks.METEORIC_IRON_ORE.get()), 3);
        List<int[]> openCells = new ArrayList<>();
        for (int x = cx - rx - 3; x <= cx + rx + 3; x++) {
            for (int y = -3; y <= cy + ry + 3; y++) {
                for (int z = cz - rz - 3; z <= cz + rz + 3; z++) {
                    double dx = (x - cx) / (double) rx, dy = (y - cy) / (double) ry, dz = (z - cz) / (double) rz;
                    double n = dx * dx + dy * dy + dz * dz + noise(x * 0.18, y * 0.18, z * 0.18) * 0.28;
                    double floorH = 0.5 + noise(x * 0.25, 0, z * 0.25) * 1.5;
                    if (n < 1.0) {
                        if (y <= floorH) bp.set(x, y, z, rock.pick(bp.random));
                        else {
                            bp.set(x, y, z, air);
                            openCells.add(new int[]{x, y, z});
                        }
                    } else if (n < 1.45) {
                        bp.setUnlessAir(x, y, z, rock.pick(bp.random));
                    }
                }
            }
        }
        // Crystals grow on every exposed surface
        for (int[] c : openCells) {
            if (bp.random.nextFloat() > 0.07F) continue;
            Direction face = null;
            for (Direction d : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockState n = bp.get(c[0] + d.getStepX(), c[1] + d.getStepY(), c[2] + d.getStepZ());
                if (n != null && !n.isAir()) {
                    face = d.getOpposite();
                    break;
                }
            }
            if (face != null) bp.set(c[0], c[1], c[2], ModBlocks.STARLIT_CRYSTAL.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, face));
        }
        // A fallen star half-buried in a meteorite mound
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 5; z <= cz - 1; z++) {
            int h = 3 - (int) Math.round(Math.sqrt((x - cx) * (x - cx) + (z - cz + 3) * (z - cz + 3)));
            for (int y = 1; y <= Math.max(1, h); y++) bp.set(x, y, z, s(ModBlocks.METEORITE.get()));
        }
        bp.set(cx, 4, cz - 3, s(ModBlocks.FALLEN_STAR.get()));
        // Pool
        for (int x = cx - 8; x <= cx - 5; x++) for (int z = cz + 3; z <= cz + 7; z++) {
            bp.set(x, 1, z, s(Blocks.WATER));
            bp.set(x, 0, z, s(Blocks.CLAY));
            bp.set(x, 2, z, air);
        }
        // Brazier on a high ledge, reached by meteorite stepping stones
        int bx = cx + 9, bz = cz;
        for (int x = bx - 1; x <= bx + 1; x++) for (int z = bz - 1; z <= bz + 1; z++) for (int y = 1; y <= 5; y++) bp.set(x, y, z, s(ModBlocks.METEORITE.get()));
        brazierSlot(bx, 6, bz);
        int[][] steps = {{bx - 6, 1, bz - 2}, {bx - 5, 2, bz - 1}, {bx - 4, 3, bz}, {bx - 3, 4, bz}, {bx - 2, 5, bz}};
        for (int[] st : steps) for (int y = 1; y <= st[1]; y++) bp.set(st[0], y, st[2], s(ModBlocks.METEORITE.get()));
        chest(bp, cx - 9, 2, cz - 4, Direction.EAST, "sanctum_cavern");
        bp.set(cx - 9, 1, cz - 4, s(ModBlocks.METEORITE.get()));
        mob(bp, cx + 0.5, 2, cz + 5.5, 180, ModEntities.METEOR_GOLEM.get());
        mob(bp, cx - 3.5, 9, cz - 2.5, 0, ModEntities.NEBULA_JELLY.get());
        mob(bp, cx + 4.5, 10, cz + 6.5, 0, ModEntities.NEBULA_JELLY.get());
        mob(bp, cx - 1.5, 6, cz - 3.5, 0, ModEntities.STAR_WISP.get());
    }

    private void hallOfWatchers() {
        room(-7, 0, 17, 7, 10, 46);
        for (int z = 18; z <= 45; z++) {
            bp.set(0, 0, z, s(ModBlocks.STARFIELD_TILES.get()));
            if ((z & 1) == 0) {
                bp.set(-1, 0, z, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
                bp.set(1, 0, z, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
            }
        }
        // Nave pillars
        for (int z : new int[]{20, 26, 34, 42}) {
            for (int x : new int[]{-5, 5}) for (int y = 1; y <= 9; y++) bp.set(x, y, z, s(ModBlocks.VOID_PILLAR.get()));
        }
        // Alcoves where the Watchers stand
        for (int z : new int[]{22, 30, 38}) {
            for (int side = -1; side <= 1; side += 2) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 1; y <= 5; y++) {
                        bp.set(side * 7, y, z + dz, air);
                        bp.set(side * 8, y, z + dz, air);
                        bp.set(side * 9, y, z + dz, walls.pick(bp.random));
                    }
                    bp.set(side * 8, 0, z + dz, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
                    bp.set(side * 7, 0, z + dz, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
                    bp.set(side * 8, 6, z + dz, walls.pick(bp.random));
                    bp.set(side * 7, 6, z + dz, walls.pick(bp.random));
                }
                for (int y = 0; y <= 6; y++) {
                    bp.setUnlessAir(side * 7, y, z - 2, walls.pick(bp.random));
                    bp.setUnlessAir(side * 8, y, z - 2, walls.pick(bp.random));
                    bp.setUnlessAir(side * 7, y, z + 2, walls.pick(bp.random));
                    bp.setUnlessAir(side * 8, y, z + 2, walls.pick(bp.random));
                }
            }
        }
        // The caged alcove breeds more of them
        for (int dz = -1; dz <= 1; dz++) for (int y = 1; y <= 4; y++) bp.set(7, y, 22 + dz, s(Blocks.IRON_BARS));
        spawner(bp, 8, 1, 22, ModEntities.VOID_STALKER.get());
        // The Watchers themselves
        mob(bp, -7.5, 1, 22.5, -90, ModEntities.VOID_STALKER.get());
        mob(bp, 8.5, 1, 30.5, 90, ModEntities.VOID_STALKER.get());
        mob(bp, -7.5, 1, 38.5, -90, ModEntities.VOID_STALKER.get());
        mob(bp, 0.5, 1, 44.5, 180, ModEntities.VOID_STALKER.get());
        // Only the entrance is lit.
        hangingLantern(bp, -3, 10, 19, 1, true);
        hangingLantern(bp, 3, 10, 19, 1, true);
        // Dais for the fourth brazier
        for (int x = -2; x <= 2; x++) for (int z = 41; z <= 45; z++) if (Math.abs(x) + Math.abs(z - 43) <= 2) bp.set(x, 0, z, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
        brazierSlot(0, 1, 43);
        for (int i = 0; i < 10; i++) {
            int x = -6 + bp.random.nextInt(13), z = 18 + bp.random.nextInt(28);
            if (Math.abs(x) >= 3 && bp.isPlannedAir(x, 1, z)) bp.set(x, 1, z, bp.random.nextBoolean() ? s(Blocks.COBWEB) : s(Blocks.SKELETON_SKULL));
        }
    }

    private void heartChamber() {
        int r = ARENA_R;
        for (int x = -r - 2; x <= r + 2; x++) {
            for (int z = ARENA_Z - r - 2; z <= ARENA_Z + r + 2; z++) {
                double d = Math.sqrt(x * x + (z - ARENA_Z) * (z - ARENA_Z));
                if (d >= r + 1.5) continue;
                // Floor & walls
                if (d < r) {
                    BlockState f = d >= 13.5 && d < 15.5 ? s(ModBlocks.STARFIELD_TILES.get())
                            : d >= 5.5 && d < 6.5 ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : floor.pick(bp.random);
                    bp.set(x, 0, z, f);
                    for (int y = 1; y <= 10; y++) bp.set(x, y, z, air);
                } else {
                    for (int y = 0; y <= 10; y++) bp.set(x, y, z, y == 5 ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : walls.pick(bp.random));
                }
                bp.set(x, -1, z, s(Blocks.DEEPSLATE_BRICKS));
                // Dome
                for (int y = 11; y <= 10 + r + 2; y++) {
                    double dd = Math.sqrt(x * x + (y - 10) * (y - 10) + (z - ARENA_Z) * (z - ARENA_Z));
                    if (dd < r) bp.set(x, y, z, air);
                    else if (dd < r + 1.5) {
                        BlockState st = bp.random.nextInt(18) == 0 ? s(ModBlocks.STARLIGHT_LAMP.get())
                                : bp.random.nextInt(3) == 0 ? s(ModBlocks.VOID_BRICKS.get()) : s(ModBlocks.STARFIELD_TILES.get());
                        bp.set(x, y, z, st);
                    }
                }
            }
        }
        // Dais and the Star Altar
        for (int x = -4; x <= 4; x++) for (int z = ARENA_Z - 4; z <= ARENA_Z + 4; z++) {
            double d = Math.sqrt(x * x + (z - ARENA_Z) * (z - ARENA_Z));
            if (d < 3.6) bp.set(x, 1, z, d < 2.2 ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : s(ModBlocks.VOID_BRICK_SLAB.get()));
            if (d < 1.6) bp.set(x, 2, z, s(ModBlocks.VOID_BRICKS.get()));
        }
        bp.set(0, 3, ARENA_Z, s(ModBlocks.STAR_ALTAR.get()));
        // Pillars of cover (the void beam and the nova cannot pass through them)
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 11), pz = ARENA_Z + (int) Math.round(Math.sin(a) * 11);
            for (int x = px - 1; x <= px + 1; x++) for (int z = pz - 1; z <= pz + 1; z++) {
                for (int y = 1; y <= 16; y++) {
                    boolean band = y == 1 || y == 8 || y == 16;
                    bp.set(x, y, z, band ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : s(ModBlocks.VOID_PILLAR.get()));
                }
            }
            brazier(bp, px, 17, pz, true);
        }
        // Braziers ring the arena floor
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int bx = (int) Math.round(Math.cos(a) * 15), bz = ARENA_Z + (int) Math.round(Math.sin(a) * 15);
            if (k == 6) continue; // north: the entrance
            brazier(bp, bx, 1, bz, true);
        }
    }

    // ------------------------------------------------------------------ connectors & the seal

    private void passageDetails() {
        bp.set(-40, 0, 0, s(ModBlocks.SPIKE_TRAP.get()));
        bp.set(-37, 2, 0, s(Blocks.COBWEB));
        bp.set(-43, 2, 0, s(Blocks.COBWEB));
        // The secret door: bookshelves in the library wall (x = -34) left in place
        bp.set(-34, 1, 0, s(Blocks.BOOKSHELF));
        bp.set(-34, 2, 0, s(Blocks.BOOKSHELF));
    }

    private void seal() {
        int sz = 52;
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 7; y++) {
                boolean frame = Math.abs(x) == 3 || y == 0 || y >= 6;
                if (frame) bp.set(x, y, sz, Math.abs(x) == 3 ? s(ModBlocks.VOID_PILLAR.get()) : s(ModBlocks.CHISELED_VOID_BRICKS.get()));
                else bp.set(x, y, sz, s(ModBlocks.SANCTUM_SEAL.get()));
            }
        }
        List<BlockPos> world = new ArrayList<>();
        for (int[] b : braziers) world.add(bp.world(b[0], b[1], b[2]));
        bp.set(0, 6, sz, s(ModBlocks.SEAL_KEYSTONE.get()), (be, r) -> {
            if (be instanceof SealKeystoneBlockEntity k) k.setBraziers(world);
        });
        sign(bp, -2, 3, 50, Direction.EAST, "sign.starfallen.seal.1", "sign.starfallen.seal.2", "sign.starfallen.seal.3", "sign.starfallen.seal.4");
        bp.set(-2, 2, 50, walls.pick(bp.random));
        // Torches of soul fire flank the gate
        for (int side = -1; side <= 1; side += 2) {
            bp.set(side * 2, 1, 48, s(ModBlocks.VOID_BRICK_WALL.get()));
            bp.set(side * 2, 2, 48, s(Blocks.SOUL_LANTERN));
        }
    }

    private void shaft(int surface) {
        int zc = SHAFT_Z;
        // Walls and core
        for (int x = -3; x <= 3; x++) {
            for (int z = zc - 3; z <= zc + 3; z++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z - zc) == 3;
                boolean core = Math.abs(x) <= 1 && Math.abs(z - zc) <= 1;
                for (int y = 0; y <= surface; y++) {
                    if (y == 0) bp.set(x, 0, z, floor.pick(bp.random));
                    else if (wall) bp.setUnlessAir(x, y, z, walls.pick(bp.random));
                    else if (core) bp.set(x, y, z, (y % 6 == 3) ? s(ModBlocks.STARLIGHT_LAMP.get()) : s(ModBlocks.VOID_PILLAR.get()));
                    else bp.set(x, y, z, air);
                }
            }
        }
        // The spiral stair around the core (16 steps per turn)
        int[][] ring = {{-2, 2}, {-2, 1}, {-2, 0}, {-2, -1}, {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {2, -1}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}};
        for (int n = 0; n < surface; n++) {
            int[] c = ring[n % 16];
            int[] next = ring[(n + 1) % 16];
            Direction dir = Direction.fromDelta(next[0] - c[0], 0, next[1] - c[1]);
            if (dir == null) dir = Direction.NORTH;
            int y = 1 + n;
            bp.set(c[0], y, zc + c[1], stairs(ModBlocks.VOID_BRICK_STAIRS.get(), dir, false));
        }
        // Opening at the bottom towards the Hall of Stars
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 4; y++) bp.set(x, y, zc + 3, air);
    }

    private void shrine(int surface) {
        int zc = SHAFT_Z;
        int y0 = surface;
        Palette plat = new Palette().add(s(ModBlocks.VOID_BRICKS.get()), 50).add(s(ModBlocks.CRACKED_VOID_BRICKS.get()), 30).add(s(Blocks.MOSSY_COBBLESTONE), 10).add(s(Blocks.GRAVEL), 10);
        for (int x = -6; x <= 6; x++) {
            for (int z = zc - 6; z <= zc + 6; z++) {
                boolean opening = Math.abs(x) <= 2 && Math.abs(z - zc) <= 2;
                for (int y = y0 + 1; y <= y0 + 7; y++) bp.set(x, y, z, air);
                if (!opening) {
                    if ((Math.abs(x) == 6 || Math.abs(z - zc) == 6) && chance(bp, 0.3F)) continue;
                    bp.set(x, y0, z, plat.pick(bp.random));
                    bp.columnDown(x, y0 - 1, z, 12, s(ModBlocks.VOID_BRICKS.get()));
                }
            }
        }
        // Rail around the opening
        for (int x = -3; x <= 3; x++) {
            for (int z = zc - 3; z <= zc + 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z - zc) == 3) {
                    if (z == zc + 3 && Math.abs(x) <= 1) continue;
                    bp.set(x, y0 + 1, z, s(ModBlocks.VOID_BRICK_WALL.get()));
                }
            }
        }
        // Broken pillars
        int[][] posts = {{-5, zc - 5}, {5, zc - 5}, {-5, zc + 5}, {5, zc + 5}};
        for (int[] p : posts) {
            int h = 2 + bp.random.nextInt(5);
            for (int y = 1; y <= h; y++) bp.set(p[0], y0 + y, p[1], s(ModBlocks.VOID_PILLAR.get()));
            if (bp.random.nextBoolean()) bp.set(p[0], y0 + h + 1, p[1], s(ModBlocks.VOID_BRICK_SLAB.get()));
        }
        // The obelisk: a landmark visible from far away
        int oz = zc - 5;
        for (int x = -1; x <= 1; x++) for (int z = oz - 1; z <= oz + 1; z++) {
            for (int y = 1; y <= 4; y++) bp.set(x, y0 + y, z, y == 4 ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : s(ModBlocks.VOID_BRICKS.get()));
        }
        for (int y = 5; y <= 10; y++) bp.set(0, y0 + y, oz, s(ModBlocks.VOID_PILLAR.get()));
        bp.set(0, y0 + 11, oz, s(ModBlocks.STARLIGHT_LAMP.get()));
        bp.set(0, y0 + 12, oz, s(ModBlocks.VOID_BRICK_WALL.get()));
        bp.set(0, y0 + 13, oz, s(Blocks.LIGHTNING_ROD));
        sign(bp, 0, y0 + 3, oz + 2, Direction.SOUTH, "sign.starfallen.sanctum.1", "sign.starfallen.sanctum.2", "sign.starfallen.sanctum.3", "sign.starfallen.sanctum.4");
    }
}
