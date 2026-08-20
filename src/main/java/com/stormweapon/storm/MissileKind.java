package com.stormweapon.storm;

/**
 * Which payload a launcher is loaded with, and what its high-altitude burst turns into.
 *
 * <p>This replaced an earlier plain {@code boolean fog} once a third payload existed. Ordinals are
 * persisted in block-entity and entity NBT, so entries may be appended but never reordered.</p>
 */
public enum MissileKind {
    /** Full thunderstorm deployment: cloud, wind, rain and lightning. */
    THUNDER,
    /** Non-destructive haze deployment: visibility cut, slowdown and periodic damage. */
    FOG,
    /**
     * No weather deployment at all. The sky darkens hard and fast and the world is bombarded with
     * falling meteors for a much shorter window than the other two payloads run for.
     */
    METEOR,
    /** Five-minute forced snowfall, accumulation and exposure-based cold attrition. */
    BLIZZARD,
    /** Five-minute restorative cherry-blossom shower with accumulating pink petals. */
    CHERRY;

    private static final MissileKind[] VALUES = values();

    public static MissileKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : THUNDER;
    }

    public boolean isFog() {
        return this == FOG;
    }

    /** Payloads backed by the shared atmospheric phase/snapshot machinery. */
    public boolean hasAtmosphericDeployment() {
        return this != METEOR;
    }
}
