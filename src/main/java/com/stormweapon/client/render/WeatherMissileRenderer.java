package com.stormweapon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stormweapon.entity.WeatherMissileEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Registration bridge for Minecraft's entity dispatcher. The actual mesh is submitted by the
 * storm frame pass, allowing its engine plume and high-altitude shockwave to share one
 * backend-agnostic translucent pipeline with weather effects.
 */
public final class WeatherMissileRenderer extends EntityRenderer<WeatherMissileEntity, EntityRenderState> {
    public WeatherMissileRenderer(EntityRendererProvider.Context context) {
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
