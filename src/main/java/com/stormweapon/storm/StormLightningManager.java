package com.stormweapon.storm;

import com.stormweapon.config.StormConfig;
import com.stormweapon.network.StormLightningPacket;
import com.stormweapon.network.StormNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/** Server-authoritative scheduler for the sparse, damaging subset of storm lightning. */
public final class StormLightningManager {
    private static final float BASE_DAMAGE = 18.0F;
    private static final double INNER_DAMAGE_RADIUS = 2.5D;
    private static final double OUTER_DAMAGE_RADIUS = 8.0D;
    private static final Map<String, Schedule> SCHEDULES = new HashMap<>();

    private record Schedule(long stormSeed, long nextStrike) {}

    private StormLightningManager() {}

    public static void tick(ServerLevel level, StormSnapshot snapshot) {
        String key = level.dimension().identifier().toString();
        if (!snapshot.active()) {
            SCHEDULES.remove(key);
            return;
        }

        long now = level.getGameTime();
        float intensity = snapshot.lightningIntensity(now, 0.0F);
        if (intensity < 0.30F) {
            return;
        }

        Schedule schedule = SCHEDULES.get(key);
        if (schedule == null || schedule.stormSeed != snapshot.seed()) {
            SCHEDULES.put(key, new Schedule(snapshot.seed(), now + nextDelay(level, intensity)));
            return;
        }
        if (now < schedule.nextStrike) {
            return;
        }

        strike(level, snapshot, intensity);
        SCHEDULES.put(key, new Schedule(snapshot.seed(), now + nextDelay(level, intensity)));
    }

    private static int nextDelay(ServerLevel level, float intensity) {
        double min = StormConfig.PHYSICAL_LIGHTNING_MIN_SECONDS.get();
        double max = Math.max(min, StormConfig.PHYSICAL_LIGHTNING_MAX_SECONDS.get());
        double seconds = min + level.getRandom().nextDouble() * (max - min);
        // Supercell ramp-up is quieter than the peak, while the configured range remains a hard ceiling.
        seconds /= Mth.clamp(intensity, 0.35F, 1.0F);
        return Math.max(10, Mth.ceil(seconds * 20.0D));
    }

    private static void strike(ServerLevel level, StormSnapshot snapshot, float intensity) {
        // A global storm samples around one randomly selected loaded player. This keeps physical
        // strikes observable without scanning chunks or targeting the player's exact position.
        // With no players present, retain the atmospheric detonation as a harmless fallback.
        ServerPlayer observer = level.players().isEmpty()
            ? null
            : level.players().get(level.getRandom().nextInt(level.players().size()));
        double anchorX = observer == null ? snapshot.centerX() : observer.getX();
        double anchorZ = observer == null ? snapshot.centerZ() : observer.getZ();
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 18.0D + Math.sqrt(level.getRandom().nextDouble()) * 78.0D;
        int x = Mth.floor(anchorX + Math.cos(angle) * radius);
        int z = Mth.floor(anchorZ + Math.sin(angle) * radius);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));

        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt == null) {
            return;
        }
        bolt.setPos(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D);
        float damage = BASE_DAMAGE * StormConfig.LIGHTNING_DAMAGE_MULTIPLIER.get().floatValue();
        // Damage is handled once, explicitly, below. A zero-damage non-visual-only bolt can still
        // perform vanilla fire/conductor interactions when the server enables them, without
        // double-hitting entities that are also inside our radial damage field.
        bolt.setDamage(0.0F);
        boolean fireEnabled = StormConfig.STORM_FIRE_ENABLED.get();
        bolt.setVisualOnly(!fireEnabled);
        level.addFreshEntity(bolt);

        // Every visible ground strike has a matching server-authoritative damage field. The inner
        // 2.5 blocks receive the full configured hit; damage then falls smoothly to zero at 8.
        AABB area = new AABB(surface).inflate(OUTER_DAMAGE_RADIUS, 6.0D, OUTER_DAMAGE_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            double dx = entity.getX() - bolt.getX();
            double dz = entity.getZ() - bolt.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            float falloff = distance <= INNER_DAMAGE_RADIUS ? 1.0F : 1.0F - Mth.clamp(
                (float)((distance - INNER_DAMAGE_RADIUS) / (OUTER_DAMAGE_RADIUS - INNER_DAMAGE_RADIUS)), 0.0F, 1.0F
            );
            if (falloff > 0.0F && damage > 0.0F) {
                entity.hurtServer(level, level.damageSources().lightningBolt(), damage * falloff);
            }
        }

        StormNetwork.sendLightning(level, new StormLightningPacket(
            bolt.getX(), bolt.getY(), bolt.getZ(), bolt.seed, intensity
        ));
    }
}
