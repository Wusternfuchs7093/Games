// Settlements are kept as JSON in world dynamic properties: one property per settlement plus an index.

import { world } from "@minecraft/server";

const INDEX = "cities:index";
const PREFIX = "cities:s:";

let settlements = null;
const dirty = new Set();

export function all() {
    if (settlements === null) {
        settlements = new Map();
        let ids = [];
        try {
            ids = JSON.parse(world.getDynamicProperty(INDEX) ?? "[]");
        } catch {
            ids = [];
        }
        for (const id of ids) {
            try {
                const raw = world.getDynamicProperty(PREFIX + id);
                if (typeof raw === "string") {
                    settlements.set(id, JSON.parse(raw));
                }
            } catch (error) {
                console.warn("[cities] could not load settlement " + id + ": " + error);
            }
        }
    }
    return settlements;
}

export function byId(id) {
    return all().get(id);
}

export function add(s) {
    all().set(s.id, s);
    world.setDynamicProperty(INDEX, JSON.stringify([...all().keys()]));
    markDirty(s);
    flush();
}

export function remove(s) {
    all().delete(s.id);
    world.setDynamicProperty(PREFIX + s.id, undefined);
    world.setDynamicProperty(INDEX, JSON.stringify([...all().keys()]));
    dirty.delete(s.id);
}

export function markDirty(s) {
    dirty.add(s.id);
}

/** Writes every changed settlement back. */
export function flush() {
    for (const id of dirty) {
        const s = all().get(id);
        if (s) {
            try {
                world.setDynamicProperty(PREFIX + id, JSON.stringify(s));
            } catch (error) {
                console.warn("[cities] could not save settlement " + id + ": " + error);
            }
        }
    }
    dirty.clear();
}
