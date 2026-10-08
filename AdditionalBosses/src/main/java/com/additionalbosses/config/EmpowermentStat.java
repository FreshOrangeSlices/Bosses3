package com.additionalbosses.config;

import com.additionalbosses.boss.BossRank;
import com.additionalbosses.item.EquipmentType;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * One stat an Empowerment Rune can carry (config.yml: empowerment.stats).
 */
public record EmpowermentStat(
    String id,
    Attribute attribute,
    AttributeModifier.Operation operation,
    String displayName,
    boolean percent,
    double weight,
    List<String> appliesTo,
    Map<BossRank, double[]> ranges
) {

    public boolean fits(EquipmentType type) {
        for (String group : appliesTo) {
            if (type.matches(group)) {
                return true;
            }
        }
        return false;
    }

    public double roll(BossRank rank) {
        double[] range = ranges.get(rank);
        if (range == null) {
            return 0;
        }
        double value = Rng.between(range[0], range[1]);
        // Round to something readable: 0.1% steps for percentages, 0.01 steps for plain numbers.
        return percent ? Math.round(value * 1000.0) / 1000.0 : Math.round(value * 100.0) / 100.0;
    }

    /** "+1.5 Attack Damage" or "+6% Movement Speed" */
    public String format(double amount) {
        if (percent) {
            return "+" + Text.num(amount * 100.0) + "% " + displayName;
        }
        return "+" + Text.num(amount) + " " + displayName;
    }

    public String rangeText(BossRank rank) {
        double[] range = ranges.get(rank);
        if (range == null) {
            return "-";
        }
        if (percent) {
            return Text.num(range[0] * 100) + "-" + Text.num(range[1] * 100) + "%";
        }
        return Text.num(range[0]) + "-" + Text.num(range[1]);
    }

    public String fitsText() {
        StringJoiner joiner = new StringJoiner(", ");
        for (String group : appliesTo) {
            joiner.add(EquipmentType.describeGroup(group));
        }
        return joiner.toString();
    }
}
