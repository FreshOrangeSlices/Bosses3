package com.additionalbosses.trait;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/**
 * A boss combat trait. Traits change how a boss fights rather than just raising its numbers.
 *
 * <p>To add a new trait: create a class extending {@link BaseTrait}, override the hooks it needs,
 * register it in {@link TraitManager#registerDefaults()}, and (optionally) add a section for it under
 * {@code traits:} in config.yml.</p>
 *
 * <p>Hooks are called by central managers, never by per-boss tasks:</p>
 * <ul>
 *   <li>{@link #onTick} - every 10 server ticks, from the single BossManager ticker</li>
 *   <li>{@link #onAttack}/{@link #onDamaged} - before damage is applied (modify the DamageContext)</li>
 *   <li>{@link #afterAttack}/{@link #afterDamaged} - after damage is final (heal, reflect, apply effects)</li>
 * </ul>
 */
public interface BossTrait {

    String id();

    TraitCategory category();

    /** Name shown in the guide book and /bosses inspect, e.g. "Vampiric". */
    String displayName();

    /** Word used in the boss name, e.g. "Ravenous" in "Nightmare Ravenous Zombie". */
    String adjective();

    /** One or two sentences, using the configured numbers. */
    String description();

    /** Trait ids this trait can never be rolled together with (config can add more pairs). */
    default Set<String> incompatibleWith() {
        return Set.of();
    }

    /** Whether this trait makes sense for the given mob. */
    default boolean supports(LivingEntity entity) {
        return true;
    }

    /** Reads this trait's own settings from its config section (traits.&lt;id&gt;). */
    default void load(ConfigurationSection section) {
    }

    /**
     * Called when the trait is attached. {@code fresh} is true when the boss is created and false when an
     * existing boss is restored after a chunk load or restart (persistent changes like attribute modifiers
     * should only be applied when fresh).
     */
    default void onApply(Boss boss, boolean fresh) {
    }

    default void onTick(Boss boss, int now) {
    }

    default void onAttack(Boss boss, LivingEntity victim, EntityDamageEvent event, DamageContext ctx) {
    }

    default void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
    }

    default void onDamaged(Boss boss, EntityDamageEvent event, DamageContext ctx) {
    }

    default void afterDamaged(Boss boss, EntityDamageEvent event, double finalDamage, DamageContext ctx) {
    }

    /** A projectile was launched by the boss; its velocity can still be changed here. */
    default void onProjectileLaunch(Boss boss, Projectile projectile) {
    }

    /** Called after every trait's onProjectileLaunch, when the projectile's velocity is final. */
    default void afterProjectileLaunch(Boss boss, Projectile projectile) {
    }

    /** Return true to cheat death (the boss resurrects like with a Totem of Undying). */
    default boolean onLethalDamage(Boss boss) {
        return false;
    }

    default void onDeath(Boss boss) {
    }
}
