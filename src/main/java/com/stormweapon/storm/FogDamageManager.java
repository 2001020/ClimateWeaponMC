package com.stormweapon.storm;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Server-authoritative periodic damage for standing in the fog missile's ground haze: 0.5 hearts
 * (1.0F) every 5 seconds. Gated by the same {@link SkyExposure} shelter check as the visual haze
 * and the movement slowdown, so a real roof excuses a player from every part of the effect
 * together.
 */
public final class FogDamageManager {
    private static final float DAMAGE = 1.0F;
    private static final int INTERVAL_TICKS = 100;

    /** Matches the haze's own formation ramp: no damage until the fog has actually started settling. */
    private static final float ACTIVATION_THRESHOLD = 0.1F;

    private FogDamageManager() {}

    public static void tick(ServerLevel level, StormSnapshot snapshot) {
        if (level.getGameTime() % INTERVAL_TICKS != 0) {
            return;
        }
        boolean fogging = snapshot.active() && snapshot.fog()
            && snapshot.fogIntensity(level.getGameTime(), 1.0F) > ACTIVATION_THRESHOLD;
        if (!fogging) {
            return;
        }
        Holder<DamageType> damageType = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.FOG);
        DamageSource source = new DamageSource(damageType);
        for (ServerPlayer player : level.players()) {
            if (SkyExposure.exposed(level, player.blockPosition())) {
                player.hurtServer(level, source, DAMAGE);
            }
        }
    }
}
