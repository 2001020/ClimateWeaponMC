package com.stormweapon.client.weather;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.StormClientManager;
import com.stormweapon.entity.MeteorEntity;
import com.stormweapon.entity.WeatherMissileEntity;
import com.stormweapon.storm.MissileKind;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;

/**
 * World-anchored weapon visuals submitted through the same Blaze3D frame pass as the storm.
 * This intentionally avoids graphics-backend calls: mesh vertices are submitted to RenderType,
 * which Minecraft translates for either Vulkan or OpenGL.
 */
public final class StormWeaponEffectsRenderer {
    private static final Identifier MISSILE_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/white_concrete.png"
    );
    private static final Identifier WAVE_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/iron_block.png"
    );
    // A StagedVertexBuffer is single-build-at-a-time in 26.2. Keep independent batches for
    // translucent metal and additive flame/wave geometry rather than switching types mid-build.
    private final StormGeometryBatch bodyBatch = new StormGeometryBatch(1 << 17);
    private final StormGeometryBatch glowBatch = new StormGeometryBatch(1 << 17);
    // Tracked per storm kind: the ring is drawn once per active slot every frame, and a single
    // shared "completed at" tick would let one kind's fade-out bookkeeping clobber the other's.
    private final Map<MissileKind, Long> completedWaveTicks = new EnumMap<>(MissileKind.class);
    private final Map<Integer, Long> lastParticleTick = new HashMap<>();

    public void render(LevelRenderState state, float partialTick) {
        Vec3 camera = state.cameraRenderState.pos;
        VertexConsumer solid = bodyBatch.buffer(RenderTypes.entityCutout(MISSILE_TEXTURE));
        VertexConsumer glow = glowBatch.buffer(StormRenderTypes.glow(WAVE_TEXTURE));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof WeatherMissileEntity missile && missile.distanceToSqr(camera) < 640_000.0D) {
                    renderMissile(solid, missile, camera, partialTick);
                } else if (entity instanceof MeteorEntity meteor && meteor.distanceToSqr(camera) < 160_000.0D) {
                    renderMeteor(solid, meteor, camera, partialTick);
                }
            }
        }
        // Each slot detonates at its own point and on its own clock, so a ring is drawn per slot
        // rather than for one merged "current" snapshot.
        renderAtmosphericWave(glow, StormClientManager.thunderSnapshot(), MissileKind.THUNDER, camera, state.gameTime);
        renderAtmosphericWave(glow, StormClientManager.fogSnapshot(), MissileKind.FOG, camera, state.gameTime);
        renderAtmosphericWave(glow, StormClientManager.blizzardSnapshot(), MissileKind.BLIZZARD, camera, state.gameTime);
        renderAtmosphericWave(glow, StormClientManager.cherrySnapshot(), MissileKind.CHERRY, camera, state.gameTime);
        bodyBatch.flush();
        glowBatch.flush();
    }

    private void renderMissile(VertexConsumer body, WeatherMissileEntity missile, Vec3 camera, float partial) {
        // Frame-pass vertices are camera-relative, just like the rain and lightning layers.
        Vec3 worldPosition = missile.getPosition(partial);
        Vec3 position = worldPosition.subtract(camera);
        // Rotation (getViewVector) is used instead of getDeltaMovement(): velocity sync packets are
        // sent conditionally and can go stale on the client for a plain custom Entity, whereas
        // rotation is part of the standard per-tick entity tracking every client always receives.
        // Using velocity here meant the mesh/exhaust orientation stayed frozen at roughly the launch
        // angle once that sync lagged, which was invisible during the straight boost climb (velocity
        // happened to still match the launch rail) but went visibly wrong the moment the missile
        // actually turned onto its target, reading as the tail flame detaching/disappearing.
        Vec3 forward = missile.getViewVector(partial);
        if (forward.lengthSqr() < 1.0E-4D) {
            double yaw = Math.toRadians(missile.launchYaw());
            forward = new Vec3(-Math.sin(yaw) * 0.259D, 0.966D, Math.cos(yaw) * 0.259D);
        } else {
            forward = forward.normalize();
        }
        Vec3 side = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 up = side.cross(forward).normalize();
        int segments = missile.distanceToSqr(camera) < 4096.0D ? 24 : missile.distanceToSqr(camera) < 25_600.0D ? 16 : 8;

        // Same 6.8-block mesh proportions as the mounted launcher model. Each payload gets its own
        // tint so it is distinguishable in flight, not just on the rail: fog light yellow, meteor
        // near-black. The meteor's darkest tones stop short of pure black so its silhouette still
        // reads against the sky it is about to blacken.
        MissileKind kind = missile.kind();
        int[] engineColor, bodyColor, ring1Color, ring2Color, noseColor, finColor;
        switch (kind) {
            case FOG -> {
                engineColor = new int[]{70, 62, 30}; bodyColor = new int[]{232, 214, 128};
                ring1Color = new int[]{96, 84, 44}; ring2Color = new int[]{100, 88, 46};
                noseColor = new int[]{110, 96, 52}; finColor = new int[]{130, 112, 58};
            }
            case METEOR -> {
                engineColor = new int[]{12, 12, 14}; bodyColor = new int[]{34, 34, 38};
                ring1Color = new int[]{18, 18, 21}; ring2Color = new int[]{20, 20, 23};
                noseColor = new int[]{26, 26, 30}; finColor = new int[]{46, 46, 51};
            }
            case BLIZZARD -> {
                engineColor = new int[]{170, 186, 198}; bodyColor = new int[]{242, 248, 252};
                ring1Color = new int[]{180, 205, 220}; ring2Color = new int[]{194, 220, 234};
                noseColor = new int[]{214, 232, 242}; finColor = new int[]{226, 240, 248};
            }
            case CHERRY -> {
                engineColor = new int[]{120, 54, 78}; bodyColor = new int[]{248, 170, 202};
                ring1Color = new int[]{180, 82, 122}; ring2Color = new int[]{198, 96, 140};
                noseColor = new int[]{228, 126, 168}; finColor = new int[]{238, 146, 184};
            }
            default -> {
                engineColor = new int[]{43, 47, 50}; bodyColor = new int[]{215, 218, 211};
                ring1Color = new int[]{61, 67, 70}; ring2Color = new int[]{64, 70, 72};
                noseColor = new int[]{72, 78, 81}; finColor = new int[]{96, 101, 102};
            }
        }

        Vec3 tail = position.subtract(forward.scale(3.4D));
        emitTube(body, tail, forward, side, up, 0.29D, 0.34D, segments, engineColor[0], engineColor[1], engineColor[2], 255);
        emitTube(body, tail.add(forward.scale(0.28D)), forward, side, up, 0.39D, 4.94D, segments, bodyColor[0], bodyColor[1], bodyColor[2], 255);
        emitTube(body, tail.add(forward.scale(1.08D)), forward, side, up, 0.415D, 0.16D, segments, ring1Color[0], ring1Color[1], ring1Color[2], 255);
        emitTube(body, tail.add(forward.scale(3.72D)), forward, side, up, 0.415D, 0.18D, segments, ring2Color[0], ring2Color[1], ring2Color[2], 255);
        emitCone(body, tail.add(forward.scale(5.22D)), forward, side, up, 0.39D, 1.58D, segments, noseColor[0], noseColor[1], noseColor[2], 255);
        for (int fin = 0; fin < 4; fin++) {
            double a = Math.PI * 0.5D * fin + Math.PI * 0.25D;
            Vec3 radial = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 tangent = forward.cross(radial).normalize();
            emitFin(body, tail.add(forward.scale(0.42D)), forward, radial, tangent, finColor[0], finColor[1], finColor[2], 255);
        }

        // The engine plume is particle-only. Keeping flame geometry out of the missile mesh avoids
        // a rigid cone silhouette and lets the exhaust break up dynamically in flight.
        if (missile.flightAge() >= 18) {
            emitEngineParticles(missile, worldPosition.subtract(forward.scale(3.52D)), forward, side, up);
        }
    }

    /**
     * A meteor's rock body plus its burning tail.
     *
     * <p>The body is a low-facet lump rather than a smooth sphere: each latitude/longitude vertex
     * is pushed in or out by a deterministic hash of its own indices and the meteor's id, so every
     * meteor gets a distinct irregular silhouette while still being identical on every client
     * without syncing any shape data.</p>
     */
    private void renderMeteor(VertexConsumer body, MeteorEntity meteor, Vec3 camera, float partial) {
        Vec3 worldPosition = meteor.getPosition(partial);
        Vec3 center = worldPosition.subtract(camera);
        int rings = meteor.distanceToSqr(camera) < 4096.0D ? 10 : 6;
        int sectors = rings;
        double radius = MeteorEntity.BODY_RADIUS;
        long id = meteor.getId();
        double spin = Math.toRadians(meteor.spin() + meteor.tickCount + partial) * 2.0D;

        for (int ring = 0; ring < rings; ring++) {
            double phi0 = Math.PI * ring / rings;
            double phi1 = Math.PI * (ring + 1) / rings;
            for (int sector = 0; sector < sectors; sector++) {
                double theta0 = Math.PI * 2.0D * sector / sectors + spin;
                double theta1 = Math.PI * 2.0D * (sector + 1) / sectors + spin;
                Vec3 a = lump(center, radius, phi0, theta0, id, ring, sector);
                Vec3 b = lump(center, radius, phi0, theta1, id, ring, sector + 1);
                Vec3 c = lump(center, radius, phi1, theta1, id, ring + 1, sector + 1);
                Vec3 d = lump(center, radius, phi1, theta0, id, ring + 1, sector);
                // Darker toward the top, hotter toward the leading (downward) face.
                float heat = (float) (phi0 / Math.PI);
                int r = (int) (46 + 165 * heat * heat);
                int g = (int) (38 + 70 * heat * heat);
                int bl = (int) (34 + 24 * heat * heat);
                quad(body, a, b, c, d, r, g, bl, 255);
            }
        }

        emitMeteorTrailParticles(meteor, worldPosition);
    }

    /**
     * The meteor's burning wake, as vanilla particles rather than mod geometry.
     *
     * <p>Emission is throttled to once per game tick, but a meteor covers well over a block in that
     * time, so spawning everything at its current position would leave a visibly dotted trail with
     * gaps between each tick's clump. Particles are instead distributed back along the segment the
     * meteor actually travelled since the last tick, which reads as one continuous streak.</p>
     */
    private void emitMeteorTrailParticles(MeteorEntity meteor, Vec3 worldPosition) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().player == null) return;
        double distanceSqr = meteor.distanceToSqr(Minecraft.getInstance().player.position());
        // Matches the 400-block mesh-render cutoff in render(), so the trail never stops short
        // while the rock itself is still being drawn.
        if (distanceSqr > 160_000.0D) return;
        long now = level.getGameTime();
        Long last = lastParticleTick.get(meteor.getId());
        if (last != null && last == now) return;
        lastParticleTick.put(meteor.getId(), now);

        Vec3 velocity = meteor.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6D) velocity = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 back = velocity.reverse();
        int flames = distanceSqr < 16_384.0D ? 30 : 18;
        for (int i = 0; i < flames; i++) {
            double along = i / (double) flames;
            double angle = Math.PI * 2.0D * ((i * 7) % flames) / flames + now * 0.6D;
            double spread = MeteorEntity.BODY_RADIUS * (0.35D + along * 0.9D);
            Vec3 at = worldPosition.add(back.scale(along))
                .add(Math.cos(angle) * spread, 0.0D, Math.sin(angle) * spread);
            Vec3 drift = back.scale(0.12D + along * 0.05D);
            var type = i % 4 == 0 ? ParticleTypes.LAVA : i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
            level.addAlwaysVisibleParticle(type, at.x, at.y, at.z, drift.x, drift.y, drift.z);
        }
        int smokes = distanceSqr < 16_384.0D ? 14 : 8;
        for (int i = 0; i < smokes; i++) {
            double along = 0.25D + 0.85D * (i / (double) smokes);
            double angle = now * 0.41D + i * 2.399D;
            double spread = MeteorEntity.BODY_RADIUS * (0.7D + along * 1.2D);
            Vec3 at = worldPosition.add(back.scale(along))
                .add(Math.cos(angle) * spread, 0.0D, Math.sin(angle) * spread);
            Vec3 drift = back.scale(0.06D);
            var type = i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
            level.addAlwaysVisibleParticle(type, at.x, at.y, at.z, drift.x, drift.y, drift.z);
        }
        if (lastParticleTick.size() > 64) lastParticleTick.entrySet().removeIf(entry -> entry.getValue() < now - 80L);
    }

    /** One displaced point on the meteor's surface; the displacement is stable per meteor. */
    private static Vec3 lump(Vec3 center, double radius, double phi, double theta, long id, int ring, int sector) {
        long hash = id * 0x9E3779B97F4A7C15L ^ (ring * 0x85EBCA6BL) ^ (sector * 0xC2B2AE35L);
        hash ^= hash >>> 29;
        double jitter = 0.78D + 0.44D * ((hash >>> 11) / (double) (1L << 53));
        double r = radius * jitter;
        return center.add(
            Math.sin(phi) * Math.cos(theta) * r,
            Math.cos(phi) * r,
            Math.sin(phi) * Math.sin(theta) * r);
    }

    /** Dense, fully particle-based exhaust generated locally without network traffic. */
    private void emitEngineParticles(WeatherMissileEntity missile, Vec3 nozzle, Vec3 forward, Vec3 side, Vec3 up) {
        ClientLevel level = Minecraft.getInstance().level;
        // Matches the 800-block mesh-render cutoff in render(): this used to be a much tighter
        // 256-block check, so the exhaust trail would vanish while the missile body was still
        // being drawn further out, reading as the trail cutting off mid-flight.
        if (level == null || Minecraft.getInstance().player == null || missile.distanceToSqr(Minecraft.getInstance().player.position()) > 640_000.0D) return;
        long now = level.getGameTime();
        Long last = lastParticleTick.get(missile.getId());
        if (last != null && last == now) return;
        lastParticleTick.put(missile.getId(), now);
        double speed = Math.max(0.35D, missile.getDeltaMovement().length());
        double pulse = 0.88D + 0.18D * Math.sin((now + missile.getId() * 11L) * 0.83D);
        int flameCount = missile.distanceToSqr(Minecraft.getInstance().player.position()) < 16_384.0D ? 24 : 14;
        for (int i = 0; i < flameCount; i++) {
            double angle = Math.PI * 2.0D * (i / (double)flameCount) + now * 0.91D;
            double radial = (0.025D + 0.18D * ((i * 37 % flameCount) / (double)flameCount)) * pulse;
            Vec3 offset = side.scale(Math.cos(angle) * radial).add(up.scale(Math.sin(angle) * radial));
            double scatter = 0.09D + (i % 5) * 0.012D;
            Vec3 exhaust = forward.scale(-(0.30D + speed * 0.10D + (i % 4) * 0.025D))
                .add(offset.scale(scatter));
            var type = i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
            level.addAlwaysVisibleParticle(type, nozzle.x + offset.x, nozzle.y + offset.y, nozzle.z + offset.z,
                exhaust.x, exhaust.y, exhaust.z);
        }
        for (int i = 0; i < 5; i++) {
            double angle = now * 0.47D + i * 2.399D;
            Vec3 offset = side.scale(Math.cos(angle) * 0.19D).add(up.scale(Math.sin(angle) * 0.19D));
            Vec3 smoke = forward.scale(-(0.09D + speed * 0.035D)).add(offset.scale(0.08D));
            var type = i < 2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
            Vec3 origin = nozzle.subtract(forward.scale(0.30D + i * 0.08D)).add(offset);
            level.addAlwaysVisibleParticle(type, origin.x, origin.y, origin.z, smoke.x, smoke.y, smoke.z);
        }
        if (lastParticleTick.size() > 64) lastParticleTick.entrySet().removeIf(entry -> entry.getValue() < now - 80L);
    }

    /** A horizontal high-altitude ring; it never places or touches geometry on the ground. */
    private void renderAtmosphericWave(VertexConsumer consumer, StormSnapshot snapshot, MissileKind kind,
                                       Vec3 camera, long gameTime) {
        if (!snapshot.active() || snapshot.waveMaxRadius() <= 0.0F || snapshot.detonationY() <= 0.0D
            || snapshot.waveProgress() <= 0.01F) return;
        long completedWaveTick = completedWaveTicks.getOrDefault(kind, -1L);
        if (snapshot.waveProgress() < 0.98F) completedWaveTick = -1L;
        if (snapshot.waveProgress() >= 0.98F && completedWaveTick < 0L) completedWaveTick = gameTime;
        completedWaveTicks.put(kind, completedWaveTick);
        float completionFade = completedWaveTick < 0L ? 1.0F : Mth.clamp(1.0F - (gameTime - completedWaveTick) / 14.0F, 0.0F, 1.0F);
        if (completionFade <= 0.01F) return;
        double radius = Math.min(snapshot.waveRadius(), snapshot.waveMaxRadius());
        int segments = 96;
        for (int band = 0; band < 3; band++) {
            double inner = radius + band * 2.3D;
            double outer = inner + 1.5D;
            int alpha = Math.round((150 - band * 42) * completionFade);
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2.0D * i / segments;
                double a1 = Math.PI * 2.0D * (i + 1) / segments;
                Vec3 p0 = new Vec3(snapshot.centerX() + Math.cos(a0) * inner, snapshot.detonationY(), snapshot.centerZ() + Math.sin(a0) * inner);
                Vec3 p1 = new Vec3(snapshot.centerX() + Math.cos(a1) * inner, snapshot.detonationY(), snapshot.centerZ() + Math.sin(a1) * inner);
                Vec3 p2 = new Vec3(snapshot.centerX() + Math.cos(a1) * outer, snapshot.detonationY(), snapshot.centerZ() + Math.sin(a1) * outer);
                Vec3 p3 = new Vec3(snapshot.centerX() + Math.cos(a0) * outer, snapshot.detonationY(), snapshot.centerZ() + Math.sin(a0) * outer);
                quad(consumer, p0.subtract(camera), p1.subtract(camera), p2.subtract(camera), p3.subtract(camera), 170, 210, 255, alpha);
            }
        }
    }

    private void emitTube(VertexConsumer c, Vec3 origin, Vec3 forward, Vec3 side, Vec3 up, double radius, double length, int segments, int r, int g, int b, int a) {
        Vec3 end = origin.add(forward.scale(length));
        for (int i = 0; i < segments; i++) {
            double t0 = Math.PI * 2.0D * i / segments, t1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 p0 = origin.add(side.scale(Math.cos(t0) * radius)).add(up.scale(Math.sin(t0) * radius));
            Vec3 p1 = origin.add(side.scale(Math.cos(t1) * radius)).add(up.scale(Math.sin(t1) * radius));
            quad(c, p0, p1, p1.add(forward.scale(length)), p0.add(forward.scale(length)), r, g, b, a);
            quad(c, origin, p1, p0, origin, r, g, b, a);
            Vec3 e0 = p0.add(forward.scale(length));
            Vec3 e1 = p1.add(forward.scale(length));
            quad(c, end, e0, e1, end, r, g, b, a);
        }
    }

    private void emitCone(VertexConsumer c, Vec3 base, Vec3 forward, Vec3 side, Vec3 up, double radius, double length, int segments, int r, int g, int b, int a) {
        Vec3 tip = base.add(forward.scale(length));
        for (int i = 0; i < segments; i++) {
            double t0 = Math.PI * 2.0D * i / segments, t1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 p0 = base.add(side.scale(Math.cos(t0) * radius)).add(up.scale(Math.sin(t0) * radius));
            Vec3 p1 = base.add(side.scale(Math.cos(t1) * radius)).add(up.scale(Math.sin(t1) * radius));
            quad(c, p0, p1, tip, tip, r, g, b, a);
        }
    }

    private void emitFin(VertexConsumer c, Vec3 tail, Vec3 forward, Vec3 radial, Vec3 tangent, int r, int g, int b, int a) {
        Vec3 p0 = tail.add(radial.scale(0.28D));
        Vec3 p1 = tail.add(radial.scale(1.08D)).add(forward.scale(0.32D));
        Vec3 p2 = tail.add(radial.scale(0.85D)).add(forward.scale(1.45D));
        Vec3 p3 = tail.add(radial.scale(0.22D)).add(forward.scale(1.08D));
        double thickness = 0.045D;
        Vec3 t = tangent.scale(thickness);
        quad(c, p0.add(t), p1.add(t), p2.add(t), p3.add(t), r, g, b, a);
        quad(c, p3.subtract(t), p2.subtract(t), p1.subtract(t), p0.subtract(t), r, g, b, a);
        quad(c, p0.subtract(t), p1.subtract(t), p1.add(t), p0.add(t), r, g, b, a);
        quad(c, p1.subtract(t), p2.subtract(t), p2.add(t), p1.add(t), r, g, b, a);
        quad(c, p2.subtract(t), p3.subtract(t), p3.add(t), p2.add(t), r, g, b, a);
    }

    private void quad(VertexConsumer c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int r, int g, int blue, int alpha) {
        int color = (alpha << 24) | (r << 16) | (g << 8) | blue;
        vertex(c, a, color, 0, 0); vertex(c, b, color, 1, 0); vertex(c, d, color, 1, 1); vertex(c, e, color, 0, 1);
    }
    private void vertex(VertexConsumer c, Vec3 p, int color, float u, float v) {
        c.addVertex((float)p.x, (float)p.y, (float)p.z, color, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, 0, 1, 0);
    }
}
