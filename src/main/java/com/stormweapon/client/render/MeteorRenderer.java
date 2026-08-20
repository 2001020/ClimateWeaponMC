package com.stormweapon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stormweapon.entity.MeteorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Registration bridge for Minecraft's entity dispatcher. Like the weather missile, the meteor's
 * actual mesh is submitted by the storm frame pass so its rock body and burning trail share one
 * backend-agnostic pipeline with the rest of the mod's effects.
 */
public final class MeteorRenderer extends EntityRenderer<MeteorEntity, EntityRenderState> {
    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // StormWeaponEffectsRenderer handles all visual submission in the level frame pass.
    }
}
