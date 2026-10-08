package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;

/** Special (Creepers only): a bigger blast on a shorter fuse. */
public final class VolatileTrait extends BaseTrait {

    private double radiusBonus = 2;
    private double fuseReduction = 30;

    public VolatileTrait() {
        super("volatile", TraitCategory.SPECIAL, "Volatile", "Volatile");
    }

    @Override
    public void load(ConfigurationSection s) {
        radiusBonus = s.getDouble("explosion-radius-bonus", 2);
        fuseReduction = s.getDouble("fuse-reduction", 30);
    }

    @Override
    public String description() {
        return "Creepers only. Explosion radius +" + Text.num(radiusBonus) + " and a " + Text.num(fuseReduction)
            + "% shorter fuse.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return entity instanceof Creeper;
    }

    @Override
    public void onApply(Boss boss, boolean fresh) {
        if (!fresh || !(boss.entity() instanceof Creeper creeper)) {
            return; // radius and fuse are saved with the creeper, so only set them once
        }
        creeper.setExplosionRadius(creeper.getExplosionRadius() + (int) Math.round(radiusBonus * boss.power()));
        creeper.setMaxFuseTicks(Math.max(10, (int) Math.round(creeper.getMaxFuseTicks() * (1.0 - fuseReduction / 100.0))));
    }
}
