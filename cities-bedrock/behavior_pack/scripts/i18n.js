// Translated messages: keys live in resource_pack/texts/*.lang, arguments may be plain values or other messages.

/** A translated message. */
export function t(key, ...args) {
    if (args.length === 0) {
        return { translate: key };
    }
    if (args.some((arg) => typeof arg === "object")) {
        return { translate: key, with: { rawtext: args.map((arg) => typeof arg === "object" ? arg : { text: String(arg) }) } };
    }
    return { translate: key, with: args.map(String) };
}

/** Several messages one after another, each on its own line. */
export function lines(...parts) {
    const rawtext = [];
    parts.filter(Boolean).forEach((part, i) => {
        if (i > 0) {
            rawtext.push({ text: "\n" });
        }
        rawtext.push(typeof part === "string" ? { text: part } : part);
    });
    return { rawtext };
}

export function text(value) {
    return { text: String(value) };
}
