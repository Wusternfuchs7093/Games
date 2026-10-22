package com.wusternfuchs.cities.quest;

import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.building.Building;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.entity.CitiesEntities;
import com.wusternfuchs.cities.entity.Guard;
import com.wusternfuchs.cities.governance.GovernanceManager;
import com.wusternfuchs.cities.settlement.ReputationRank;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.trade.Caravan;
import com.wusternfuchs.cities.war.War;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What a town offers players: houses for sale, a tavern full of rumours and swords for hire. */
public final class TownServices {
    public static final int HOUSE_PRICE = 32;
    public static final int MANOR_PRICE = 64;
    public static final int MERCENARY_PRICE = 16;
    public static final int RUMOUR_PRICE = 1;
    public static final int MERCENARY_DAYS = 3;
    private static final int STRUCTURE_SEARCH_RADIUS = 64;

    private record Rumoured(TagKey<Structure> tag, String key) {
    }

    private static final List<Rumoured> STRUCTURES = List.of(
            new Rumoured(StructureTags.VILLAGE, "village"),
            new Rumoured(StructureTags.ON_TREASURE_MAPS, "treasure"),
            new Rumoured(StructureTags.ON_WOODLAND_EXPLORER_MAPS, "mansion"),
            new Rumoured(StructureTags.ON_OCEAN_EXPLORER_MAPS, "monument"),
            new Rumoured(StructureTags.RUINED_PORTAL, "portal"),
            new Rumoured(StructureTags.MINESHAFT, "mineshaft"),
            new Rumoured(StructureTags.SHIPWRECK, "shipwreck"));

    private TownServices() {
    }

    // ------------------------------------------------------------------ houses

    @Nullable
    public static Building buildingAt(Settlement s, BlockPos pos) {
        for (Building building : s.buildings()) {
            if (building.bounds(building.blueprint(s.style())).isInside(pos)) {
                return building;
            }
        }
        return null;
    }

    /** Whether the player may break or place blocks here: bought houses belong to their owners only. */
    public static boolean canEdit(ServerLevel level, Player player, BlockPos pos) {
        if (player.hasPermissions(2)) {
            return true;
        }
        Settlement s = SettlementData.get(level).at(pos);
        if (s == null) {
            return true;
        }
        Building building = buildingAt(s, pos);
        if (building == null) {
            return true;
        }
        UUID owner = s.houseOwners().get(building.origin());
        if (owner != null) {
            return owner.equals(player.getUUID());
        }
        // Optionally the whole town is off limits to anyone it does not count as a friend.
        return !CitiesConfig.PROTECT_BUILDINGS.get() || GovernanceManager.isMayor(s, player)
                || ReputationRank.of(s.reputation(player.getUUID())).ordinal() >= ReputationRank.FRIEND.ordinal();
    }

    public static int price(BuildingType type) {
        return type == BuildingType.MANOR ? MANOR_PRICE : HOUSE_PRICE;
    }

    /** Returns null on success, or the translation key of the reason it failed. */
    @Nullable
    public static String buy(ServerLevel level, ServerPlayer player, Settlement s) {
        Building building = buildingAt(s, player.blockPosition());
        if (building == null || (building.type() != BuildingType.HOUSE && building.type() != BuildingType.MANOR)) {
            return "cities.command.not_in_house";
        }
        if (s.houseOwners().containsKey(building.origin())) {
            return "cities.command.house_taken";
        }
        if (ReputationRank.of(s.reputation(player.getUUID())).ordinal() < ReputationRank.FRIEND.ordinal()) {
            return "cities.command.low_reputation";
        }
        int price = price(building.type());
        if (QuestManager.countOf(player.getInventory(), Items.EMERALD) < price) {
            return "cities.command.not_enough_emeralds";
        }
        QuestManager.take(player.getInventory(), Items.EMERALD, price);
        s.addWealth(price * GovernanceManager.WEALTH_PER_EMERALD);
        s.houseOwners().put(building.origin(), player.getUUID());
        s.changeReputation(player.getUUID(), 10);
        s.record(SettlementManager.day(level), "house_bought", player.getGameProfile().getName());
        SettlementData.get(level).setDirty();
        return null;
    }

    @Nullable
    public static String sell(ServerLevel level, ServerPlayer player, Settlement s) {
        Building building = buildingAt(s, player.blockPosition());
        if (building == null || !player.getUUID().equals(s.houseOwners().get(building.origin()))) {
            return "cities.command.not_your_house";
        }
        s.houseOwners().remove(building.origin());
        int refund = Math.min(price(building.type()) / 2, s.wealth() / GovernanceManager.WEALTH_PER_EMERALD);
        s.addWealth(-refund * GovernanceManager.WEALTH_PER_EMERALD);
        QuestManager.give(player, new ItemStack(Items.EMERALD, refund));
        SettlementData.get(level).setDirty();
        return null;
    }

    public static int housesOwnedBy(Settlement s, UUID player) {
        int count = 0;
        for (Map.Entry<BlockPos, UUID> entry : s.houseOwners().entrySet()) {
            if (entry.getValue().equals(player)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ tavern

    /** One rumour for one emerald: neighbours, wars, caravans, or something worth finding out there. */
    @Nullable
    public static Component rumour(ServerLevel level, ServerPlayer player, Settlement s) {
        if (QuestManager.countOf(player.getInventory(), Items.EMERALD) < RUMOUR_PRICE) {
            return null;
        }
        QuestManager.take(player.getInventory(), Items.EMERALD, RUMOUR_PRICE);
        s.addWealth(RUMOUR_PRICE * GovernanceManager.WEALTH_PER_EMERALD);
        SettlementData data = SettlementData.get(level);
        RandomSource random = level.getRandom();

        int roll = random.nextInt(4);
        if (roll == 0 && !data.wars().isEmpty()) {
            War war = data.wars().get(random.nextInt(data.wars().size()));
            Settlement a = data.byId(war.attacker());
            Settlement b = data.byId(war.defender());
            if (a != null && b != null) {
                return Component.translatable("cities.rumour.war", a.name(), b.name(),
                        war.score() > 0 ? a.name() : b.name());
            }
        }
        if (roll == 1 && !data.caravans().isEmpty()) {
            Caravan caravan = data.caravans().get(random.nextInt(data.caravans().size()));
            Settlement from = data.byId(caravan.from());
            Settlement to = data.byId(caravan.to());
            if (from != null && to != null) {
                return Component.translatable("cities.rumour.caravan", from.name(), to.name(),
                        caravan.stock().displayName());
            }
        }
        if (roll == 2) {
            Settlement closest = null;
            double best = Double.MAX_VALUE;
            for (Settlement candidate : data.all()) {
                double dist = candidate.center().distSqr(s.center());
                if (candidate != s && dist < best) {
                    best = dist;
                    closest = candidate;
                }
            }
            if (closest != null) {
                return Component.translatable("cities.rumour.town", closest.name(), closest.tier().displayName(),
                        direction(s.center(), closest.center()), roundDistance(s.center(), closest.center()));
            }
        }
        Rumoured rumoured = STRUCTURES.get(random.nextInt(STRUCTURES.size()));
        BlockPos found = level.findNearestMapStructure(rumoured.tag(), s.center(), STRUCTURE_SEARCH_RADIUS, false);
        if (found != null) {
            return Component.translatable("cities.rumour.structure",
                    Component.translatable("cities.rumour.structure." + rumoured.key()),
                    direction(s.center(), found), roundDistance(s.center(), found));
        }
        return Component.translatable("cities.rumour.nothing");
    }

    private static Component direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), from.getZ() - to.getZ()));
        String[] winds = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        int index = (int) Math.round(((angle + 360) % 360) / 45.0) % 8;
        return Component.translatable("cities.dir." + winds[index]);
    }

    private static int roundDistance(BlockPos from, BlockPos to) {
        double distance = Math.sqrt(from.distSqr(to));
        return (int) (Math.round(distance / 50.0) * 50);
    }

    // ------------------------------------------------------------------ mercenaries

    @Nullable
    public static String hireMercenary(ServerLevel level, ServerPlayer player, Settlement s) {
        if (s.count(BuildingType.TAVERN) == 0) {
            return "cities.command.no_tavern";
        }
        if (ReputationRank.of(s.reputation(player.getUUID())).ordinal() < ReputationRank.FRIEND.ordinal()) {
            return "cities.command.low_reputation";
        }
        if (!mercenariesOf(level, player).isEmpty()) {
            return "cities.command.already_hired";
        }
        if (QuestManager.countOf(player.getInventory(), Items.EMERALD) < MERCENARY_PRICE) {
            return "cities.command.not_enough_emeralds";
        }
        Guard guard = CitiesEntities.GUARD.get().create(level);
        if (guard == null) {
            return "cities.command.hire_failed";
        }
        QuestManager.take(player.getInventory(), Items.EMERALD, MERCENARY_PRICE);
        s.addWealth(MERCENARY_PRICE * GovernanceManager.WEALTH_PER_EMERALD);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().east(2));
        guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0f);
        guard.enlist(s, pos, 16, s.tier());
        guard.becomeCompanion(player, SettlementManager.day(level) + MERCENARY_DAYS);
        level.addFreshEntity(guard);
        return null;
    }

    public static List<Guard> mercenariesOf(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(Guard.class, new AABB(player.blockPosition()).inflate(96),
                guard -> guard.isAlive() && player.getUUID().equals(guard.owner()));
    }
}
