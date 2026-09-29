package net.starfallen.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SFConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue STARFALL_CHANCE;
    public static final ForgeConfigSpec.IntValue STARFALL_FIRST_NIGHT;
    public static final ForgeConfigSpec.IntValue METEOR_MIN_INTERVAL;
    public static final ForgeConfigSpec.IntValue METEOR_MAX_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue METEOR_CRATERS;
    public static final ForgeConfigSpec.BooleanValue STAFF_CRATERS;
    public static final ForgeConfigSpec.DoubleValue GOLDEN_METEOR_CHANCE;
    public static final ForgeConfigSpec.DoubleValue EGG_METEOR_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_STARFALL;
    public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue HALO_FLIGHT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Starfall - the meteor shower event").push("starfall");
        STARFALL_CHANCE = b.comment("Chance (0-1) that any given night becomes a Starfall night.")
                .defineInRange("starfallChance", 0.2D, 0.0D, 1.0D);
        STARFALL_FIRST_NIGHT = b.comment("Earliest in-game day on which a natural Starfall can happen (0 = the very first night).")
                .defineInRange("firstStarfallDay", 1, 0, 1000);
        METEOR_MIN_INTERVAL = b.comment("Minimum ticks between meteors per player during a Starfall.")
                .defineInRange("meteorMinInterval", 90, 10, 24000);
        METEOR_MAX_INTERVAL = b.comment("Maximum ticks between meteors per player during a Starfall.")
                .defineInRange("meteorMaxInterval", 260, 10, 24000);
        METEOR_CRATERS = b.comment("Whether falling meteors carve craters into the terrain.")
                .define("meteorCraters", true);
        GOLDEN_METEOR_CHANCE = b.comment("Chance that a landing meteor is a rare Golden Meteor full of treasure.")
                .defineInRange("goldenMeteorChance", 0.04D, 0.0D, 1.0D);
        EGG_METEOR_CHANCE = b.comment("Chance that a large meteor carries a Stellar Egg.")
                .defineInRange("eggMeteorChance", 0.12D, 0.0D, 1.0D);
        ANNOUNCE_STARFALL = b.comment("Announce Starfall nights to all players with a title and sound.")
                .define("announceStarfall", true);
        b.pop();

        b.comment("Gear and bosses").push("gear");
        STAFF_CRATERS = b.comment("Whether meteors called by the Starcaller Staff carve small craters (also requires the mobGriefing gamerule).")
                .define("staffCraters", true);
        BOSS_HEALTH_MULTIPLIER = b.comment("Multiplier applied to Astraeon's maximum health.")
                .defineInRange("bossHealthMultiplier", 1.0D, 0.1D, 20.0D);
        HALO_FLIGHT = b.comment("Whether the Halo of the Fallen Star grants creative-style flight.")
                .define("haloFlight", true);
        b.pop();

        SPEC = b.build();
    }

    private SFConfig() {}
}
