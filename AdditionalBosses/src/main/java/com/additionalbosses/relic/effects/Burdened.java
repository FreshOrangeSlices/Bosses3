package com.additionalbosses.relic.effects;

import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Burdened" relics: good relics with a built-in drawback. Taking one is a deliberate risk, unlike a curse,
 * which you only discover when a corrupted relic is bound.
 */
public final class Burdened {

    private Burdened() {
    }

    public static final class Greed extends BaseRelic {
        private double extraDropChance = 40;
        private double hungerSeconds = 20;

        public Greed() {
            super("greed", "Greed", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            extraDropChance = s.getDouble("extra-drop-chance", 40);
            hungerSeconds = s.getDouble("hunger-drain-seconds", 20);
        }

        @Override
        public String description() {
            return "Burdened: " + Text.num(extraDropChance) + "% chance mobs drop an extra item, but you lose"
                + " hunger every " + Text.num(hungerSeconds) + "s.";
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (ctx.manager().ready(p, id(), (int) Math.round(hungerSeconds * 20))) {
                p.setExhaustion(Math.min(40f, p.getExhaustion() + 4f)); // 4 exhaustion = 1 hunger point
            }
        }

        @Override
        public void onKill(RelicContext ctx, EntityDeathEvent event, boolean victimWasBoss) {
            // Normal mobs only, so Greed doesn't overlap with Hunter's Mark's boss rewards.
            if (victimWasBoss || event.getEntity() instanceof Player || event.getDrops().isEmpty()
                || !Rng.chance(extraDropChance)) {
                return;
            }
            List<ItemStack> drops = new ArrayList<>(event.getDrops());
            ItemStack pick = drops.get(Rng.between(0, drops.size() - 1));
            event.getDrops().add(pick.asQuantity(1));
        }
    }

    public static final class HeavyCrown extends BaseRelic {
        private double armor = 4;
        private double knockbackResistance = 0.5;
        private double slowness = 15;

        public HeavyCrown() {
            super("heavy-crown", "Heavy Crown", false);
        }

        @Override
        public void load(ConfigurationSection s) {
            armor = s.getDouble("armor", 4);
            knockbackResistance = s.getDouble("knockback-resistance", 0.5);
            slowness = s.getDouble("speed-penalty", 15);
        }

        @Override
        public String description() {
            return "Burdened: +" + Text.num(armor) + " armor and +" + Text.num(knockbackResistance * 100)
                + "% knockback resistance, but " + Text.num(slowness) + "% slower.";
        }

        @Override
        public List<AttributeBonus> attributeBonuses() {
            return List.of(
                new AttributeBonus(Attribute.ARMOR, armor, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeBonus(Attribute.KNOCKBACK_RESISTANCE, knockbackResistance, AttributeModifier.Operation.ADD_NUMBER),
                new AttributeBonus(Attribute.MOVEMENT_SPEED, -slowness / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
}
