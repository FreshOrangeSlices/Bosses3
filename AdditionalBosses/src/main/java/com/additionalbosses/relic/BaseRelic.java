package com.additionalbosses.relic;

/**
 * Convenience base class holding a relic's identity.
 */
public abstract class BaseRelic implements RelicEffect {

    private final String id;
    private final String displayName;
    private final boolean curse;

    protected BaseRelic(String id, String displayName, boolean curse) {
        this.id = id;
        this.displayName = displayName;
        this.curse = curse;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean curse() {
        return curse;
    }
}
