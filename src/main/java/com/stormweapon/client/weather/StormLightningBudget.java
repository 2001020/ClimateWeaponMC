package com.stormweapon.client.weather;

import com.stormweapon.config.StormQuality;

/**
 * Hard per-preset caps for storm lightning visuals.
 *
 * <p>{@link StormLightningField} rolls one candidate per world cell per time slice inside
 * {@link #flashRadius()} of the camera, and {@link #flashesPerMinute()} is the rate the field
 * solves its per-cell probability from. Because the rate is defined <em>per observer</em> rather
 * than per storm, it does not thin out as the storm radius grows. Bolt complexity is capped
 * independently so a burst of flashes can never multiply into unbounded geometry.</p>
 *
 * @param maxCloudFlashes   simultaneous intra-cloud flashes kept live near the camera
 * @param flashRadius       radius in blocks of the sampled flash neighbourhood
 * @param flashesPerMinute  target visible intra-cloud flash rate at full lightning envelope
 * @param detailedFlashes   nearest flashes that also receive a procedural branching channel
 * @param maxActiveBolts    simultaneous procedural physical-strike bolts
 * @param maxBoltSegments   polyline segments in one main channel
 * @param maxBranches       branches hanging off one main channel
 * @param maxBranchSegments polyline segments in one branch
 * @param glowRings         concentric quads used for one diffuse cloud flash
 * @param maxVertices       absolute vertex ceiling for one frame of lightning geometry
 */
public record StormLightningBudget(
    int maxCloudFlashes,
    int flashRadius,
    float flashesPerMinute,
    int detailedFlashes,
    int maxActiveBolts,
    int maxBoltSegments,
    int maxBranches,
    int maxBranchSegments,
    int glowRings,
    int maxVertices
) {
    public static final StormLightningBudget LOW =
        new StormLightningBudget(3, 220, 12.0F, 1, 1, 8, 1, 3, 1, 1_600);
    public static final StormLightningBudget MEDIUM =
        new StormLightningBudget(6, 300, 22.0F, 2, 2, 12, 2, 4, 2, 4_000);
    public static final StormLightningBudget HIGH =
        new StormLightningBudget(10, 384, 34.0F, 4, 3, 16, 3, 5, 3, 9_000);
    public static final StormLightningBudget ULTRA =
        new StormLightningBudget(14, 448, 48.0F, 6, 4, 20, 3, 6, 3, 14_000);

    public static StormLightningBudget of(StormQuality quality) {
        return switch (quality) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case ULTRA -> ULTRA;
        };
    }
}
