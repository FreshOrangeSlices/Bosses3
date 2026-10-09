package com.bloompets;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * How a group of pets moves when ridden and follows on foot.
 *
 * <ul>
 *     <li>Combat pets fight, and are the slowest: their power has a price.</li>
 *     <li>Pack pets carry the most and step up 1.5 blocks, so rough ground isn't a chore.</li>
 *     <li>Speedsters are the fastest, with a good jump, built for travel.</li>
 *     <li>Utility pets are about their bonus.</li>
 * </ul>
 *
 * <p>Speeds are blocks per tick while ridden (a walking player is about 0.22, sprinting 0.28).</p>
 */
public enum Category {

    COMBAT("Combat", NamedTextColor.RED, 0.22, 0.45, 1.0, 1.0),
    PACK("Pack", NamedTextColor.GOLD, 0.30, 0.55, 1.5, 1.2),
    SPEEDSTER("Speedster", NamedTextColor.AQUA, 0.42, 0.75, 1.0, 1.4),
    UTILITY("Utility", NamedTextColor.GREEN, 0.27, 0.52, 1.0, 1.2);

    private final String displayName;
    private final TextColor color;
    private final double rideSpeed;
    private final double jumpVelocity;
    private final double stepHeight;
    private final double followSpeed;

    Category(String displayName, TextColor color, double rideSpeed, double jumpVelocity, double stepHeight,
             double followSpeed) {
        this.displayName = displayName;
        this.color = color;
        this.rideSpeed = rideSpeed;
        this.jumpVelocity = jumpVelocity;
        this.stepHeight = stepHeight;
        this.followSpeed = followSpeed;
    }

    public String displayName() {
        return displayName;
    }

    public TextColor color() {
        return color;
    }

    /** Blocks per tick while ridden, before level bonuses. */
    public double rideSpeed() {
        return rideSpeed;
    }

    /** Upward velocity of a ridden jump (0.42 clears one block, 0.75 about two and a half). */
    public double jumpVelocity() {
        return jumpVelocity;
    }

    public double stepHeight() {
        return stepHeight;
    }

    /** Pathfinding speed multiplier when following on foot. */
    public double followSpeed() {
        return followSpeed;
    }
}
