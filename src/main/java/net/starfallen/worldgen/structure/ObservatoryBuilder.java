package net.starfallen.worldgen.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.starfallen.block.AstralTelescopeBlock;
import net.starfallen.registry.ModBlocks;
import net.starfallen.registry.ModEntities;

import static net.starfallen.worldgen.structure.BuildKit.*;

/**
 * Astral Observatory layout (local coordinates, y = 0 is the ground floor):
 * <pre>
 *  y 22..33  observation dome with the Grand Telescope and the High Starseer
 *  y 15..20  orrery laboratory
 *  y  8..13  star library
 *  y  1..6   entrance hall (secret trapdoor under a carpet)
 *  y -6..-1  hidden vault
 * </pre>
 */
final class ObservatoryBuilder {
    private static final double WALL_IN = 6.5, WALL_OUT = 7.5;
    private static final int TOWER_TOP = 25;
    private static final int[] FLOORS = {0, 7, 14, 21};

    private ObservatoryBuilder() {}

    static void build(Blueprint bp) {
        BlockState air = Blocks.AIR.defaultBlockState();
        Palette bricks = new Palette().add(s(ModBlocks.ASTRAL_BRICKS.get()), 70).add(s(ModBlocks.CRACKED_ASTRAL_BRICKS.get()), 22)
                .add(s(Blocks.CALCITE), 8);
        Palette ground = new Palette().add(s(ModBlocks.ASTRAL_BRICKS.get()), 30).add(s(ModBlocks.CRACKED_ASTRAL_BRICKS.get()), 15)
                .add(s(Blocks.STONE_BRICKS), 15).add(s(Blocks.CRACKED_STONE_BRICKS), 10).add(s(Blocks.GRAVEL), 8)
                .add(s(Blocks.COARSE_DIRT), 8).add(s(Blocks.MOSS_BLOCK), 6).add(s(Blocks.GRASS_BLOCK), 8);
        Palette floor = new Palette().add(s(ModBlocks.ASTRAL_BRICKS.get()), 60).add(s(Blocks.POLISHED_DIORITE), 25).add(s(ModBlocks.CRACKED_ASTRAL_BRICKS.get()), 15);

        // ---- Site: clear the air above the courtyard and lay the platform ----
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double d = Math.sqrt(x * x + z * z);
                boolean edge = Math.abs(x) >= 15 || Math.abs(z) >= 15;
                if (edge && chance(bp, 0.35F)) continue; // crumbling edges
                bp.air(x, 1, z, x, 14, z);
                if (d >= WALL_OUT) {
                    bp.set(x, 0, z, ground.pick(bp.random));
                    bp.columnDown(x, -1, z, 20, s(Blocks.STONE_BRICKS));
                }
            }
        }
        // Path to the door
        for (int z = 8; z <= 16; z++) {
            for (int x = -1; x <= 1; x++) {
                bp.set(x, 0, z, x == 0 ? s(ModBlocks.CHISELED_ASTRAL_BRICKS.get()) : s(Blocks.DIRT_PATH));
            }
        }
        // Ruined perimeter wall
        for (int i = -16; i <= 16; i++) {
            ruinWall(bp, i, -16);
            if (Math.abs(i) > 2) ruinWall(bp, i, 16);
            ruinWall(bp, -16, i);
            ruinWall(bp, 16, i);
        }
        // Lamp posts along the path
        for (int side = -1; side <= 1; side += 2) {
            for (int z : new int[]{10, 15}) {
                bp.set(side * 3, 1, z, s(ModBlocks.ASTRAL_BRICK_WALL.get()));
                bp.set(side * 3, 2, z, s(ModBlocks.ASTRAL_BRICK_WALL.get()));
                bp.set(side * 3, 3, z, s(ModBlocks.STARLIGHT_LAMP.get()));
            }
        }
        garden(bp, -12, -12);
        garden(bp, 12, -12);
        garden(bp, -12, 11);
        miniCrater(bp, 11, 11);

        // ---- Tower shell ----
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < WALL_OUT) bp.columnDown(x, -8, z, 12, s(Blocks.STONE_BRICKS));
                if (d >= WALL_IN && d < WALL_OUT) {
                    boolean pillar = Math.abs(Math.abs(x) - Math.abs(z)) <= 0 && Math.abs(x) >= 4;
                    for (int y = -7; y <= TOWER_TOP; y++) {
                        BlockState st = pillar ? s(ModBlocks.ASTRAL_PILLAR.get())
                                : (y == 6 || y == 13 || y == 20) ? s(ModBlocks.CHISELED_ASTRAL_BRICKS.get())
                                : y < 0 ? s(ModBlocks.VOID_BRICKS.get()) : bricks.pick(bp.random);
                        bp.set(x, y, z, st);
                    }
                } else if (d < WALL_IN) {
                    // Interior levels
                    for (int y = 1; y <= TOWER_TOP; y++) bp.set(x, y, z, air);
                    for (int fy : FLOORS) bp.set(x, fy, z, floor.pick(bp.random));
                }
            }
        }
        // Windows on each floor (N, E, W; south above the ground floor)
        for (int base : new int[]{0, 7, 14}) {
            for (int y = base + 2; y <= base + 4; y++) {
                for (int i = -1; i <= 1; i++) {
                    window(bp, i, y, -7);
                    window(bp, 7, y, i);
                    window(bp, -7, y, i);
                    if (base > 0) window(bp, i, y, 7);
                }
            }
        }
        // Door
        for (int z = 6; z <= 8; z++) {
            for (int x = -1; x <= 1; x++) bp.air(x, 1, z, x, 3, z);
            bp.set(0, 4, z, air);
        }
        bp.set(-1, 4, 7, stairs(ModBlocks.ASTRAL_BRICK_STAIRS.get(), Direction.EAST, true));
        bp.set(1, 4, 7, stairs(ModBlocks.ASTRAL_BRICK_STAIRS.get(), Direction.WEST, true));
        bp.set(0, 5, 7, s(ModBlocks.CHISELED_ASTRAL_BRICKS.get()));
        bp.set(-2, 3, 8, s(Blocks.SOUL_LANTERN));
        bp.set(2, 3, 8, s(Blocks.SOUL_LANTERN));
        bp.set(-2, 2, 8, s(ModBlocks.ASTRAL_BRICK_WALL.get()));
        bp.set(-2, 1, 8, s(ModBlocks.ASTRAL_BRICK_WALL.get()));
        bp.set(2, 2, 8, s(ModBlocks.ASTRAL_BRICK_WALL.get()));
        bp.set(2, 1, 8, s(ModBlocks.ASTRAL_BRICK_WALL.get()));

        // ---- Staircases between floors ----
        for (int k = 0; k < 3; k++) staircase(bp, k);

        groundFloor(bp);
        library(bp);
        laboratory(bp);
        dome(bp);
        basement(bp);
    }

    private static void window(Blueprint bp, int x, int y, int z) {
        bp.set(x, y, z, s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE));
    }

    private static void ruinWall(Blueprint bp, int x, int z) {
        int h = bp.random.nextInt(4);
        for (int y = 1; y <= h; y++) {
            bp.set(x, y, z, y == h && chance(bp, 0.5F) ? s(ModBlocks.ASTRAL_BRICK_WALL.get())
                    : chance(bp, 0.3F) ? s(ModBlocks.CRACKED_ASTRAL_BRICKS.get()) : s(ModBlocks.ASTRAL_BRICKS.get()));
        }
        if (h == 0 && chance(bp, 0.3F)) bp.set(x, 1, z, slab(ModBlocks.ASTRAL_BRICK_SLAB.get(), false));
    }

    private static void garden(Blueprint bp, int cx, int cz) {
        Block[] flowers = {Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY, Blocks.OXEYE_DAISY, Blocks.BLUE_ORCHID};
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                bp.set(x, 0, z, s(Blocks.GRASS_BLOCK));
                if (chance(bp, 0.55F)) bp.set(x, 1, z, s(flowers[bp.random.nextInt(flowers.length)]));
                else if (chance(bp, 0.3F)) bp.set(x, 1, z, s(Blocks.GRASS));
            }
        }
        bp.set(cx, 1, cz, s(ModBlocks.ASTRAL_BRICK_WALL.get()));
        bp.set(cx, 2, cz, s(ModBlocks.STARLIGHT_LAMP.get()));
    }

    /** The star that fell here first, long ago - why the Order built their tower on this spot. */
    private static void miniCrater(Blueprint bp, int cx, int cz) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 3.2) continue;
                bp.set(x, 0, z, d < 1.5 ? s(ModBlocks.METEORIC_IRON_ORE.get()) : s(ModBlocks.METEORITE.get()));
                if (d < 2.5 && d >= 1.2 && chance(bp, 0.4F)) {
                    bp.set(x, 1, z, ModBlocks.STARLIT_CRYSTAL.get().defaultBlockState());
                }
            }
        }
        bp.set(cx, 1, cz, s(ModBlocks.FALLEN_STAR.get()));
    }

    private static void staircase(Blueprint bp, int k) {
        int base = FLOORS[k];
        boolean even = k % 2 == 0;
        int xa = even ? 4 : -5, xb = even ? 5 : -4;
        Direction up = even ? Direction.NORTH : Direction.SOUTH;
        for (int i = 0; i <= 6; i++) {
            int z = even ? 3 - i : -3 + i;
            int y = base + 1 + i;
            for (int x = xa; x <= xb; x++) {
                bp.set(x, y, z, stairs(ModBlocks.ASTRAL_BRICK_STAIRS.get(), up, false));
                // Support under each step
                for (int yy = base + 1; yy < y; yy++) bp.set(x, yy, z, s(ModBlocks.ASTRAL_BRICKS.get()));
                if (i >= 3) bp.set(x, base + 7, z, i == 6 ? stairs(ModBlocks.ASTRAL_BRICK_STAIRS.get(), up, false) : Blocks.AIR.defaultBlockState());
                for (int yy = y + 1; yy <= Math.min(base + 6, y + 3); yy++) bp.set(x, yy, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void groundFloor(Blueprint bp) {
        // Star-map mosaic
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 3.2) bp.set(x, 0, z, d < 1 ? s(ModBlocks.CHISELED_ASTRAL_BRICKS.get()) : s(ModBlocks.STARFIELD_TILES.get()));
            }
        }
        bp.set(0, 0, 0, s(ModBlocks.STARLIGHT_LAMP.get()));
        bp.set(-2, 0, 1, s(ModBlocks.STARLIGHT_LAMP.get()));
        bp.set(2, 0, -1, s(ModBlocks.STARLIGHT_LAMP.get()));
        // Furniture
        bp.set(-4, 1, -3, s(Blocks.CARTOGRAPHY_TABLE));
        bp.set(-3, 1, -4, s(Blocks.CRAFTING_TABLE));
        lecternBook(bp, -2, 1, -5, Direction.SOUTH, "book.starfallen.observatory.title",
                "book.starfallen.observatory.1", "book.starfallen.observatory.2", "book.starfallen.observatory.3");
        chest(bp, -5, 1, 1, Direction.EAST, "observatory_common");
        barrel(bp, -5, 1, 2, Direction.EAST, "observatory_common");
        // A second "chest" that is not a chest at all
        mimic(bp, -5, 1, 0, Direction.EAST.toYRot());
        candles(bp, -4, 2, -3, 3, true);
        hangingLantern(bp, 0, 7, 0, 1, false);
        hangingLantern(bp, -3, 7, 3, 0, true);
        // Secret: trapdoor hidden under a rug, ladder down to the vault
        bp.set(-3, 0, 3, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH));
        bp.set(-3, 1, 3, s(Blocks.RED_CARPET));
        for (int x = -4; x <= -2; x++) for (int z = 2; z <= 4; z++) if (!(x == -3 && z == 3)) bp.set(x, 1, z, s(Blocks.PURPLE_CARPET));
        seer(bp, 1.5, 1, -2.5, 180, false);
        seer(bp, -1.5, 1, 3.5, 0, false);
    }

    private static void library(Blueprint bp) {
        int base = 7;
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= 5.4 && d < WALL_IN) {
                    boolean window = (Math.abs(x) <= 1 && Math.abs(z) >= 5) || (Math.abs(z) <= 1 && Math.abs(x) >= 5);
                    boolean stairsHere = (x >= 3 && z <= 3 && z >= -4) || (x <= -3 && z >= -3 && z <= 3);
                    if (window || stairsHere) continue;
                    for (int y = base + 1; y <= base + 4; y++) bp.set(x, y, z, s(Blocks.BOOKSHELF));
                }
            }
        }
        bp.set(0, base + 1, 0, s(Blocks.DARK_OAK_FENCE));
        bp.set(0, base + 2, 0, slab(Blocks.DARK_OAK_SLAB, false));
        candles(bp, 0, base + 3, 0, 4, true);
        lecternBook(bp, 0, base + 1, -2, Direction.SOUTH, "book.starfallen.sanctum.title",
                "book.starfallen.sanctum.1", "book.starfallen.sanctum.2", "book.starfallen.sanctum.3");
        chest(bp, 1, base + 1, 3, Direction.NORTH, "observatory_library");
        bp.set(-1, base + 1, 3, s(Blocks.CHISELED_BOOKSHELF));
        hangingLantern(bp, 0, base + 7, 2, 1, false);
        seer(bp, -1.5, base + 1, 1.5, 90, false);
    }

    private static void laboratory(Blueprint bp) {
        int base = 14;
        bp.set(-3, base + 1, -3, s(Blocks.BREWING_STAND));
        bp.set(-2, base + 1, -4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        bp.set(-4, base + 1, -2, s(Blocks.CAULDRON));
        chest(bp, -4, base + 1, 1, Direction.EAST, "observatory_lab");
        barrel(bp, -4, base + 1, 2, Direction.EAST, "observatory_lab");
        bp.set(2, base + 1, 4, s(Blocks.ENCHANTING_TABLE));
        // The orrery: a sun and its planets hanging beneath the ceiling
        bp.set(0, base + 5, 0, s(Blocks.CHAIN));
        bp.set(0, base + 4, 0, s(Blocks.SHROOMLIGHT));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 3), pz = (int) Math.round(Math.sin(a) * 3);
            bp.set(px, base + 6, pz, s(Blocks.CHAIN));
            bp.set(px, base + 5, pz, i % 2 == 0 ? s(ModBlocks.STARLIGHT_LAMP.get()) : s(Blocks.AMETHYST_BLOCK));
        }
        bp.set(0, base + 1, 0, s(ModBlocks.CHISELED_ASTRAL_BRICKS.get()));
        bp.set(0, base + 2, 0, s(ModBlocks.FALLEN_STAR.get()));
        seer(bp, 2.5, base + 1, -2.5, 225, false);
    }

    private static void dome(Blueprint bp) {
        int base = 21;
        BlockState glass = s(Blocks.LIGHT_BLUE_STAINED_GLASS);
        BlockState tinted = s(Blocks.TINTED_GLASS);
        int cy = TOWER_TOP;
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                for (int y = cy; y <= cy + 9; y++) {
                    double dx = x, dy = y - cy, dz = z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < 7.3) {
                        bp.set(x, y, z, Blocks.AIR.defaultBlockState());
                    } else if (d < 8.3) {
                        boolean slit = z < 0 && Math.abs(x) <= 1 && y > cy + 1;
                        if (slit) {
                            bp.set(x, y, z, Blocks.AIR.defaultBlockState());
                            continue;
                        }
                        double ang = Math.atan2(z, x);
                        boolean rib = Math.abs(Math.sin(ang * 4)) < 0.2 || y == cy;
                        bp.set(x, y, z, rib ? s(ModBlocks.ASTRAL_PILLAR.get()) : (y - cy) % 3 == 0 ? tinted : glass);
                    }
                }
            }
        }
        bp.set(0, cy + 9, 0, s(Blocks.GOLD_BLOCK));
        bp.set(0, cy + 10, 0, s(Blocks.LIGHTNING_ROD));
        // Telescope platform
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) <= 3) bp.set(x, base + 1, z, slab(ModBlocks.ASTRAL_BRICK_SLAB.get(), false));
            }
        }
        bp.set(0, base + 1, 0, s(ModBlocks.CHISELED_ASTRAL_BRICKS.get()));
        bp.set(0, base + 2, 0, ModBlocks.ASTRAL_TELESCOPE.get().defaultBlockState().setValue(AstralTelescopeBlock.FACING, Direction.NORTH));
        chest(bp, 0, base + 1, 5, Direction.NORTH, "observatory_top");
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * 5.5), pz = (int) Math.round(Math.sin(a) * 5.5);
            if (px >= 3 && pz <= 3) continue; // keep the stairwell exit clear
            bp.set(px, base + 1, pz, s(ModBlocks.STARLIGHT_LAMP.get()));
        }
        seer(bp, 3.5, base + 1, 1.5, 270, true);
    }

    private static void basement(Blueprint bp) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 5.5) {
                    bp.set(x, -7, z, d < 2 ? s(ModBlocks.STARFIELD_TILES.get()) : s(ModBlocks.VOID_BRICKS.get()));
                    for (int y = -6; y <= -1; y++) bp.set(x, y, z, Blocks.AIR.defaultBlockState());
                } else if (d < 6.5) {
                    for (int y = -7; y <= -1; y++) bp.set(x, y, z, y == -4 ? s(ModBlocks.CHISELED_VOID_BRICKS.get()) : s(ModBlocks.VOID_BRICKS.get()));
                }
            }
        }
        // Ladder shaft under the hidden trapdoor
        for (int y = -6; y <= -1; y++) {
            bp.set(-3, y, 4, s(ModBlocks.VOID_BRICKS.get()));
            bp.set(-3, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        chest(bp, 4, -6, 0, Direction.WEST, "observatory_secret");
        chest(bp, 0, -6, -4, Direction.SOUTH, "observatory_secret");
        mimic(bp, 3, -6, -3, Direction.WEST.toYRot());
        bp.set(0, -6, 0, s(ModBlocks.CHISELED_VOID_BRICKS.get()));
        brazier(bp, 0, -5, 0, true);
        lantern(bp, 4, -5, 0, true);
        lecternBook(bp, -4, -6, -1, Direction.EAST, "book.starfallen.secret.title",
                "book.starfallen.secret.1", "book.starfallen.secret.2");
        candles(bp, 2, -6, 3, 3, true);
    }
}
