package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.Set;

/** Offense (archers only): every arrow is joined by extra arrows in a spread. */
public final class VolleyTrait extends BaseTrait {

    private static final Set<EntityType> ARCHERS = Set.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED,
        EntityType.PARCHED, EntityType.PILLAGER, EntityType.PIGLIN, EntityType.ILLUSIONER);

    /** Guard so the extra arrows we launch don't trigger another volley. */
    private static boolean spawning;
    private int extraArrows = 2;
    private double spread = 8;

    public VolleyTrait() {
        super("volley", TraitCategory.OFFENSE, "Volley", "Volleying");
    }

    @Override
    public void load(ConfigurationSection s) {
        extraArrows = Math.max(0, s.getInt("extra-arrows", 2));
        spread = s.getDouble("spread", 8);
    }

    @Override
    public String description() {
        return "Archers only. Every shot fires " + extraArrows + " extra arrows in a " + Text.num(spread) + "° spread.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return ARCHERS.contains(entity.getType());
    }

    /** True while the extra arrows are being launched (other traits use this to avoid boosting them twice). */
    public static boolean isSpawning() {
        return spawning;
    }

    @Override
    public void afterProjectileLaunch(Boss boss, Projectile projectile) {
        if (spawning || !(projectile instanceof AbstractArrow original)) {
            return;
        }
        Vector base = projectile.getVelocity();
        spawning = true;
        try {
            for (int i = 1; i <= extraArrows; i++) {
                double side = (i % 2 == 0) ? 1 : -1;
                double angle = Math.toRadians(spread * side * Math.ceil(i / 2.0));
                Vector velocity = base.clone().rotateAroundY(angle);
                Arrow extra = boss.entity().launchProjectile(Arrow.class, velocity);
                extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                extra.setDamage(original.getDamage());
                extra.setCritical(original.isCritical());
            }
        } finally {
            spawning = false;
        }
    }
}
