package com.stormweapon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stormweapon.block.WeatherLauncherBlock;
import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.storm.MissileKind;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Oversized industrial launcher assembled from backend-agnostic submitted geometry. The logical
 * block stays compact, while the visible installation has a turntable, armor, hydraulic elevation
 * arms, twin rails, retaining hoops and a full 6.8-block missile when loaded.
 */
public final class WeatherLauncherRenderer implements BlockEntityRenderer<WeatherLauncherBlockEntity, WeatherLauncherRenderState> {
    /** One quarter of a model pixel prevents the foundation from sharing the ground depth plane. */
    private static final double FOUNDATION_CLEARANCE = 0.015625D;
    private static final Identifier METAL = Identifier.withDefaultNamespace("textures/block/iron_block.png");
    private static final Identifier MISSILE_SKIN = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final Vec3 X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 Y = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 Z = new Vec3(0.0D, 0.0D, 1.0D);
    private static final double ELEVATION = Math.toRadians(75.0D);
    private static final Vec3 RAIL_FORWARD = new Vec3(0.0D, Math.sin(ELEVATION), Math.cos(ELEVATION));
    private static final Vec3 RAIL_UP = new Vec3(0.0D, Math.cos(ELEVATION), -Math.sin(ELEVATION));

    public WeatherLauncherRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public WeatherLauncherRenderState createRenderState() {
        return new WeatherLauncherRenderState();
    }

    @Override
    public void extractRenderState(
        WeatherLauncherBlockEntity blockEntity,
        WeatherLauncherRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(WeatherLauncherBlock.FACING);
        state.hasMissile = blockEntity.hasMissile();
        state.missileKind = blockEntity.missileKind();
    }

    @Override
    public void submit(WeatherLauncherRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.facing.toYRot()));
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(METAL),
            (pose, vertices) -> renderAssembly(pose, vertices, light));
        if (state.hasMissile) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(MISSILE_SKIN),
                (pose, vertices) -> renderMountedMissile(
                    pose, vertices,
                    // Move forward along the 75-degree rail to lift the engine clear of the lower
                    // block, while reducing the normal offset so the body rests on both guide rails
                    // instead of hovering above them.
                    new Vec3(0.0D, 1.18D, -0.12D).add(RAIL_FORWARD.scale(-0.94D)).add(RAIL_UP.scale(0.645D)),
                    light, state.missileKind
                ));
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    private static void renderAssembly(PoseStack.Pose pose, VertexConsumer out, int light) {
        // Broad poured foundation, corner feet and armored rotating machinery enclosure.
        box(pose, out, new Vec3(0.0D, 0.14D + FOUNDATION_CLEARANCE, 0.0D), X, Y, Z, 1.65D, 0.14D, 1.18D, rgb(54, 59, 62), light);
        for (double sx : new double[]{-1.38D, 1.38D}) {
            for (double sz : new double[]{-0.92D, 0.92D}) {
                box(pose, out, new Vec3(sx, 0.08D + FOUNDATION_CLEARANCE, sz), X, Y, Z, 0.25D, 0.08D, 0.25D, rgb(35, 39, 41), light);
            }
        }
        cylinder(pose, out, new Vec3(0.0D, 0.27D, 0.0D), Y, 0.88D, 0.24D, 20, rgb(50, 57, 60), light);
        cylinder(pose, out, new Vec3(0.0D, 0.49D, 0.0D), Y, 0.68D, 0.22D, 20, rgb(86, 91, 91), light);
        box(pose, out, new Vec3(0.0D, 0.68D, -0.16D), X, Y, Z, 0.72D, 0.20D, 0.58D, rgb(76, 82, 81), light);

        // Side armor and a small industrial control cabinet with a warning face.
        box(pose, out, new Vec3(-1.02D, 0.48D, -0.24D), X, Y, Z, 0.34D, 0.34D, 0.62D, rgb(72, 77, 75), light);
        box(pose, out, new Vec3(1.03D, 0.55D, 0.34D), X, Y, Z, 0.30D, 0.48D, 0.38D, rgb(65, 70, 70), light);
        box(pose, out, new Vec3(1.03D, 0.73D, 0.735D), X, Y, Z, 0.22D, 0.19D, 0.025D, rgb(218, 156, 31), light);

        // Twin elevation trunnions and hydraulic rams actually connect the base to the rail.
        cylinder(pose, out, new Vec3(-0.73D, 0.64D, -0.10D), X, 0.22D, 1.46D, 14, rgb(42, 47, 48), light);
        Vec3 railPivot = new Vec3(0.0D, 1.18D, -0.12D);
        cylinderBetween(pose, out, new Vec3(-0.56D, 0.48D, -0.72D), railPivot.add(-0.56D, 0.0D, 0.0D).add(RAIL_FORWARD.scale(1.15D)), 0.105D, 12, rgb(122, 128, 126), light);
        cylinderBetween(pose, out, new Vec3(0.56D, 0.48D, -0.72D), railPivot.add(0.56D, 0.0D, 0.0D).add(RAIL_FORWARD.scale(1.15D)), 0.105D, 12, rgb(122, 128, 126), light);
        cylinderBetween(pose, out, new Vec3(-0.56D, 0.48D, -0.72D), railPivot.add(-0.56D, 0.0D, 0.0D).add(RAIL_FORWARD.scale(0.42D)), 0.06D, 10, rgb(41, 45, 46), light);
        cylinderBetween(pose, out, new Vec3(0.56D, 0.48D, -0.72D), railPivot.add(0.56D, 0.0D, 0.0D).add(RAIL_FORWARD.scale(0.42D)), 0.06D, 10, rgb(41, 45, 46), light);

        // A long central launch beam and two raised guide rails, close to the silhouette in ref.jpg.
        Vec3 railCenter = railPivot.add(RAIL_FORWARD.scale(0.80D));
        orientedBox(pose, out, railCenter, X, RAIL_UP, RAIL_FORWARD, 0.76D, 0.10D, 2.62D, rgb(57, 63, 64), light);
        for (double side : new double[]{-0.58D, 0.58D}) {
            orientedBox(pose, out, railCenter.add(X.scale(side)).add(RAIL_UP.scale(0.16D)), X, RAIL_UP, RAIL_FORWARD,
                0.075D, 0.095D, 2.68D, rgb(135, 139, 135), light);
        }
        for (double along : new double[]{-1.68D, -0.55D, 0.65D, 1.80D}) {
            orientedBox(pose, out, railCenter.add(RAIL_FORWARD.scale(along)).add(RAIL_UP.scale(0.10D)),
                X, RAIL_UP, RAIL_FORWARD, 0.70D, 0.07D, 0.10D, rgb(43, 48, 49), light);
        }

    }

    private static void renderMountedMissile(PoseStack.Pose pose, VertexConsumer out, Vec3 tail, int light, MissileKind kind) {
        // Full-size, round 24-sided missile: engine bell, body, structure rings, segmented nose and fins.
        // Every payload reuses the exact same geometry and differs only by tint. These palettes
        // are kept identical to the in-flight ones in StormWeaponEffectsRenderer.renderMissile:
        // when only the fog flag was carried here, a meteor missile sat white on the rail and then
        // turned black the instant it launched.
        int engineColor, bodyColor, ring1Color, ring2Color, noseColor, finColor;
        switch (kind) {
            case FOG -> {
                engineColor = rgb(70, 62, 30); bodyColor = rgb(232, 214, 128);
                ring1Color = rgb(96, 84, 44); ring2Color = rgb(100, 88, 46);
                noseColor = rgb(110, 96, 52); finColor = rgb(130, 112, 58);
            }
            case METEOR -> {
                engineColor = rgb(12, 12, 14); bodyColor = rgb(34, 34, 38);
                ring1Color = rgb(18, 18, 21); ring2Color = rgb(20, 20, 23);
                noseColor = rgb(26, 26, 30); finColor = rgb(46, 46, 51);
            }
            case BLIZZARD -> {
                engineColor = rgb(170, 186, 198); bodyColor = rgb(242, 248, 252);
                ring1Color = rgb(180, 205, 220); ring2Color = rgb(194, 220, 234);
                noseColor = rgb(214, 232, 242); finColor = rgb(226, 240, 248);
            }
            case CHERRY -> {
                engineColor = rgb(120, 54, 78); bodyColor = rgb(248, 170, 202);
                ring1Color = rgb(180, 82, 122); ring2Color = rgb(198, 96, 140);
                noseColor = rgb(228, 126, 168); finColor = rgb(238, 146, 184);
            }
            default -> {
                engineColor = rgb(43, 47, 50); bodyColor = rgb(215, 218, 211);
                ring1Color = rgb(61, 67, 70); ring2Color = rgb(64, 70, 72);
                noseColor = rgb(72, 78, 81); finColor = rgb(96, 101, 102);
            }
        }

        cylinder(pose, out, tail, RAIL_FORWARD, 0.29D, 0.34D, 20, engineColor, light);
        cylinder(pose, out, tail.add(RAIL_FORWARD.scale(0.28D)), RAIL_FORWARD, 0.39D, 4.94D, 24, bodyColor, light);
        cylinder(pose, out, tail.add(RAIL_FORWARD.scale(1.08D)), RAIL_FORWARD, 0.415D, 0.16D, 24, ring1Color, light);
        cylinder(pose, out, tail.add(RAIL_FORWARD.scale(3.72D)), RAIL_FORWARD, 0.415D, 0.18D, 24, ring2Color, light);
        // One continuous nose cone. The former second cone touched this one at only a single
        // vertex, which rasterized as a detached floating spike from oblique viewpoints.
        cone(pose, out, tail.add(RAIL_FORWARD.scale(5.22D)), RAIL_FORWARD, 0.39D, 1.58D, 24, noseColor, light);

        for (int fin = 0; fin < 4; fin++) {
            // A 45-degree clocking puts the two lower fins outside the twin rails instead of
            // driving a single vertical fin through the central launch beam.
            double a = Math.PI * 0.5D * fin + Math.PI * 0.25D;
            Vec3 radial = X.scale(Math.cos(a)).add(RAIL_UP.scale(Math.sin(a)));
            Vec3 tangent = RAIL_FORWARD.cross(radial).normalize();
            fin(pose, out, tail.add(RAIL_FORWARD.scale(0.42D)), RAIL_FORWARD, radial, tangent, finColor, light);
        }
    }

    private static void fin(PoseStack.Pose pose, VertexConsumer out, Vec3 tail, Vec3 forward, Vec3 radial, Vec3 tangent, int color, int light) {
        Vec3 a = tail.add(radial.scale(0.28D));
        Vec3 b = tail.add(radial.scale(1.05D)).add(forward.scale(0.25D));
        Vec3 c = tail.add(radial.scale(0.82D)).add(forward.scale(1.38D));
        Vec3 d = tail.add(radial.scale(0.25D)).add(forward.scale(1.05D));
        double thickness = 0.045D;
        Vec3 t = tangent.scale(thickness);
        quad(pose, out, a.add(t), b.add(t), c.add(t), d.add(t), tangent, color, light);
        quad(pose, out, d.subtract(t), c.subtract(t), b.subtract(t), a.subtract(t), tangent.scale(-1.0D), color, light);
        quad(pose, out, a.subtract(t), b.subtract(t), b.add(t), a.add(t), radial, color, light);
        quad(pose, out, b.subtract(t), c.subtract(t), c.add(t), b.add(t), radial, color, light);
        quad(pose, out, c.subtract(t), d.subtract(t), d.add(t), c.add(t), radial, color, light);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer out, Vec3 center, Vec3 ax, Vec3 ay, Vec3 az,
                            double hx, double hy, double hz, int color, int light) {
        orientedBox(pose, out, center, ax, ay, az, hx, hy, hz, color, light);
    }

    private static void orientedBox(PoseStack.Pose pose, VertexConsumer out, Vec3 center, Vec3 ax, Vec3 ay, Vec3 az,
                                    double hx, double hy, double hz, int color, int light) {
        Vec3 x = ax.normalize().scale(hx), y = ay.normalize().scale(hy), z = az.normalize().scale(hz);
        Vec3 p000 = center.subtract(x).subtract(y).subtract(z), p001 = center.subtract(x).subtract(y).add(z);
        Vec3 p010 = center.subtract(x).add(y).subtract(z), p011 = center.subtract(x).add(y).add(z);
        Vec3 p100 = center.add(x).subtract(y).subtract(z), p101 = center.add(x).subtract(y).add(z);
        Vec3 p110 = center.add(x).add(y).subtract(z), p111 = center.add(x).add(y).add(z);
        quad(pose, out, p100, p101, p111, p110, ax, color, light);
        quad(pose, out, p001, p000, p010, p011, ax.scale(-1.0D), color, light);
        quad(pose, out, p010, p110, p111, p011, ay, color, light);
        quad(pose, out, p000, p001, p101, p100, ay.scale(-1.0D), color, light);
        quad(pose, out, p001, p011, p111, p101, az, color, light);
        quad(pose, out, p000, p100, p110, p010, az.scale(-1.0D), color, light);
    }

    private static void cylinderBetween(PoseStack.Pose pose, VertexConsumer out, Vec3 from, Vec3 to, double radius, int segments, int color, int light) {
        Vec3 delta = to.subtract(from);
        cylinder(pose, out, from, delta.normalize(), radius, delta.length(), segments, color, light);
    }

    private static void cylinder(PoseStack.Pose pose, VertexConsumer out, Vec3 base, Vec3 axis, double radius, double length, int segments, int color, int light) {
        Vec3 f = axis.normalize();
        Vec3 side = Math.abs(f.y) < 0.95D ? f.cross(Y).normalize() : X;
        Vec3 up = side.cross(f).normalize();
        Vec3 end = base.add(f.scale(length));
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 n0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 n1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            quad(pose, out, base.add(n0.scale(radius)), base.add(n1.scale(radius)), end.add(n1.scale(radius)), end.add(n0.scale(radius)), n0.add(n1).normalize(), color, light);
            quad(pose, out, base, base.add(n1.scale(radius)), base.add(n0.scale(radius)), base, f.scale(-1.0D), color, light);
            quad(pose, out, end, end.add(n0.scale(radius)), end.add(n1.scale(radius)), end, f, color, light);
        }
    }

    private static void cone(PoseStack.Pose pose, VertexConsumer out, Vec3 base, Vec3 axis, double radius, double length, int segments, int color, int light) {
        Vec3 f = axis.normalize(), side = Math.abs(f.y) < 0.95D ? f.cross(Y).normalize() : X, up = side.cross(f).normalize();
        Vec3 tip = base.add(f.scale(length));
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 n0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 n1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            quad(pose, out, base.add(n0.scale(radius)), base.add(n1.scale(radius)), tip, tip, n0.add(n1).add(f.scale(radius / length)).normalize(), color, light);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color, int light) {
        vertex(pose, out, a, normal, color, 0.0F, 0.0F, light);
        vertex(pose, out, b, normal, color, 1.0F, 0.0F, light);
        vertex(pose, out, c, normal, color, 1.0F, 1.0F, light);
        vertex(pose, out, d, normal, color, 0.0F, 1.0F, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vec3 n, int color, float u, float v, int light) {
        Vector3f position = pose.pose().transformPosition((float)p.x, (float)p.y, (float)p.z, new Vector3f());
        Vector3f normal = pose.transformNormal((float)n.x, (float)n.y, (float)n.z, new Vector3f());
        out.addVertex(position.x, position.y, position.z, color, u, v, OverlayTexture.NO_OVERLAY, light, normal.x, normal.y, normal.z);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
