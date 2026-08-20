package com.stormweapon.config;

/**
 * The settings currently in force for this client/server.
 *
 * <p>Deliberately free of any client-only or server-only imports so both sides can hold the same
 * value: the server updates it when an operator edits the Storm Controller, and each client
 * updates it from the resulting sync packet. Read sites just call {@link #current()} and never
 * need to know which side they are on.</p>
 */
public final class StormSettingsState {
    private static volatile StormSettings current = StormSettings.DEFAULT;

    private StormSettingsState() {}

    public static StormSettings current() {
        return current;
    }

    public static void set(StormSettings settings) {
        current = settings.sanitized();
    }

    /** Called when leaving a world, so a stale server's settings never leak into the next one. */
    public static void reset() {
        current = StormSettings.DEFAULT;
    }
}
