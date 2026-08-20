package com.stormweapon.config;

public enum StormQuality {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA;

    public static StormQuality parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return HIGH;
        }
    }
}
