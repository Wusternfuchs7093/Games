package com.wusternfuchs.cities.network;

import com.wusternfuchs.cities.governance.GovernanceManager;
import com.wusternfuchs.cities.quest.QuestManager;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementManager;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds what the city screen shows. Each tab is filled by running the matching {@code /city} command with its
 * output captured, so the screen and the chat always say the same thing.
 */
public final class CityView {
    private static final double NEARBY = 256;

    private CityView() {
    }

    public static CityViewPayload build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SettlementData data = SettlementData.get(level);
        Settlement s = data.at(player.blockPosition());
        if (s == null) {
            s = data.nearest(player.blockPosition(), NEARBY);
        }
        List<List<Component>> pages = new ArrayList<>();
        if (s == null) {
            for (int i = 0; i < CityViewPayload.TABS.size(); i++) {
                pages.add(List.of(Component.translatable("cities.command.not_in_settlement")));
            }
            return new CityViewPayload("", false, pages);
        }
        for (String tab : CityViewPayload.TABS) {
            if (tab.equals("board")) {
                List<Component> lines = new ArrayList<>();
                QuestManager.showBoard(lines::add, player.getUUID(), s, SettlementManager.day(level));
                pages.add(lines);
            } else {
                pages.add(capture(player, "city " + tab));
            }
        }
        return new CityViewPayload(s.name(), GovernanceManager.isMayor(s, player), pages);
    }

    /** Runs a command as the player and returns what it would have printed in chat. */
    private static List<Component> capture(ServerPlayer player, String command) {
        List<Component> lines = new ArrayList<>();
        CommandSource collector = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                lines.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = player.createCommandSourceStack().withSource(collector);
        player.server.getCommands().performPrefixedCommand(source, command);
        return lines;
    }
}
