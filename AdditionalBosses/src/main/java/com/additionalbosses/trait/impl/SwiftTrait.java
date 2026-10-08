package com.additionalbosses.trait.impl;

import com.additionalbosses.boss.Boss;
import com.additionalbosses.trait.BaseTrait;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

/** Movement: permanently faster. */
public final class SwiftTrait extends BaseTrait {

    private double speedBonus = 25;

    public SwiftTrait() {
        super("swift", TraitCategory.MOVEMENT, "Swift", "Swift");
    }

    @Override
    public void load(ConfigurationSection s) {
        speedBonus = s.getDouble("speed-bonus", 25);
    }

    @Override
    public String description() {
        return "Moves " + Text.num(speedBonus) + "% faster.";
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return walks(entity);
    }

    @Override
    public void onApply(Boss boss, boolean fresh) {
        AttributeInstance speed = boss.entity().getAttribute(Attribute.MOVEMENT_SPEED);
        if (fresh && speed != null && speed.getModifier(Keys.MOD_TRAIT_SWIFT) == null) {
            speed.addModifier(new AttributeModifier(Keys.MOD_TRAIT_SWIFT, speedBonus * boss.power() / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
}
