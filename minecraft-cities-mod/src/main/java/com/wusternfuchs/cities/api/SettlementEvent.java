package com.wusternfuchs.cities.api;

import com.wusternfuchs.cities.building.Building;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementTier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * Events other mods can listen to on {@code NeoForge.EVENT_BUS}. All are fired on the server thread after the
 * change has happened.
 */
public abstract class SettlementEvent extends Event {
    private final ServerLevel level;
    private final Settlement settlement;

    protected SettlementEvent(ServerLevel level, Settlement settlement) {
        this.level = level;
        this.settlement = settlement;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    /** A village was discovered or a town was founded. */
    public static class Founded extends SettlementEvent {
        public Founded(ServerLevel level, Settlement settlement) {
            super(level, settlement);
        }
    }

    /** The settlement grew or declined to another tier. */
    public static class TierChanged extends SettlementEvent {
        private final SettlementTier previous;

        public TierChanged(ServerLevel level, Settlement settlement, SettlementTier previous) {
            super(level, settlement);
            this.previous = previous;
        }

        public SettlementTier getPrevious() {
            return previous;
        }
    }

    /** A builder finished a building (also fired after repairs and stone rebuilds). */
    public static class BuildingCompleted extends SettlementEvent {
        private final Building building;

        public BuildingCompleted(ServerLevel level, Settlement settlement, Building building) {
            super(level, settlement);
            this.building = building;
        }

        public Building getBuilding() {
            return building;
        }
    }

    /** A band of raiders started marching on the settlement. */
    public static class SiegeStarted extends SettlementEvent {
        public SiegeStarted(ServerLevel level, Settlement settlement) {
            super(level, settlement);
        }
    }

    /** The settlement declared war on {@link #getDefender()}. */
    public static class WarDeclared extends SettlementEvent {
        private final Settlement defender;

        public WarDeclared(ServerLevel level, Settlement attacker, Settlement defender) {
            super(level, attacker);
            this.defender = defender;
        }

        public Settlement getDefender() {
            return defender;
        }
    }
}
