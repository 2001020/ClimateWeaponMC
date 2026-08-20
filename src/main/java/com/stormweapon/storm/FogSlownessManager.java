package com.stormweapon.storm;

import com.stormweapon.registry.ModContent;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Server-authoritative movement penalty for standing in the fog missile's ground haze. Gated by
 * the same {@link SkyExposure} shelter check the client-side tint uses, so a roof excuses the
 * player from both the visual haze and the slowdown together, exactly like rain. Applied as a
 * vanilla-style {@link MobEffectInstance} so it shows up in the effect HUD/inventory screen like
 * any other potion effect, rather than as an invisible attribute change.
 */
public final class FogSlownessManager {
    /** Matches the haze's own formation ramp: no penalty until the fog has actually started settling. */
    private static final float ACTIVATION_THRESHOLD = 0.1F;

    // Refreshed well inside its own duration so the HUD icon never visibly flickers off between ticks.
    private static final int EFFECT_DURATION_TICKS = 40;
    private static final int EFFECT_REFRESH_THRESHOLD = 20;

    private FogSlownessManager() {}

    public static void tick(ServerLevel level, StormSnapshot snapshot) {
        boolean fogging = snapshot.active() && snapshot.fog()
            && snapshot.fogIntensity(level.getGameTime(), 1.0F) > ACTIVATION_THRESHOLD;
        Holder<MobEffect> holder = ModContent.FOG_CHILL.getHolder().orElseThrow();
        for (ServerPlayer player : level.players()) {
            boolean shouldSlow = fogging && SkyExposure.exposed(level, player.blockPosition())
                && !player.isCreative() && !player.isSpectator();
            if (shouldSlow) {
                MobEffectInstance current = player.getEffect(holder);
                if (current == null || current.getDuration() <= EFFECT_REFRESH_THRESHOLD) {
                    player.addEffect(new MobEffectInstance(holder, EFFECT_DURATION_TICKS, 0, false, false, true), null);
                }
            } else if (player.hasEffect(holder)) {
                player.removeEffect(holder);
            }
        }
    }
}
