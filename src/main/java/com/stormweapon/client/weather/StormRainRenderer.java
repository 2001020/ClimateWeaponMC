package com.stormweapon.client.weather;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.StormClientManager;
import com.stormweapon.config.StormConfig;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * World-anchored regional rain field.
 *
 * <p>Rain used to be a set of hashed slots wrapped inside a cylinder centred on the camera. Every
 * slot's position was expressed relative to the camera, so the whole field translated with the
 * player: walking dragged the identical rain column along instead of revealing new rain, which is
 * exactly what was reported.</p>
 *
 * <p>A streak is now owned by a world cell. Its cell, its offset inside that cell, its fall speed
 * and its phase are all pure functions of the storm seed and the integer world cell coordinates, so
 * the rain field is a property of the world, not of the viewer. Only the cells inside a bounded
 * disc around the camera are submitted, which is what keeps the cost fixed, but that disc slides
 * over a stationary field: walking uncovers the next column of world cells. Two players standing in
 * different parts of the storm each see rain around themselves, and the radial storm influence
 * thins it out only at the regional boundary.</p>
 *
 * <p>Vertically the field is a lattice of period {@code columnHeight} anchored to world Y zero. The
 * renderer draws, for each streak, the lattice instance nearest the camera and fades it out at both
 * ends of the visible column, so moving up or down slides through the lattice without any pop.</p>
 *
 * <p>Streaks are billboards oriented along their own velocity vector, so the tilt away from
 * vertical is the true wind-to-fall-speed ratio: roughly 30 degrees at full base wind and up to
 * about 46 degrees at a full gust, inside the intended 20-50 degree band.</p>
 *
 * <p>The renderer stays on the Blaze3D abstraction used by {@link StormCloudRenderer}: a staged
 * vertex buffer replayed through {@link StormRenderTypes}. No OpenGL symbol is referenced.</p>
 */
public final class StormRainRenderer {
    private static final Identifier STREAK_TEXTURE =
        Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "textures/environment/storm_rain_streak.png");

    private static final long CELL_SALT = 0x51ED2701A3C97F4DL;

    /** Vertical fall speed range in blocks per second. Fast rain reads as water, not floating wire. */
    private static final float MIN_FALL_SPEED = 34.0F;
    private static final float FALL_SPEED_RANGE = 22.0F;

    /** Per-drop exposure range. Variation prevents a screen full of identical laser-like lines. */
    private static final float MIN_STREAK_SECONDS = 0.038F;
    private static final float STREAK_SECONDS_RANGE = 0.047F;

    /** Bounded near-player probe radius for splash checks, in blocks. */
    private static final int SPLASH_RADIUS = 9;
    private static final int SPLASH_VERTICAL_RANGE = 14;

    /** Blocks of terrain slack below a cell's surface before a streak is discarded. */
    private static final float GROUND_SLACK = 1.5F;

    /**
     * Largest wind shear a streak may accumulate, in seconds of travel.
     *
     * <p>This bounds how far a streak can be displaced from the cell that owns it, and therefore
     * how far the submitted cell disc has to be widened to still contain every streak that blows
     * into view.</p>
     */
    private static final float MAX_SHEAR_SECONDS = 0.55F;

    /** Fraction of a lattice period the visible column is biased above the camera. */
    private static final float COLUMN_BIAS = 0.15F;

    private final StormGeometryBatch batch = new StormGeometryBatch(1 << 20);

    private int lastStreakCount;
    private int lastStreakBudget;
    private int lastCellCount;
    private int lastSplashCount;
    private float lastTiltDegrees;

    public int lastStreakCount() {
        return this.lastStreakCount;
    }

    public int lastStreakBudget() {
        return this.lastStreakBudget;
    }

    public int lastCellCount() {
        return this.lastCellCount;
    }

    public int lastSplashCount() {
        return this.lastSplashCount;
    }

    public float lastTiltDegrees() {
        return this.lastTiltDegrees;
    }

    public void render(LevelRenderState state, float partialTick) {
        this.lastStreakCount = 0;
        this.lastCellCount = 0;
        StormSnapshot snapshot = StormClientManager.thunderSnapshot();
        if (!snapshot.active()) {
            return;
        }

        Vec3 camera = state.cameraRenderState.pos;
        long gameTime = state.gameTime;
        float influence = snapshot.radialInfluence(camera.x, camera.z);
        if (influence <= 0.0F) {
            return;
        }
        float envelope = StormClientManager.smoothedRainIntensity();
        if (envelope * influence <= 0.02F) {
            return;
        }

        StormRainBudget budget = StormRainBudget.of(StormConfig.quality());
        double density = StormConfig.rainDensity();
        this.lastStreakBudget = budget.streakCeiling();

        StormWindField.Sample wind = StormWindField.sample(
            snapshot, camera.x, camera.z, gameTime, partialTick, StormClientManager.smoothedWindIntensity()
        );
        this.lastTiltDegrees = tiltDegrees(wind);

        // Elapsed storm time drives both the fall and the horizontal shear, so every client with
        // the same snapshot renders the same field without any extra synchronization.
        double seconds = (gameTime + partialTick - snapshot.startGameTime()) / 20.0D;

        RenderType renderType = StormRenderTypes.soft(STREAK_TEXTURE);
        VertexConsumer consumer = this.batch.buffer(renderType);

        ClientLevel level = Minecraft.getInstance().level;
        int cellSize = budget.cellSize();
        float radius = budget.radius();
        float radiusSq = radius * radius;
        float fadeStart = radius * 0.72F;
        float height = budget.columnHeight();
        int ceiling = budget.streakCeiling();

        // Wind shear moves a streak away from the world cell that owns it, so the cell disc has to
        // be widened by the largest possible displacement. Without this the downwind side of the
        // camera would be missing exactly the streaks that blew into it.
        float shearMargin = MAX_SHEAR_SECONDS * wind.speed();
        float cellRadius = radius + shearMargin;
        // Half diagonal of a cell, so a cell that only clips the disc is still accepted.
        float cellReach = radius + cellSize * 0.7072F;
        float cellReachSq = cellReach * cellReach;

        int minCellX = Mth.floor((camera.x - cellRadius) / cellSize);
        int maxCellX = Mth.floor((camera.x + cellRadius) / cellSize);
        int minCellZ = Mth.floor((camera.z - cellRadius) / cellSize);
        int maxCellZ = Mth.floor((camera.z + cellRadius) / cellSize);

        int emitted = 0;
        for (int cx = minCellX; cx <= maxCellX && emitted < ceiling; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ && emitted < ceiling; cz++) {
                double cellOriginX = cx * (double)cellSize;
                double cellOriginZ = cz * (double)cellSize;
                double cellCenterX = cellOriginX + cellSize * 0.5D;
                double cellCenterZ = cellOriginZ + cellSize * 0.5D;
                // Shear is applied along the wind axis only, so a cell's reachable footprint is a
                // segment, not a disc. Testing the closest point of that segment instead of a
                // uniformly widened radius keeps roughly half the candidate cells out of the loop
                // while still catching every cell that can blow a streak into view.
                double dx = cellCenterX - camera.x;
                double dz = cellCenterZ - camera.z;
                double along = Mth.clamp(-(dx * wind.directionX() + dz * wind.directionZ()), -shearMargin, shearMargin);
                double reachX = dx + wind.directionX() * along;
                double reachZ = dz + wind.directionZ() * along;
                if (reachX * reachX + reachZ * reachZ > cellReachSq) {
                    continue;
                }

                float cellInfluence = snapshot.radialInfluence(cellCenterX, cellCenterZ);
                if (cellInfluence <= 0.02F) {
                    continue;
                }
                this.lastCellCount++;

                // One heightmap probe per cell, never per streak: rain is not drawn below the local
                // surface, so it does not fill caves or the inside of a roofed building.
                float surface = level == null
                    ? Float.NEGATIVE_INFINITY
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)Math.floor(cellCenterX), (int)Math.floor(cellCenterZ));

                long cellKey = ((long)cx * 0x9E3779B97F4A7C15L) ^ ((long)cz * 0xC2B2AE3D27D4EB4FL) ^ CELL_SALT;
                float wanted = budget.cellStreaks(density, envelope, cellInfluence);
                int slots = (int)wanted;
                // Dither the fractional remainder per cell so a thinning field loses streaks
                // smoothly instead of stepping a whole streak at every cell boundary.
                if (StormWindField.hashUnit(snapshot.seed(), cellKey ^ 0x5BF0D9E1A4C73B25L) < wanted - slots) {
                    slots++;
                }
                if (slots <= 0) {
                    continue;
                }

                for (int slot = 0; slot < slots && emitted < ceiling; slot++) {
                    long key = cellKey + (long)slot * 0x9E3779B97F4A7C15L;
                    float hx = StormWindField.hashUnit(snapshot.seed(), key);
                    float hz = StormWindField.hashUnit(snapshot.seed(), key ^ 0x85EBCA6B12345678L);
                    float hp = StormWindField.hashUnit(snapshot.seed(), key ^ 0xC2B2AE3591827364L);
                    float hs = StormWindField.hashUnit(snapshot.seed(), key ^ 0x165667B19E3779F9L);
                    float hv = StormWindField.hashUnit(snapshot.seed(), key ^ 0xD6E8FEB86659FD93L);
                    float hw = StormWindField.hashUnit(snapshot.seed(), key ^ 0xA0761D6478BD642FL);

                    float fall = MIN_FALL_SPEED + FALL_SPEED_RANGE * hs;
                    // Distance fallen inside the current lattice period, from world time only. The
                    // modulo is taken in double precision: after a few minutes of storm the raw
                    // distance is in the thousands of blocks, where a float step is large enough to
                    // quantize the fall into visible stutter.
                    float fallen = (float)Mth.positiveModulo(seconds * fall + hp * (double)height, (double)height);
                    // The world rain lattice has period columnHeight and is anchored to world Y
                    // zero; this picks the instance of this streak that is nearest the camera. The
                    // lattice itself never moves, so the submitted column is a window onto a
                    // stationary world field rather than a cylinder carried by the camera.
                    long instance = Math.round((camera.y + height * COLUMN_BIAS + fallen) / height);
                    float worldY = (float)(instance * (double)height - fallen);
                    if (worldY < surface - GROUND_SLACK) {
                        continue;
                    }
                    float localY = (float)(worldY - camera.y);

                    // Position inside the lattice cell, normalised to [-1, 1]. The fade reaches
                    // zero at 0.96, strictly inside the cell boundary, so a streak swapping to the
                    // neighbouring lattice instance as the camera rises or falls is already fully
                    // invisible with margin to spare rather than only to within rounding.
                    float lattice = (localY - height * COLUMN_BIAS) / (height * 0.5F);
                    float verticalFade = 1.0F - StormNoise.smoothstep(0.66F, 0.96F, Math.abs(lattice));
                    if (verticalFade <= 0.0F) {
                        continue;
                    }

                    // Wind shear referenced to the camera's own altitude: a streak level with the
                    // camera sits over its cell, and streaks above or below are displaced by the
                    // distance the wind carried them in between. Clamping bounds the displacement,
                    // which is what keeps the widened cell disc above small enough to be cheap.
                    float shear = Mth.clamp(-localY / fall, -MAX_SHEAR_SECONDS, MAX_SHEAR_SECONDS) * wind.speed();
                    double worldX = cellOriginX + hx * cellSize + wind.directionX() * shear;
                    double worldZ = cellOriginZ + hz * cellSize + wind.directionZ() * shear;

                    float localX = (float)(worldX - camera.x);
                    float localZ = (float)(worldZ - camera.z);
                    float planarSq = localX * localX + localZ * localZ;
                    if (planarSq > radiusSq) {
                        continue;
                    }

                    // Fade at the outer rim of the disc as well, so a streak entering or leaving
                    // the submitted window is never seen to pop.
                    float planar = Mth.sqrt(planarSq);
                    float rimFade = 1.0F - StormNoise.smoothstep(fadeStart, radius, planar);
                    float visibility = rimFade * verticalFade * cellInfluence;
                    if (visibility <= 0.02F) {
                        continue;
                    }

                    // Each streak receives a small deterministic cross-wind flutter. The whole
                    // rain curtain still follows the prevailing wind, but it no longer forms a
                    // perfectly parallel comb. The sine term makes gust fronts visibly breathe.
                    float directionJitter = (hv - 0.5F) * 0.24F
                        + Mth.sin((float)(seconds * (1.1D + hs * 0.9D) + hp * 6.2831853D)) * 0.045F;
                    float jitterSin = Mth.sin(directionJitter);
                    float jitterCos = Mth.cos(directionJitter);
                    float localWindX = wind.directionX() * jitterCos - wind.directionZ() * jitterSin;
                    float localWindZ = wind.directionX() * jitterSin + wind.directionZ() * jitterCos;
                    float localWindSpeed = wind.speed() * (0.82F + 0.30F * hw);
                    float velocityX = localWindX * localWindSpeed;
                    float velocityZ = localWindZ * localWindSpeed;
                    float velocityY = -fall;
                    float speed = Mth.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
                    float dirX = velocityX / speed;
                    float dirY = velocityY / speed;
                    float dirZ = velocityZ / speed;
                    float exposure = MIN_STREAK_SECONDS + STREAK_SECONDS_RANGE * hv;
                    float length = Mth.clamp(speed * exposure, 1.35F, 5.2F);
                    float halfWidth = 0.025F + 0.027F * hs;

                    // Billboard the streak around its own axis: the camera sits at the local origin,
                    // so the view vector is simply the negated streak position.
                    float sideX = dirY * (-localZ) - dirZ * (-localY);
                    float sideY = dirZ * (-localX) - dirX * (-localZ);
                    float sideZ = dirX * (-localY) - dirY * (-localX);
                    float sideLength = Mth.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
                    if (sideLength < 1.0E-4F) {
                        sideX = halfWidth;
                        sideY = 0.0F;
                        sideZ = 0.0F;
                    } else {
                        sideX = sideX / sideLength * halfWidth;
                        sideY = sideY / sideLength * halfWidth;
                        sideZ = sideZ / sideLength * halfWidth;
                    }

                    float tailX = localX - dirX * length;
                    float tailY = localY - dirY * length;
                    float tailZ = localZ - dirZ * length;

                    // Distance and per-drop optical weight create three readable depth bands.
                    // Rain is deliberately not near-white or fully opaque: FULL_BRIGHT keeps the
                    // streak available in a dark thunder sky, while this tint keeps it watery.
                    float depth = 1.0F - Mth.clamp(planar / radius, 0.0F, 1.0F);
                    float opticalWeight = 0.48F + 0.52F * hw;
                    float brightness = 0.50F + 0.18F * envelope + 0.13F * depth;
                    float alpha = (0.20F + 0.30F * envelope) * visibility
                        * opticalWeight * (0.62F + 0.38F * depth);
                    int packedAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
                    int red = Mth.clamp(Math.round(126.0F * brightness), 20, 190);
                    int green = Mth.clamp(Math.round(148.0F * brightness), 24, 205);
                    int blue = Mth.clamp(Math.round(178.0F * brightness), 32, 225);
                    int color = (packedAlpha << 24) | (red << 16) | (green << 8) | blue;

                    int light = LightCoordsUtil.FULL_BRIGHT;
                    int overlay = OverlayTexture.NO_OVERLAY;
                    // The 32px texture contains two soft streak profiles. Sampling one half per
                    // billboard avoids drawing an artificial pair of perfectly parallel lines.
                    float u0 = (slot & 1) == 0 ? 0.0F : 0.5F;
                    float u1 = u0 + 0.5F;
                    consumer.addVertex(localX + sideX, localY + sideY, localZ + sideZ, color, u0, 0.0F, overlay, light, 0.0F, 1.0F, 0.0F);
                    consumer.addVertex(localX - sideX, localY - sideY, localZ - sideZ, color, u1, 0.0F, overlay, light, 0.0F, 1.0F, 0.0F);
                    consumer.addVertex(tailX - sideX, tailY - sideY, tailZ - sideZ, color, u1, 1.0F, overlay, light, 0.0F, 1.0F, 0.0F);
                    consumer.addVertex(tailX + sideX, tailY + sideY, tailZ + sideZ, color, u0, 1.0F, overlay, light, 0.0F, 1.0F, 0.0F);
                    emitted++;
                }
            }
        }

        this.lastStreakCount = emitted;
        this.batch.flush();
    }

    /** Streak tilt away from vertical, i.e. the angle of the true fall vector. */
    private static float tiltDegrees(StormWindField.Sample wind) {
        return wind.tiltDegrees();
    }

    /**
     * Bounded near-player splash pass.
     *
     * <p>Runs once per client tick and performs at most {@code splashChecks} heightmap probes in a
     * small radius around the player. There is no ray casting, no block iteration and no work at
     * all outside that radius, so the cost is a fixed handful of lookups per tick regardless of
     * storm size.</p>
     */
    public void tickSplashes() {
        this.lastSplashCount = 0;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }

        StormSnapshot snapshot = StormClientManager.thunderSnapshot();
        if (!snapshot.active()) {
            return;
        }
        float influence = snapshot.radialInfluence(player.getX(), player.getZ());
        if (influence <= 0.0F) {
            return;
        }
        float envelope = StormClientManager.smoothedRainIntensity() * influence;
        if (envelope <= 0.05F) {
            return;
        }

        StormRainBudget budget = StormRainBudget.of(StormConfig.quality());
        int checks = (int)Math.ceil(budget.splashChecks() * Math.min(StormConfig.rainDensity(), 2.0D) * envelope);
        checks = Math.min(checks, budget.splashChecks());
        if (checks <= 0) {
            return;
        }

        RandomSource random = level.getRandom();
        int playerY = Mth.floor(player.getY());
        for (int i = 0; i < checks; i++) {
            int x = Mth.floor(player.getX()) + random.nextInt(SPLASH_RADIUS * 2 + 1) - SPLASH_RADIUS;
            int z = Mth.floor(player.getZ()) + random.nextInt(SPLASH_RADIUS * 2 + 1) - SPLASH_RADIUS;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (surface <= level.getMinY() || Math.abs(surface - playerY) > SPLASH_VERTICAL_RANGE) {
                continue;
            }
            BlockPos pos = new BlockPos(x, surface, z);
            if (!level.canSeeSky(pos)) {
                continue;
            }

            // One block read per accepted probe decides between a water ripple and a hard splash.
            boolean water = !level.getBlockState(pos.below()).getFluidState().isEmpty();
            ParticleOptions particle = water ? ParticleTypes.SPLASH : ParticleTypes.RAIN;
            level.addParticle(
                particle,
                x + random.nextDouble(),
                surface + 0.05D,
                z + random.nextDouble(),
                0.0D,
                0.0D,
                0.0D
            );
            this.lastSplashCount++;
        }
    }
}
