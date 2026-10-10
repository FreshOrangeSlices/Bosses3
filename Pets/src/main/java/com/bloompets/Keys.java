package com.bloompets;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Data keys. "bloompets:pet" on a mob marks a pet (Additional Bosses reads it too, to leave pets alone). */
public final class Keys {

    public static NamespacedKey PET;          // string, pet id, on a summoned pet
    public static NamespacedKey OWNER;        // string, owner uuid, on a summoned pet
    public static NamespacedKey BOND;         // string "<player uuid>:<feedings>", on a wild mob being tamed
    public static NamespacedKey BLOOM;        // string, pet id, on a Pet Bloom item
    public static NamespacedKey BLOOM_OWNER;  // string, owner uuid, on a Pet Bloom item
    public static NamespacedKey TOY;          // byte, on the Pet Toy item
    public static NamespacedKey MOD_LEVEL;
    public static NamespacedKey MOD_STEP;
    public static NamespacedKey MOD_RIDE_SCALE;
    public static NamespacedKey MOD_BONUS;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        PET = new NamespacedKey(plugin, "pet");
        OWNER = new NamespacedKey(plugin, "owner");
        BOND = new NamespacedKey(plugin, "bond");
        BLOOM = new NamespacedKey(plugin, "bloom");
        BLOOM_OWNER = new NamespacedKey(plugin, "bloom_owner");
        TOY = new NamespacedKey(plugin, "toy");
        MOD_LEVEL = new NamespacedKey(plugin, "pet_level");
        MOD_STEP = new NamespacedKey(plugin, "pet_step");
        MOD_RIDE_SCALE = new NamespacedKey(plugin, "ride_scale");
        MOD_BONUS = new NamespacedKey(plugin, "pet_bonus");
    }

    public static boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(PET, PersistentDataType.STRING);
    }

    /** Bosses, boss minions and statues from Additional Bosses can't be tamed. */
    public static boolean isBossThing(Entity entity) {
        return hasBossKey(entity, "boss") || hasBossKey(entity, "minion_of") || isStatue(entity);
    }

    /** An Additional Bosses statue: a frozen mob on display, never something to attack. */
    public static boolean isStatue(Entity entity) {
        return hasBossKey(entity, "statue");
    }

    private static boolean hasBossKey(Entity entity, String key) {
        NamespacedKey k = NamespacedKey.fromString("additionalbosses:" + key);
        return k != null && entity.getPersistentDataContainer().has(k);
    }
}
