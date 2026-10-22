package com.wusternfuchs.cities;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server settings (saved in world/serverconfig/cities-server.toml). */
public final class CitiesConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("simulation");
    }

    public static final ModConfigSpec.IntValue CENSUS_INTERVAL = BUILDER
            .comment("How often (in ticks) each loaded settlement is simulated: census, economy, happiness, growth.")
            .defineInRange("censusIntervalTicks", 1200, 100, 72000);

    public static final ModConfigSpec.IntValue DETECTION_INTERVAL = BUILDER
            .comment("How often (in ticks) the area around players is scanned for new villages.")
            .defineInRange("detectionIntervalTicks", 200, 20, 6000);

    public static final ModConfigSpec.IntValue DETECTION_RADIUS = BUILDER
            .comment("Radius (in blocks) around each player that is scanned for village bells.")
            .defineInRange("detectionRadius", 96, 16, 256);

    public static final ModConfigSpec.IntValue MIN_DISTANCE = BUILDER
            .comment("Minimum distance (in blocks) between the centers of two settlements.")
            .defineInRange("minDistanceBetweenSettlements", 160, 32, 2048);

    public static final ModConfigSpec.DoubleValue POPULATION_SCALE = BUILDER
            .comment("Multiplier for population and housing requirements of every tier.")
            .defineInRange("populationScale", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.IntValue FOOD_PER_VILLAGER = BUILDER
            .comment("Food points every villager eats per census.")
            .defineInRange("foodPerVillager", 1, 0, 64);

    static {
        BUILDER.pop().push("growth");
    }

    public static final ModConfigSpec.BooleanValue IGNORE_POTENTIAL = BUILDER
            .comment("If true, every settlement can grow up to a capital regardless of its hidden potential.")
            .define("ignorePotential", false);

    public static final ModConfigSpec.DoubleValue IMMIGRATION_CHANCE = BUILDER
            .comment("Chance per census that a new villager moves into a prosperous settlement with free beds.")
            .defineInRange("immigrationChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue ALLOW_EMIGRATION = BUILDER
            .comment("If true, villagers may leave very unhappy settlements.")
            .define("allowEmigration", true);

    static {
        BUILDER.pop().push("construction");
    }

    public static final ModConfigSpec.BooleanValue CONSTRUCTION_ENABLED = BUILDER
            .comment("If false, settlements never build anything.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue BUILD_INTERVAL = BUILDER
            .comment("Ticks between two blocks placed by a builder. Lower is faster.")
            .defineInRange("buildIntervalTicks", 10, 1, 200);

    public static final ModConfigSpec.BooleanValue FREE_CONSTRUCTION = BUILDER
            .comment("If true, building does not use up the settlement's materials.")
            .define("freeConstruction", false);

    static {
        BUILDER.pop().push("defense");
    }

    public static final ModConfigSpec.BooleanValue GUARDS_ENABLED = BUILDER
            .comment("If true, settlements hire guards with their wealth.")
            .define("guardsEnabled", true);

    public static final ModConfigSpec.BooleanValue SIEGES_ENABLED = BUILDER
            .comment("If true, bands of raiders attack settlements at night while a player is inside.")
            .define("siegesEnabled", true);

    public static final ModConfigSpec.DoubleValue SIEGE_CHANCE = BUILDER
            .comment("Chance per night-time census and per settlement tier that a siege starts.")
            .defineInRange("siegeChancePerTier", 0.01, 0.0, 1.0);

    static {
        BUILDER.pop().push("display");
    }

    public static final ModConfigSpec.BooleanValue ENTER_TITLES = BUILDER
            .comment("Show a title when a player enters a settlement.")
            .define("showEnterTitles", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CitiesConfig() {
    }
}
