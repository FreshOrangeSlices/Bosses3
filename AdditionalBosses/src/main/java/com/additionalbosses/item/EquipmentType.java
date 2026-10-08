package com.additionalbosses.item;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.Locale;

/**
 * Broad equipment classes, used to decide which Empowerments fit an item and where its bonuses are active.
 */
public enum EquipmentType {
    MELEE("Melee Weapons"),
    RANGED("Bows & Crossbows"),
    HELMET("Helmets"),
    CHESTPLATE("Chestplates"),
    LEGGINGS("Leggings"),
    BOOTS("Boots"),
    SHIELD("Shields"),
    TOOL("Tools"),
    OTHER("Other");

    private final String plural;

    EquipmentType(String plural) {
        this.plural = plural;
    }

    public String plural() {
        return plural;
    }

    public static EquipmentType of(Material material) {
        String n = material.name();
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR") || material == Material.MACE
            || material == Material.TRIDENT) {
            return MELEE;
        }
        if (material == Material.BOW || material == Material.CROSSBOW) {
            return RANGED;
        }
        if (n.endsWith("_HELMET")) {
            return HELMET;
        }
        if (n.endsWith("_CHESTPLATE") || material == Material.ELYTRA) {
            return CHESTPLATE;
        }
        if (n.endsWith("_LEGGINGS")) {
            return LEGGINGS;
        }
        if (n.endsWith("_BOOTS")) {
            return BOOTS;
        }
        if (material == Material.SHIELD) {
            return SHIELD;
        }
        if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || material == Material.FISHING_ROD
            || material == Material.SHEARS || material == Material.BRUSH) {
            return TOOL;
        }
        return OTHER;
    }

    public boolean isEquipment() {
        return this != OTHER;
    }

    public boolean isArmor() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    /** Where an attribute bonus on this item is active. */
    public EquipmentSlotGroup slotGroup() {
        return switch (this) {
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
            case SHIELD -> EquipmentSlotGroup.HAND;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }

    /**
     * Matches a group name from config: ANY, WEAPON, ARMOR, or any single type (MELEE, RANGED, HELMET, ...).
     */
    public boolean matches(String group) {
        String g = group.trim().toUpperCase(Locale.ROOT);
        return switch (g) {
            case "ANY" -> isEquipment();
            case "WEAPON", "WEAPONS" -> this == MELEE || this == RANGED;
            case "ARMOR", "ARMOUR" -> isArmor();
            default -> name().equals(g) || (name() + "S").equals(g);
        };
    }

    /** Friendly text for a config group name, used in lore and the guide book. */
    public static String describeGroup(String group) {
        String g = group.trim().toUpperCase(Locale.ROOT);
        return switch (g) {
            case "ANY" -> "Any Equipment";
            case "WEAPON", "WEAPONS" -> "Weapons";
            case "ARMOR", "ARMOUR" -> "Armor";
            default -> {
                for (EquipmentType t : values()) {
                    if (t.name().equals(g) || (t.name() + "S").equals(g)) {
                        yield t.plural();
                    }
                }
                yield group;
            }
        };
    }
}
