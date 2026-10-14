package com.questbook;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Data keys. */
final class Keys {

    /** byte, on the Quest Book item. */
    static NamespacedKey BOOK;

    private Keys() {
    }

    static void init(Plugin plugin) {
        BOOK = new NamespacedKey(plugin, "quest_book");
    }
}
