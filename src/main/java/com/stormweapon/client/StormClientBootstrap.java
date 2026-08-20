package com.stormweapon.client;

import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.weather.StormWeatherPass;
import com.stormweapon.client.render.StormClientRenderers;

/**
 * Single client entry point.
 *
 * <p>This class and everything it reaches may reference client-only Minecraft code, so it must
 * only ever be touched from a {@code Dist.CLIENT} branch. {@link StormWeaponMod} guards the call,
 * and no common or server class references this type, which keeps dedicated-server class loading
 * safe: the JVM never resolves it there.</p>
 */
public final class StormClientBootstrap {
    private static boolean initialized;

    private StormClientBootstrap() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        StormClientManager.register();
        CameraShakeManager.register();
        StormClientRenderers.register();
        StormWeatherPass.register();
        StormDebugHud.register();
        StormFogOverlay.register();
        LauncherAlertClientState.register();
        LauncherAlertOverlay.register();
        initialized = true;
        StormWeaponMod.LOGGER.info("Storm Weapon client weather systems initialized");
    }
}
