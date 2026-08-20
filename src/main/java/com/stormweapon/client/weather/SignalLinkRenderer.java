package com.stormweapon.client.weather;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.item.SignalConnectorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the signal connector's wiring while that tool is held: a white beam from each linked
 * launcher to its paired button/lever, plus a highlighted box around a half-finished selection so
 * the player can tell which endpoint they already picked.
 *
 * <p>Link data needs no network code of its own: it lives on the launcher's block entity, which
 * already syncs to clients through {@code getUpdateTag}, and the pending selection lives on the
 * connector ItemStack, which syncs as part of the player's inventory. Loaded launchers are found by
 * walking the block entities of the chunks around the player, since the client has no global block
 * entity index.</p>
 */
public final class SignalLinkRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
    /** Chunk radius scanned for launchers; comfortably beyond any practical wiring distance. */
    private static final int CHUNK_RADIUS = 8;
    private static final double BEAM_THICKNESS = 0.05D;
    private static final int LINK_COLOR = 0xFFFFFFFF;
    /** Deliberately not white, so a pending pick is never mistaken for a finished link. */
    private static final int PENDING_COLOR = 0xFF45E0FF;

    private final StormGeometryBatch batch = new StormGeometryBatch(1 << 15);

    public void render(LevelRenderState state, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !SignalConnectorItem.isHeldBy(player)) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        VertexConsumer consumer = batch.buffer(StormRenderTypes.soft(TEXTURE));
        boolean submitted = false;

        int centerX = player.blockPosition().getX() >> 4;
        int centerZ = player.blockPosition().getZ() >> 4;
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                LevelChunk chunk = level.getChunk(centerX + dx, centerZ + dz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof WeatherLauncherBlockEntity launcher)) {
                        continue;
                    }
                    BlockPos signal = launcher.linkedSignalPos();
                    if (signal == null) {
                        continue;
                    }
                    emitBeam(consumer, launcherAnchor(launcher.getBlockPos()).subtract(camera),
                        center(signal).subtract(camera), LINK_COLOR);
                    submitted = true;
                }
            }
        }

        ItemStack connector = SignalConnectorItem.heldBy(player);
        BlockPos pending = connector.isEmpty() ? null : SignalConnectorItem.anchor(connector);
        if (pending != null) {
            emitBox(consumer, center(pending).subtract(camera), 0.58D, PENDING_COLOR);
            submitted = true;
        }
        if (submitted) {
            batch.flush();
        }
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /** Raised above the block origin so the beam leaves the launcher's tall model, not its base. */
    private static Vec3 launcherAnchor(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D);
    }

    /**
     * A beam is two perpendicular quads through the same axis rather than a tube: it costs four
     * triangles instead of dozens and, with culling disabled on this render type, still reads as a
     * solid line from every viewing angle.
     */
    private void emitBeam(VertexConsumer c, Vec3 from, Vec3 to, int color) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1.0E-6D) {
            return;
        }
        axis = axis.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = axis.cross(reference).normalize().scale(BEAM_THICKNESS);
        Vec3 up = axis.cross(side).normalize().scale(BEAM_THICKNESS);
        quad(c, from.subtract(side), from.add(side), to.add(side), to.subtract(side), color);
        quad(c, from.subtract(up), from.add(up), to.add(up), to.subtract(up), color);
    }

    /** The twelve edges of a cube centred on {@code center}, drawn as beams. */
    private void emitBox(VertexConsumer c, Vec3 center, double half, int color) {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = center.add(
                (i & 1) == 0 ? -half : half,
                (i & 2) == 0 ? -half : half,
                (i & 4) == 0 ? -half : half);
        }
        // Corner indices differing in exactly one bit share an edge.
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i | bit;
                if (j != i) {
                    emitBeam(c, corners[i], corners[j], color);
                }
            }
        }
    }

    private void quad(VertexConsumer c, Vec3 a, Vec3 b, Vec3 d, Vec3 e, int color) {
        vertex(c, a, color, 0, 0);
        vertex(c, b, color, 1, 0);
        vertex(c, d, color, 1, 1);
        vertex(c, e, color, 0, 1);
    }

    private void vertex(VertexConsumer c, Vec3 p, int color, float u, float v) {
        c.addVertex((float)p.x, (float)p.y, (float)p.z, color, u, v,
            OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, 0, 1, 0);
    }
}
