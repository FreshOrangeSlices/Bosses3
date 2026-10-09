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
        DEFAULTS.put("confirm-relic-catalyst", "<dark_red>This item holds a Relic Catalyst:</dark_red> <red><count>% chance the relic brings a curse with it.</red>");
        DEFAULTS.put("cursed-armor-locked", "<dark_red>It won't come off. Only death will part you.</dark_red>");
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
        DEFAULTS.put("last-stand", "<boss> <dark_red><bold>makes its Last Stand!</bold></dark_red>");
        DEFAULTS.put("unstuck", "<boss> <gray>tears itself free!</gray>");
        DEFAULTS.put("nemesis-born", "<dark_red>☠</dark_red> <boss> <red>has marked you. It will return.</red>");
        DEFAULTS.put("nemesis-grows", "<dark_red>☠</dark_red> <boss> <red>grows stronger from your defeat...</red>");
        DEFAULTS.put("nemesis-provoked", "<boss> <red>turns on you!</red>");
        DEFAULTS.put("nemesis-flee", "<dark_red>☠</dark_red> <boss> <gray>loses your trail... for now.</gray>");
        DEFAULTS.put("nemesis-return-title", "<dark_red>☠ NEMESIS ☠</dark_red>");
        DEFAULTS.put("nemesis-return-subtitle", "<boss> <gray>has returned for you</gray>");
        DEFAULTS.put("nemesis-slain", "<player> <gray>has slain their Nemesis</gray> <boss><gray>!</gray>");
        DEFAULTS.put("revenge", "<gold><bold>REVENGE!</bold></gold> <yellow>Bonus rewards for avenging your death.</yellow>");
        DEFAULTS.put("escalation-title", "<dark_red><bold>⚠ ESCALATION ⚠</bold></dark_red>");
        DEFAULTS.put("escalation-subtitle", "<red>Your hunting has drawn something worse...</red>");
        DEFAULTS.put("totem-ritual", "<dark_purple>The totem shudders... something answers.</dark_purple>");
        DEFAULTS.put("totem-blocked", "<red>The totem stays silent here.</red>");
        DEFAULTS.put("compass-upgraded", "<gold>Your Hunter's Compass sharpens!</gold> <gray>Now tier <count>.</gray>");
        DEFAULTS.put("promoted-subtitle", "<boss> <gray>rises!</gray>");
        DEFAULTS.put("ascended", "<player> <gray>has raised</gray> <boss> <gray>to Ascendance!</gray>");
        DEFAULTS.put("ascendant-phase", "<boss> <white><bold>breaks into a new phase!</bold></white>");
        DEFAULTS.put("promote-fail", "<gray>The soul fades away... nothing answers.</gray>");
        DEFAULTS.put("promote-blocked", "<red>That boss can't rise any higher.</red>");
        DEFAULTS.put("waystone-placed", "<aqua>Waystone</aqua> <white><name></white> <aqua>joins the network.</aqua>");
        DEFAULTS.put("waystone-removed", "<gray>Waystone</gray> <white><name></white> <gray>was taken down.</gray>");
        DEFAULTS.put("waystone-not-owner", "<red>Only <owner> can take this waystone down.</red>");
        DEFAULTS.put("waystone-renamed", "<aqua>Waystone renamed to</aqua> <white><name></white><aqua>.</aqua>");
        DEFAULTS.put("waystone-warmup", "<aqua>Travelling to</aqua> <white><name></white><aqua>... hold still.</aqua>");
        DEFAULTS.put("waystone-cancelled", "<red>Travel cancelled.</red>");
        DEFAULTS.put("waystone-arrived", "<aqua>Arrived at</aqua> <white><name></white><aqua>.</aqua>");
        DEFAULTS.put("waystone-combat", "<red>The waystones won't answer while a boss is hunting you.</red>");
        DEFAULTS.put("waystone-cooldown", "<red>The waystone is still recharging (<count>s).</red>");
        DEFAULTS.put("waystone-blocked", "<red>That waystone is blocked or in a world you can't reach.</red>");
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
