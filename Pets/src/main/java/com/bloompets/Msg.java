package com.bloompets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Messages. Everyday ones go above the hotbar so chat stays clean; only big moments use chat. */
public final class Msg {

    /** mcMMO's ability bar (and Additional Bosses) step aside while this is in the future. */
    private static final NamespacedKey ACTION_BAR_BUSY = NamespacedKey.fromString("additionalbosses:actionbar_busy_until");
    private static final long BUSY_MILLIS = 2500;

    public static final TextColor PINK = TextColor.color(0xF5A9D0);

    private Msg() {
    }

    public static void bar(Player player, String text, TextColor color) {
        bar(player, Component.text(text, color));
    }

    public static void bar(Player player, Component message) {
        player.sendActionBar(message);
        if (ACTION_BAR_BUSY != null) {
            player.getPersistentDataContainer().set(ACTION_BAR_BUSY, PersistentDataType.LONG,
                System.currentTimeMillis() + BUSY_MILLIS);
        }
    }

    public static void chat(Player player, String text, TextColor color) {
        chat(player, Component.text(text, color));
    }

    public static void chat(Player player, Component message) {
        player.sendMessage(Component.text("✿ ", PINK).append(message));
    }

    public static void error(Player player, String text) {
        bar(player, text, NamedTextColor.RED);
    }
}
