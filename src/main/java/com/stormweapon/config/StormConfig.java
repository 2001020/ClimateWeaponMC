package com.stormweapon.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class StormConfig {
    private StormConfig() {}

    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue STORM_RADIUS = COMMON_BUILDER
        .comment("Total weapon effect radius in blocks; intensity fades continuously to zero at this edge")
        .defineInRange("stormRadius", 40, 16, 4096);
    public static final ForgeConfigSpec.IntValue TRANSITION_WIDTH = COMMON_BUILDER
        .comment("Legacy setting retained for old configs; weapon effects now fade inside stormRadius")
        .defineInRange("stormTransitionWidth", 0, 0, 2048);
    public static final ForgeConfigSpec.IntValue ATMOSPHERIC_WAVE_SECONDS = seconds(
        "weapon.atmosphericWaveSeconds", 4
    );
    public static final ForgeConfigSpec.IntValue ATMOSPHERIC_WAVE_RADIUS = COMMON_BUILDER
        .comment("Visual high-altitude burst ring radius in blocks; this is not the storm radius")
        .defineInRange("weapon.atmosphericWaveRadius", 2000, 4, 4096);
    public static final ForgeConfigSpec.IntValue WEAPON_EFFECT_RAMP_SECONDS = seconds(
        "weapon.effectRampSeconds", 10
    );
    public static final ForgeConfigSpec.IntValue WEAPON_ACTIVE_SECONDS = seconds(
        "weapon.activeSeconds", 300
    );
    public static final ForgeConfigSpec.IntValue LAUNCHER_COOLDOWN_SECONDS = seconds(
        "launcher.cooldownSeconds", 45
    );
    public static final ForgeConfigSpec.IntValue SEEDING_SECONDS = seconds("phaseSeconds.seeding", 8);
    public static final ForgeConfigSpec.IntValue CLOUD_BUILDUP_SECONDS = seconds("phaseSeconds.cloudBuildup", 17);
    public static final ForgeConfigSpec.IntValue WIND_RISING_SECONDS = seconds("phaseSeconds.windRising", 15);
    public static final ForgeConfigSpec.IntValue HEAVY_RAIN_SECONDS = seconds("phaseSeconds.heavyRain", 18);
    public static final ForgeConfigSpec.IntValue SUPERCELL_SECONDS = seconds("phaseSeconds.supercell", 20);
    /** Default/debug storm peak duration. Weapon deployments use weapon.activeSeconds instead. */
    public static final ForgeConfigSpec.IntValue PEAK_STORM_SECONDS = seconds("phaseSeconds.peakStorm", 222);
    public static final ForgeConfigSpec.IntValue DECAY_SECONDS = seconds("phaseSeconds.decay", 45);
    public static final ForgeConfigSpec.IntValue CLEARING_SECONDS = seconds("phaseSeconds.clearing", 45);
    public static final ForgeConfigSpec.DoubleValue PHYSICAL_LIGHTNING_MIN_SECONDS = COMMON_BUILDER
        .comment("Minimum delay between server-authoritative physical strikes during peak storm")
        .defineInRange("physicalLightningMinSeconds", 1.5D, 0.5D, 60.0D);
    public static final ForgeConfigSpec.DoubleValue PHYSICAL_LIGHTNING_MAX_SECONDS = COMMON_BUILDER
        .comment("Maximum delay between server-authoritative physical strikes during peak storm")
        .defineInRange("physicalLightningMaxSeconds", 4.5D, 0.5D, 120.0D);
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_DAMAGE_MULTIPLIER = COMMON_BUILDER
        .defineInRange("lightningDamageMultiplier", 1.0D, 0.0D, 10.0D);
    public static final ForgeConfigSpec.BooleanValue STORM_FIRE_ENABLED = COMMON_BUILDER
        .define("stormFireEnabled", false);

    /** Seconds of falling meteors after a meteor missile's sky-darkening burst. */
    public static final ForgeConfigSpec.IntValue METEOR_ACTIVE_SECONDS = seconds(
        "weapon.meteorActiveSeconds", 15
    );

    public static final ForgeConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private static ForgeConfigSpec.IntValue seconds(String path, int defaultValue) {
        return COMMON_BUILDER.defineInRange(path, defaultValue, 1, 3600);
    }

    // The Storm Controller's values are no longer per-client config entries: they are one
    // server-owned, world-persisted value broadcast to every client (see StormSettings). These
    // accessors keep the many render-side read sites unaware of that move.

    public static StormQuality quality() {
        return StormSettingsState.current().quality();
    }

    public static int cloudQuality() {
        return StormSettingsState.current().cloudQuality();
    }

    public static double rainDensity() {
        return StormSettingsState.current().rainDensity();
    }

    public static double cameraShake() {
        return StormSettingsState.current().cameraShake();
    }

    public static double lightningFlash() {
        return StormSettingsState.current().lightningFlash();
    }

    public static boolean stormFog() {
        return StormSettingsState.current().stormFog();
    }

    public static double meteorDensity() {
        return StormSettingsState.current().meteorDensity();
    }
}
