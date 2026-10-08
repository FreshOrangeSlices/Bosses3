package com.additionalbosses.trait;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Special two-trait combinations. A boss with both traits gets the synergy's title in its name
 * (e.g. "Legendary Bloodhunter Skeleton") and slightly stronger traits.
 */
public final class Synergies {

    public record Synergy(String first, String second, String title) {
        boolean matches(List<BossTrait> traits) {
            boolean a = false;
            boolean b = false;
            for (BossTrait t : traits) {
                a |= t.id().equals(first);
                b |= t.id().equals(second);
            }
            return a && b;
        }
    }

    public static final List<Synergy> ALL = List.of(
        new Synergy("swift", "vampiric", "Bloodhunter"),
        new Synergy("bulwark", "regenerating", "Unyielding"),
        new Synergy("berserk", "executioner", "Headsman"),
        new Synergy("blinking", "shadowed", "Shadowstep"),
        new Synergy("gravitic", "quaking", "Earthbreaker"),
        new Synergy("deadeye", "volley", "Stormbow"));

    /** Trait power multiplier for bosses that have a synergy. */
    public static final double POWER_BONUS = 1.15;

    private Synergies() {
    }

    public static @Nullable Synergy find(List<BossTrait> traits) {
        for (Synergy s : ALL) {
            if (s.matches(traits)) {
                return s;
            }
        }
        return null;
    }
}
