package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Text;
import net.kyori.adventure.util.TriState;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

/** Special: wreathed in harmless flames, immune to fire, and its hits set targets alight. */
public final class InfernalTrait extends BaseTrait {

    private static final Set<DamageType> FIRE = Set.of(
        DamageType.IN_FIRE, DamageType.ON_FIRE, DamageType.LAVA, DamageType.HOT_FLOOR, DamageType.CAMPFIRE);
    private double seconds = 4;

    public InfernalTrait() {
        super("infernal", TraitCategory.SPECIAL, "Infernal", "Infernal");
    }

    @Override
    public void load(ConfigurationSection s) {
        seconds = s.getDouble("ignite-seconds", 4);
    }

    @Override
    public String description() {
        return "Immune to fire and lava; its hits set targets ablaze for " + Text.num(seconds) + "s.";
    }

    @Override
    public void onApply(Boss boss, boolean fresh) {
        boss.entity().setVisualFire(TriState.TRUE);
    }

    @Override
    public void onDamaged(Boss boss, EntityDamageEvent event, DamageContext ctx) {
        if (FIRE.contains(event.getDamageSource().getDamageType())) {
            ctx.cancel();
            boss.entity().setFireTicks(0);
        }
    }

    @Override
    public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
        if (ctx.secondary() || finalDamage <= 0) {
            return;
        }
        int ticks = (int) Math.round(seconds * 20 * boss.power());
        victim.setFireTicks(Math.max(victim.getFireTicks(), ticks));
    }

    @Override
    public void onTick(Boss boss, int now) {
        if (now % 20 < 10) {
            Fx.particle(Fx.center(boss.entity()), Particle.FLAME, 3, 0.35, 0.01);
        }
    }
}
