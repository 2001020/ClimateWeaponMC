package com.stormweapon.storm;

import com.stormweapon.config.StormConfig;
import com.stormweapon.entity.MeteorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Spawns the falling meteors while a meteor deployment is running.
 *
 * <p>Meteors are seeded around each online player rather than from one global point: the effect is
 * meant to be world-wide, but only loaded chunks can host an entity, and the loaded area is
 * exactly the area around players. Overlapping players are de-duplicated by chunk so two people
 * standing together do not get double the bombardment they would each see alone.</p>
 */
public final class MeteorStormManager {
    /**
     * Meteors per player per second at density 1.0.
     *
     * <p>The default bombardment was raised by lifting this base rate rather than by raising the
     * default of the density setting itself: that setting is persisted per world, so a bigger
     * default would only ever reach brand-new worlds and leave every existing save on the old,
     * thinner shower. Changing the base rate makes the shower denser everywhere at once and keeps
     * the slider meaning the same relative thing it always did.</p>
     */
    private static final double BASE_PER_SECOND = 10.0D;
    private static final int SPAWN_INTERVAL_TICKS = 10;
    private static final double SPAWN_RADIUS = 70.0D;
    /** Spawn height above the terrain, high enough to be seen falling in. */
    private static final int SPAWN_ALTITUDE = 90;

    private MeteorStormManager() {}

    public static void tick(ServerLevel level, StormSavedData data) {
        if (!data.meteorActive() || level.getGameTime() % SPAWN_INTERVAL_TICKS != 0) {
            return;
        }
        double perBurst = BASE_PER_SECOND * StormConfig.meteorDensity() * (SPAWN_INTERVAL_TICKS / 20.0D);
        Set<Long> claimedChunks = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            long chunkKey = BlockPos.asLong(player.blockPosition().getX() >> 4, 0, player.blockPosition().getZ() >> 4);
            if (!claimedChunks.add(chunkKey)) {
                continue;
            }
            spawnAround(level, player.position(), perBurst);
        }
    }

    private static void spawnAround(ServerLevel level, Vec3 center, double amount) {
        // A fractional remainder becomes a probabilistic extra meteor, so low density settings
        // still thin the shower out smoothly instead of rounding down to a fixed integer.
        int count = Mth.floor(amount);
        if (level.getRandom().nextDouble() < amount - count) {
            count++;
        }
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = SPAWN_RADIUS * Math.sqrt(level.getRandom().nextDouble());
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            // Skip rather than let getHeight pull an unloaded chunk in: the spawn radius reaches
            // past the loaded area at the edge of a player's render distance, and force-loading
            // there would churn chunks every burst for meteors nobody can see anyway.
            if (!level.hasChunkAt(BlockPos.containing(x, 0.0D, z))) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            float driftX = (level.getRandom().nextFloat() - 0.5F) * 0.5F;
            float driftZ = (level.getRandom().nextFloat() - 0.5F) * 0.5F;
            MeteorEntity.spawn(level, new Vec3(x, ground + SPAWN_ALTITUDE, z), driftX, driftZ);
        }
    }
}
