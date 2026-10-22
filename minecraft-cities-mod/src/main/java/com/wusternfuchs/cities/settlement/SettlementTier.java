package com.wusternfuchs.cities.settlement;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Development stages of a settlement, from a lone farmstead to a capital. */
public enum SettlementTier {
    //         population, beds, workstations, wealth, happiness, territory radius (chunks)
    HAMLET(1, 1, 0, 0, 0, 2),
    VILLAGE(5, 5, 2, 0, 30, 2),
    TOWNSHIP(16, 16, 6, 256, 45, 3),
    SMALL_TOWN(36, 36, 14, 1024, 55, 4),
    CITY(71, 71, 28, 4096, 60, 5),
    METROPOLIS(151, 151, 60, 16384, 65, 6),
    CAPITAL(300, 300, 120, 65536, 70, 8);

    private final int minPopulation;
    private final int minBeds;
    private final int minWorkstations;
    private final int minWealth;
    private final int minHappiness;
    private final int radiusChunks;

    SettlementTier(int minPopulation, int minBeds, int minWorkstations, int minWealth, int minHappiness, int radiusChunks) {
        this.minPopulation = minPopulation;
        this.minBeds = minBeds;
        this.minWorkstations = minWorkstations;
        this.minWealth = minWealth;
        this.minHappiness = minHappiness;
        this.radiusChunks = radiusChunks;
    }

    public int minPopulation(double scale) {
        return Math.max(1, (int) Math.ceil(minPopulation * scale));
    }

    public int minBeds(double scale) {
        return Math.max(1, (int) Math.ceil(minBeds * scale));
    }

    public int minWorkstations() {
        return minWorkstations;
    }

    public int minWealth() {
        return minWealth;
    }

    public int minHappiness() {
        return minHappiness;
    }

    public int radiusChunks() {
        return radiusChunks;
    }

    public SettlementTier next() {
        return this == CAPITAL ? null : values()[ordinal() + 1];
    }

    public SettlementTier previous() {
        return this == HAMLET ? null : values()[ordinal() - 1];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.tier." + id());
    }

    public static SettlementTier byId(String id) {
        for (SettlementTier tier : values()) {
            if (tier.id().equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }
}
