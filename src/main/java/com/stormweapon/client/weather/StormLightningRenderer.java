package com.stormweapon.client.weather;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.StormClientManager;
import com.stormweapon.config.StormConfig;
import com.stormweapon.network.StormLightningPacket;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Backend agnostic procedural cloud flashes and enhanced physical lightning channels.
 *
 * <p>Intra-cloud flashes come from {@link StormLightningField}, which rolls candidates on a world
 * cell grid inside a bounded neighbourhood of the camera. That is the change that makes lightning
 * actually visible: the previous implementation drew one candidate for the whole 768 block storm,
 * so nearly every flash happened somewhere the player was not looking.</p>
 *
 * <p>The glow is drawn through {@link StormRenderTypes#glow}, an additive Blaze3D render type, so a
 * flash brightens the cloud mass in front of it instead of pasting an opaque sprite over it. The
 * same field also feeds {@link StormCloudRenderer}, which raises the cloud vertex brightness near a
 * live flash, so the deck itself lights up from within.</p>
 *
 * <p>Sparse server-authoritative damaging strikes are unchanged on the wire; only their client
 * channel and near flash are enhanced here.</p>
 */
public final class StormLightningRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        StormWeaponMod.MOD_ID, "textures/environment/storm_rain_streak.png"
    );
    private static final List<PhysicalStrike> PHYSICAL = new ArrayList<>();

    /** Distance in blocks beyond which a physical bolt is no longer worth drawing. */
    private static final double PHYSICAL_DRAW_DISTANCE = 1152.0D;

    private record PhysicalStrike(double x, double y, double z, long seed, float strength, long startTime) {}

    private final StormGeometryBatch batch = new StormGeometryBatch(1 << 18);
    private float flashStrength;
    private int lastCloudFlashes;
    private int lastPhysicalBolts;
    private int vertices;

    public static void acceptPhysicalStrike(StormLightningPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        PHYSICAL.add(new PhysicalStrike(packet.x(), packet.y(), packet.z(), packet.seed(), packet.strength(), level.getGameTime()));
        if (PHYSICAL.size() > StormLightningBudget.ULTRA.maxActiveBolts() * 2) {
            PHYSICAL.remove(0);
        }
    }

    public static void clearEvents() {
        PHYSICAL.clear();
        StormLightningField.clear();
    }

    public float flashStrength() {
        return this.flashStrength;
    }

    public int lastCloudFlashes() {
        return this.lastCloudFlashes;
    }

    public int lastPhysicalBolts() {
        return this.lastPhysicalBolts;
    }

    /**
     * Refreshes the deterministic flash field for this frame. {@link StormWeatherPass} calls this
     * before the cloud pass so the deck can already be lit by the flashes drawn afterwards.
     */
    public void updateField(LevelRenderState state, float partialTick) {
        StormSnapshot snapshot = StormClientManager.thunderSnapshot();
        Vec3 camera = state.cameraRenderState.pos;
        StormLightningField.update(
            snapshot, camera.x, camera.z, state.gameTime, partialTick,
            StormLightningBudget.of(StormConfig.quality()), StormCloudRenderer.BASE_HEIGHT
        );
    }

    public void render(LevelRenderState state, float partialTick) {
        this.flashStrength = 0.0F;
        this.lastCloudFlashes = 0;
        this.lastPhysicalBolts = 0;
        this.vertices = 0;
        long gameTime = state.gameTime;
        Vec3 camera = state.cameraRenderState.pos;
        StormSnapshot snapshot = StormClientManager.thunderSnapshot();
        StormLightningBudget budget = StormLightningBudget.of(StormConfig.quality());
        RenderType renderType = StormRenderTypes.glow(TEXTURE);
        VertexConsumer consumer = this.batch.buffer(renderType);

        renderCloudFlashes(consumer, snapshot, camera, budget);
        renderPhysicalStrikes(consumer, camera, gameTime, partialTick, budget);

        this.batch.flush();
        this.flashStrength = Math.min(this.flashStrength, 1.0F) * (float) StormConfig.lightningFlash();
    }

    private void renderCloudFlashes(
        VertexConsumer consumer, StormSnapshot snapshot, Vec3 camera, StormLightningBudget budget
    ) {
        List<StormLightningField.Flash> flashes = StormLightningField.flashes();
        if (flashes.isEmpty()) {
            return;
        }
        float cameraInfluence = snapshot.radialInfluence(camera.x, camera.z);

        for (int index = 0; index < flashes.size(); index++) {
            if (this.vertices >= budget.maxVertices()) {
                break;
            }
            StormLightningField.Flash flash = flashes.get(index);
            float pulse = flash.pulse();

            // Diffuse glow: a few concentric horizontal quads under the cloud base. Additive
            // blending means these read as the cloud itself lighting up.
            int rings = Math.max(1, budget.glowRings());
            for (int ring = rings - 1; ring >= 0 && this.vertices + 4 <= budget.maxVertices(); ring--) {
                float scale = 1.0F + ring * 0.85F;
                float ringAlpha = pulse * flash.influence() / (1.0F + ring * 1.35F);
                int alpha = Mth.clamp(Math.round(155.0F * ringAlpha), 0, 235);
                if (alpha <= 2) {
                    continue;
                }
                emitHorizontalQuad(
                    consumer, camera, flash.x(), flash.y() + ring * 0.4D, flash.z(),
                    flash.size() * scale, pack(150, 190, 255, alpha)
                );
            }

            // Only the nearest few flashes get a channel; the rest stay as diffuse sheet lightning,
            // which is both cheaper and closer to how distant intra-cloud lightning actually looks.
            if (index < budget.detailedFlashes()) {
                renderCloudChannel(consumer, flash, camera, budget);
            }
            this.lastCloudFlashes++;

            double dx = flash.x() - camera.x;
            double dz = flash.z() - camera.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            float proximity = 1.0F - Mth.clamp((float)(distance / Math.max(1, budget.flashRadius())), 0.0F, 1.0F);
            this.flashStrength = Math.max(this.flashStrength, pulse * proximity * cameraInfluence * 0.30F);
        }
    }

    /** Short branching channel trapped inside the deck, which is what reads as intra-cloud lightning. */
    private void renderCloudChannel(
        VertexConsumer consumer, StormLightningField.Flash flash, Vec3 camera, StormLightningBudget budget
    ) {
        float size = flash.size();
        int segments = Math.min(8, budget.maxBoltSegments());
        Vec3 previous = new Vec3(flash.x() - size * 0.5D, flash.y() + 3.0D, flash.z());
        for (int i = 1; i <= segments && this.vertices + 8 <= budget.maxVertices(); i++) {
            double t = i / (double)segments;
            double jitterX = hashSigned(flash.key(), i * 31L) * size * 0.14D;
            double jitterY = hashSigned(flash.key(), i * 47L) * 4.5D;
            double jitterZ = hashSigned(flash.key(), i * 59L) * size * 0.20D;
            Vec3 next = new Vec3(
                flash.x() - size * 0.5D + size * t + jitterX,
                flash.y() + 3.0D + jitterY,
                flash.z() + jitterZ
            );
            emitRibbon(consumer, camera, previous, next, 0.46F, pack(150, 190, 255, 150), flash.pulse());
            emitRibbon(consumer, camera, previous, next, 0.14F, pack(245, 250, 255, 245), flash.pulse());
            previous = next;
        }
    }

    private void renderPhysicalStrikes(
        VertexConsumer consumer, Vec3 camera, long gameTime, float partialTick, StormLightningBudget budget
    ) {
        Iterator<PhysicalStrike> iterator = PHYSICAL.iterator();
        while (iterator.hasNext()) {
            PhysicalStrike strike = iterator.next();
            float age = gameTime + partialTick - strike.startTime;
            if (age > 12.0F) {
                iterator.remove();
                continue;
            }
            if (this.lastPhysicalBolts >= budget.maxActiveBolts() || this.vertices >= budget.maxVertices()) {
                continue;
            }
            double dx = strike.x - camera.x;
            double dz = strike.z - camera.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > PHYSICAL_DRAW_DISTANCE) {
                continue;
            }
            float pulse = pulse(age);
            if (pulse <= 0.01F) {
                continue;
            }
            renderPhysicalBolt(consumer, strike, camera, pulse, budget);
            this.lastPhysicalBolts++;
            float proximity = 1.0F - Mth.clamp((float)(distance / 320.0D), 0.0F, 1.0F);
            this.flashStrength = Math.max(this.flashStrength, pulse * proximity * strike.strength);
        }
    }

    private void renderPhysicalBolt(
        VertexConsumer consumer, PhysicalStrike strike, Vec3 camera, float pulse, StormLightningBudget budget
    ) {
        int segments = budget.maxBoltSegments();
        double topY = Math.max(StormCloudRenderer.BASE_HEIGHT + 12.0D, strike.y + 110.0D);

        // A wide bloom where the channel meets the cloud base sells the strike as coming out of the
        // deck rather than starting in clear air.
        for (int ring = 2; ring >= 0 && this.vertices + 4 <= budget.maxVertices(); ring--) {
            int alpha = Mth.clamp(Math.round(140.0F * pulse * strike.strength / (ring + 1.0F)), 0, 230);
            if (alpha <= 2) {
                continue;
            }
            emitHorizontalQuad(
                consumer, camera, strike.x, StormCloudRenderer.BASE_HEIGHT - 2.0D + ring * 0.4D, strike.z,
                30.0F + ring * 26.0F, pack(160, 200, 255, alpha)
            );
        }

        Vec3 previous = new Vec3(strike.x, topY, strike.z);
        Vec3[] points = new Vec3[segments + 1];
        points[0] = previous;
        for (int i = 1; i <= segments && this.vertices + 8 <= budget.maxVertices(); i++) {
            double t = i / (double)segments;
            double taper = Math.sin(Math.PI * t);
            double jitter = 10.0D * taper;
            Vec3 next = new Vec3(
                strike.x + hashSigned(strike.seed, i * 73L) * jitter,
                Mth.lerp(t, topY, strike.y),
                strike.z + hashSigned(strike.seed, i * 97L) * jitter
            );
            points[i] = next;
            emitRibbon(consumer, camera, previous, next, 0.78F, pack(105, 165, 255, 165), pulse);
            emitRibbon(consumer, camera, previous, next, 0.24F, pack(250, 252, 255, 255), pulse);
            previous = next;
        }

        int branches = Math.min(budget.maxBranches(), 3);
        for (int branch = 0; branch < branches; branch++) {
            int startIndex = 2 + Math.floorMod((int)(strike.seed >>> (branch * 8)), Math.max(1, segments - 4));
            if (startIndex >= points.length) {
                continue;
            }
            Vec3 start = points[startIndex];
            if (start == null) {
                continue;
            }
            Vec3 branchPrevious = start;
            int branchSegments = budget.maxBranchSegments();
            for (int i = 1; i <= branchSegments && this.vertices + 4 <= budget.maxVertices(); i++) {
                double t = i / (double)branchSegments;
                double direction = hashSigned(strike.seed, 1200L + branch * 11L) * 22.0D;
                Vec3 next = new Vec3(
                    start.x + direction * t + hashSigned(strike.seed, 1400L + branch * 31L + i) * 3.0D,
                    start.y - 15.0D * t,
                    start.z + hashSigned(strike.seed, 1600L + branch * 37L) * 22.0D * t
                );
                emitRibbon(consumer, camera, branchPrevious, next, 0.14F, pack(195, 220, 255, 210), (float)(pulse * (1.0D - t * 0.55D)));
                branchPrevious = next;
            }
        }
    }

    private void emitHorizontalQuad(VertexConsumer consumer, Vec3 camera, double x, double y, double z, float size, int color) {
        float x0 = (float)(x - camera.x - size);
        float x1 = (float)(x - camera.x + size);
        float yy = (float)(y - camera.y);
        float z0 = (float)(z - camera.z - size);
        float z1 = (float)(z - camera.z + size);
        vertex(consumer, x0, yy, z0, color, 0.0F, 0.0F);
        vertex(consumer, x0, yy, z1, color, 0.0F, 1.0F);
        vertex(consumer, x1, yy, z1, color, 1.0F, 1.0F);
        vertex(consumer, x1, yy, z0, color, 1.0F, 0.0F);
    }

    private void emitRibbon(VertexConsumer consumer, Vec3 camera, Vec3 from, Vec3 to, float width, int color, float pulse) {
        Vec3 line = to.subtract(from);
        Vec3 midpoint = from.add(to).scale(0.5D);
        Vec3 view = camera.subtract(midpoint);
        Vec3 side = line.cross(view);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        side = side.scale(width * pulse);
        Vec3 a = from.add(side).subtract(camera);
        Vec3 b = from.subtract(side).subtract(camera);
        Vec3 c = to.subtract(side).subtract(camera);
        Vec3 d = to.add(side).subtract(camera);
        vertex(consumer, (float)a.x, (float)a.y, (float)a.z, color, 0.0F, 0.0F);
        vertex(consumer, (float)b.x, (float)b.y, (float)b.z, color, 1.0F, 0.0F);
        vertex(consumer, (float)c.x, (float)c.y, (float)c.z, color, 1.0F, 1.0F);
        vertex(consumer, (float)d.x, (float)d.y, (float)d.z, color, 0.0F, 1.0F);
    }

    private void vertex(VertexConsumer consumer, float x, float y, float z, int color, float u, float v) {
        consumer.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F, 0.0F);
        this.vertices++;
    }

    private static float pulse(float age) {
        if (age < 2.2F) return 1.0F - age / 2.8F;
        if (age >= 4.5F && age < 7.0F) return 0.72F * (1.0F - (age - 4.5F) / 2.5F);
        return 0.0F;
    }

    private static double hashSigned(long seed, long salt) {
        return StormWindField.hashUnit(seed, salt) * 2.0D - 1.0D;
    }

    private static int pack(int red, int green, int blue, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (red << 16) | (green << 8) | blue;
    }
}
