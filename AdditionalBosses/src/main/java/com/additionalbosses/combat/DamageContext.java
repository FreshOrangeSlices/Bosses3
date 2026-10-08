package com.additionalbosses.combat;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Collects every damage change from boss traits and relics during one damage event,
 * so the event is only modified once: final = base * multiplier + flat.
 */
public final class DamageContext {

    private final @Nullable LivingEntity attacker;
    private final @Nullable Entity direct;
    private final boolean melee;
    private final boolean projectile;
    private final boolean secondary;
    private double multiplier = 1.0;
    private double flat = 0.0;
    private boolean cancelled;

    private DamageContext(@Nullable LivingEntity attacker, @Nullable Entity direct, boolean melee, boolean projectile, boolean secondary) {
        this.attacker = attacker;
        this.direct = direct;
        this.melee = melee;
        this.projectile = projectile;
        this.secondary = secondary;
    }

    public static DamageContext of(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        Entity causing = source.getCausingEntity();
        Entity direct = source.getDirectEntity();
        LivingEntity attacker = causing instanceof LivingEntity living ? living : null;
        boolean melee = attacker != null && direct == causing
            && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
        boolean projectile = direct instanceof Projectile;
        return new DamageContext(attacker, direct, melee, projectile, CombatGuard.active());
    }

    /** The living entity responsible for the damage (the shooter for projectiles). */
    public @Nullable LivingEntity attacker() {
        return attacker;
    }

    public @Nullable Entity direct() {
        return direct;
    }

    public boolean melee() {
        return melee;
    }

    public boolean projectile() {
        return projectile;
    }

    /** True for reflected / echoed damage that must not trigger further on-hit effects. */
    public boolean secondary() {
        return secondary;
    }

    public void multiply(double factor) {
        this.multiplier *= factor;
    }

    public void addFlat(double amount) {
        this.flat += amount;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public boolean changed() {
        return multiplier != 1.0 || flat != 0.0;
    }

    public double apply(double base) {
        return Math.max(0.0, base * multiplier + flat);
    }
}
