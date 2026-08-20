package com.stormweapon.storm;

import net.minecraft.util.Mth;

public record StormSnapshot(
    boolean active,
    StormPhase phase,
    double centerX,
    double centerZ,
    int coreRadius,
    int transitionRadius,
    long seed,
    long startGameTime,
    long phaseStartGameTime,
    int phaseDurationTicks,
    double detonationY,
    float waveRadius,
    float waveMaxRadius,
    float waveProgress,
    boolean debug,
    boolean fog
) {
    public static final StormSnapshot CLEAR = new StormSnapshot(
        false, StormPhase.CLEAR, 0.0D, 0.0D, 0, 0, 0L, 0L, 0L, 0, 0.0D, 0.0F, 0.0F, 1.0F, false, false
    );

    public float phaseProgress(long gameTime, float partialTick) {
        if (!active || phaseDurationTicks <= 0) {
            return phase == StormPhase.CLEAR ? 0.0F : 1.0F;
        }
        return Mth.clamp((float)((gameTime + partialTick - phaseStartGameTime) / phaseDurationTicks), 0.0F, 1.0F);
    }

    public float radialInfluence(double x, double z) {
        // A deployed weather weapon affects the complete loaded dimension. The detonation center
        // remains meaningful for missile flight and the four-second atmospheric shockwave only;
        // it no longer limits wind, rain, lightning, entity pressure or interaction penalties.
        return active ? 1.0F : 0.0F;
    }

    public float cloudIntensity(long gameTime, float partialTick) {
        float p = phaseProgress(gameTime, partialTick);
        return switch (phase) {
            case CLEAR -> 0.0F;
            case ATMOSPHERIC_WAVE -> p;
            case SEEDING -> 0.15F * p;
            case CLOUD_BUILDUP -> 0.15F + 0.65F * p;
            case WIND_RISING -> 0.80F + 0.15F * p;
            case HEAVY_RAIN, SUPERCELL, PEAK_STORM -> 1.0F;
            case DECAY -> 1.0F - 0.30F * p;
            case CLEARING -> 0.70F * (1.0F - p);
        };
    }

    public float windIntensity(long gameTime, float partialTick) {
        float p = phaseProgress(gameTime, partialTick);
        float intensity = switch (phase) {
            case CLEAR, SEEDING -> 0.0F;
            case ATMOSPHERIC_WAVE -> p;
            case CLOUD_BUILDUP -> 0.15F * p;
            case WIND_RISING -> 0.15F + 0.60F * p;
            case HEAVY_RAIN -> 0.75F + 0.15F * p;
            case SUPERCELL, PEAK_STORM -> 1.0F;
            case DECAY -> 1.0F - 0.45F * p;
            case CLEARING -> 0.55F * (1.0F - p);
        };
        // Fog weather keeps the air calm so the ground fog settles and thickens instead of
        // being blown apart like a supercell's gust front.
        return fog ? intensity * 0.30F : intensity;
    }

    /** Fog weapon deployments are a non-destructive cloud/fog event: no rain ever falls. */
    public float rainIntensity(long gameTime, float partialTick) {
        if (fog) {
            return 0.0F;
        }
        float p = phaseProgress(gameTime, partialTick);
        return switch (phase) {
            case CLEAR, SEEDING, CLOUD_BUILDUP, WIND_RISING -> 0.0F;
            case ATMOSPHERIC_WAVE -> p;
            case HEAVY_RAIN -> 0.75F * p;
            case SUPERCELL -> 0.75F + 0.20F * p;
            case PEAK_STORM -> 1.0F;
            case DECAY -> 1.0F - 0.50F * p;
            case CLEARING -> 0.50F * (1.0F - p);
        };
    }

    /** Fog weapon deployments never carry an electrical charge: no lightning of either kind. */
    public float lightningIntensity(long gameTime, float partialTick) {
        if (fog) {
            return 0.0F;
        }
        float p = phaseProgress(gameTime, partialTick);
        return switch (phase) {
            case CLEAR, SEEDING, CLOUD_BUILDUP, WIND_RISING -> 0.0F;
            case ATMOSPHERIC_WAVE -> p;
            case HEAVY_RAIN -> 0.15F * p;
            case SUPERCELL -> 0.35F + 0.55F * p;
            case PEAK_STORM -> 1.0F;
            case DECAY -> 0.80F * (1.0F - p);
            case CLEARING -> 0.10F * (1.0F - p);
        };
    }

    /** Seconds the ground fog takes to build from the moment of detonation to full density. */
    private static final float FOG_FORMATION_SECONDS = 8.0F;

    /**
     * Ground-fog visibility envelope for fog weapon deployments. Unlike {@link #cloudIntensity},
     * this ramps on a fixed 8-second clock measured from the detonation itself, independent of the
     * configurable atmospheric wave ramp, then follows the same hold/decay/clearing shape as the
     * cloud deck so it thins out together with the rest of the weather system. Always 0 for a
     * storm deployment.
     */
    public float fogIntensity(long gameTime, float partialTick) {
        if (!fog) {
            return 0.0F;
        }
        if (phase == StormPhase.ATMOSPHERIC_WAVE) {
            float elapsed = (float)(gameTime + partialTick - startGameTime);
            return Mth.clamp(elapsed / (FOG_FORMATION_SECONDS * 20.0F), 0.0F, 1.0F);
        }
        return cloudIntensity(gameTime, partialTick);
    }
}
