package com.plantdesk.asset;

/**
 * Plant → Area → Line → Machine → Component: the functional-location tree a plant's
 * equipment register is already organised by.
 */
public enum AssetLevel {
    PLANT("PLT", 2),
    AREA("AREA", 2),
    LINE("LN", 2),
    MACHINE(null, 3),
    COMPONENT(null, 3);

    /** Fixed prefix for structural levels; machines and components carry an equipment-type prefix (MTR, PMP…). */
    private final String fixedPrefix;
    private final int digits;

    AssetLevel(String fixedPrefix, int digits) {
        this.fixedPrefix = fixedPrefix;
        this.digits = digits;
    }

    public String fixedPrefix() {
        return fixedPrefix;
    }

    public int digits() {
        return digits;
    }

    public boolean needsTypePrefix() {
        return fixedPrefix == null;
    }

    /**
     * A child must sit deeper than its parent, but may skip levels: a standalone compressor
     * house has machines directly under the area, no "line". Components may nest under
     * components because real equipment does (motor → bearing housing → bearing).
     */
    public boolean canParent(AssetLevel child) {
        if (this == COMPONENT) {
            return child == COMPONENT;
        }
        return child.ordinal() > this.ordinal();
    }
}
