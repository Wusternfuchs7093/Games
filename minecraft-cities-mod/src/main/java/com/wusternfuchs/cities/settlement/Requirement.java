package com.wusternfuchs.cities.settlement;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.research.Era;
import com.wusternfuchs.cities.research.ResearchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One condition a settlement must satisfy to reach a tier, with the current value for display. */
public record Requirement(Component label, int current, int required) {
    public boolean met() {
        return current >= required;
    }

    public Component describe() {
        return Component.literal(met() ? "  ✔ " : "  ✘ ").withStyle(met() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.translatable("cities.requirement.line", label, current, required)
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Requirement of(String key, int current, int required) {
        return new Requirement(Component.translatable("cities.requirement." + key), current, required);
    }

    public static List<Requirement> forTier(Settlement s, SettlementTier tier) {
        double scale = CitiesConfig.POPULATION_SCALE.get();
        List<Requirement> list = new ArrayList<>(List.of(
                of("population", s.population(), tier.minPopulation(scale)),
                of("beds", s.beds(), tier.minBeds(scale)),
                of("workstations", s.workstations(), tier.minWorkstations()),
                of("wealth", s.wealth(), tier.minWealth()),
                of("happiness", s.happiness(), tier.minHappiness()),
                of("food", s.food(), 1)
        ));
        for (Map.Entry<BuildingType, Integer> entry : BuildingType.requiredFor(tier).entrySet()) {
            list.add(new Requirement(entry.getKey().displayName(), s.count(entry.getKey()), entry.getValue()));
        }
        if (tier == SettlementTier.CAPITAL) {
            list.add(new Requirement(Component.translatable("cities.requirement.vassals"), s.vassals(), 2));
        }
        Era era = ResearchManager.requiredEra(tier);
        if (era.ordinal() > 0) {
            list.add(new Requirement(Component.translatable("cities.requirement.era", era.displayName()),
                    s.era().ordinal() >= era.ordinal() ? 1 : 0, 1));
        }
        return list;
    }
}
