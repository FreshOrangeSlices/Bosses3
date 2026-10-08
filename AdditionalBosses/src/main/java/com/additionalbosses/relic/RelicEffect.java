package com.additionalbosses.relic;

import com.additionalbosses.combat.DamageContext;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

/**
 * A Relic effect (good) or Curse (bad). Relics are bound permanently to a piece of equipment and are active
 * while that item is held in the main hand (HAND) or worn / held as a shield (ARMOR). An effect can behave
 * differently depending on where its item is used, via {@link RelicContext#slot()}.
 *
 * <p>To add one: extend {@link BaseRelic}, override the hooks you need, register it in
 * {@link RelicManager#registerDefaults()}, and optionally add a config section under relics.effects / relics.curses.</p>
 */
public interface RelicEffect {

    String id();

    String displayName();

    /** One short sentence for lore and the guide book. */
    String description();

    boolean curse();

    default void load(ConfigurationSection section) {
    }

    /** True if {@link #onPassive} should be called about once a second while equipped. */
    default boolean passive() {
        return false;
    }

    default void onAttack(RelicContext ctx, LivingEntity victim, EntityDamageEvent event, DamageContext damage,
                          boolean victimIsBoss) {
    }

    default void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
    }

    default void onDamaged(RelicContext ctx, EntityDamageEvent event, DamageContext damage) {
    }

    default void afterDamaged(RelicContext ctx, EntityDamageEvent event, double finalDamage, DamageContext damage) {
    }

    default void onKill(RelicContext ctx, EntityDeathEvent event, boolean victimWasBoss) {
    }

    /** Return true to cheat death (only called when the player has no totem). */
    default boolean onLethal(RelicContext ctx) {
        return false;
    }

    default void onPassive(RelicContext ctx) {
    }

    default void onJump(RelicContext ctx, PlayerJumpEvent event) {
    }

    default boolean preventsSleep() {
        return false;
    }

    /** A stat change applied to the player while this relic is active (removed automatically when it isn't). */
    record AttributeBonus(Attribute attribute, double amount, AttributeModifier.Operation operation) {
    }

    /** Attribute changes held while equipped (e.g. Heavy Crown's armor, Reduction's smaller size). */
    default List<AttributeBonus> attributeBonuses() {
        return List.of();
    }

    /** Called when this relic stops being active for a player (unequipped, logged out, plugin disabled). */
    default void onDeactivate(Player player) {
    }

    /** Multiplies the killer's boss reward chances. */
    default double rewardChanceMultiplier() {
        return 1.0;
    }
}
