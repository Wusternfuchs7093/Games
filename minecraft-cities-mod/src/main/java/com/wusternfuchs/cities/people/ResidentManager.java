package com.wusternfuchs.cities.people;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.data.CultureNames;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;

/** Names, temperaments, classes and ages of the people living in a settlement. */
public final class ResidentManager {
    private static final String KEY_TRAIT = "cities_trait";
    private static final String KEY_BORN = "cities_born";
    private static final String KEY_NAMED = "cities_named";
    private static final String KEY_OLD_AGE = "cities_old_age";

    private static final String[] PLAINS = {"Hans", "Greta", "Friedrich", "Liesel", "Klaus", "Ingrid", "Otto", "Heidi",
            "Wilhelm", "Ursula", "Konrad", "Marta", "Dieter", "Anke", "Ernst", "Frieda"};
    private static final String[] DESERT = {"Amir", "Dalia", "Farid", "Hana", "Idris", "Jamila", "Kasim", "Layla",
            "Nadir", "Rana", "Samir", "Yara", "Zaid", "Leila", "Omar", "Safa"};
    private static final String[] SAVANNA = {"Abeni", "Chike", "Dayo", "Ekon", "Femi", "Imani", "Jabari", "Kesi",
            "Nia", "Obi", "Sefu", "Zola", "Amara", "Kito", "Malik", "Tendai"};
    private static final String[] SNOWY = {"Aslaug", "Bjorn", "Dagny", "Eirik", "Frida", "Gunnar", "Hilde", "Ivar",
            "Kari", "Leif", "Sigrid", "Torvald", "Ylva", "Ragna", "Sven", "Astrid"};
    private static final String[] TAIGA = {"Ivan", "Darya", "Mikhail", "Anya", "Boris", "Olga", "Dmitri", "Vera",
            "Fyodor", "Nadya", "Pavel", "Lyudmila", "Yaroslav", "Zlata", "Gleb", "Milena"};
    private static final String[] OTHER = {"Arthur", "Edith", "George", "Mabel", "Henry", "Florence", "William",
            "Agnes", "Thomas", "Beatrice", "Oliver", "Eleanor", "Albert", "Rose", "Edmund", "Matilda"};
    private static final String[] CHERRY = {"Haruto", "Sakura", "Kenji", "Yuki", "Takeshi", "Aiko", "Hiroshi", "Emi",
            "Daichi", "Hana", "Ren", "Mei", "Sora", "Rin", "Kaito", "Nao"};
    private static final String[] JUNGLE = {"Itzel", "Balam", "Ixchel", "Kinich", "Nikte", "Chac", "Yaretzi", "Kan",
            "Sak", "Ek", "Muluk", "Ixel", "Pakal", "Kukul", "Yax", "Naal"};

    private ResidentManager() {
    }

    /** Runs once per census over the villagers found in the settlement. */
    public static void process(ServerLevel level, Settlement s, List<Villager> residents) {
        long day = SettlementManager.day(level);
        int lifespan = CitiesConfig.LIFESPAN_DAYS.get();
        RandomSource random = level.getRandom();
        int[] classes = new int[SocialClass.values().length];
        int[] traits = new int[Trait.values().length];
        Villager oldest = null;
        long oldestAge = -1;

        for (Villager villager : residents) {
            CompoundTag data = villager.getPersistentData();
            if (!data.contains(KEY_BORN)) {
                // Grown-ups who were here before us get a plausible age.
                long age = villager.isBaby() ? 0 : random.nextInt(Math.max(1, lifespan > 0 ? lifespan / 2 : 20));
                data.putLong(KEY_BORN, day - age);
            }
            if (Trait.byId(data.getString(KEY_TRAIT)) == null) {
                data.putString(KEY_TRAIT, Trait.values()[random.nextInt(Trait.values().length)].id());
            }
            if (CitiesConfig.NAME_VILLAGERS.get() && !villager.hasCustomName() && !data.getBoolean(KEY_NAMED)) {
                villager.setCustomName(Component.literal(randomName(s.style(), random)));
                data.putBoolean(KEY_NAMED, true);
            }
            if (villager.isBaby()) {
                continue;
            }
            classes[SocialClass.of(villager).ordinal()]++;
            traits[trait(villager).ordinal()]++;
            long age = day - data.getLong(KEY_BORN);
            if (age > oldestAge) {
                oldestAge = age;
                oldest = villager;
            }
        }
        s.setPeople(classes, traits);

        // At most one villager passes away of old age per census.
        if (lifespan > 0 && oldest != null && oldestAge > lifespan) {
            oldest.getPersistentData().putBoolean(KEY_OLD_AGE, true);
            oldest.hurt(level.damageSources().generic(), Float.MAX_VALUE);
        }
    }

    public static Trait trait(Villager villager) {
        Trait trait = Trait.byId(villager.getPersistentData().getString(KEY_TRAIT));
        return trait == null ? Trait.CHEERFUL : trait;
    }

    public static long age(ServerLevel level, Villager villager) {
        CompoundTag data = villager.getPersistentData();
        return data.contains(KEY_BORN) ? SettlementManager.day(level) - data.getLong(KEY_BORN) : 0;
    }

    public static boolean diedOfOldAge(Villager villager) {
        return villager.getPersistentData().getBoolean(KEY_OLD_AGE);
    }

    public static String nameOf(Villager villager) {
        return villager.hasCustomName() ? villager.getCustomName().getString() : "";
    }

    /** Shift-clicking a villager tells who they are. */
    public static void describe(ServerLevel level, ServerPlayer player, Villager villager) {
        Settlement s = SettlementData.get(level).at(villager.blockPosition());
        Component name = villager.hasCustomName() ? villager.getCustomName() : villager.getName();
        Component message = Component.empty()
                .append(name.copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(villager.isBaby() ? Component.translatable("cities.class.child") : SocialClass.of(villager).displayName())
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(trait(villager).displayName())
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("cities.resident.age", age(level, villager)));
        if (s != null) {
            message = message.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(s.name()).withStyle(ChatFormatting.YELLOW));
        }
        player.displayClientMessage(message, true);
    }

    private static String randomName(ArchitectureStyle style, RandomSource random) {
        CultureNames.Names custom = CultureNames.get(style);
        if (custom != null && !custom.people().isEmpty()) {
            return custom.people().get(random.nextInt(custom.people().size()));
        }
        String[] names = switch (style) {
            case PLAINS -> PLAINS;
            case DESERT -> DESERT;
            case SAVANNA -> SAVANNA;
            case SNOWY -> SNOWY;
            case TAIGA -> TAIGA;
            case OTHER -> OTHER;
            case CHERRY -> CHERRY;
            case JUNGLE -> JUNGLE;
        };
        return names[random.nextInt(names.length)];
    }
}
