package com.stormweapon.storm;

import com.stormweapon.config.StormConfig;

import java.util.Locale;

public enum StormPhase {
    CLEAR,
    /**
     * The high-altitude atmospheric payload has burst and its weather front is spreading over
     * the target area. This is a weapon-only phase; ordinary debug storms still begin at
     * {@link #SEEDING}.
     */
    ATMOSPHERIC_WAVE,
    SEEDING,
    CLOUD_BUILDUP,
    WIND_RISING,
    HEAVY_RAIN,
    SUPERCELL,
    PEAK_STORM,
    DECAY,
    CLEARING;

    public int durationTicks() {
        int seconds = switch (this) {
            case CLEAR -> 0;
            // The visual pressure wave has its own four-second clock. This phase remains active
            // for the independently configurable weather ramp, then hands off at full strength.
            case ATMOSPHERIC_WAVE -> StormConfig.WEAPON_EFFECT_RAMP_SECONDS.get();
            case SEEDING -> StormConfig.SEEDING_SECONDS.get();
            case CLOUD_BUILDUP -> StormConfig.CLOUD_BUILDUP_SECONDS.get();
            case WIND_RISING -> StormConfig.WIND_RISING_SECONDS.get();
            case HEAVY_RAIN -> StormConfig.HEAVY_RAIN_SECONDS.get();
            case SUPERCELL -> StormConfig.SUPERCELL_SECONDS.get();
            case PEAK_STORM -> StormConfig.PEAK_STORM_SECONDS.get();
            case DECAY -> StormConfig.DECAY_SECONDS.get();
            case CLEARING -> StormConfig.CLEARING_SECONDS.get();
        };
        return seconds * 20;
    }

    public StormPhase next() {
        return switch (this) {
            case CLEAR -> SEEDING;
            case ATMOSPHERIC_WAVE -> PEAK_STORM;
            case SEEDING -> CLOUD_BUILDUP;
            case CLOUD_BUILDUP -> WIND_RISING;
            case WIND_RISING -> HEAVY_RAIN;
            case HEAVY_RAIN -> SUPERCELL;
            case SUPERCELL -> PEAK_STORM;
            case PEAK_STORM -> DECAY;
            case DECAY -> CLEARING;
            case CLEARING -> CLEAR;
        };
    }

    public static StormPhase parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
