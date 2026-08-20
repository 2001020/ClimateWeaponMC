package com.stormweapon.client.weather;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.StormClientManager;
import com.stormweapon.config.StormConfig;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Regional multi-layer storm cloud field.
 *
 * <p>The field is a set of depth separated horizontal sheets centered on the synchronized storm
 * center. Each sheet is a grid of camera-culled tiles, but a tile is no longer a unit of
 * <em>appearance</em>: it is only a unit of culling and budgeting. Height, opacity, coverage and
 * tint are continuous {@link StormNoise} fields evaluated at every emitted <em>vertex</em>, and a
 * tile is subdivided so those fields are sampled several times across its span.</p>
 *
 * <p>That is the fix for the hard sheet boundaries the deck used to show. Previously a tile picked
 * one hashed height and one hashed opacity for its whole quad, so neighbouring tiles sat at
 * different altitudes and different alphas and the step between them was drawn as a hard rectangle
 * edge, which at an oblique upward angle stacked into the reported polygonal bands. Now two quads
 * that share an edge evaluate the same field at the same world position, using exact integer world
 * coordinates so the shared vertices are bit-identical, and the deck is continuous by construction
 * with no seam to see.</p>
 *
 * <p>Geometry is submitted through {@link StormGeometryBatch} using {@link StormRenderTypes}, i.e.
 * Blaze3D render types only. There is no OpenGL access, no custom shader and no framebuffer work
 * anywhere in this class.</p>
 */
public final class StormCloudRenderer {
    private static final Identifier UPPER_TEXTURE = texture("storm_cloud_upper");
    private static final Identifier MID_TEXTURE = texture("storm_cloud_mid");
    // Minecraft's own cloud coverage mask has real transparency. The previous original placeholder
    // textures were opaque RGB images, which turned every tile into a visible rectangular slab.
    // Shape comes from this mask; all storm colour, motion, height and density remain mod-authored.
    private static final Identifier BASE_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/environment/clouds.png"
    );
    private static final Identifier WASH_TEXTURE = texture("storm_sky_wash");

    /** Index of the high sky wash inside {@link #LAYERS}. */
    private static final int WASH_INDEX = 5;

    /** Altitude of the lowest storm deck; other systems anchor themselves to it. */
    public static final float BASE_HEIGHT = 170.0F;

    /** Radians per block of the coverage field; roughly a 380 block primary swell. */
    private static final double COVERAGE_FREQUENCY = 0.0165D;

    /** Radians per block of the height field; a much longer swell so the deck undulates gently. */
    private static final double SWELL_FREQUENCY = 0.0092D;

    /** Largest supported sub-quads per tile edge; sizes the reusable vertex sample grid. */
    private static final int MAX_SUBDIVISIONS = 4;

    /**
     * Storm sheets ordered from the lowest base upwards, all inside the Y 180-260 band, followed
     * by the sky wash that closes the remaining gaps overhead. LOW keeps only the base, MEDIUM adds
     * the middle deck, HIGH renders all three storm layers plus the wash, and ULTRA renders the
     * same set at higher density and distance.
     *
     * <p>Per-layer alpha is deliberately below one and the coverage field swings widely. Multiplied
     * by each cloud texture's own alpha, the four sheets composite to about 60% opacity where the
     * coverage field is thin and about 94% where it is dense, so the deck reads as a heavy but
     * churning overcast with structure in it. The previous values composited to over 99% almost
     * everywhere, and that flat ceiling is what the report described as a solid black overhead mass.
     */
    private static final Layer[] LAYERS = {
        // texture, height, heightVariation, uvScale, drift, rotation rad/s, alpha, coverage bias/gain, r, g, b
        // The vanilla mask is intentionally sampled at a much smaller world scale than before.
        // At 1/512 a single transparent texel group became a many-block hole; these scales make
        // separate rotated sheets interlock into a dense cloud deck while retaining movement.
        new Layer(BASE_TEXTURE, BASE_HEIGHT, 8.0F, 1.0F / 96.0F, 0.75F, 0.0035F, 0.95F, 0.88F, 0.12F, 10, 15, 27),
        new Layer(MID_TEXTURE, 184.0F, 10.0F, 1.0F / 112.0F, 0.92F, -0.0027F, 0.91F, 0.88F, 0.12F, 13, 19, 32),
        new Layer(MID_TEXTURE, 202.0F, 12.0F, 1.0F / 128.0F, 1.16F, 0.0022F, 0.86F, 0.87F, 0.13F, 17, 24, 39),
        new Layer(UPPER_TEXTURE, 220.0F, 14.0F, 1.0F / 144.0F, 1.32F, -0.0016F, 0.80F, 0.86F, 0.14F, 22, 30, 47),
        new Layer(UPPER_TEXTURE, 240.0F, 16.0F, 1.0F / 160.0F, 1.56F, 0.0011F, 0.74F, 0.85F, 0.15F, 28, 38, 57),
        // The wash texture is opaque by design: it is the continuous dark cloud base that seals
        // the last holes between the five moving silhouettes. Its geometry is still clipped to
        // the weapon circle and softly fades only across the final 12% of that circle.
        new Layer(WASH_TEXTURE, 258.0F, 7.0F, 1.0F / 192.0F, 0.40F, 0.0006F, 0.68F, 0.94F, 0.06F, 11, 17, 31)
    };

    private final StormGeometryBatch batch = new StormGeometryBatch(1 << 21);

    /** Reusable per-tile vertex sample grid; sized for the largest supported subdivision. */
    private final Sample[] samples = new Sample[(MAX_SUBDIVISIONS + 1) * (MAX_SUBDIVISIONS + 1)];

    private int lastTileCount;
    private int lastTileBudget;
    private int lastQuadCount;

    private record Layer(
        Identifier texture,
        float height,
        float heightVariation,
        float uvScale,
        float drift,
        float rotationRate,
        float alpha,
        float coverageBias,
        float coverageGain,
        int red,
        int green,
        int blue
    ) {}

    /** One evaluated cloud vertex: displaced height, packed colour and texture coordinates. */
    private static final class Sample {
        float y;
        float u;
        float v;
        int color;
        int alpha;
    }

    public StormCloudRenderer() {
        for (int i = 0; i < this.samples.length; i++) {
            this.samples[i] = new Sample();
        }
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "textures/environment/" + name + ".png");
    }

    public int lastTileCount() {
        return this.lastTileCount;
    }

    public int lastTileBudget() {
        return this.lastTileBudget;
    }

    public int lastQuadCount() {
        return this.lastQuadCount;
    }

    public void render(LevelRenderState state, float partialTick) {
        this.lastTileCount = 0;
        this.lastQuadCount = 0;
        // Thunder and fog decks are independent clouds centered on their own detonation points, so
        // each gets its own pass instead of being merged into one intensity/position.
        boolean rendered = renderDeck(state, partialTick, StormClientManager.thunderSnapshot(), StormClientManager.smoothedCloudIntensity());
        rendered |= renderDeck(state, partialTick, StormClientManager.fogSnapshot(), StormClientManager.smoothedFogCloudIntensity());
        if (rendered) {
            this.batch.flush();
        }
    }

    private boolean renderDeck(LevelRenderState state, float partialTick, StormSnapshot snapshot, float intensity) {
        if (!snapshot.active() || intensity <= 0.01F) {
            return false;
        }

        Vec3 camera = state.cameraRenderState.pos;
        long gameTime = state.gameTime;
        // Do not multiply the entire field by the observer's local influence. Doing so made the
        // whole cloud disc disappear as the player approached its rim even though most of the
        // weapon area was still in view. Geometry itself is clipped to the synchronized circle.

        StormCloudBudget budget = StormCloudBudget.of(StormConfig.quality());
        this.lastTileBudget = budget.maxTilesPerFrame();
        int[] selected = selectedLayers(budget);
        int tileAllowance = Math.max(8, budget.maxTilesPerFrame() / selected.length);
        int quadAllowance = Math.max(8, budget.maxVertices() / StormCloudBudget.VERTICES_PER_QUAD / selected.length);
        double seconds = (gameTime + partialTick) / 20.0D;

        // Translucent sheets are blended without depth writes, so they must be submitted from the
        // layer farthest from the camera to the nearest one.
        sortFarToNear(selected, camera.y);
        for (int index : selected) {
            Layer layer = LAYERS[index];
            int tileSize = budget.tileSize();
            renderLayer(
                snapshot, layer, camera, gameTime, partialTick, seconds, intensity,
                tileSize, budget, tileAllowance, quadAllowance
            );
        }
        return true;
    }

    /**
     * Storm sheets for the active preset: the requested number of storm layers counted up from
     * the base, plus the high sky wash.
     */
    private static int[] selectedLayers(StormCloudBudget budget) {
        int stormLayers = Mth.clamp(budget.layers(), 1, WASH_INDEX);
        int[] selected = new int[stormLayers + (budget.skyWash() ? 1 : 0)];
        for (int i = 0; i < stormLayers; i++) {
            selected[i] = i;
        }
        if (budget.skyWash()) {
            selected[stormLayers] = WASH_INDEX;
        }
        return selected;
    }

    /** Orders layer indices by decreasing vertical distance from the camera, in place. */
    private static void sortFarToNear(int[] indices, double cameraY) {
        for (int i = 1; i < indices.length; i++) {
            int current = indices[i];
            double currentDistance = Math.abs(LAYERS[current].height() - cameraY);
            int j = i - 1;
            while (j >= 0 && Math.abs(LAYERS[indices[j]].height() - cameraY) < currentDistance) {
                indices[j + 1] = indices[j];
                j--;
            }
            indices[j + 1] = current;
        }
    }

    private void renderLayer(
        StormSnapshot snapshot,
        Layer layer,
        Vec3 camera,
        long gameTime,
        float partialTick,
        double seconds,
        float intensity,
        int tileSize,
        StormCloudBudget budget,
        int tileAllowance,
        int quadAllowance
    ) {
        int subdivisions = Mth.clamp(budget.subdivisions(), 1, MAX_SUBDIVISIONS);
        // A tile is culled by its centre, and the half diagonal below keeps tiles that only clip
        // the disc, so the tiles actually walked are those centred inside radius + cullSlack.
        // Subtracting the slack from the budget radius rather than relying on a flat percentage of
        // headroom is what guarantees the per-layer cap is never reached: hitting it would abandon
        // the walk part way through a ring and leave a visible wedge missing from the deck edge.
        double cullSlack = tileSize * 0.7072D;
        float budgetRadius = (float)(tileSize * Math.sqrt(tileAllowance / (Math.PI * 1.10D)) - cullSlack);
        float radius = Math.max(tileSize, Math.min(budget.viewRadius(), budgetRadius));
        // The fade only bites in the outermost fifth of the disc; everything closer stays at full
        // strength so there is never a thinned patch or a bare strip directly overhead.
        float fadeStart = radius * 0.92F;

        float driftX = StormWindField.driftX(snapshot, gameTime, partialTick, layer.drift());
        float driftZ = StormWindField.driftZ(snapshot, gameTime, partialTick, layer.drift());
        float rotation = (float)(seconds * layer.rotationRate())
            + StormWindField.hashUnit(snapshot.seed(), 0x1234567L + layer.texture().hashCode()) * 6.2831853F;
        float rotationSin = Mth.sin(rotation);
        float rotationCos = Mth.cos(rotation);
        double coveragePhase = StormNoise.phase(snapshot.seed(), 0x51ED2701L + Float.floatToIntBits(layer.height()));
        double swellPhase = StormNoise.phase(snapshot.seed(), 0x27F4A3C9L + Float.floatToIntBits(layer.height()));
        boolean twoOctave = subdivisions > 1;

        double outerLimit = snapshot.coreRadius() + tileSize;
        int minI = Mth.floor((Math.max(camera.x - radius, snapshot.centerX() - outerLimit)) / tileSize);
        int maxI = Mth.ceil((Math.min(camera.x + radius, snapshot.centerX() + outerLimit)) / tileSize);
        int minJ = Mth.floor((Math.max(camera.z - radius, snapshot.centerZ() - outerLimit)) / tileSize);
        int maxJ = Mth.ceil((Math.min(camera.z + radius, snapshot.centerZ() + outerLimit)) / tileSize);

        // Moving silhouette layers use Minecraft's alpha cloud mask; the final wash uses the
        // mod's continuous dark texture so the complete weapon circle cannot develop sky holes.
        Identifier renderTexture = layer.texture().equals(WASH_TEXTURE) ? WASH_TEXTURE : BASE_TEXTURE;
        RenderType renderType = StormRenderTypes.soft(renderTexture);
        VertexConsumer consumer = this.batch.buffer(renderType);
        int tiles = 0;
        int quads = 0;
        int stride = subdivisions + 1;

        for (int i = minI; i <= maxI && tiles < tileAllowance && quads < quadAllowance; i++) {
            for (int j = minJ; j <= maxJ && tiles < tileAllowance && quads < quadAllowance; j++) {
                long tileX = (long)i * tileSize;
                long tileZ = (long)j * tileSize;
                double centerX = tileX + tileSize * 0.5D;
                double centerZ = tileZ + tileSize * 0.5D;

                double dx = centerX - camera.x;
                double dz = centerZ - camera.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance - cullSlack > radius) {
                    continue;
                }
                double stormDx = centerX - snapshot.centerX();
                double stormDz = centerZ - snapshot.centerZ();
                if (Math.sqrt(stormDx * stormDx + stormDz * stormDz) - cullSlack > snapshot.coreRadius()) {
                    continue;
                }

                // Evaluate the shared vertex grid once. Corner world coordinates are computed with
                // integer arithmetic, so the last column of one tile is the exact same world
                // coordinate as the first column of the next and the two evaluations agree bitwise.
                int visible = 0;
                for (int sx = 0; sx <= subdivisions; sx++) {
                    long worldX = tileX + (long)sx * tileSize / subdivisions;
                    for (int sz = 0; sz <= subdivisions; sz++) {
                        long worldZ = tileZ + (long)sz * tileSize / subdivisions;
                        Sample sample = this.samples[sx * stride + sz];
                        evaluate(
                            sample, snapshot, layer, worldX, worldZ, camera, intensity, radius, fadeStart,
                            driftX, driftZ, rotationSin, rotationCos, coveragePhase, swellPhase, twoOctave
                        );
                        if (sample.alpha > 0) {
                            visible++;
                        }
                    }
                }
                tiles++;
                if (visible == 0) {
                    continue;
                }

                for (int sx = 0; sx < subdivisions && quads < quadAllowance; sx++) {
                    long x0 = tileX + (long)sx * tileSize / subdivisions;
                    long x1 = tileX + (long)(sx + 1) * tileSize / subdivisions;
                    for (int sz = 0; sz < subdivisions && quads < quadAllowance; sz++) {
                        long z0 = tileZ + (long)sz * tileSize / subdivisions;
                        long z1 = tileZ + (long)(sz + 1) * tileSize / subdivisions;
                        Sample s00 = this.samples[sx * stride + sz];
                        Sample s01 = this.samples[sx * stride + sz + 1];
                        Sample s11 = this.samples[(sx + 1) * stride + sz + 1];
                        Sample s10 = this.samples[(sx + 1) * stride + sz];
                        if (s00.alpha == 0 && s01.alpha == 0 && s11.alpha == 0 && s10.alpha == 0) {
                            continue;
                        }
                        emit(consumer, camera, x0, z0, s00);
                        emit(consumer, camera, x0, z1, s01);
                        emit(consumer, camera, x1, z1, s11);
                        emit(consumer, camera, x1, z0, s10);
                        quads++;
                    }
                }
            }
        }
        this.lastTileCount += tiles;
        this.lastQuadCount += quads;
    }

    /**
     * Evaluates the continuous cloud field at one world position.
     *
     * <p>Coverage, height, tint and the texture coordinates all live in the same rotated and
     * drifted storm-local frame, so the deck's internal structure moves with the wind instead of
     * only the texture sliding underneath a static shape. That coupling is what makes the cloud
     * drift readable.</p>
     */
    private void evaluate(
        Sample out,
        StormSnapshot snapshot,
        Layer layer,
        long worldX,
        long worldZ,
        Vec3 camera,
        float intensity,
        float radius,
        float fadeStart,
        float driftX,
        float driftZ,
        float rotationSin,
        float rotationCos,
        double coveragePhase,
        double swellPhase,
        boolean twoOctave
    ) {
        double localX = worldX - snapshot.centerX();
        double localZ = worldZ - snapshot.centerZ();
        double flowX = localX * rotationCos - localZ * rotationSin - driftX;
        double flowZ = localX * rotationSin + localZ * rotationCos - driftZ;

        out.u = (float)(flowX * layer.uvScale());
        out.v = (float)(flowZ * layer.uvScale());

        float coverageNoise = twoOctave
            ? StormNoise.field2(flowX, flowZ, COVERAGE_FREQUENCY, coveragePhase)
            : StormNoise.field(flowX, flowZ, COVERAGE_FREQUENCY, coveragePhase);
        float swellNoise = StormNoise.field(flowX, flowZ, SWELL_FREQUENCY, swellPhase);
        out.y = layer.height() + (swellNoise - 0.5F) * 2.0F * layer.heightVariation();

        float influence = cloudAreaInfluence(snapshot, worldX, worldZ);
        double dx = worldX - camera.x;
        double dz = worldZ - camera.z;
        float distance = (float)Math.sqrt(dx * dx + dz * dz);
        float distanceFade = 1.0F - StormNoise.smoothstep(fadeStart, radius, distance);

        // Coverage has a deliberately high floor. Texture silhouettes provide the cloud shapes;
        // procedural noise now modulates density instead of cutting additional holes through it.
        float coverage = Mth.clamp(layer.coverageBias() + layer.coverageGain() * coverageNoise, 0.82F, 1.0F);
        float alpha = intensity * layer.alpha() * influence * distanceFade * coverage;
        int packedAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        out.alpha = packedAlpha;
        if (packedAlpha == 0) {
            out.color = 0;
            return;
        }

        // Denser cloud is darker and the storm core is darker than the rim, but both terms keep a
        // floor: the deck is a deep blue-gray overcast, never a black ceiling.
        float shading = 1.04F - 0.34F * coverageNoise - 0.18F * influence;
        float illumination = StormLightningField.illumination(worldX, worldZ);
        shading += illumination * 0.95F;
        shading = Mth.clamp(shading, 0.38F, 2.4F);

        int red = Mth.clamp(Math.round(layer.red() * shading), 7, 255);
        int green = Mth.clamp(Math.round(layer.green() * shading), 11, 255);
        int blue = Mth.clamp(Math.round(layer.blue() * shading), 20, 255);
        out.color = (packedAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Cloud-only regional envelope. The deck remains fully dense across most of the exact weapon
     * circle and fades in its final edge band. Gameplay intensity still uses
     * {@link StormSnapshot#radialInfluence(double, double)} and therefore remains graduated.
     */
    private static float cloudAreaInfluence(StormSnapshot snapshot, double x, double z) {
        double dx = x - snapshot.centerX();
        double dz = z - snapshot.centerZ();
        float distance = (float)Math.sqrt(dx * dx + dz * dz);
        float radius = Math.max(1.0F, snapshot.coreRadius());
        if (distance >= radius) {
            return 0.0F;
        }

        float edge = 1.0F - StormNoise.smoothstep(radius * 0.88F, radius, distance);
        if (snapshot.waveMaxRadius() > 0.0F && snapshot.waveRadius() < snapshot.waveMaxRadius() - 0.5F) {
            float behindFront = Mth.clamp((snapshot.waveRadius() - distance) / 8.0F, 0.0F, 1.0F);
            edge *= behindFront;
        }
        return edge;
    }

    private void emit(VertexConsumer consumer, Vec3 camera, long worldX, long worldZ, Sample sample) {
        consumer.addVertex(
            (float)(worldX - camera.x),
            (float)(sample.y - camera.y),
            (float)(worldZ - camera.z),
            sample.color,
            sample.u,
            sample.v,
            OverlayTexture.NO_OVERLAY,
            LightCoordsUtil.FULL_BRIGHT,
            0.0F,
            1.0F,
            0.0F
        );
    }
}
