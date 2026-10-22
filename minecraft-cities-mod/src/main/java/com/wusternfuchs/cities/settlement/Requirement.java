package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.CitiesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** One condition a settlement must satisfy to reach a tier, with the current value for display. */
public record Requirement(String key, int current, int required) {
    public boolean met() {
        return current >= required;
    }

    public Component describe() {
        return Component.literal(met() ? "  ✔ " : "  ✘ ").withStyle(met() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.translatable("cities.requirement." + key, current, required)
                        .withStyle(ChatFormatting.GRAY));
    }

    public static List<Requirement> forTier(Settlement s, SettlementTier tier) {
        double scale = CitiesConfig.POPULATION_SCALE.get();
        return List.of(
                new Requirement("population", s.population(), tier.minPopulation(scale)),
                new Requirement("beds", s.beds(), tier.minBeds(scale)),
                new Requirement("workstations", s.workstations(), tier.minWorkstations()),
                new Requirement("wealth", s.wealth(), tier.minWealth()),
                new Requirement("happiness", s.happiness(), tier.minHappiness()),
                new Requirement("food", s.food(), 1)
        );
    }
}
