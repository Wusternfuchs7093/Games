package com.wusternfuchs.cities.quest;

import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.defense.DefenseManager;
import com.wusternfuchs.cities.governance.GovernanceManager;
import com.wusternfuchs.cities.people.ResidentManager;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.Stock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** The settlement's job board and the personal wishes of its villagers. */
public final class QuestManager {
    private static final int CONTRACT_DAYS = 3;
    private static final int REQUEST_DAYS = 2;
    private static final int MAX_REQUESTS = 3;
    private static final double REQUEST_CHANCE = 0.3;
    private static final int REPUTATION_PER_CONTRACT = 5;
    private static final int REPUTATION_PER_REQUEST = 5;

    /** Something a town may ask for: an item, how many and what it pays in emeralds. */
    private record Template(Item item, int count, int reward) {
    }

    private static final Template[] MATERIALS = {
            new Template(Items.OAK_LOG, 32, 6), new Template(Items.COBBLESTONE, 64, 4), new Template(Items.GLASS, 16, 4)};
    private static final Template[] FOOD = {
            new Template(Items.BREAD, 16, 5), new Template(Items.CARROT, 32, 4), new Template(Items.COOKED_BEEF, 8, 4)};
    private static final Template[] DEFENSE = {new Template(Items.IRON_INGOT, 12, 8)};
    private static final Template[] LEARNING = {new Template(Items.BOOK, 6, 6), new Template(Items.PAPER, 24, 4)};
    private static final Template[] ANYTHING = {
            new Template(Items.TORCH, 64, 3), new Template(Items.HAY_BLOCK, 8, 4), new Template(Items.WHITE_WOOL, 16, 4)};

    private static final Item[] WISHES = {Items.CAKE, Items.COOKIE, Items.HONEY_BOTTLE, Items.BOOK, Items.POPPY,
            Items.SUNFLOWER, Items.GOLDEN_CARROT, Items.PUMPKIN_PIE, Items.GLOW_BERRIES, Items.AMETHYST_SHARD,
            Items.CANDLE, Items.APPLE};

    private QuestManager() {
    }

    public static int maxContracts(Settlement s) {
        return 3 + s.count(BuildingType.MARKET);
    }

    // ------------------------------------------------------------------ census

    public static void update(ServerLevel level, Settlement s, List<Villager> residents) {
        long day = SettlementManager.day(level);
        s.contracts().removeIf(c -> c.expires() < day);
        s.requests().removeIf(r -> r.expires() < day);
        RandomSource random = level.getRandom();

        if (s.contracts().size() < maxContracts(s) && s.wealth() >= 27) {
            s.contracts().add(newContract(s, random, day));
        }
        if (s.requests().size() < MAX_REQUESTS && !residents.isEmpty() && random.nextDouble() < REQUEST_CHANCE) {
            Villager villager = residents.get(random.nextInt(residents.size()));
            String name = ResidentManager.nameOf(villager);
            boolean asked = s.requests().stream().anyMatch(r -> r.villager().equals(villager.getUUID()));
            if (!villager.isBaby() && !name.isEmpty() && !asked) {
                Item wish = WISHES[random.nextInt(WISHES.length)];
                s.requests().add(new Request(villager.getUUID(), name, BuiltInRegistries.ITEM.getKey(wish).toString(),
                        1 + random.nextInt(3), day + REQUEST_DAYS));
            }
        }
    }

    /** Picks a job from what the town lacks most right now. */
    private static Contract newContract(Settlement s, RandomSource random, long day) {
        if (random.nextInt(4) == 0) {
            return new Contract(s.nextContractId(), Contract.Kind.HUNT, "minecraft:air", 5 + random.nextInt(6),
                    6 + random.nextInt(5), day + CONTRACT_DAYS);
        }
        Template[] pool;
        if (s.food() < s.population() * 10) {
            pool = FOOD;
        } else if (s.materials() < 200 || s.project() != null && s.project().stalled()) {
            pool = MATERIALS;
        } else if (s.guards() < DefenseManager.wantedGuards(s)) {
            pool = DEFENSE;
        } else if (s.researching() != null) {
            pool = LEARNING;
        } else {
            pool = ANYTHING;
        }
        Template template = pool[random.nextInt(pool.length)];
        return new Contract(s.nextContractId(), Contract.Kind.DELIVER,
                BuiltInRegistries.ITEM.getKey(template.item()).toString(), template.count(), template.reward(),
                day + CONTRACT_DAYS);
    }

    // ------------------------------------------------------------------ board

    public static void showBoard(Consumer<Component> out, UUID viewer, Settlement s, long day) {
        out.accept(Component.translatable("cities.board.header", s.name()).withStyle(ChatFormatting.GOLD));
        if (s.contracts().isEmpty() && s.requests().isEmpty()) {
            out.accept(Component.translatable("cities.board.empty").withStyle(ChatFormatting.GRAY));
        }
        for (Contract contract : s.contracts()) {
            Component taken = contract.taker() == null ? Component.empty()
                    : Component.translatable(contract.taker().equals(viewer)
                    ? "cities.board.taken_by_you" : "cities.board.taken");
            out.accept(Component.literal("  #" + contract.id() + " ").withStyle(ChatFormatting.YELLOW)
                    .append(contract.describe())
                    .append(Component.translatable("cities.board.progress", contract.progress(), contract.count(),
                            contract.reward(), Math.max(0, contract.expires() - day)).withStyle(ChatFormatting.GRAY))
                    .append(taken));
        }
        for (Request request : s.requests()) {
            out.accept(Component.literal("  ♥ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(request.describe()));
        }
        out.accept(Component.translatable("cities.board.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Nullable
    public static Contract contract(Settlement s, int id) {
        for (Contract contract : s.contracts()) {
            if (contract.id() == id) {
                return contract;
            }
        }
        return null;
    }

    /** Hands over as many of the wanted items as the player carries. Returns how many were delivered. */
    public static int deliver(ServerLevel level, ServerPlayer player, Settlement s, Contract contract) {
        int taken = take(player.getInventory(), contract.item(), contract.remaining());
        if (taken == 0) {
            return 0;
        }
        contract.addProgress(taken);
        Stock.Value value = Stock.of(new ItemStack(contract.item(), taken));
        value.stock().add(s, value.points());
        if (contract.done()) {
            complete(level, player, s, contract);
        }
        return taken;
    }

    /** Hunting contracts only count the kills of the player who took them. */
    public static void onMonsterKilled(ServerLevel level, LivingEntity monster, ServerPlayer killer) {
        Settlement s = SettlementData.get(level).at(monster.blockPosition());
        if (s == null) {
            return;
        }
        for (Contract contract : new ArrayList<>(s.contracts())) {
            if (contract.kind() == Contract.Kind.HUNT && killer.getUUID().equals(contract.taker())) {
                contract.addProgress(1);
                killer.displayClientMessage(Component.translatable("cities.board.hunt_progress", contract.progress(),
                        contract.count()), true);
                if (contract.done()) {
                    complete(level, killer, s, contract);
                }
                SettlementData.get(level).setDirty();
                return;
            }
        }
    }

    private static void complete(ServerLevel level, ServerPlayer player, Settlement s, Contract contract) {
        s.contracts().remove(contract);
        int emeralds = Math.min(contract.reward(), s.wealth() / GovernanceManager.WEALTH_PER_EMERALD);
        s.addWealth(-emeralds * GovernanceManager.WEALTH_PER_EMERALD);
        give(player, new ItemStack(Items.EMERALD, emeralds));
        s.changeReputation(player.getUUID(), REPUTATION_PER_CONTRACT);
        if (contract.kind() == Contract.Kind.HUNT) {
            s.setHappiness(s.happiness() + 3);
        }
        s.record(SettlementManager.day(level), "contract", player.getGameProfile().getName());
        player.sendSystemMessage(Component.translatable("cities.board.completed", contract.id(), emeralds,
                REPUTATION_PER_CONTRACT).withStyle(ChatFormatting.GREEN));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------ wishes

    /** If the player holds what this villager wished for, the wish is granted. */
    public static boolean tryFulfil(ServerLevel level, ServerPlayer player, Villager villager) {
        Settlement s = SettlementData.get(level).at(villager.blockPosition());
        if (s == null) {
            return false;
        }
        Iterator<Request> it = s.requests().iterator();
        while (it.hasNext()) {
            Request request = it.next();
            if (!request.villager().equals(villager.getUUID())) {
                continue;
            }
            ItemStack held = player.getMainHandItem();
            if (!held.is(request.resolveItem()) || held.getCount() < request.count()) {
                return false;
            }
            held.shrink(request.count());
            it.remove();
            int emeralds = 1 + level.getRandom().nextInt(4);
            give(player, new ItemStack(Items.EMERALD, emeralds));
            s.changeReputation(player.getUUID(), REPUTATION_PER_REQUEST);
            s.setHappiness(s.happiness() + 1);
            s.record(SettlementManager.day(level), "wish", request.name());
            level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getY() + 2.0, villager.getZ(),
                    5, 0.4, 0.3, 0.4, 0.0);
            villager.playSound(SoundEvents.VILLAGER_YES, 1.0f, villager.getVoicePitch());
            player.sendSystemMessage(Component.translatable("cities.board.wish_granted", request.name(), emeralds,
                    REPUTATION_PER_REQUEST).withStyle(ChatFormatting.LIGHT_PURPLE));
            SettlementData.get(level).setDirty();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ inventory helpers

    /** Removes up to {@code max} of {@code item} from the inventory; returns how many were removed. */
    public static int take(Inventory inventory, Item item, int max) {
        int taken = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && taken < max; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int part = Math.min(stack.getCount(), max - taken);
                stack.shrink(part);
                taken += part;
            }
        }
        return taken;
    }

    public static int countOf(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
