package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.combat.DamageContext;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent;

/** Defense: takes less damage from everything. */
public final class BulwarkTrait extends BaseTrait {

    private double reduction = 20;

    public BulwarkTrait() {
        super("bulwark", TraitCategory.DEFENSE, "Bulwark", "Ironclad");
    }

    @Override
    public void load(ConfigurationSection s) {
        reduction = s.getDouble("damage-reduction", 20);
    }

    @Override
    public String description() {
        return "Takes " + Text.num(reduction) + "% less damage from all sources.";
    }

    @Override
    public void onDamaged(Boss boss, EntityDamageEvent event, DamageContext ctx) {
        double percent = Math.min(60.0, reduction * boss.power());
        ctx.multiply(1.0 - percent / 100.0);
    }
}
