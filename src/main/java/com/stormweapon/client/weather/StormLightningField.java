package com.stormweapon.client.weather;

import com.stormweapon.client.StormClientManager;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic intra-cloud flash field in the observer's own neighbourhood.
 *
 * <p>The previous implementation drew one candidate per 0.9 s <em>for the whole storm</em> and then
 * placed it uniformly inside the 768 block core radius. The chance that a given flash landed inside
 * the player's view was therefore tiny, and the storm read as having no lightning at all.</p>
 *
 * <p>This field instead evaluates candidates on a world-aligned cell grid restricted to a bounded
 * neighbourhood around the camera, one Bernoulli trial per cell per time slice. The per-cell
 * probability is solved from the preset's target flash rate and the number of cells actually
 * examined, so the observed rate near <em>any</em> observer inside the storm is the preset rate,
 * independent of storm radius and of where in the storm that observer stands. Placement stays a
 * pure function of the storm seed, the world cell and the time slice, so two clients standing
 * together see the same flashes without a single extra packet.</p>
 *
 * <p>Radial storm influence still gates every cell, so the flash density falls off through the
 * transition zone and stops outside it.</p>
 */
public final class StormLightningField {
    /** Edge length in blocks of one flash candidate cell. */
    public static final int CELL = 96;

    /** Ticks per candidate slice. Each cell rolls once per slice. */
    private static final long SLICE_TICKS = 8L;

    /** Visible lifetime of one flash in ticks; drives how many past slices stay live. */
    private static final float LIFETIME_TICKS = 14.0F;

    private static final long FLASH_SALT = 0x4C494748544E494EL;

    /**
     * One live intra-cloud flash.
     *
     * @param x         world X of the flash centre
     * @param y         world Y of the flash centre, just under the storm cloud base
     * @param z         world Z of the flash centre
     * @param key       deterministic identity used to derive the channel shape
     * @param pulse     current brightness envelope in {@code [0, 1]}
     * @param size      horizontal radius in blocks of the diffuse glow
     * @param influence radial storm influence at the flash position
     */
    public record Flash(double x, double y, double z, long key, float pulse, float size, float influence) {}

    private static final List<Flash> LIVE = new ArrayList<>();
    private static final List<Flash> VIEW = java.util.Collections.unmodifiableList(LIVE);
    private static int lastCandidateCells;

    private StormLightningField() {}

    /** Live flashes for the current frame, ordered nearest to farthest from the camera. */
    public static List<Flash> flashes() {
        return VIEW;
    }

    /** Candidate cells examined on the last update; exposed for the debug HUD. */
    public static int lastCandidateCells() {
        return lastCandidateCells;
    }

    public static void clear() {
        LIVE.clear();
        lastCandidateCells = 0;
    }

    /**
     * Rebuilds the live flash list for this frame. Must be called once per frame before any
     * renderer reads {@link #flashes()}.
     */
    public static void update(
        StormSnapshot snapshot,
        double cameraX,
        double cameraZ,
        long gameTime,
        float partialTick,
        StormLightningBudget budget,
        float cloudBaseY
    ) {
        LIVE.clear();
        lastCandidateCells = 0;
        if (!snapshot.active()) {
            return;
        }
        float intensity = StormClientManager.smoothedLightningIntensity();
        if (intensity <= 0.02F) {
            return;
        }

        int radius = budget.flashRadius();
        // Expected number of cells inside the sampled disc. The per-cell probability is solved from
        // this so the observed rate matches the preset target regardless of cell size or radius.
        double expectedCells = Math.PI * radius * (double)radius / (CELL * (double)CELL);
        double sliceSeconds = SLICE_TICKS / 20.0D;
        double probability = budget.flashesPerMinute() / 60.0D * sliceSeconds / Math.max(1.0D, expectedCells);
        probability = Math.min(probability, 0.5D);

        long currentSlice = Math.floorDiv(gameTime, SLICE_TICKS);
        int sliceDepth = (int)Math.ceil(LIFETIME_TICKS / SLICE_TICKS);
        int minCell = Mth.floor((cameraX - radius) / CELL);
        int maxCell = Mth.floor((cameraX + radius) / CELL);
        int minCellZ = Mth.floor((cameraZ - radius) / CELL);
        int maxCellZ = Mth.floor((cameraZ + radius) / CELL);
        double radiusSq = (double)radius * radius;
        int cap = budget.maxCloudFlashes();

        for (int cx = minCell; cx <= maxCell; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                double cellCenterX = (cx + 0.5D) * CELL;
                double cellCenterZ = (cz + 0.5D) * CELL;
                double dx = cellCenterX - cameraX;
                double dz = cellCenterZ - cameraZ;
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                lastCandidateCells++;
                float cellInfluence = snapshot.radialInfluence(cellCenterX, cellCenterZ);
                if (cellInfluence <= 0.05F) {
                    continue;
                }
                float cellChance = (float)probability * intensity * cellInfluence;
                if (cellChance <= 0.0F) {
                    continue;
                }

                long cellKey = ((long)cx * 0x9E3779B97F4A7C15L) ^ ((long)cz * 0xC2B2AE3D27D4EB4FL);
                for (int back = 0; back <= sliceDepth && LIVE.size() < cap; back++) {
                    long slice = currentSlice - back;
                    float roll = StormClientManager.deterministic(snapshot.seed(), slice, FLASH_SALT ^ cellKey);
                    if (roll >= cellChance) {
                        continue;
                    }
                    float age = (gameTime + partialTick) - (slice * SLICE_TICKS);
                    float pulse = pulse(age);
                    if (pulse <= 0.01F) {
                        continue;
                    }

                    float px = StormClientManager.deterministic(snapshot.seed(), slice, FLASH_SALT ^ cellKey ^ 0x11L);
                    float pz = StormClientManager.deterministic(snapshot.seed(), slice, FLASH_SALT ^ cellKey ^ 0x22L);
                    float py = StormClientManager.deterministic(snapshot.seed(), slice, FLASH_SALT ^ cellKey ^ 0x33L);
                    float ps = StormClientManager.deterministic(snapshot.seed(), slice, FLASH_SALT ^ cellKey ^ 0x44L);
                    double x = cx * (double)CELL + px * CELL;
                    double z = cz * (double)CELL + pz * CELL;
                    double y = cloudBaseY - 2.0D + py * 16.0D;
                    float size = 22.0F + 34.0F * ps + 16.0F * intensity;
                    LIVE.add(new Flash(x, y, z, slice * 31L + cellKey, pulse, size, cellInfluence));
                }
                if (LIVE.size() >= cap) {
                    break;
                }
            }
            if (LIVE.size() >= cap) {
                break;
            }
        }

        LIVE.sort((a, b) -> Double.compare(
            distanceSq(a, cameraX, cameraZ), distanceSq(b, cameraX, cameraZ)
        ));
    }

    /**
     * Additive illumination in {@code [0, 1]} contributed by the live flashes at a world position.
     *
     * <p>The cloud deck adds this to its vertex brightness, which is what makes a flash read as
     * light inside the cloud mass instead of a sprite floating in front of it. Only the nearest few
     * flashes are considered so the per-vertex cost stays a fixed handful of operations.</p>
     */
    public static float illumination(double x, double z) {
        int count = Math.min(LIVE.size(), 4);
        float total = 0.0F;
        for (int i = 0; i < count; i++) {
            Flash flash = LIVE.get(i);
            double dx = x - flash.x();
            double dz = z - flash.z();
            double falloffSq = (double)flash.size() * flash.size() * 9.0D;
            double d2 = dx * dx + dz * dz;
            if (d2 >= falloffSq) {
                continue;
            }
            float attenuation = 1.0F - (float)(d2 / falloffSq);
            total += flash.pulse() * attenuation * attenuation;
        }
        return Math.min(total, 1.4F);
    }

    private static double distanceSq(Flash flash, double x, double z) {
        double dx = flash.x() - x;
        double dz = flash.z() - z;
        return dx * dx + dz * dz;
    }

    /** Two lobed flicker: a hard leading stroke and a weaker return stroke. */
    private static float pulse(float age) {
        if (age < 0.0F) {
            return 0.0F;
        }
        if (age < 3.0F) {
            return 1.0F - age / 3.4F;
        }
        if (age >= 5.0F && age < 9.0F) {
            return 0.62F * (1.0F - (age - 5.0F) / 4.0F);
        }
        if (age >= 10.0F && age < LIFETIME_TICKS) {
            return 0.30F * (1.0F - (age - 10.0F) / (LIFETIME_TICKS - 10.0F));
        }
        return 0.0F;
    }
}
