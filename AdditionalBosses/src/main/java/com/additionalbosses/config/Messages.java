package com.additionalbosses.config;

import com.additionalbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player-facing messages (MiniMessage format). Anything missing from config.yml falls back to the default here.
 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("prefix", "<dark_gray>[<gold>Bosses</gold>]</dark_gray> ");
        DEFAULTS.put("spawn-chat", "<gray>A</gray> <boss> <gray>has appeared nearby...</gray>");
        DEFAULTS.put("spawn-actionbar", "<boss> <gray>is somewhere nearby...</gray>");
        DEFAULTS.put("spawn-title", "<rank>");
        DEFAULTS.put("spawn-subtitle", "<boss> <gray>has appeared!</gray>");
        DEFAULTS.put("enraged", "<boss> <red>becomes enraged!</red>");
        DEFAULTS.put("undying", "<boss> <gold>refuses to die!</gold>");
        DEFAULTS.put("slain", "<player> <gray>has slain</gray> <boss><gray>!</gray>");
        DEFAULTS.put("slain-unknown", "<boss> <gray>has fallen.</gray>");
        DEFAULTS.put("rewards", "<gray>The boss dropped:</gray> <items>");
        DEFAULTS.put("rune-applied", "<aqua>Empowered!</aqua> <gray>Your</gray> <item> <gray>gained</gray> <aqua><stat></aqua><gray>.</gray>");
        DEFAULTS.put("relic-bound", "<light_purple>Relic bound!</light_purple> <gray>Your</gray> <item> <gray>now carries</gray> <light_purple><relic></light_purple><gray>.</gray>");
        DEFAULTS.put("relic-corrupted", "<dark_red><bold>CORRUPTED!</bold></dark_red> <red>The relic carried a curse:</red> <dark_red><curse></dark_red>");
        DEFAULTS.put("catalyst-applied", "<gold>Relic Catalyst absorbed!</gold> <gray>Your</gray> <item> <gray>now has</gray> <gold><count></gold> <gray>Relic slots.</gray>");
        DEFAULTS.put("confirm-relic", "<yellow>Binding is permanent.</yellow> <gray>Do it again within 10 seconds to bind</gray> <light_purple><relic></light_purple> <gray>to your</gray> <item><gray>.</gray>");
        DEFAULTS.put("confirm-catalyst", "<yellow>This is permanent.</yellow> <gray>Do it again within 10 seconds to use the Catalyst on your</gray> <item><gray>.</gray>");
        DEFAULTS.put("apply-not-equipment", "<red>That item can't be upgraded. Use weapons, armor, shields or tools.</red>");
        DEFAULTS.put("apply-wrong-type", "<red>This rune only fits: <fits>.</red>");
        DEFAULTS.put("apply-empowerment-full", "<red>That item already holds the maximum of <count> Empowerments.</red>");
        DEFAULTS.put("apply-no-relic-slot", "<red>That item has no free Relic slot.</red>");
        DEFAULTS.put("apply-duplicate-relic", "<red>That item already carries <relic>.</red>");
        DEFAULTS.put("apply-catalyst-max", "<red>That item already has the maximum of <count> Relic slots.</red>");
        DEFAULTS.put("apply-creative", "<yellow>In creative mode, hold the equipment in your main hand and the rune/relic in your off hand, then use</yellow> <white>/bosses apply</white><yellow>.</yellow>");
        DEFAULTS.put("apply-how", "<gray>Hold the equipment in your <white>main hand</white> and the Rune, Relic or Catalyst in your <white>off hand</white>, then run</gray> <white>/bosses apply</white><gray>. You can also pick it up in your inventory and click it onto the item.</gray>");
        DEFAULTS.put("second-dawn", "<gold>Second Dawn</gold> <yellow>pulls you back from the brink!</yellow>");
        DEFAULTS.put("insomnia", "<dark_purple>Your Insomnia curse won't let you sleep.</dark_purple>");
        DEFAULTS.put("butterfingers", "<red>Butterfingers!</red> <gray>You fumbled your item.</gray>");
        DEFAULTS.put("guide-received", "<gold>You received the</gold> <yellow>Boss Hunter's Compendium</yellow><gold>. Read it to learn about bosses!</gold>");
    }

    private final Map<String, String> templates = new HashMap<>(DEFAULTS);

    public Messages(@Nullable ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : DEFAULTS.keySet()) {
            String value = section.getString(key);
            if (value != null) {
                templates.put(key, value);
            }
        }
    }

    public Component get(String key, TagResolver... resolvers) {
        String template = templates.getOrDefault(key, key);
        return Text.mm(template, resolvers);
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return Text.mm(templates.getOrDefault("prefix", "")).append(get(key, resolvers));
    }

    public static Map<String, String> defaults() {
        return DEFAULTS;
    }
}
