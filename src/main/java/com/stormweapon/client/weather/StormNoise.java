package com.stormweapon.client.weather;

import net.minecraft.util.Mth;

/**
 * Continuous scalar fields shared by the storm visuals.
 *
 * <p>The cloud deck used to derive its per-tile height, opacity and tint from a hash of the tile
 * index. Two neighbouring tiles therefore received unrelated values, and because a tile is a single
 * flat quad the difference showed up as a hard rectangular step: the giant sheet boundaries and
 * polygonal bands the deck was reported for.</p>
 *
 * <p>Everything here is instead an infinitely differentiable function of a world position, so two
 * quads that share an edge sample the identical value at the shared vertices and the mesh is
 * continuous by construction. There is no lattice, so there is no grid to become visible either.
 * The functions are pure and seeded, so every client in a storm derives the same field.</p>
 */
public final class StormNoise {
    private StormNoise() {}

    /**
     * Smooth field in {@code [0, 1]} sampled at a world position.
     *
     * <p>Uses {@link Mth#sin}, i.e. Minecraft's own 64k entry sine table, not {@link Math#sin}.
     * The cloud deck evaluates this at every emitted vertex, several thousand times per frame, so
     * the table lookup is the difference between a fraction of a millisecond and several
     * milliseconds of frame time. The table's roughly 1e-4 output resolution is four orders of
     * magnitude below anything visible at cloud scale.</p>
     *
     * @param x         sample X, in the storm-local frame so the magnitudes stay small
     * @param z         sample Z, in the storm-local frame
     * @param frequency radians per block
     * @param phase     seeded phase offset that decorrelates different uses of the field
     */
    public static float field(double x, double z, double frequency, double phase) {
        double u = x * frequency;
        double v = z * frequency;
        float a = Mth.sin(u + phase);
        float b = Mth.sin(v * 1.131D + phase * 1.7D + 2.11D);
        float c = Mth.sin((u + v) * 0.613D + phase * 0.53D + 4.31D);
        float d = Mth.sin((u - v) * 0.371D - phase * 1.29D + 1.07D);
        float sum = a * 0.33F + b * 0.28F + c * 0.23F + d * 0.16F;
        return sum * 0.5F + 0.5F;
    }

    /** Two octave variant: broad swells with a finer churn riding on top. */
    public static float field2(double x, double z, double frequency, double phase) {
        float coarse = field(x, z, frequency, phase);
        float fine = field(x, z, frequency * 2.73D, phase * 1.61D + 3.7D);
        return coarse * 0.68F + fine * 0.32F;
    }

    /** Seeded phase in {@code [0, 2 pi)} for a given salt. */
    public static double phase(long seed, long salt) {
        return StormWindField.hashUnit(seed, salt) * 6.283185307179586D;
    }

    /** Smoothstep of {@code (value - edge0) / (edge1 - edge0)}, clamped to {@code [0, 1]}. */
    public static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 - edge0 <= 1.0E-6F) {
            return value < edge1 ? 0.0F : 1.0F;
        }
        float t = (value - edge0) / (edge1 - edge0);
        t = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        return t * t * (3.0F - 2.0F * t);
    }
}
