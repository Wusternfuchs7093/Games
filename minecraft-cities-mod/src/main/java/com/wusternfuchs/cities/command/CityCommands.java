package com.wusternfuchs.cities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wusternfuchs.cities.building.Building;
import com.wusternfuchs.cities.building.BuildingType;
import com.wusternfuchs.cities.building.ConstructionProject;
import com.wusternfuchs.cities.building.TownPlanner;
import com.wusternfuchs.cities.defense.DefenseManager;
import com.wusternfuchs.cities.diplomacy.DiplomacyManager;
import com.wusternfuchs.cities.disaster.DisasterManager;
import com.wusternfuchs.cities.settlement.Mood;
import com.wusternfuchs.cities.people.SocialClass;
import com.wusternfuchs.cities.quest.Contract;
import com.wusternfuchs.cities.quest.QuestManager;
import com.wusternfuchs.cities.quest.TownServices;
import com.wusternfuchs.cities.entity.Guard;
import com.wusternfuchs.cities.people.Trait;
import com.wusternfuchs.cities.diplomacy.Relation;
import com.wusternfuchs.cities.governance.GovernanceManager;
import com.wusternfuchs.cities.governance.Law;
import com.wusternfuchs.cities.governance.TaxRate;
import com.wusternfuchs.cities.research.ResearchManager;
import com.wusternfuchs.cities.research.Tech;
import com.wusternfuchs.cities.CitiesConfig;
import com.wusternfuchs.cities.trade.Caravan;
import com.wusternfuchs.cities.trade.TradeManager;
import com.wusternfuchs.cities.war.War;
import com.wusternfuchs.cities.war.WarManager;
import com.wusternfuchs.cities.settlement.HistoryEntry;
import com.wusternfuchs.cities.settlement.ReputationRank;
import com.wusternfuchs.cities.settlement.Requirement;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import com.wusternfuchs.cities.settlement.Stock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CityCommands {
    private static final double NEARBY_RADIUS = 256;
    private static final int DONATION_POINTS_PER_REPUTATION = 16;
    private static final int HISTORY_LINES = 10;

    private static final SimpleCommandExceptionType NOT_FOUND =
            new SimpleCommandExceptionType(Component.translatable("cities.command.not_found"));
    private static final SimpleCommandExceptionType NOT_IN_SETTLEMENT =
            new SimpleCommandExceptionType(Component.translatable("cities.command.not_in_settlement"));
    private static final SimpleCommandExceptionType EMPTY_HAND =
            new SimpleCommandExceptionType(Component.translatable("cities.command.empty_hand"));
    private static final SimpleCommandExceptionType NAME_TAKEN =
            new SimpleCommandExceptionType(Component.translatable("cities.command.name_taken"));
    private static final SimpleCommandExceptionType TOO_CLOSE =
            new SimpleCommandExceptionType(Component.translatable("cities.command.too_close"));
    private static final SimpleCommandExceptionType UNKNOWN_TIER =
            new SimpleCommandExceptionType(Component.translatable("cities.command.unknown_tier"));
    private static final SimpleCommandExceptionType UNKNOWN_BUILDING =
            new SimpleCommandExceptionType(Component.translatable("cities.command.unknown_building"));
    private static final SimpleCommandExceptionType BUSY =
            new SimpleCommandExceptionType(Component.translatable("cities.command.busy"));
    private static final SimpleCommandExceptionType NO_SITE =
            new SimpleCommandExceptionType(Component.translatable("cities.command.no_site"));
    private static final SimpleCommandExceptionType SIEGE_FAILED =
            new SimpleCommandExceptionType(Component.translatable("cities.command.siege_failed"));
    private static final SimpleCommandExceptionType HIRE_FAILED =
            new SimpleCommandExceptionType(Component.translatable("cities.command.hire_failed"));
    private static final SimpleCommandExceptionType NOT_MAYOR =
            new SimpleCommandExceptionType(Component.translatable("cities.command.not_mayor"));
    private static final SimpleCommandExceptionType NEED_BELL =
            new SimpleCommandExceptionType(Component.translatable("cities.command.need_bell"));
    private static final SimpleCommandExceptionType FOUNDING_DISABLED =
            new SimpleCommandExceptionType(Component.translatable("cities.command.founding_disabled"));
    private static final SimpleCommandExceptionType LOW_REPUTATION =
            new SimpleCommandExceptionType(Component.translatable("cities.command.low_reputation"));
    private static final SimpleCommandExceptionType TOO_SMALL =
            new SimpleCommandExceptionType(Component.translatable("cities.command.too_small"));
    private static final SimpleCommandExceptionType UNKNOWN_LAW =
            new SimpleCommandExceptionType(Component.translatable("cities.command.unknown_law"));
    private static final SimpleCommandExceptionType UNKNOWN_TAX =
            new SimpleCommandExceptionType(Component.translatable("cities.command.unknown_tax"));
    private static final SimpleCommandExceptionType CANNOT_AFFORD =
            new SimpleCommandExceptionType(Component.translatable("cities.command.cannot_afford"));
    private static final SimpleCommandExceptionType NOTHING_CHANGED =
            new SimpleCommandExceptionType(Component.translatable("cities.command.nothing_changed"));
    private static final SimpleCommandExceptionType UNKNOWN_TECH =
            new SimpleCommandExceptionType(Component.translatable("cities.command.unknown_tech"));
    private static final SimpleCommandExceptionType TECH_UNAVAILABLE =
            new SimpleCommandExceptionType(Component.translatable("cities.command.tech_unavailable"));
    private static final SimpleCommandExceptionType CANNOT_DECLARE =
            new SimpleCommandExceptionType(Component.translatable("cities.command.cannot_declare"));
    private static final SimpleCommandExceptionType NOT_AT_WAR =
            new SimpleCommandExceptionType(Component.translatable("cities.command.not_at_war"));
    private static final SimpleCommandExceptionType NO_WAR_HERE =
            new SimpleCommandExceptionType(Component.translatable("cities.command.no_war_here"));
    private static final SimpleCommandExceptionType DISASTER_FAILED =
            new SimpleCommandExceptionType(Component.translatable("cities.command.disaster_failed"));
    private static final SimpleCommandExceptionType NOT_BUILDING =
            new SimpleCommandExceptionType(Component.translatable("cities.command.not_building"));

    private static final SuggestionProvider<CommandSourceStack> SETTLEMENT_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(SettlementData.get(ctx.getSource().getLevel()).all().stream()
                    .map(Settlement::name), builder);

    private static final SuggestionProvider<CommandSourceStack> TIER_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(SettlementTier.values()).map(SettlementTier::id), builder);

    private static final SuggestionProvider<CommandSourceStack> LAW_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Law.values()).map(Law::id), builder);

    private static final SuggestionProvider<CommandSourceStack> TAX_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(TaxRate.values()).map(TaxRate::id), builder);

    private static final SuggestionProvider<CommandSourceStack> TECH_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Tech.values()).map(Tech::id), builder);

    private static final SuggestionProvider<CommandSourceStack> DISASTER_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(DisasterManager.Kind.values())
                    .map(kind -> kind.name().toLowerCase(Locale.ROOT)), builder);

    private static final SuggestionProvider<CommandSourceStack> BUILDING_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(BuildingType.values()).map(BuildingType::id), builder);

    private CityCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .executes(ctx -> create(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(CityCommands::rename)))
                .then(Commands.literal("setlevel")
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests(TIER_IDS)
                                .executes(CityCommands::setLevel)))
                .then(Commands.literal("census").executes(CityCommands::census))
                .then(Commands.literal("build")
                        .then(Commands.argument("building", StringArgumentType.word())
                                .suggests(BUILDING_IDS)
                                .executes(CityCommands::build)))
                .then(Commands.literal("cancelbuild").executes(CityCommands::cancelBuild))
                .then(Commands.literal("siege").executes(CityCommands::siege))
                .then(Commands.literal("hireguard").executes(CityCommands::hireGuard))
                .then(Commands.literal("election").executes(CityCommands::forceElection))
                .then(Commands.literal("war")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> declareWar(ctx, true))))
                .then(Commands.literal("battle").executes(CityCommands::forceBattle))
                .then(Commands.literal("disaster")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(DISASTER_IDS)
                                .executes(CityCommands::forceDisaster)))
                .then(Commands.literal("relation")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(SETTLEMENT_NAMES)
                                        .executes(CityCommands::setRelation))))
                .then(Commands.literal("remove").executes(CityCommands::remove))
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(CityCommands::teleport)));

        dispatcher.register(Commands.literal("city")
                .then(Commands.literal("list").executes(CityCommands::list))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> info(ctx, named(ctx)))))
                .then(Commands.literal("history")
                        .executes(ctx -> history(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> history(ctx, named(ctx)))))
                .then(Commands.literal("buildings")
                        .executes(ctx -> buildings(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> buildings(ctx, named(ctx)))))
                .then(Commands.literal("trade")
                        .executes(ctx -> trade(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> trade(ctx, named(ctx)))))
                .then(Commands.literal("relations")
                        .executes(ctx -> relations(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> relations(ctx, named(ctx)))))
                .then(Commands.literal("donate").executes(CityCommands::donate))
                .then(Commands.literal("found")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(CityCommands::found)))
                .then(Commands.literal("run").executes(CityCommands::run))
                .then(Commands.literal("wars").executes(CityCommands::wars))
                .then(Commands.literal("board")
                        .executes(CityCommands::board)
                        .then(Commands.literal("deliver")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(CityCommands::deliver)))
                        .then(Commands.literal("take")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(CityCommands::takeContract))))
                .then(Commands.literal("buy").executes(CityCommands::buyHouse))
                .then(Commands.literal("sell").executes(CityCommands::sellHouse))
                .then(Commands.literal("rumour").executes(CityCommands::rumour))
                .then(Commands.literal("hire").executes(CityCommands::hireMercenary))
                .then(Commands.literal("dismiss").executes(CityCommands::dismiss))
                .then(Commands.literal("people")
                        .executes(ctx -> people(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> people(ctx, named(ctx)))))
                .then(Commands.literal("mood")
                        .executes(ctx -> mood(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> mood(ctx, named(ctx)))))
                .then(Commands.literal("war")
                        .then(Commands.literal("declare")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(SETTLEMENT_NAMES)
                                        .executes(ctx -> declareWar(ctx, false))))
                        .then(Commands.literal("peace")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(SETTLEMENT_NAMES)
                                        .executes(CityCommands::peace))))
                .then(Commands.literal("resign").executes(CityCommands::resign))
                .then(Commands.literal("laws")
                        .executes(ctx -> laws(ctx, here(ctx)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SETTLEMENT_NAMES)
                                .executes(ctx -> laws(ctx, named(ctx)))))
                .then(Commands.literal("law")
                        .then(Commands.argument("law", StringArgumentType.word())
                                .suggests(LAW_IDS)
                                .then(Commands.argument("active", BoolArgumentType.bool())
                                        .executes(CityCommands::law))))
                .then(Commands.literal("tax")
                        .then(Commands.argument("rate", StringArgumentType.word())
                                .suggests(TAX_IDS)
                                .executes(CityCommands::tax)))
                .then(Commands.literal("festival").executes(CityCommands::festival))
                .then(Commands.literal("research")
                        .executes(ctx -> research(ctx, here(ctx)))
                        .then(Commands.argument("tech", StringArgumentType.word())
                                .suggests(TECH_IDS)
                                .executes(CityCommands::chooseResearch)))
                .then(Commands.literal("treasury")
                        .then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 64))
                                .executes(CityCommands::treasury)))
                .then(Commands.literal("order")
                        .then(Commands.argument("building", StringArgumentType.word())
                                .suggests(BUILDING_IDS)
                                .executes(CityCommands::order)))
                .then(admin));
    }

    // ------------------------------------------------------------------ lookup helpers

    private static SettlementData data(CommandContext<CommandSourceStack> ctx) {
        return SettlementData.get(ctx.getSource().getLevel());
    }

    private static Settlement named(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = data(ctx).byName(StringArgumentType.getString(ctx, "name"));
        if (s == null) {
            throw NOT_FOUND.create();
        }
        return s;
    }

    /** The settlement the source stands in, or the nearest one around. */
    private static Settlement here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        SettlementData data = data(ctx);
        Settlement s = data.at(pos);
        if (s == null) {
            s = data.nearest(pos, NEARBY_RADIUS);
        }
        if (s == null) {
            throw NOT_IN_SETTLEMENT.create();
        }
        return s;
    }

    /** Strictly the settlement the source stands in; used by commands that change things. */
    private static Settlement inside(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = data(ctx).at(BlockPos.containing(ctx.getSource().getPosition()));
        if (s == null) {
            throw NOT_IN_SETTLEMENT.create();
        }
        return s;
    }

    // ------------------------------------------------------------------ player commands

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Settlement> all = data(ctx).all().stream()
                .sorted((a, b) -> b.tier().ordinal() != a.tier().ordinal()
                        ? b.tier().ordinal() - a.tier().ordinal()
                        : b.population() - a.population())
                .toList();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("cities.command.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("cities.command.list.header", all.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (Settlement s : all) {
            BlockPos c = s.center();
            source.sendSuccess(() -> Component.translatable("cities.command.list.entry",
                    Component.literal(s.name()).withStyle(ChatFormatting.YELLOW), s.tier().displayName(),
                    s.population(), c.getX(), c.getY(), c.getZ()), false);
        }
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("=== " + s.name() + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("cities.info.tier", s.tier().displayName(),
                s.abandoned() ? Component.translatable("cities.info.abandoned") : Component.empty()), false);
        source.sendSuccess(() -> Component.translatable("cities.info.founded", s.foundedDay(),
                Component.translatable("cities.style." + s.style().name().toLowerCase(Locale.ROOT))), false);
        source.sendSuccess(() -> Component.translatable("cities.info.population", s.population(), s.adults(),
                s.children(), s.beds()), false);
        source.sendSuccess(() -> Component.translatable("cities.info.work", s.employed(), s.workstations(),
                s.foodProducers(), s.golems()), false);
        source.sendSuccess(() -> Component.translatable("cities.info.economy", s.food(), s.materials(),
                s.wealth(), s.happiness()), false);
        source.sendSuccess(() -> Component.translatable("cities.info.mayor", GovernanceManager.mayorName(s),
                s.tax().displayName(), s.laws().size()), false);
        Settlement lord = data(ctx).byId(s.overlord());
        if (lord != null || s.vassals() > 0) {
            source.sendSuccess(() -> lord != null
                    ? Component.translatable("cities.info.vassal_of", lord.name(), s.vassals())
                    : Component.translatable("cities.info.realm", s.vassals()), false);
        }
        for (War war : WarManager.warsOf(data(ctx), s)) {
            boolean attacking = war.attacker().equals(s.id());
            Settlement enemy = data(ctx).byId(attacking ? war.defender() : war.attacker());
            int ourScore = attacking ? war.score() : -war.score();
            source.sendSuccess(() -> Component.translatable("cities.info.war", enemy == null ? "?" : enemy.name(),
                    ourScore).withStyle(ChatFormatting.RED), false);
        }
        if (s.fire() != null) {
            source.sendSuccess(() -> Component.translatable("cities.info.fire", s.fire().type().displayName())
                    .withStyle(ChatFormatting.RED), false);
        }
        if (s.epidemic() > 0) {
            source.sendSuccess(() -> Component.translatable("cities.info.epidemic", s.sick())
                    .withStyle(ChatFormatting.DARK_GREEN), false);
        }
        if (s.drought() > 0) {
            source.sendSuccess(() -> Component.translatable("cities.info.drought").withStyle(ChatFormatting.GOLD), false);
        }
        Tech studying = s.researching();
        source.sendSuccess(() -> studying == null
                ? Component.translatable("cities.info.research_none", s.era().displayName())
                : Component.translatable("cities.info.research", s.era().displayName(), studying.displayName(),
                        s.researchProgress(), studying.cost(), ResearchManager.pointsPerCensus(s)), false);
        source.sendSuccess(() -> Component.translatable("cities.info.territory", s.chunks().size(),
                s.buildings().size()), false);
        ConstructionProject project = s.project();
        if (project != null) {
            source.sendSuccess(() -> Component.translatable(project.stalled()
                            ? "cities.info.project_stalled" : "cities.info.project",
                    project.building().type().displayName(), project.percent()).withStyle(ChatFormatting.AQUA), false);
        }

        int caravans = TradeManager.outgoing(data(ctx), s).size();
        if (caravans > 0) {
            source.sendSuccess(() -> Component.translatable("cities.info.caravans", caravans), false);
        }
        source.sendSuccess(() -> Component.translatable("cities.info.guards", s.guards(),
                DefenseManager.wantedGuards(s)), false);
        if (s.siege() != null) {
            source.sendSuccess(() -> Component.translatable("cities.info.siege", s.siege().attackers().size())
                    .withStyle(ChatFormatting.RED), false);
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            int houses = TownServices.housesOwnedBy(s, player.getUUID());
            if (houses > 0) {
                source.sendSuccess(() -> Component.translatable("cities.info.your_houses", houses), false);
            }
            int rep = s.reputation(player.getUUID());
            source.sendSuccess(() -> Component.translatable("cities.info.reputation",
                    ReputationRank.of(rep).displayName(), rep), false);
        }

        SettlementTier next = s.tier().next();
        if (next == null) {
            source.sendSuccess(() -> Component.translatable("cities.info.max_tier").withStyle(ChatFormatting.GOLD), false);
        } else {
            source.sendSuccess(() -> Component.translatable("cities.info.next", next.displayName())
                    .withStyle(ChatFormatting.YELLOW), false);
            for (Requirement requirement : Requirement.forTier(s, next)) {
                source.sendSuccess(requirement::describe, false);
            }
        }

        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.translatable("cities.info.potential", s.potential(),
                    s.maxTier().displayName()).withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("cities.command.history.header", s.name())
                .withStyle(ChatFormatting.GOLD), false);
        List<HistoryEntry> entries = s.history();
        for (HistoryEntry entry : entries.subList(Math.max(0, entries.size() - HISTORY_LINES), entries.size())) {
            source.sendSuccess(entry::describe, false);
        }
        return entries.size();
    }

    private static int trade(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        SettlementData data = data(ctx);
        source.sendSuccess(() -> Component.translatable("cities.command.trade.header", s.name())
                .withStyle(ChatFormatting.GOLD), false);
        for (Stock stock : List.of(Stock.FOOD, Stock.MATERIALS)) {
            source.sendSuccess(() -> Component.translatable("cities.command.trade.price", stock.title(),
                    TradeManager.amount(s, stock), TradeManager.target(s, stock),
                    String.format(Locale.ROOT, "%.2f", TradeManager.price(s, stock))), false);
        }
        if (!TradeManager.canTrade(s)) {
            source.sendSuccess(() -> Component.translatable("cities.command.trade.no_market")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        long now = ctx.getSource().getLevel().getGameTime();
        int shown = 0;
        for (Caravan caravan : data.caravans()) {
            boolean outgoing = caravan.from().equals(s.id());
            if (!outgoing && !caravan.to().equals(s.id())) {
                continue;
            }
            Settlement other = data.byId(outgoing ? caravan.to() : caravan.from());
            String otherName = other == null ? "?" : other.name();
            long minutes = Math.max(0, (caravan.arriveTick() - now) / 1200);
            source.sendSuccess(() -> Component.translatable(outgoing
                            ? "cities.command.trade.outgoing" : "cities.command.trade.incoming",
                    caravan.amount(), caravan.stock().displayName(), otherName, caravan.price(), minutes), false);
            shown++;
        }
        if (shown == 0) {
            source.sendSuccess(() -> Component.translatable("cities.command.trade.none"), false);
        }
        return shown;
    }

    private static int relations(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("cities.command.relations.header", s.name())
                .withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (Settlement other : data(ctx).all()) {
            if (other == s || !DiplomacyManager.inRange(s, other)) {
                continue;
            }
            int value = s.relation(other.id());
            int distance = (int) Math.sqrt(s.center().distSqr(other.center()));
            source.sendSuccess(() -> Component.translatable("cities.command.relations.entry", other.name(),
                    Relation.of(value).displayName(), value, distance), false);
            shown++;
        }
        if (shown == 0) {
            source.sendSuccess(() -> Component.translatable("cities.command.relations.none"), false);
        }
        return shown;
    }

    // ------------------------------------------------------------------ government

    /** The settlement the player stands in, if the player rules it (operators may act as any mayor). */
    private static Settlement governed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        if (!GovernanceManager.isMayor(s, player) && !ctx.getSource().hasPermission(2)) {
            throw NOT_MAYOR.create();
        }
        return s;
    }

    private static int found(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!CitiesConfig.PLAYER_FOUNDING.get()) {
            throw FOUNDING_DISABLED.create();
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.getMainHandItem().is(Items.BELL)) {
            throw NEED_BELL.create();
        }
        ServerLevel level = ctx.getSource().getLevel();
        SettlementData data = data(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        if (data.isNameTaken(name)) {
            throw NAME_TAKEN.create();
        }
        BlockPos pos = player.blockPosition();
        if (!SettlementManager.canFoundAt(level, data, pos)) {
            throw TOO_CLOSE.create();
        }
        player.getMainHandItem().shrink(1);
        // The charter bell becomes the heart of the new town, right in front of its founder.
        BlockPos bell = pos.relative(player.getDirection());
        if (level.getBlockState(bell).canBeReplaced() && !level.getBlockState(bell.below()).isAir()) {
            level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
        }
        Settlement s = SettlementManager.found(level, data, pos, name);
        s.setMayor(player.getUUID(), player.getGameProfile().getName());
        s.changeReputation(player.getUUID(), 20);
        s.record(SettlementManager.day(level), "founded_by", player.getGameProfile().getName());
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.founded", s.name())
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        if (s.tier().ordinal() < SettlementTier.VILLAGE.ordinal()) {
            throw TOO_SMALL.create();
        }
        if (ReputationRank.of(s.reputation(player.getUUID())).ordinal() < ReputationRank.FRIEND.ordinal()) {
            throw LOW_REPUTATION.create();
        }
        GovernanceManager.addCandidate(s, player);
        data(ctx).setDirty();
        long daysLeft = Math.max(0, s.lastElectionDay() + CitiesConfig.ELECTION_INTERVAL_DAYS.get()
                - SettlementManager.day(ctx.getSource().getLevel()));
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.running", s.name(), daysLeft), false);
        return 1;
    }

    private static int resign(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        if (!GovernanceManager.isMayor(s, player)) {
            throw NOT_MAYOR.create();
        }
        s.setMayor(null, "");
        s.record(SettlementManager.day(ctx.getSource().getLevel()), "resigned", player.getGameProfile().getName());
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.resigned", s.name()), true);
        return 1;
    }

    private static int laws(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("cities.command.laws.header", s.name(),
                GovernanceManager.mayorName(s)).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("cities.command.laws.tax", s.tax().displayName()), false);
        for (Law law : Law.values()) {
            boolean active = s.hasLaw(law);
            source.sendSuccess(() -> Component.literal(active ? "  ● " : "  ○ ")
                    .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(law.displayName().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + law.id() + "): ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(law.description().copy().withStyle(ChatFormatting.GRAY)), false);
        }
        return s.laws().size();
    }

    private static int law(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        Law law = Law.byId(StringArgumentType.getString(ctx, "law"));
        if (law == null) {
            throw UNKNOWN_LAW.create();
        }
        boolean active = BoolArgumentType.getBool(ctx, "active");
        if (!GovernanceManager.setLaw(ctx.getSource().getLevel(), s, law, active)) {
            throw NOTHING_CHANGED.create();
        }
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable(active
                ? "cities.command.law_enacted" : "cities.command.law_repealed", law.displayName(), s.name()), true);
        return 1;
    }

    private static int tax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        TaxRate rate = TaxRate.byId(StringArgumentType.getString(ctx, "rate"));
        if (rate == null) {
            throw UNKNOWN_TAX.create();
        }
        s.setTax(rate);
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.tax_set", s.name(),
                rate.displayName()), true);
        return 1;
    }

    private static int festival(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        if (!GovernanceManager.canAffordFestival(s)) {
            throw CANNOT_AFFORD.create();
        }
        GovernanceManager.startFestival(ctx.getSource().getLevel(), s);
        data(ctx).setDirty();
        return 1;
    }

    /** The mayor takes emeralds out of the treasury. The townsfolk notice. */
    private static int treasury(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int emeralds = IntegerArgumentType.getInteger(ctx, "emeralds");
        int cost = emeralds * GovernanceManager.WEALTH_PER_EMERALD;
        if (s.wealth() < cost) {
            throw CANNOT_AFFORD.create();
        }
        s.addWealth(-cost);
        s.setHappiness(s.happiness() - Math.max(1, cost / 30));
        s.record(SettlementManager.day(ctx.getSource().getLevel()), "treasury", player.getGameProfile().getName());
        data(ctx).setDirty();
        ItemStack stack = new ItemStack(Items.EMERALD, emeralds);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.treasury", emeralds, s.name()), false);
        return emeralds;
    }

    private static int order(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        BuildingType type = BuildingType.byId(StringArgumentType.getString(ctx, "building"));
        if (type == null) {
            throw UNKNOWN_BUILDING.create();
        }
        if (s.project() != null) {
            throw BUSY.create();
        }
        if (!TownPlanner.start(ctx.getSource().getLevel(), data(ctx), s, type)) {
            throw NO_SITE.create();
        }
        BlockPos origin = s.project().building().origin();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.build_started", type.displayName(),
                origin.getX(), origin.getY(), origin.getZ()), false);
        return 1;
    }

    private static int research(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("cities.command.research.header", s.name(),
                s.era().displayName(), ResearchManager.pointsPerCensus(s)).withStyle(ChatFormatting.GOLD), false);
        for (Tech tech : Tech.values()) {
            String state;
            ChatFormatting color;
            if (s.hasTech(tech)) {
                state = "  ✔ ";
                color = ChatFormatting.GREEN;
            } else if (tech == s.researching()) {
                state = "  ▶ ";
                color = ChatFormatting.YELLOW;
            } else if (ResearchManager.isAvailable(s, tech)) {
                state = "  ○ ";
                color = ChatFormatting.WHITE;
            } else {
                state = "  ✘ ";
                color = ChatFormatting.DARK_GRAY;
            }
            Component progress = tech == s.researching()
                    ? Component.literal(" [" + s.researchProgress() + "/" + tech.cost() + "]")
                    : Component.literal(" [" + tech.cost() + "]");
            source.sendSuccess(() -> Component.literal(state).withStyle(color)
                    .append(tech.displayName().copy().withStyle(color))
                    .append(Component.literal(" (" + tech.id() + ", ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(tech.era().displayName().copy().withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY))
                    .append(progress.copy().withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(tech.description().copy().withStyle(ChatFormatting.GRAY)), false);
        }
        return s.techs().size();
    }

    private static int chooseResearch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        Tech tech = Tech.byId(StringArgumentType.getString(ctx, "tech"));
        if (tech == null) {
            throw UNKNOWN_TECH.create();
        }
        if (!ResearchManager.isAvailable(s, tech)) {
            throw TECH_UNAVAILABLE.create();
        }
        s.setResearching(tech);
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.research_set", s.name(),
                tech.displayName()), false);
        return 1;
    }

    private static int mood(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        List<Mood.Factor> factors = Mood.factors(ctx.getSource().getLevel(), s);
        int target = Mood.target(factors);
        source.sendSuccess(() -> Component.translatable("cities.command.mood.header", s.name(), s.happiness(), target)
                .withStyle(ChatFormatting.GOLD), false);
        for (Mood.Factor factor : factors) {
            source.sendSuccess(factor::describe, false);
        }
        return target;
    }

    private static CommandSyntaxException failure(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }

    private static int board(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestManager.showBoard(player, here(ctx), SettlementManager.day(ctx.getSource().getLevel()));
        return 1;
    }

    private static Contract contractArg(CommandContext<CommandSourceStack> ctx, Settlement s) throws CommandSyntaxException {
        Contract contract = QuestManager.contract(s, IntegerArgumentType.getInteger(ctx, "id"));
        if (contract == null) {
            throw failure("cities.command.no_contract");
        }
        return contract;
    }

    private static int deliver(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        Contract contract = contractArg(ctx, s);
        if (contract.kind() != Contract.Kind.DELIVER) {
            throw failure("cities.command.not_delivery");
        }
        int delivered = QuestManager.deliver(ctx.getSource().getLevel(), player, s, contract);
        if (delivered == 0) {
            throw failure("cities.command.nothing_to_deliver");
        }
        data(ctx).setDirty();
        if (!contract.done()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("cities.board.delivered", delivered,
                    contract.remaining()), false);
        }
        return delivered;
    }

    private static int takeContract(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        Contract contract = contractArg(ctx, s);
        if (contract.kind() != Contract.Kind.HUNT) {
            throw failure("cities.command.not_hunt");
        }
        if (contract.taker() != null && !contract.taker().equals(player.getUUID())) {
            throw failure("cities.command.contract_taken");
        }
        contract.setTaker(player.getUUID());
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.board.took", contract.id()), false);
        return 1;
    }

    private static int buyHouse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        String error = TownServices.buy(ctx.getSource().getLevel(), player, s);
        if (error != null) {
            throw failure(error);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.house_bought", s.name())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int sellHouse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        String error = TownServices.sell(ctx.getSource().getLevel(), player, s);
        if (error != null) {
            throw failure(error);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.house_sold"), false);
        return 1;
    }

    private static int rumour(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        if (s.count(BuildingType.TAVERN) == 0) {
            throw failure("cities.command.no_tavern");
        }
        Component rumour = TownServices.rumour(ctx.getSource().getLevel(), player, s);
        if (rumour == null) {
            throw failure("cities.command.not_enough_emeralds");
        }
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.rumour.prefix").withStyle(ChatFormatting.GOLD)
                .append(rumour.copy().withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int hireMercenary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        String error = TownServices.hireMercenary(ctx.getSource().getLevel(), player, s);
        if (error != null) {
            throw failure(error);
        }
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.mercenary_hired",
                TownServices.MERCENARY_DAYS).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int dismiss(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<Guard> mercenaries = TownServices.mercenariesOf(ctx.getSource().getLevel(), player);
        mercenaries.forEach(Guard::discard);
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.mercenary_dismissed",
                mercenaries.size()), false);
        return mercenaries.size();
    }

    private static int people(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("cities.command.people.header", s.name(), s.adults(),
                s.children(), s.deathsTotal()).withStyle(ChatFormatting.GOLD), false);
        for (SocialClass socialClass : SocialClass.values()) {
            int count = s.count(socialClass);
            source.sendSuccess(() -> Component.literal("  ").append(socialClass.displayName())
                    .append(Component.literal(": " + count + " · ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("cities.class." + socialClass.id() + ".needs")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        MutableComponent temperaments = Component.translatable("cities.command.people.traits").copy();
        for (Trait trait : Trait.values()) {
            temperaments.append(Component.literal(" ")).append(trait.displayName())
                    .append(Component.literal(" " + s.count(trait) + ";").withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> temperaments, false);
        source.sendSuccess(() -> Component.translatable("cities.command.people.hint").withStyle(ChatFormatting.DARK_GRAY),
                false);
        return s.population();
    }

    private static int forceDisaster(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        DisasterManager.Kind kind = DisasterManager.kindById(StringArgumentType.getString(ctx, "kind"));
        if (kind == null || !DisasterManager.start(ctx.getSource().getLevel(), data(ctx), s, kind)) {
            throw DISASTER_FAILED.create();
        }
        data(ctx).setDirty();
        return 1;
    }

    private static int wars(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SettlementData data = data(ctx);
        if (data.wars().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("cities.command.wars.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("cities.command.wars.header").withStyle(ChatFormatting.GOLD), false);
        for (War war : data.wars()) {
            Settlement attacker = data.byId(war.attacker());
            Settlement defender = data.byId(war.defender());
            source.sendSuccess(() -> Component.translatable("cities.command.wars.entry",
                    attacker == null ? "?" : attacker.name(), defender == null ? "?" : defender.name(), war.score(),
                    war.battleInProgress() ? Component.translatable("cities.command.wars.battle") : Component.empty()),
                    false);
        }
        return data.wars().size();
    }

    /** A mayor (or an operator with {@code force}) declares war on another settlement. */
    private static int declareWar(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        Settlement s = force ? inside(ctx) : governed(ctx);
        Settlement enemy = named(ctx);
        ServerLevel level = ctx.getSource().getLevel();
        SettlementData data = data(ctx);
        boolean allowed = force ? enemy != s && !WarManager.atWar(data, s.id(), enemy.id())
                : WarManager.canDeclare(level, data, s, enemy);
        if (!allowed) {
            throw CANNOT_DECLARE.create();
        }
        WarManager.declare(level, data, s, enemy);
        return 1;
    }

    private static int peace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = governed(ctx);
        Settlement enemy = named(ctx);
        War war = WarManager.warBetween(data(ctx), s.id(), enemy.id());
        if (war == null) {
            throw NOT_AT_WAR.create();
        }
        WarManager.makePeace(ctx.getSource().getLevel(), data(ctx), war, false);
        return 1;
    }

    private static int forceBattle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        SettlementData data = data(ctx);
        for (War war : WarManager.warsOf(data, s)) {
            if (!war.battleInProgress()) {
                Settlement attacker = data.byId(war.attacker());
                Settlement defender = data.byId(war.defender());
                if (attacker != null && defender != null) {
                    WarManager.launchBattle(ctx.getSource().getLevel(), data, war, attacker, defender);
                    return 1;
                }
            }
        }
        throw NO_WAR_HERE.create();
    }

    private static int forceElection(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        GovernanceManager.holdElection(ctx.getSource().getLevel(), data(ctx), s);
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.election_held", s.name(),
                GovernanceManager.mayorName(s)), true);
        return 1;
    }

    private static int donate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            throw EMPTY_HAND.create();
        }

        int count = stack.getCount();
        Stock.Value value = Stock.of(stack);
        int points = value.points();
        value.stock().add(s, points);
        Component itemName = stack.getHoverName();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        int reputation = Math.max(1, points / DONATION_POINTS_PER_REPUTATION);
        s.changeReputation(player.getUUID(), reputation);
        s.record(SettlementManager.day(ctx.getSource().getLevel()), "donation", player.getGameProfile().getName());
        data(ctx).setDirty();

        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.donate", count, itemName, s.name(),
                points, value.stock().displayName(), reputation).withStyle(ChatFormatting.GREEN), false);
        return points;
    }

    private static int buildings(CommandContext<CommandSourceStack> ctx, Settlement s) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("cities.command.buildings.header", s.name(),
                s.buildings().size()).withStyle(ChatFormatting.GOLD), false);
        for (BuildingType type : BuildingType.values()) {
            int count = s.count(type);
            if (count > 0) {
                source.sendSuccess(() -> Component.literal("  ").append(type.displayName())
                        .append(Component.literal(": " + count)), false);
            }
        }
        return s.buildings().size();
    }

    // ------------------------------------------------------------------ admin commands

    private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        BuildingType type = BuildingType.byId(StringArgumentType.getString(ctx, "building"));
        if (type == null) {
            throw UNKNOWN_BUILDING.create();
        }
        if (s.project() != null) {
            throw BUSY.create();
        }
        if (!TownPlanner.start(ctx.getSource().getLevel(), data(ctx), s, type)) {
            throw NO_SITE.create();
        }
        Building building = s.project().building();
        BlockPos origin = building.origin();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.build_started", type.displayName(),
                origin.getX(), origin.getY(), origin.getZ()), true);
        return 1;
    }

    private static int siege(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        if (!DefenseManager.maybeStartSiege(ctx.getSource().getLevel(), data(ctx), s, true)) {
            throw SIEGE_FAILED.create();
        }
        return 1;
    }

    private static int hireGuard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        if (DefenseManager.hireGuard(ctx.getSource().getLevel(), s) == null) {
            throw HIRE_FAILED.create();
        }
        s.setGuards(s.guards() + 1);
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.guard_hired", s.name()), true);
        return 1;
    }

    private static int setRelation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        Settlement other = named(ctx);
        int value = IntegerArgumentType.getInteger(ctx, "value");
        Relation before = DiplomacyManager.relation(s, other);
        s.setRelation(other.id(), value);
        other.setRelation(s.id(), value);
        Relation after = DiplomacyManager.relation(s, other);
        if (before != after) {
            DiplomacyManager.announce(ctx.getSource().getLevel(), s, other, after);
        }
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.relation_set", s.name(),
                other.name(), after.displayName(), value), true);
        return 1;
    }

    private static int cancelBuild(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        if (s.project() == null) {
            throw NOT_BUILDING.create();
        }
        s.setProject(null);
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.build_cancelled"), true);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        SettlementData data = data(ctx);
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        if (name != null && data.isNameTaken(name)) {
            throw NAME_TAKEN.create();
        }
        if (!SettlementManager.canFoundAt(level, data, pos)) {
            throw TOO_CLOSE.create();
        }
        Settlement s = SettlementManager.found(level, data, pos, name);
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.created", s.name()), true);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        if (data(ctx).isNameTaken(name)) {
            throw NAME_TAKEN.create();
        }
        String old = s.name();
        s.setName(name);
        s.record(SettlementManager.day(ctx.getSource().getLevel()), "renamed", old);
        data(ctx).setDirty();
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.renamed", old, name), true);
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        SettlementTier tier = SettlementTier.byId(StringArgumentType.getString(ctx, "tier"));
        if (tier == null) {
            throw UNKNOWN_TIER.create();
        }
        if (tier != s.tier()) {
            SettlementManager.changeTier(ctx.getSource().getLevel(), data(ctx), s, tier);
        }
        return 1;
    }

    private static int census(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        SettlementManager.simulate(ctx.getSource().getLevel(), data(ctx), s);
        return info(ctx, s);
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Settlement s = inside(ctx);
        data(ctx).remove(s);
        ctx.getSource().sendSuccess(() -> Component.translatable("cities.command.removed", s.name()), true);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = named(ctx);
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.center().east(2));
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        return 1;
    }
}
