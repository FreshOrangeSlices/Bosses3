package com.additionalbosses.relic.effects;

import com.additionalbosses.item.EquipmentType;
import com.additionalbosses.relic.BaseRelic;
import com.additionalbosses.relic.RelicContext;
import com.additionalbosses.util.Text;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.function.Predicate;

/**
 * "Aura" relics: a potion effect that stays on while the relic is equipped, sometimes only under a condition
 * (in water, underground, at night...). The effect level is configurable per relic.
 */
public final class Auras {

    private Auras() {
    }

    public static Aura ironWill() {
        return new Aura("iron-will", "Iron Will", PotionEffectType.RESISTANCE, "Resistance", 1,
            "Grants {effect} while equipped.", p -> true);
    }

    public static Aura bloodMending() {
        return new Aura("blood-mending", "Blood Mending", PotionEffectType.REGENERATION, "Regeneration", 1,
            "Grants {effect} while equipped.", p -> true);
    }

    public static Aura skybound() {
        return new Aura("skybound", "Skybound", PotionEffectType.JUMP_BOOST, "Jump Boost", 2,
            "Grants {effect} while equipped.", p -> true);
    }

    public static Aura emberWard() {
        return new Aura("ember-ward", "Ember Ward", PotionEffectType.FIRE_RESISTANCE, "Fire Resistance", 1,
            "Grants {effect} while equipped.", p -> true);
    }

    public static Aura tidebound() {
        return new Aura("tidebound", "Tidebound", PotionEffectType.CONDUIT_POWER, "Conduit Power", 1,
            "Grants {effect} while you're in water.", Player::isInWater);
    }

    public static Aura oceanGrace() {
        return new Aura("ocean-grace", "Ocean Grace", PotionEffectType.DOLPHINS_GRACE, "Dolphin's Grace", 1,
            "Grants {effect} while you're in water.", Player::isInWater);
    }

    public static Aura villagerFavor() {
        return new Aura("villager-favor", "Villager Favor", PotionEffectType.HERO_OF_THE_VILLAGE,
            "Hero of the Village", 1, "Grants {effect} (better trades) while equipped.", p -> true);
    }

    public static Aura minersFavor() {
        return new Aura("miners-favor", "Miner's Favor", PotionEffectType.HASTE, "Haste", 1,
            "Grants {effect} while holding a tool or underground.",
            p -> EquipmentType.of(p.getInventory().getItemInMainHand().getType()) == EquipmentType.TOOL
                || p.getEyeLocation().getBlock().getLightFromSky() == 0);
    }

    public static Aura nightstalker() {
        return new Aura("nightstalker", "Nightstalker", PotionEffectType.SPEED, "Speed", 1,
            "Grants {effect} at night or in the dark.",
            p -> isNight(p.getWorld()) || p.getEyeLocation().getBlock().getLightLevel() <= 7);
    }

    public static Aura sunblessed() {
        return new Aura("sunblessed", "Sunblessed", PotionEffectType.REGENERATION, "Regeneration", 1,
            "Grants {effect} while you stand in sunlight.",
            p -> {
                World w = p.getWorld();
                Block head = p.getEyeLocation().getBlock();
                return w.getEnvironment() == World.Environment.NORMAL && !isNight(w) && !w.hasStorm()
                    && head.getLightFromSky() >= 15;
            });
    }

    static boolean isNight(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        long time = world.getTime();
        return time >= 13000 && time < 23000;
    }

    /** One configurable potion aura. */
    public static final class Aura extends BaseRelic {
        private final PotionEffectType type;
        private final String effectName;
        private final int defaultLevel;
        private final String template;
        private final Predicate<Player> condition;
        private int level;

        Aura(String id, String name, PotionEffectType type, String effectName, int defaultLevel, String template,
             Predicate<Player> condition) {
            super(id, name, false);
            this.type = type;
            this.effectName = effectName;
            this.defaultLevel = defaultLevel;
            this.level = defaultLevel;
            this.template = template;
            this.condition = condition;
        }

        @Override
        public void load(ConfigurationSection s) {
            level = Math.max(1, s.getInt("level", defaultLevel));
        }

        @Override
        public String description() {
            return template.replace("{effect}", effectName + " " + Text.roman(level));
        }

        @Override
        public boolean passive() {
            return true;
        }

        @Override
        public void onPassive(RelicContext ctx) {
            Player p = ctx.player();
            if (!condition.test(p)) {
                return;
            }
            // Short, constantly refreshed effect: it fades a few seconds after the condition stops.
            PotionEffect current = p.getPotionEffect(type);
            if (current == null || (current.getAmplifier() <= level - 1 && current.getDuration() >= 0
                && current.getDuration() < 60)) {
                p.addPotionEffect(new PotionEffect(type, 100, level - 1, true, false, true));
            }
        }
    }
}
