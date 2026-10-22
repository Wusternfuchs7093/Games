package com.wusternfuchs.cities.people;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Locale;

/** Where a villager stands in town society, from their profession and mastery. */
public enum SocialClass {
    PEASANT,
    ARTISAN,
    SCHOLAR,
    ELITE;

    /** Villagers at this trade level or higher count as the town's elite. */
    public static final int ELITE_LEVEL = 4;

    public static SocialClass of(Villager villager) {
        if (villager.getVillagerData().getLevel() >= ELITE_LEVEL) {
            return ELITE;
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.LIBRARIAN || profession == VillagerProfession.CLERIC
                || profession == VillagerProfession.CARTOGRAPHER) {
            return SCHOLAR;
        }
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT
                || profession == VillagerProfession.FARMER || profession == VillagerProfession.FISHERMAN
                || profession == VillagerProfession.SHEPHERD) {
            return PEASANT;
        }
        return ARTISAN;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("cities.class." + id());
    }
}
