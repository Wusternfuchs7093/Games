package com.wusternfuchs.cities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wusternfuchs.cities.settlement.HistoryEntry;
import com.wusternfuchs.cities.settlement.ReputationRank;
import com.wusternfuchs.cities.settlement.Requirement;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    private static final SuggestionProvider<CommandSourceStack> SETTLEMENT_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(SettlementData.get(ctx.getSource().getLevel()).all().stream()
                    .map(Settlement::name), builder);

    private static final SuggestionProvider<CommandSourceStack> TIER_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(SettlementTier.values()).map(SettlementTier::id), builder);

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
                .then(Commands.literal("donate").executes(CityCommands::donate))
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
        source.sendSuccess(() -> Component.translatable("cities.info.economy", s.food(), s.wealth(),
                s.happiness()), false);
        source.sendSuccess(() -> Component.translatable("cities.info.territory", s.chunks().size()), false);

        if (source.getEntity() instanceof ServerPlayer player) {
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

    private static int donate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Settlement s = inside(ctx);
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            throw EMPTY_HAND.create();
        }

        int count = stack.getCount();
        FoodProperties food = stack.get(DataComponents.FOOD);
        int points;
        if (food != null) {
            points = Math.max(1, food.nutrition()) * count;
            s.addFood(points);
        } else {
            int each = stack.is(Items.EMERALD_BLOCK) ? 81 : stack.is(Items.EMERALD) ? 9 : 1;
            points = each * count;
            s.addWealth(points);
        }
        Component itemName = stack.getHoverName();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        int reputation = Math.max(1, points / DONATION_POINTS_PER_REPUTATION);
        s.changeReputation(player.getUUID(), reputation);
        s.record(SettlementManager.day(ctx.getSource().getLevel()), "donation", player.getGameProfile().getName());
        data(ctx).setDirty();

        ctx.getSource().sendSuccess(() -> Component.translatable(food != null
                        ? "cities.command.donate.food" : "cities.command.donate.wealth",
                count, itemName, s.name(), points, reputation).withStyle(ChatFormatting.GREEN), false);
        return points;
    }

    // ------------------------------------------------------------------ admin commands

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
