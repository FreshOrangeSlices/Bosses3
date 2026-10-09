package com.bloompets;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One pet a player owns. While dismissed, the pet lives entirely in here: {@link #snapshot} is the whole mob (its
 * look, variant, health, Vex gear...) saved byte for byte, and comes back exactly the same when summoned.
 */
public final class Pet {

    /** How a small pet is ridden, decided once when it is tamed. */
    public enum RideStyle {
        /** Big enough already. */
        NORMAL,
        /** The pet grows to carry you. */
        GROW,
        /** You shrink to ride it. */
        SHRINK
    }

    public static final int MAX_LEVEL = 10;

    public final UUID id;
    public final UUID owner;
    public final Species species;
    public String name;
    public int level = 1;
    public int xp;
    public RideStyle rideStyle;
    /** Epoch millis until which a fainted pet rests. */
    public long restingUntil;
    /** XP an Allay has gathered towards its next Bottle o' Enchanting. */
    public int xpBank;
    /** Order in the pet menu. */
    public int order;
    /** Health as a share of max health when it was put away (-1: unknown, use what the snapshot says). */
    public double health = -1;
    /** When feeding last gave bond XP (not saved; feeding gives XP at most every 30 seconds). */
    public long lastFeedXp;
    public byte @Nullable [] snapshot;
    public ItemStack[] storage;

    public Pet(UUID id, UUID owner, Species species, String name, RideStyle rideStyle) {
        this.id = id;
        this.owner = owner;
        this.species = species;
        this.name = name;
        this.rideStyle = rideStyle;
        this.storage = new ItemStack[species.storage().slots(MAX_LEVEL)];
    }

    /** Bond XP needed to go from this level to the next. */
    public static int xpToNext(int level) {
        return 40 + 30 * level;
    }

    public boolean maxed() {
        return level >= MAX_LEVEL;
    }

    /** 1.0 at level 1, a little more each level (1.45 at level 10): how much stronger its bonus and stats are. */
    public double power() {
        return 1.0 + 0.05 * (level - 1);
    }

    public int storageSlots() {
        return species.storage().slots(level);
    }

    public boolean resting(long now) {
        return restingUntil > now;
    }
}
