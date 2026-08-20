package com.stormweapon.client.weather;

import com.stormweapon.client.StormClientManager;
import com.stormweapon.storm.IndoorShelter;
import com.stormweapon.storm.SkyExposure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Emits Minecraft's native snowflake and cherry-leaf particles from the synchronized deployments. */
public final class SeasonalPrecipitationManager {
    private static ClientLevel shelterLevel;
    private static BlockPos shelterPosition = BlockPos.ZERO;
    private static int shelterCacheTicks;
    private static boolean cachedIndoors;

    private SeasonalPrecipitationManager() {}

    public static void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            resetShelterCache();
            return;
        }
        float blizzard = Mth.clamp(StormClientManager.smoothedBlizzardIntensity(), 0.0F, 1.0F);
        float cherry = Mth.clamp(StormClientManager.smoothedCherryIntensity(), 0.0F, 1.0F);
        BlockPos playerPosition = minecraft.player.blockPosition();
        boolean blizzardVisible = blizzard > 0.01F && !isStrictlyIndoors(level, playerPosition);
        boolean cherryVisible = cherry > 0.01F && SkyExposure.exposed(level, playerPosition);
        if (!blizzardVisible && !cherryVisible) {
            return;
        }
        Vec3 center = minecraft.player.position();

        if (blizzardVisible) {
            int count = Math.max(1, Math.round(30.0F * blizzard));
            for (int i = 0; i < count; i++) {
                double x = center.x + level.getRandom().triangle(0.0D, 18.0D);
                double y = center.y + 5.0D + level.getRandom().nextDouble() * 14.0D;
                double z = center.z + level.getRandom().triangle(0.0D, 18.0D);
                double vx = level.getRandom().triangle(0.12D, 0.10D);
                double vz = level.getRandom().triangle(0.04D, 0.10D);
                level.addParticle(ParticleTypes.SNOWFLAKE, true, false, x, y, z, vx, -0.18D, vz);
            }
        }
        if (cherryVisible) {
            int count = Math.max(1, Math.round(10.0F * cherry));
            for (int i = 0; i < count; i++) {
                double x = center.x + level.getRandom().triangle(0.0D, 15.0D);
                double y = center.y + 4.0D + level.getRandom().nextDouble() * 12.0D;
                double z = center.z + level.getRandom().triangle(0.0D, 15.0D);
                level.addParticle(ParticleTypes.CHERRY_LEAVES, true, false, x, y, z, 0.0D, -0.035D, 0.0D);
            }
        }
    }

    /** Mirrors the server's strict enclosure test and refresh cadence for visual/gameplay parity. */
    private static boolean isStrictlyIndoors(ClientLevel level, BlockPos playerPosition) {
        if (shelterLevel != level || shelterCacheTicks <= 0 ||
            playerPosition.distManhattan(shelterPosition) > 1) {
            shelterLevel = level;
            shelterPosition = playerPosition.immutable();
            cachedIndoors = IndoorShelter.isIndoors(level, playerPosition);
            shelterCacheTicks = 20;
        } else {
            shelterCacheTicks--;
        }
        return cachedIndoors;
    }

    private static void resetShelterCache() {
        shelterLevel = null;
        shelterPosition = BlockPos.ZERO;
        shelterCacheTicks = 0;
        cachedIndoors = false;
    }
}
