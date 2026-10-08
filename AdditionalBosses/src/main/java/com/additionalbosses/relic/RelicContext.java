package com.additionalbosses.relic;

import org.bukkit.entity.Player;

/**
 * Where an active relic is: on the item in the player's main hand, or on worn armor / an off-hand shield.
 */
public record RelicContext(Player player, Slot slot, RelicManager manager) {

    public enum Slot { HAND, ARMOR }

    public boolean inHand() {
        return slot == Slot.HAND;
    }
}
