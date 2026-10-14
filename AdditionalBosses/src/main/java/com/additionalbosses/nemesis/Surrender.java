package com.additionalbosses.nemesis;

import com.additionalbosses.boss.BossManager;
import com.additionalbosses.util.Rng;
import org.bukkit.Material;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A Nemesis's Terms of Surrender: an easy, common way to die. Die like that while carrying its Challenge and the feud
 * is over (no reward). Picked once per Nemesis and never changes. A Nemesis never asks you to die to its own kind,
 * and no boss ever counts as the one that killed you.
 */
public enum Surrender {
    DROWNING("Sink beneath the water and stay there. Then I will consider our feud ended.", "drown", Set.of()),
    BURNING("Let fire finish what I began.", "burn to death (fire or lava)", Set.of()),
    FALL("Throw yourself from somewhere high enough that the ground may judge you.", "die from a fall", Set.of()),
    CREEPER("Stand before the hissing one. Do not run.", "be blown up by a creeper", Set.of("CREEPER")),
    ZOMBIE("Let the dead drag you down.", "be killed by a zombie", Set.of("ZOMBIE", "HUSK", "ZOMBIE_VILLAGER")),
    SKELETON("Take an arrow meant for the grave.", "be killed by a skeleton",
        Set.of("SKELETON", "STRAY", "BOGGED", "PARCHED")),
    SPIDER("Feed yourself to the crawling dark.", "be killed by a spider", Set.of("SPIDER", "CAVE_SPIDER")),
    ENDERMAN("Look one in the eyes. Accept what follows.", "be killed by an enderman", Set.of("ENDERMAN")),
    DROWNED("Return to the water and let its dead claim you.", "be killed by a drowned", Set.of("DROWNED")),
    CACTUS("Embrace the thorns until there is nothing left to bleed.", "die to a cactus", Set.of()),
    SUFFOCATION("Bury yourself where even the air cannot follow.", "suffocate in blocks", Set.of()),
    COMMON_MOB("Let any common beast strike the final blow.", "be killed by an ordinary monster", Set.of());

    /** Not "common beasts": bosses of their own, and what the spec rules out (pillagers...). */
    private static final Set<String> NOT_COMMON = Set.of("WARDEN", "WITHER", "ENDER_DRAGON", "ELDER_GUARDIAN",
        "RAVAGER", "EVOKER", "VINDICATOR", "PILLAGER", "ILLUSIONER", "PIGLIN_BRUTE", "VEX", "BREEZE", "CREAKING");

    /** Families: a Husk Nemesis counts zombies as its own kind, and so on. */
    private static final List<Set<String>> FAMILIES = List.of(
        Set.of("ZOMBIE", "HUSK", "ZOMBIE_VILLAGER", "DROWNED", "ZOMBIFIED_PIGLIN"),
        Set.of("SKELETON", "STRAY", "BOGGED", "PARCHED", "WITHER_SKELETON"),
        Set.of("SPIDER", "CAVE_SPIDER"));

    private static boolean sameKind(EntityType a, EntityType b) {
        if (a == b) {
            return true;
        }
        for (Set<String> family : FAMILIES) {
            if (family.contains(a.name()) && family.contains(b.name())) {
                return true;
            }
        }
        return false;
    }

    private final String words;
    private final String plain;
    private final Set<String> mobs;

    Surrender(String words, String plain, Set<String> mobs) {
        this.words = words;
        this.plain = plain;
        this.mobs = mobs;
    }

    /** What the Nemesis says. */
    public String words() {
        return words;
    }

    /** What it means, plainly. */
    public String plain() {
        return plain;
    }

    /** A random condition that doesn't involve the Nemesis's own kind (a Zombie Nemesis won't ask for a zombie). */
    public static Surrender pick(EntityType nemesisType) {
        List<Surrender> options = new ArrayList<>();
        for (Surrender s : values()) {
            boolean ownKind = false;
            for (String mob : s.mobs) {
                EntityType type = EntityType.fromName(mob.toLowerCase(Locale.ROOT));
                ownKind |= type != null && sameKind(type, nemesisType);
            }
            if (!ownKind) {
                options.add(s);
            }
        }
        return options.get(Rng.between(0, options.size() - 1));
    }

    public static @Nullable Surrender parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Whether this death meets the terms. Never when a boss (or a boss's minion) had a hand in it, and never at the
     * hands of the Nemesis's own kind.
     */
    public boolean matches(PlayerDeathEvent event, EntityType nemesisType) {
        Player p = event.getEntity();
        Entity killer = event.getDamageSource().getCausingEntity();
        if (killer != null && (BossManager.isBoss(killer) || BossManager.isMinion(killer) || BossManager.isPet(killer)
            || sameKind(killer.getType(), nemesisType))) {
            return false;
        }
        EntityDamageEvent last = p.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = last == null ? null : last.getCause();
        return switch (this) {
            case DROWNING -> cause == EntityDamageEvent.DamageCause.DROWNING;
            case BURNING -> cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA;
            case FALL -> cause == EntityDamageEvent.DamageCause.FALL;
            case SUFFOCATION -> cause == EntityDamageEvent.DamageCause.SUFFOCATION;
            case CACTUS -> cause == EntityDamageEvent.DamageCause.CONTACT && last instanceof EntityDamageByBlockEvent byBlock
                && byBlock.getDamagerBlockState() != null && byBlock.getDamagerBlockState().getType() == Material.CACTUS;
            case COMMON_MOB -> killer instanceof Enemy && !(killer instanceof Player)
                && !NOT_COMMON.contains(killer.getType().name());
            default -> killer != null && mobs.contains(killer.getType().name());
        };
    }
}
