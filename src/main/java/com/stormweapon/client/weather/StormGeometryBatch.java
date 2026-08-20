package com.stormweapon.client.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal immediate-mode batcher for storm geometry.
 *
 * <p>Minecraft 26.2 removed {@code MultiBufferSource}, so world geometry is built into a
 * {@link StagedVertexBuffer} and replayed through {@link PreparedRenderType}. This mirrors the
 * exact path vanilla's own {@code RenderTypeFeatureRenderer} uses, which keeps the mod on the
 * Blaze3D abstraction layer: no GL types, no raw shaders, no framebuffer handling.</p>
 *
 * <p>The batch owns one staged buffer that is reused for the lifetime of the game and recycled
 * after every flush.</p>
 */
public final class StormGeometryBatch {
    private static final int DEFAULT_CAPACITY = 262144;

    private final StagedVertexBuffer buffer;
    private final List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
    private final List<PreparedRenderType> prepared = new ArrayList<>();
    private RenderType currentType;
    private StagedVertexBuffer.Draw currentDraw;

    public StormGeometryBatch() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * @param initialCapacity initial staging size in bytes; the underlying builder still grows on
     *                        demand, so this only avoids reallocation for known-large batches
     */
    public StormGeometryBatch(int initialCapacity) {
        this.buffer = new StagedVertexBuffer(() -> "Storm Weapon Geometry", initialCapacity);
    }

    public VertexConsumer buffer(RenderType renderType) {
        if (this.currentDraw == null || this.currentType != renderType || !renderType.canConsolidateConsecutiveGeometry()) {
            this.currentDraw = appendDraw(renderType);
            this.currentType = renderType;
        }
        return this.buffer.getVertexBuilder(this.currentDraw);
    }

    private StagedVertexBuffer.Draw appendDraw(RenderType renderType) {
        PreparedRenderType preparedType = renderType.prepare();
        int existing = renderType.canConsolidateConsecutiveGeometry() ? this.prepared.indexOf(preparedType) : -1;
        if (existing != -1) {
            return this.draws.get(existing);
        }
        VertexSorting sorting = renderType.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null;
        StagedVertexBuffer.Draw draw = this.buffer.appendDraw(renderType.format(), renderType.primitiveTopology(), sorting);
        this.draws.add(draw);
        this.prepared.add(preparedType);
        return draw;
    }

    /** Uploads and draws everything queued since the last flush, then recycles the buffers. */
    public void flush() {
        this.currentDraw = null;
        this.currentType = null;
        if (this.draws.isEmpty()) {
            return;
        }
        try {
            this.buffer.upload();
            for (int i = 0; i < this.draws.size(); i++) {
                StagedVertexBuffer.ExecuteInfo info = this.buffer.getExecuteInfo(this.draws.get(i));
                if (info != null) {
                    this.prepared.get(i).drawFromBuffer(info);
                }
            }
        } finally {
            this.draws.clear();
            this.prepared.clear();
            this.buffer.endFrame();
        }
    }
}
