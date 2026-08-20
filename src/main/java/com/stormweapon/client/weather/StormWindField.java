package com.stormweapon.client.weather;

import com.stormweapon.storm.StormSnapshot;
import net.minecraft.util.Mth;

/**
 * Deterministic client wind field.
 *
 * <p>Every value is a pure function of the synchronized storm seed, the storm center, the
 * client game time and the sample position, so two clients with the same snapshot always
 * derive the same wind without any extra packets. Nothing here touches Minecraft state, and
 * it is the single source of movement for clouds, fog drift and later rain/debris.</p>
 */
public final class StormWindField {
    /**
     * Blocks per second at full envelope strength before gusts are added.
     *
     * <p>Raised from the original 17 because wind was reported as imperceptible: with a mean rain
     * fall speed of 37 blocks per second this puts the base streak tilt at about 27 degrees and a
     * full gust at about 43, i.e. the whole intended 20-50 degree band is actually used instead of
     * the bottom third of it.</p>
     */
    public static final float MAX_BASE_SPEED = 28.0F;
    /** Extra blocks per second contributed by a full gust. */
    public static final float MAX_GUST_SPEED = 26.0F;

    /** Mean rain fall speed used to express wind as a streak tilt; matches StormRainRenderer. */
    public static final float MEAN_FALL_SPEED = 37.0F;

    private StormWindField() {}

    /** Immutable wind sample; all directions are normalized world-space XZ vectors. */
    public record Sample(
        float directionX,
        float directionZ,
        float speed,
        float base,
        float gust,
        float turbulence,
        float swirlX,
        float swirlZ
    ) {
        public float angleDegrees() {
            return (float)Math.toDegrees(Math.atan2(directionZ, directionX));
        }

        /**
         * Streak tilt in degrees away from vertical.
         *
         * <p>This is the true geometric angle of the fall vector against the mean rain fall speed,
         * so the debug overlay reports the angle the renderer actually builds rather than a
         * separately tuned approximation that could drift away from it.</p>
         */
        public float tiltDegrees() {
            return (float)Math.toDegrees(Math.atan2(speed, MEAN_FALL_SPEED));
        }
    }

    public static Sample sample(StormSnapshot snapshot, double x, double z, long gameTime, float partialTick) {
        return sample(snapshot, x, z, gameTime, partialTick, snapshot.windIntensity(gameTime, partialTick));
    }

    /** Samples the same deterministic field using a caller-provided, smoothly released envelope. */
    public static Sample sample(
        StormSnapshot snapshot, double x, double z, long gameTime, float partialTick, float envelope
    ) {
        float influence = snapshot.radialInfluence(x, z);
        float strength = envelope * influence;
        double seconds = (gameTime + partialTick) / 20.0D;

        float baseAngle = baseAngleRadians(snapshot.seed());
        // Slow large-scale meander of the prevailing direction (period ~2 and ~7 minutes).
        float meander = 0.55F * sin(seconds * 0.0085D + hashUnit(snapshot.seed(), 0x51ED2701L) * 6.2831853D)
            + 0.22F * sin(seconds * 0.031D + hashUnit(snapshot.seed(), 0x27F4A3C9L) * 6.2831853D);
        float angle = baseAngle + meander;

        float gust = gust(snapshot.seed(), seconds);
        float turbulence = turbulence(snapshot.seed(), x, z, seconds);
        // Turbulence bends the local direction without changing the regional flow.
        angle += (turbulence - 0.5F) * 0.9F * strength;

        float dirX = Mth.cos(angle);
        float dirZ = Mth.sin(angle);

        // Cyclonic component: tangential flow around the storm center, strongest in the core.
        double dx = x - snapshot.centerX();
        double dz = z - snapshot.centerZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        float swirlX = 0.0F;
        float swirlZ = 0.0F;
        if (distance > 1.0E-3D && snapshot.coreRadius() > 0) {
            float radial = (float)Mth.clamp(distance / Math.max(1, snapshot.coreRadius()), 0.0D, 1.0D);
            float swirlAmount = 0.45F * (1.0F - radial * radial) * strength;
            swirlX = (float)(-dz / distance) * swirlAmount;
            swirlZ = (float)(dx / distance) * swirlAmount;
        }

        float mixedX = dirX + swirlX;
        float mixedZ = dirZ + swirlZ;
        float length = Mth.sqrt(mixedX * mixedX + mixedZ * mixedZ);
        if (length > 1.0E-4F) {
            mixedX /= length;
            mixedZ /= length;
        } else {
            mixedX = 1.0F;
            mixedZ = 0.0F;
        }

        float speed = strength * MAX_BASE_SPEED + strength * gust * MAX_GUST_SPEED;
        return new Sample(mixedX, mixedZ, speed, strength, gust, turbulence, swirlX, swirlZ);
    }

    /** Prevailing direction of the storm, fixed for the whole storm lifetime. */
    public static float baseAngleRadians(long seed) {
        return hashUnit(seed, 0x9E3779B97F4A7C15L) * 6.2831853F;
    }

    /**
     * Gust envelope in [0, 1]: three incommensurate sine bands so gusts never repeat on a
     * short cycle, sharpened so calm stretches are longer than the peaks.
     */
    public static float gust(long seed, double seconds) {
        float a = sin(seconds * 0.37D + hashUnit(seed, 0x2545F4914F6CDD1DL) * 6.2831853D);
        float b = sin(seconds * 0.83D + hashUnit(seed, 0xBF58476D1CE4E5B9L) * 6.2831853D);
        float c = sin(seconds * 1.61D + hashUnit(seed, 0x94D049BB133111EBL) * 6.2831853D);
        float raw = (a * 0.5F + b * 0.32F + c * 0.18F) * 0.5F + 0.5F;
        return Mth.clamp(raw * raw * (3.0F - 2.0F * raw), 0.0F, 1.0F);
    }

    /** Local turbulence in [0, 1] from a coarse moving spatial pattern. */
    public static float turbulence(long seed, double x, double z, double seconds) {
        double u = x * 0.006D + seconds * 0.11D;
        double v = z * 0.006D - seconds * 0.07D;
        float n = sin(u + hashUnit(seed, 0xD1B54A32D192ED03L) * 6.2831853D)
            * sin(v * 1.31D + hashUnit(seed, 0xAEF17502108EF2D9L) * 6.2831853D);
        float m = sin(u * 2.7D - v * 1.9D + hashUnit(seed, 0xDB4F0B9175AE2165L) * 6.2831853D);
        return Mth.clamp((n * 0.65F + m * 0.35F) * 0.5F + 0.5F, 0.0F, 1.0F);
    }

    /**
     * Accumulated wind displacement in blocks, used for cloud transport and UV scrolling.
     *
     * <p>The direction is the storm's fixed prevailing angle rather than a live local sample. A
     * live sample would be multiplied by an accumulated distance of thousands of blocks, so even
     * the slow turbulent wander of the local angle would swing the whole deck sideways faster than
     * it travels forward. Layer rotation supplies the rotating look instead; local turbulence still
     * drives rain and debris, where it is applied to positions rather than to an integral.</p>
     */
    public static float driftX(StormSnapshot snapshot, long gameTime, float partialTick, float layerFactor) {
        return Mth.cos(baseAngleRadians(snapshot.seed())) * driftDistance(snapshot, gameTime, partialTick, layerFactor);
    }

    public static float driftZ(StormSnapshot snapshot, long gameTime, float partialTick, float layerFactor) {
        return Mth.sin(baseAngleRadians(snapshot.seed())) * driftDistance(snapshot, gameTime, partialTick, layerFactor);
    }

    private static float driftDistance(StormSnapshot snapshot, long gameTime, float partialTick, float layerFactor) {
        double seconds = (gameTime + partialTick - snapshot.startGameTime()) / 20.0D;
        return (float)flowDistance(
            snapshot.seed(), seconds, MAX_BASE_SPEED * 0.55F * layerFactor, MAX_GUST_SPEED * 0.40F * layerFactor
        );
    }

    /**
     * Exact integral of a gusting flow speed, in blocks travelled since the storm started.
     *
     * <p>This replaces the old {@code elapsedSeconds * currentSpeed} form. That form is only
     * correct while the speed is constant: whenever the phase envelope changed, the new speed was
     * retroactively applied to the entire elapsed time, so the cloud field jumped by hundreds of
     * blocks in a single frame at every phase transition. Here the base term is integrated exactly
     * and each gust band is a sine whose integral is a cosine, so the result is continuous in time,
     * monotone while the base term dominates, and identical on every client.</p>
     *
     * @param baseSpeed  steady flow speed in blocks per second
     * @param swingSpeed peak amplitude of the gusting component, in blocks per second
     */
    public static double flowDistance(long seed, double seconds, float baseSpeed, float swingSpeed) {
        double total = baseSpeed * seconds;
        total += swingIntegral(seconds, 0.37D, hashUnit(seed, 0x2545F4914F6CDD1DL), swingSpeed * 0.50D);
        total += swingIntegral(seconds, 0.83D, hashUnit(seed, 0xBF58476D1CE4E5B9L), swingSpeed * 0.32D);
        total += swingIntegral(seconds, 1.61D, hashUnit(seed, 0x94D049BB133111EBL), swingSpeed * 0.18D);
        return total;
    }

    /** Integral of {@code amplitude * sin(omega * t + phase)} from zero to {@code seconds}. */
    private static double swingIntegral(double seconds, double omega, float phaseUnit, double amplitude) {
        double phase = phaseUnit * 6.283185307179586D;
        return amplitude / omega * (Math.cos(phase) - Math.cos(omega * seconds + phase));
    }

    private static float sin(double value) {
        return (float)Math.sin(value);
    }

    /** SplitMix64-derived unit value; identical on every client for a given seed and salt. */
    public static float hashUnit(long seed, long salt) {
        long z = seed + salt;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (float)((z >>> 11) / (double)(1L << 53));
    }
}
