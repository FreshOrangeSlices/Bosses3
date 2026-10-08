package com.additionalbosses.trait;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum TraitCategory {
    OFFENSE("Offense", NamedTextColor.DARK_RED),
    DEFENSE("Defense", NamedTextColor.DARK_BLUE),
    MOVEMENT("Movement", NamedTextColor.DARK_GREEN),
    CONTROL("Control", NamedTextColor.DARK_AQUA),
    SPECIAL("Special", NamedTextColor.DARK_PURPLE);

    private final String displayName;
    private final TextColor bookColor;

    TraitCategory(String displayName, TextColor bookColor) {
        this.displayName = displayName;
        this.bookColor = bookColor;
    }

    public String displayName() {
        return displayName;
    }

    public TextColor bookColor() {
        return bookColor;
    }
}
