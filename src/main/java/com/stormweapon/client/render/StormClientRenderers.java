package com.stormweapon.client.render;

import com.stormweapon.registry.ModContent;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Client-only dispatcher registration kept separate from common content. */
public final class StormClientRenderers {
    private static boolean registered;
    private StormClientRenderers() {}

    public static void register() {
        if (registered) return;
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event ->
            {
                event.registerEntityRenderer(ModContent.WEATHER_MISSILE_ENTITY.get(), WeatherMissileRenderer::new);
                event.registerEntityRenderer(ModContent.METEOR_ENTITY.get(), MeteorRenderer::new);
                event.registerBlockEntityRenderer(ModContent.WEATHER_LAUNCHER_BLOCK_ENTITY.get(), WeatherLauncherRenderer::new);
            }
        );
        registered = true;
    }
}
