package com.wusternfuchs.cities;

import com.mojang.logging.LogUtils;
import com.wusternfuchs.cities.entity.CitiesEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CitiesMod.MODID)
public final class CitiesMod {
    public static final String MODID = "cities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitiesMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CitiesConfig.SPEC);
        CitiesEntities.register(modBus);
        NeoForge.EVENT_BUS.register(GameEvents.class);
    }
}
