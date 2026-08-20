package com.stormweapon.storm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative falling ember hazard released by a fog missile's high-altitude detonation.
 *
 * <p>Each ember is a lightweight simulated point, not an entity: its position is computed
 * analytically every tick and mirrored to nearby clients with plain particle packets, avoiding the
 * network/entity-tracking cost of dozens of short-lived real entities. The first entity an ember's
 * position overlaps takes a one-time hit and the ember is consumed; embers that reach the ground
 * untouched simply vanish.</p>
 */
public final class FogEmberManager {
    private static final int EMBER_COUNT = 80;
    private static final double SCATTER_RADIUS = 62.0D;
    private static final double MIN_FALL_SPEED = 1.1D;
    private static final double MAX_FALL_SPEED = 1.9D;
    private static final float EMBER_DAMAGE = 20.0F;
    private static final double HIT_RADIUS = 0.65D;

    private static final Map<String, List<Ember>> ACTIVE = new HashMap<>();

    private FogEmberManager() {}

    private static final class Ember {
        double x;
        double y;
        double z;
        final double groundY;
        final double fallSpeed;
        final double driftX;
        final double driftZ;
        final double wobblePhase;

        Ember(double x, double y, double z, double groundY, double fallSpeed, double driftX, double driftZ, double wobblePhase) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.groundY = groundY;
            this.fallSpeed = fallSpeed;
            this.driftX = driftX;
            this.driftZ = driftZ;
            this.wobblePhase = wobblePhase;
        }
    }

    /** Releases a scattered burst of falling embers around the fog missile's detonation point. */
    public static void spawnBurst(ServerLevel level, double centerX, double centerZ, double startY) {
        List<Ember> embers = ACTIVE.computeIfAbsent(key(level), ignored -> new ArrayList<>());
        for (int i = 0; i < EMBER_COUNT; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = SCATTER_RADIUS * Math.sqrt(level.getRandom().nextDouble());
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0.0D, z));
            double fallSpeed = MIN_FALL_SPEED + level.getRandom().nextDouble() * (MAX_FALL_SPEED - MIN_FALL_SPEED);
            double driftX = (level.getRandom().nextDouble() - 0.5D) * 0.12D;
            double driftZ = (level.getRandom().nextDouble() - 0.5D) * 0.12D;
            double wobblePhase = level.getRandom().nextDouble() * Math.PI * 2.0D;
            embers.add(new Ember(x, startY, z, ground.getY(), fallSpeed, driftX, driftZ, wobblePhase));
        }
    }

    public static void tick(ServerLevel level) {
        List<Ember> embers = ACTIVE.get(key(level));
        if (embers == null || embers.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        List<Ember> finished = new ArrayList<>();
        for (Ember ember : embers) {
            if (ember.y <= ember.groundY) {
                finished.add(ember);
                level.sendParticles(ParticleTypes.SMOKE, true, true, ember.x, ember.groundY, ember.z, 6, 0.25D, 0.05D, 0.25D, 0.02D);
                continue;
            }

            AABB hitBox = new AABB(ember.x, ember.y, ember.z, ember.x, ember.y, ember.z).inflate(HIT_RADIUS);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, hitBox);
            if (!victims.isEmpty()) {
                for (LivingEntity victim : victims) {
                    victim.hurtServer(level, level.damageSources().hotFloor(), EMBER_DAMAGE);
                }
                finished.add(ember);
                level.sendParticles(ParticleTypes.LAVA, true, true, ember.x, ember.y, ember.z, 10, 0.2D, 0.2D, 0.2D, 0.05D);
                continue;
            }

            ember.y -= ember.fallSpeed;
            double wobble = Math.sin(gameTime * 0.35D + ember.wobblePhase) * 0.06D;
            ember.x += ember.driftX + wobble;
            ember.z += ember.driftZ + wobble;

            level.sendParticles(ParticleTypes.FLAME, true, true, ember.x, ember.y, ember.z, 2, 0.03D, 0.03D, 0.03D, 0.005D);
            level.sendParticles(ParticleTypes.SMALL_FLAME, true, true, ember.x, ember.y, ember.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        embers.removeAll(finished);
        if (embers.isEmpty()) {
            ACTIVE.remove(key(level));
        }
    }

    private static String key(ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
