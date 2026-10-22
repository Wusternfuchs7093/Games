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

    public static final ModConfigSpec.DoubleValue OLD_TOWN_CHANCE = BUILDER
            .comment("Chance that a newly found village with good land starts as an old, wealthy town that builds up fast.")
            .defineInRange("oldTownChance", 0.15, 0.0, 1.0);

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

    public static final ModConfigSpec.BooleanValue WARS_ENABLED = BUILDER
            .comment("If true, hostile settlements wage wars and the losers become vassals.")
            .define("warsEnabled", true);

    public static final ModConfigSpec.DoubleValue SIEGE_CHANCE = BUILDER
            .comment("Chance per night-time census and per settlement tier that a siege starts.")
            .defineInRange("siegeChancePerTier", 0.01, 0.0, 1.0);

    static {
        BUILDER.pop().push("trade");
    }

    public static final ModConfigSpec.BooleanValue TRADE_ENABLED = BUILDER
            .comment("If true, settlements with a warehouse or market send caravans with their surplus.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue ROBBERY_CHANCE = BUILDER
            .comment("Chance that a caravan is robbed on the way, before the sender's guards are counted.")
            .defineInRange("robberyChance", 0.15, 0.0, 1.0);

    static {
        BUILDER.pop().push("government");
    }

    public static final ModConfigSpec.IntValue ELECTION_INTERVAL_DAYS = BUILDER
            .comment("In-game days between elections. Elections only happen if a player is running or rules.")
            .defineInRange("electionIntervalDays", 7, 1, 365);

    public static final ModConfigSpec.BooleanValue PLAYER_FOUNDING = BUILDER
            .comment("If true, players may found their own settlement with a bell and /city found.")
            .define("allowPlayerFounding", true);

    public static final ModConfigSpec.IntValue MAX_TOWNS_PER_PLAYER = BUILDER
            .comment("How many settlements one player may rule at once (founded or won in elections) to found another.")
            .defineInRange("maxTownsPerPlayer", 1, 0, 100);

    public static final ModConfigSpec.BooleanValue PLAYER_WARS = BUILDER
            .comment("If true, player mayors may declare war on other settlements.")
            .define("allowPlayerWars", true);

    public static final ModConfigSpec.BooleanValue PROTECT_BUILDINGS = BUILDER
            .comment("If true, only the mayor, operators and players with at least Friend reputation may break or place",
                    "blocks inside a settlement's buildings. Bought houses are always protected for their owner.")
            .define("protectBuildings", false);

    static {
        BUILDER.pop().push("disasters");
    }

    public static final ModConfigSpec.BooleanValue DISASTERS_ENABLED = BUILDER
            .comment("If true, settlements suffer fires, epidemics and droughts.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue FIRE_CHANCE = BUILDER
            .comment("Chance per census and per wooden building (up to 10) that a fire breaks out; wells divide it.")
            .defineInRange("fireChance", 0.004, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue DISEASE_CHANCE = BUILDER
            .comment("Base chance per census that an epidemic starts; crowding, no well and hunger raise it.")
            .defineInRange("diseaseChance", 0.004, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue DROUGHT_CHANCE = BUILDER
            .comment("Chance per census of a drought when it is not raining.")
            .defineInRange("droughtChance", 0.003, 0.0, 1.0);

    static {
        BUILDER.pop().push("people");
    }

    public static final ModConfigSpec.IntValue LIFESPAN_DAYS = BUILDER
            .comment("In-game days a villager lives before dying of old age. 0 means villagers never age.")
            .defineInRange("lifespanDays", 60, 0, 10000);

    public static final ModConfigSpec.BooleanValue NAME_VILLAGERS = BUILDER
            .comment("If true, villagers in settlements get names from their culture (shown when you look at them).")
            .define("nameVillagers", true);

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
