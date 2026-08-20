package com.stormweapon.client.weather;

import com.stormweapon.StormWeaponMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;

import com.mojang.blaze3d.framegraph.FramePass;

/**
 * Frame graph pass that draws the storm weather layers.
 *
 * <p>Minecraft 26.2 renders the level through a frame graph, and Forge exposes modded passes via
 * {@link AddFramePassEvent}. The pass binds the main target, so it is ordered after the vanilla
 * terrain, cloud and weather passes and is depth tested against the world.</p>
 *
 * <p>The former custom black cloud sheets are intentionally not submitted. Minecraft's own
 * client-side thunderstorm sky and cloud tint are supplied by {@link StormVanillaWeatherBridge};
 * this pass keeps the effects that make the weapon distinct: world-anchored rain and
 * synchronized visual/physical lightning.</p>
 */
public final class StormWeatherPass implements FramePassManager.PassDefinition {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "storm_weather");

    private static final StormWeatherPass INSTANCE = new StormWeatherPass();

    private final StormCloudRenderer cloudRenderer = new StormCloudRenderer();
    private final StormRainRenderer rainRenderer = new StormRainRenderer();
    private final StormLightningRenderer lightningRenderer = new StormLightningRenderer();
    private final StormWeaponEffectsRenderer weaponEffectsRenderer = new StormWeaponEffectsRenderer();
    private final SignalLinkRenderer signalLinkRenderer = new SignalLinkRenderer();
    private float partialTick;

    private StormWeatherPass() {}

    public static void register() {
        AddFramePassEvent.BUS.addListener(event -> event.addPass(ID, INSTANCE));
    }

    public static StormCloudRenderer cloudRenderer() {
        return INSTANCE.cloudRenderer;
    }

    public static StormRainRenderer rainRenderer() {
        return INSTANCE.rainRenderer;
    }

    public static StormLightningRenderer lightningRenderer() {
        return INSTANCE.lightningRenderer;
    }

    @Override
    public void extracts(LevelTargetBundle bundle, FramePass pass, DeltaTracker deltaTracker) {
        this.partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        bundle.main = pass.readsAndWrites(bundle.main);
        // The storm layers are the only consumer of this pass, so it must survive graph culling.
        pass.disableCulling();
    }

    @Override
    public void executes(LevelRenderState state) {
        // Minecraft supplies the thunderstorm sky. The custom black cloud deck is intentionally
        // disabled; this pass retains only Storm Weapon's distinct weather effects.
        this.lightningRenderer.updateField(state, this.partialTick);
        this.rainRenderer.render(state, this.partialTick);
        this.weaponEffectsRenderer.render(state, this.partialTick);
        this.lightningRenderer.render(state, this.partialTick);
        this.signalLinkRenderer.render(state, this.partialTick);
    }
}
