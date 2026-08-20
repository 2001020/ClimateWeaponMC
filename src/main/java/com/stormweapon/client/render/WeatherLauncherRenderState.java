package com.stormweapon.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Render-thread snapshot of the synchronized launcher state. */
public final class WeatherLauncherRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.NORTH;
    public boolean hasMissile;
    public com.stormweapon.storm.MissileKind missileKind = com.stormweapon.storm.MissileKind.THUNDER;
}
