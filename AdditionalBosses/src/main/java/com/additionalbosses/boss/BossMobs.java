package com.additionalbosses.boss;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.config.MobCategory;
import com.additionalbosses.util.Rng;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a fitting mob for bosses that are summoned rather than spawned naturally (Boss Totems, Escalation):
 * Overworld bosses are overworld mobs, Nether bosses are Nether mobs, and so on.
 */
public final class BossMobs {

    private static final List<EntityType> OVERWORLD = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK,
        EntityType.STRAY, EntityType.SPIDER, EntityType.CREEPER, EntityType.VINDICATOR, EntityType.PILLAGER,
        EntityType.WITCH, EntityType.BOGGED, EntityType.PARCHED);
    private static final List<EntityType> NETHER = List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE,
        EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.MAGMA_CUBE, EntityType.ZOMBIFIED_PIGLIN);
    private static final List<EntityType> END = List.of(EntityType.ENDERMAN);

    private BossMobs() {
    }

    /** A random mob for this dimension, skipping mobs the server has excluded or disabled. */
    public static EntityType pick(AdditionalBosses plugin, World world) {
        List<EntityType> pool = switch (world.getEnvironment()) {
            case NETHER -> NETHER;
            case THE_END -> END;
            default -> OVERWORLD;
        };
        List<EntityType> allowed = new ArrayList<>();
        for (EntityType type : pool) {
            if (plugin.settings().excludedMobs.contains(type)) {
                continue;
            }
            MobCategory category = plugin.settings().categoryFor(type);
            if (category != null && !category.enabled()) {
                continue;
            }
            allowed.add(type);
        }
        if (allowed.isEmpty()) {
            allowed.addAll(pool);
        }
        return allowed.get(Rng.between(0, allowed.size() - 1));
    }

    public static String categoryId(AdditionalBosses plugin, EntityType type) {
        MobCategory category = plugin.settings().categoryFor(type);
        return category != null ? category.id() : "custom";
    }

    /** Weighted rank roll; falls back to {@code fallback} if every weight is zero. */
    public static BossRank rollRank(java.util.Map<BossRank, Double> weights, BossRank fallback) {
        BossRank rank = Rng.weighted(weights);
        return rank == null ? fallback : rank;
    }

    public static @Nullable BossRank parseOrNull(@Nullable String raw) {
        return raw == null || raw.isBlank() ? null : BossRank.parse(raw);
    }
}
