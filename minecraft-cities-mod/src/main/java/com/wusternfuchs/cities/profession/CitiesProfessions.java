package com.wusternfuchs.cities.profession;

import com.google.common.collect.ImmutableSet;
import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.block.CitiesBlocks;
import com.wusternfuchs.cities.settlement.ArchitectureStyle;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The town's own professions. Scholars do the research, builders speed up construction, artisans practise the
 * craft of their culture. Each takes a job at its workstation like any vanilla villager.
 */
public final class CitiesProfessions {
    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CitiesMod.MODID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, CitiesMod.MODID);

    public static final DeferredHolder<PoiType, PoiType> SCHOLAR_POI = poi("scholar", CitiesBlocks.SCHOLAR_DESK);
    public static final DeferredHolder<PoiType, PoiType> BUILDER_POI = poi("builder", CitiesBlocks.BUILDER_BENCH);
    public static final DeferredHolder<PoiType, PoiType> ARTISAN_POI = poi("artisan", CitiesBlocks.CRAFT_BENCH);

    public static final DeferredHolder<VillagerProfession, VillagerProfession> SCHOLAR =
            profession("scholar", SCHOLAR_POI);
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BUILDER =
            profession("builder", BUILDER_POI);
    public static final DeferredHolder<VillagerProfession, VillagerProfession> ARTISAN =
            profession("artisan", ARTISAN_POI);

    private CitiesProfessions() {
    }

    private static DeferredHolder<PoiType, PoiType> poi(String name, Supplier<? extends Block> block) {
        return POI_TYPES.register(name, () -> {
            Set<BlockState> states = ImmutableSet.copyOf(block.get().getStateDefinition().getPossibleStates());
            return new PoiType(states, 1, 1);
        });
    }

    private static DeferredHolder<VillagerProfession, VillagerProfession> profession(
            String name, DeferredHolder<PoiType, PoiType> poi) {
        return PROFESSIONS.register(name, () -> new VillagerProfession(name,
                holder -> holder.is(poi.getKey()), holder -> holder.is(poi.getKey()),
                ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_LIBRARIAN));
    }

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(CitiesProfessions::onTrades);
    }

    public static boolean is(VillagerProfession profession, DeferredHolder<VillagerProfession, VillagerProfession> ours) {
        return ours.isBound() && profession == ours.get();
    }

    // ------------------------------------------------------------------ trades

    private static void onTrades(VillagerTradesEvent event) {
        VillagerProfession type = event.getType();
        if (is(type, SCHOLAR)) {
            add(event, 1, buy(Items.PAPER, 24, 1), sell(1, Items.BOOK, 1));
            add(event, 2, buy(Items.BOOK, 4, 1), sell(3, Items.COMPASS, 1));
            add(event, 3, buy(Items.INK_SAC, 5, 1), sell(4, Items.EXPERIENCE_BOTTLE, 3));
            add(event, 4, sell(8, Items.SPYGLASS, 1), sell(5, Items.MAP, 1));
            add(event, 5, sell(12, Items.NAME_TAG, 1));
        } else if (is(type, BUILDER)) {
            add(event, 1, buy(Items.COBBLESTONE, 32, 1), sell(1, Items.BRICKS, 12));
            add(event, 2, buy(Items.OAK_LOG, 16, 1), sell(1, Items.STONE_BRICKS, 16));
            add(event, 3, sell(1, Items.GLASS, 8), sell(1, Items.SCAFFOLDING, 12));
            add(event, 4, sell(2, Items.POLISHED_ANDESITE, 16), sell(2, Items.DEEPSLATE_TILES, 12));
            add(event, 5, sell(3, Items.LANTERN, 6));
        } else if (is(type, ARTISAN)) {
            for (int slot = 0; slot < 3; slot++) {
                int index = slot;
                add(event, slot + 1, (trader, random) -> craftOffer(trader, index));
            }
        }
    }

    @SafeVarargs
    private static void add(VillagerTradesEvent event, int level, VillagerTrades.ItemListing... listings) {
        List<VillagerTrades.ItemListing> list = event.getTrades().computeIfAbsent(level, key -> new ArrayList<>());
        list.addAll(List.of(listings));
    }

    /** The villager buys {@code count} of {@code item} for emeralds. */
    private static VillagerTrades.ItemListing buy(Item item, int count, int emeralds) {
        return (trader, random) -> offer(item, count, new ItemStack(Items.EMERALD, emeralds));
    }

    /** The villager sells {@code count} of {@code item} for emeralds. */
    private static VillagerTrades.ItemListing sell(int emeralds, Item item, int count) {
        return (trader, random) -> offer(Items.EMERALD, emeralds, new ItemStack(item, count));
    }

    private static MerchantOffer offer(Item cost, int count, ItemStack result) {
        return new MerchantOffer(new ItemCost(cost, count), result, 12, 5, 0.05f);
    }

    /** Artisans sell what their culture is famous for. */
    private static MerchantOffer craftOffer(Entity trader, int slot) {
        ArchitectureStyle culture = ArchitectureStyle.PLAINS;
        if (trader.level() instanceof ServerLevel level) {
            Settlement s = SettlementData.get(level).at(trader.blockPosition());
            if (s != null) {
                culture = s.style();
            }
        }
        return switch (culture.craft()) {
            case CLOCKMAKING -> pick(slot, offer(Items.EMERALD, 5, new ItemStack(Items.CLOCK)),
                    offer(Items.GOLD_INGOT, 3, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 4, new ItemStack(Items.COMPASS)));
            case CARPETS -> pick(slot, offer(Items.EMERALD, 1, new ItemStack(Items.RED_CARPET, 4)),
                    offer(Items.WHITE_WOOL, 12, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 1, new ItemStack(Items.CYAN_CARPET, 4)));
            case POTTERY -> pick(slot, offer(Items.EMERALD, 1, new ItemStack(Items.DECORATED_POT)),
                    offer(Items.CLAY_BALL, 10, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 1, new ItemStack(Items.ORANGE_TERRACOTTA, 8)));
            case SMOKEHOUSE -> pick(slot, offer(Items.EMERALD, 1, new ItemStack(Items.COOKED_COD, 6)),
                    offer(Items.COD, 12, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 1, new ItemStack(Items.COOKED_SALMON, 5)));
            case HONEY -> pick(slot, offer(Items.EMERALD, 1, new ItemStack(Items.HONEY_BOTTLE, 2)),
                    offer(Items.SUNFLOWER, 6, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 2, new ItemStack(Items.HONEYCOMB, 3)));
            case WOOL -> pick(slot, offer(Items.EMERALD, 1, new ItemStack(Items.WHITE_WOOL, 8)),
                    offer(Items.STRING, 16, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 2, new ItemStack(Items.WHITE_BANNER)));
            case SWORDSMITH -> pick(slot, offer(Items.EMERALD, 6, new ItemStack(Items.IRON_SWORD)),
                    offer(Items.IRON_INGOT, 4, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 3, new ItemStack(Items.SHIELD)));
            case CHOCOLATE -> pick(slot, offer(Items.EMERALD, 1, new ItemStack(Items.COOKIE, 8)),
                    offer(Items.COCOA_BEANS, 16, new ItemStack(Items.EMERALD)),
                    offer(Items.EMERALD, 3, new ItemStack(Items.CAKE)));
        };
    }

    private static MerchantOffer pick(int slot, MerchantOffer first, MerchantOffer second, MerchantOffer third) {
        return switch (slot) {
            case 0 -> first;
            case 1 -> second;
            default -> third;
        };
    }
}
