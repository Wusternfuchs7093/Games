// The job board: the town asks for goods it lacks and pays emeralds from its treasury.

import { addStock, pay } from "./census.js";
import { t } from "./i18n.js";
import { changeReputation, count, record } from "./settlement.js";
import * as store from "./store.js";

const CONTRACT_DAYS = 3;
const REPUTATION = 5;

const FOOD = [["bread", 16, 5], ["carrot", 32, 4], ["cooked_beef", 8, 4], ["baked_potato", 16, 4]];
const MATERIALS = [["oak_log", 32, 6], ["cobblestone", 64, 4], ["glass", 16, 4], ["spruce_log", 32, 6]];
const LEARNING = [["book", 6, 6], ["paper", 24, 4]];
const ANYTHING = [["torch", 64, 3], ["hay_block", 8, 4], ["white_wool", 16, 4], ["iron_ingot", 12, 8]];

export function maxContracts(s) {
    return 3 + count(s, "market");
}

/** Called at each census: old jobs expire, new ones appear while the town can pay. */
export function update(s) {
    s.contracts = s.contracts.filter((c) => c.expires >= s.day);
    if (s.contracts.length >= maxContracts(s) || s.wealth < 27) {
        return;
    }
    const pop = s.adults + s.children;
    let pool = ANYTHING;
    if (s.food < pop * 10) {
        pool = FOOD;
    } else if (s.materials < 200 || s.stalled) {
        pool = MATERIALS;
    } else if (count(s, "library") > 0 && Math.random() < 0.5) {
        pool = LEARNING;
    }
    const [item, amount, reward] = pool[Math.floor(Math.random() * pool.length)];
    s.contracts.push({ id: s.nextContract++, item: "minecraft:" + item, count: amount, reward, progress: 0,
        expires: s.day + CONTRACT_DAYS });
}

/** How many of {@code typeId} the player carries. */
export function carried(player, typeId) {
    const inventory = player.getComponent("minecraft:inventory")?.container;
    let total = 0;
    for (let slot = 0; inventory && slot < inventory.size; slot++) {
        const item = inventory.getItem(slot);
        if (item?.typeId === typeId) {
            total += item.amount;
        }
    }
    return total;
}

/** Removes up to {@code max} of an item from the player's inventory; returns how many were taken. */
export function take(player, typeId, max) {
    const inventory = player.getComponent("minecraft:inventory")?.container;
    let taken = 0;
    for (let slot = 0; inventory && slot < inventory.size && taken < max; slot++) {
        const item = inventory.getItem(slot);
        if (item?.typeId !== typeId) {
            continue;
        }
        const part = Math.min(item.amount, max - taken);
        taken += part;
        if (part === item.amount) {
            inventory.setItem(slot);
        } else {
            item.amount -= part;
            inventory.setItem(slot, item);
        }
    }
    return taken;
}

/** Hands over what the player has of the wanted item. Returns a message for the player. */
export function deliver(s, player, contract) {
    const given = take(player, contract.item, contract.count - contract.progress);
    if (given === 0) {
        return t("cities.board.nothing");
    }
    contract.progress += given;
    addStock(s, contract.item, given);
    store.markDirty(s);
    if (contract.progress < contract.count) {
        return t("cities.board.delivered", given, contract.count - contract.progress);
    }
    s.contracts = s.contracts.filter((c) => c !== contract);
    const paid = pay(s, player, contract.reward);
    changeReputation(s, player.id, REPUTATION);
    record(s, s.day, "contract", player.name);
    try {
        player.playSound("random.levelup");
    } catch {
        // sound is optional
    }
    return t("cities.board.completed", contract.id, paid, REPUTATION);
}
