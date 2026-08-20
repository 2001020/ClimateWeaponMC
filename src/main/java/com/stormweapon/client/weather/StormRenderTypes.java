package com.stormweapon.client.weather;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.stormweapon.StormWeaponMod;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Render types for the storm weather layers.
 *
 * <p>Every vanilla entity, item and particle pipeline compiles its fragment shader with an
 * {@code ALPHA_CUTOUT} of 0.1, i.e. the shader discards any fragment whose final alpha falls below
 * that threshold. That is fatal for a soft weather deck: the discard threshold is crossed somewhere
 * inside every fading tile, and because the vertex alpha differs from one submission to the next the
 * discard boundary lands in a different place on each one. The result is exactly the hard sheet
 * seams and abrupt polygonal bands the storm deck used to show.</p>
 *
 * <p>These two pipelines therefore reuse the vanilla <em>beacon beam</em> shader pair, which is the
 * only textured, fog-aware, lightmap-free vanilla shader with no discard at all, and only override
 * blend mode, depth-stencil state and face culling. That is the same construction Forge itself uses
 * for its loading overlay pipeline, so it stays entirely inside the Blaze3D abstraction: no OpenGL
 * symbol, no GL constant, no hand written shader and no framebuffer handling, which keeps the mod
 * working on the native Vulkan backend as well as on OpenGL.</p>
 *
 * <p>Both pipelines disable depth writes so the stacked storm sheets blend into each other instead
 * of masking one another, and disable culling so a horizontal sheet is visible from below and from
 * above without duplicated geometry.</p>
 */
public final class StormRenderTypes {
    /** Vertex layout of both pipelines: position, colour, UV0 and an unused lightmap slot. */
    private static final RenderPipeline SOFT_PIPELINE = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
        .withLocation(pipelineId("storm_soft"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build();

    /** Additive variant used for lightning glow so a flash brightens the deck it sits under. */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
        .withLocation(pipelineId("storm_glow"))
        .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .build();

    private static final Function<Identifier, RenderType> SOFT = Util.memoize(
        texture -> RenderType.create(
            "stormweapon_soft",
            RenderSetup.builder(SOFT_PIPELINE).withTexture("Sampler0", texture).createRenderSetup()
        )
    );

    private static final Function<Identifier, RenderType> GLOW = Util.memoize(
        texture -> RenderType.create(
            "stormweapon_glow",
            RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", texture).createRenderSetup()
        )
    );

    private StormRenderTypes() {}

    /** Soft translucent, no alpha cutout, no depth write, no culling. */
    public static RenderType soft(Identifier texture) {
        return SOFT.apply(texture);
    }

    /** Additive glow, no alpha cutout, no depth write, no culling. */
    public static RenderType glow(Identifier texture) {
        return GLOW.apply(texture);
    }

    private static Identifier pipelineId(String name) {
        return Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "pipeline/" + name);
    }
}
