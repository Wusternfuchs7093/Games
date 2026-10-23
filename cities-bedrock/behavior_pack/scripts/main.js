// Cities — Bedrock add-on. Villages grow into towns, cities and capitals.
// Entry point: wires the game events to the simulation.

import { ItemStack, system, world } from "@minecraft/server";
import { census } from "./census.js";
import { work } from "./construction.js";
import { detectAround } from "./detect.js";
import { t } from "./i18n.js";
import { at } from "./settlement.js";
import * as store from "./store.js";
import { openMenu } from "./ui.js";

const CENSUS_TICKS = 1200;
const BUILD_TICKS = 10;
const DETECT_TICKS = 200;
const TITLE_TICKS = 20;
const SAVE_TICKS = 600;
const BOOK = "cities:city_book";

const lastTown = new Map();

/** Runs {@code fn}, logging instead of letting one broken town stop everything. */
function safely(label, fn) {
    try {
        fn();
    } catch (error) {
        console.warn("[cities] " + label + ": " + error + (error?.stack ? "\n" + error.stack : ""));
    }
}

system.runInterval(() => {
    for (const s of store.all().values()) {
        safely("census " + s.name, () => census(s));
    }
    store.flush();
}, CENSUS_TICKS);

system.runInterval(() => {
    for (const s of store.all().values()) {
        if (s.project) {
            safely("build " + s.name, () => {
                const dim = world.getDimension(s.dim);
                const builders = 2 + Math.min(3, Math.floor((s.adults ?? 0) / 8));
                work(dim, s, builders);
            });
        }
    }
}, BUILD_TICKS);

system.runInterval(() => {
    for (const player of world.getAllPlayers()) {
        safely("detect", () => detectAround(player));
    }
}, DETECT_TICKS);

system.runInterval(() => store.flush(), SAVE_TICKS);

// Titles when entering and leaving a town.
system.runInterval(() => {
    for (const player of world.getAllPlayers()) {
        safely("titles", () => {
            const inOverworld = player.dimension.id === "minecraft:overworld";
            const s = inOverworld ? at("overworld", player.location.x, player.location.z) : undefined;
            const previous = lastTown.get(player.id);
            const current = s?.id ?? null;
            if (previous === current) {
                return;
            }
            lastTown.set(player.id, current);
            if (previous === undefined) {
                return; // just joined: no title
            }
            if (s) {
                player.onScreenDisplay.setTitle(s.name, {
                    subtitle: t("cities.title.subtitle", t("cities.tier." + ["hamlet", "village", "township", "small_town",
                        "city", "metropolis", "capital"][s.tier]), s.adults + s.children),
                    fadeInDuration: 10, stayDuration: 50, fadeOutDuration: 15,
                });
            } else {
                const left = store.byId(previous);
                if (left) {
                    player.onScreenDisplay.setActionBar(t("cities.title.leaving", left.name));
                }
            }
        });
    }
}, TITLE_TICKS);

// The city book opens the menu.
world.afterEvents.itemUse.subscribe((event) => {
    if (event.itemStack?.typeId === BOOK) {
        system.run(() => openMenu(event.source).catch((error) => console.warn("[cities] menu: " + error)));
    }
});

// Every player gets a city book once.
world.afterEvents.playerSpawn.subscribe((event) => {
    const player = event.player;
    if (!event.initialSpawn || player.getDynamicProperty("cities:got_book")) {
        return;
    }
    safely("book", () => {
        player.getComponent("minecraft:inventory")?.container?.addItem(new ItemStack(BOOK, 1));
        player.setDynamicProperty("cities:got_book", true);
        player.sendMessage(t("cities.welcome"));
    });
});
