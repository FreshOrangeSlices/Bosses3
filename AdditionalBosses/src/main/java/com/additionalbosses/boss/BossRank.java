package com.additionalbosses.boss;

import com.additionalbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The boss ranks. Colour and star count are fixed identity; every balance value lives in config.yml.
 * The first five appear naturally; Ascendant is only reached by promoting a boss with trophies.
 */
public enum BossRank {

    GRAY(1, 0xB8B8B8, 0x4A4A4A, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS, "Hard"),
    GREEN(2, 0x55E655, 0x1D7A1D, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS, "Very Hard"),
    RED(3, 0xFF5050, 0xA01414, BossBar.Color.RED, BossBar.Overlay.NOTCHED_6, "Brutal"),
    PURPLE(4, 0xC77DFF, 0x6A1B9A, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10, "Nightmare"),
    GOLD(5, 0xFFC125, 0x9A6A00, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20, "Legendary"),
    ASCENDANT(6, 0xEAF4FF, 0x3F5F86, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_20, "Ascendant");

    /** Pearl-white shimmer used for Ascendant names (Nemeses keep plain white). */
    private static final String ASCENDANT_GRADIENT = "<gradient:#FFFFFF:#A8DCFF:#FFFFFF>";

    private final int stars;
    private final TextColor color;
    private final TextColor bookColor;
    private final Color bukkitColor;
    private final BossBar.Color barColor;
    private final BossBar.Overlay barOverlay;
    private final String defaultName;

    BossRank(int stars, int rgb, int bookRgb, BossBar.Color barColor, BossBar.Overlay barOverlay, String defaultName) {
        this.stars = stars;
        this.color = TextColor.color(rgb);
        this.bookColor = TextColor.color(bookRgb);
        this.bukkitColor = Color.fromRGB(rgb);
        this.barColor = barColor;
        this.barOverlay = barOverlay;
        this.defaultName = defaultName;
    }

    public int stars() {
        return stars;
    }

    public String starText() {
        return Text.stars(stars);
    }

    /** Bright colour for names, chat and lore. */
    public TextColor color() {
        return color;
    }

    /** Darker variant that stays readable on the beige page of a written book. */
    public TextColor bookColor() {
        return bookColor;
    }

    public Color bukkitColor() {
        return bukkitColor;
    }

    public BossBar.Color barColor() {
        return barColor;
    }

    public BossBar.Overlay barOverlay() {
        return barOverlay;
    }

    public String defaultName() {
        return defaultName;
    }

    /** Ranks that can appear when a mob spawns. Ascendant only comes from promotion. */
    public boolean natural() {
        return this != ASCENDANT;
    }

    /** The next rank up, or this rank if it is already the highest. */
    public BossRank up(int steps) {
        BossRank[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal() + steps))];
    }

    /** Text in this rank's style: Legendary is bold gold, Ascendant a bold pearl shimmer. */
    public Component styled(String text) {
        if (this == ASCENDANT) {
            return Text.noItalic(Text.mm("<bold>" + ASCENDANT_GRADIENT + Text.MM.escapeTags(text) + "</gradient></bold>"));
        }
        Component c = Component.text(text, color);
        return this == GOLD ? c.decorate(TextDecoration.BOLD) : c;
    }

    /** Legendary and above get the big effects (totem particles, server broadcasts...). */
    public boolean top() {
        return atLeast(GOLD);
    }

    public boolean atLeast(BossRank other) {
        return ordinal() >= other.ordinal();
    }

    public static @Nullable BossRank parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        for (BossRank rank : values()) {
            if (rank.name().equals(s)) {
                return rank;
            }
        }
        // Allow the friendly names too: "legendary", "nightmare", ...
        for (BossRank rank : values()) {
            if (rank.defaultName.replace(" ", "").equalsIgnoreCase(s.replace(" ", "").replace("_", ""))) {
                return rank;
            }
        }
        return null;
    }
}
