package com.questbook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/** Messages and text: quest texts in quests.yml may use MiniMessage tags (&lt;gold&gt;, &lt;bold&gt;...). */
final class Msg {

    static final TextColor GOLD = TextColor.color(0xE8B04B);

    private Msg() {
    }

    static Component mini(String text) {
        try {
            return MiniMessage.miniMessage().deserialize(text);
        } catch (RuntimeException ex) {
            return Component.text(text);
        }
    }

    static void chat(Player p, Component message) {
        p.sendMessage(Component.text("✎ ", GOLD).append(message));
    }

    static void error(Player p, String text) {
        p.sendActionBar(Component.text(text, NamedTextColor.RED));
    }
}
