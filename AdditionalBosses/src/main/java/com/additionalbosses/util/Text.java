package com.additionalbosses.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;

/**
 * Small text helpers: MiniMessage parsing, name prettifying, numbers and roman numerals.
 */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String input, TagResolver... resolvers) {
        return MM.deserialize(input, resolvers);
    }

    /** Lore and item names are italic by default in Minecraft; this turns that off. */
    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component line(String text, TextColor color) {
        return noItalic(Component.text(text, color));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String stars(int count) {
        return "★".repeat(Math.max(0, count));
    }

    /** ZOMBIE_VILLAGER -> Zombie Villager, blood-pact -> Blood Pact */
    public static String pretty(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /** 2.0 -> "2", 1.25 -> "1.25", 0.333 -> "0.33" */
    public static String num(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        String s = String.format(Locale.ROOT, "%.2f", rounded);
        if (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String roman(int value) {
        if (value <= 0 || value > 3999) {
            return String.valueOf(value);
        }
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) {
            while (value >= numbers[i]) {
                value -= numbers[i];
                out.append(letters[i]);
            }
        }
        return out.toString();
    }
}
