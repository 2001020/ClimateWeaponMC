package com.stormweapon.client;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Tracks whether the red countdown border should currently be visible. The server pings this once
 * a second while a launcher counts down; each ping resets a short expiry window instead of an
 * explicit on/off flag, so the border simply stops appearing shortly after the pings do (countdown
 * finished, player left range, disconnect, etc.) rather than risking getting stuck visible forever
 * if some "turn it off" signal were ever missed.
 */
public final class LauncherAlertClientState {
    /** Comfortably longer than the 20-tick ping interval so back-to-back pings never gap visibly. */
    private static final int EXPIRY_TICKS = 30;

    private static int ticksRemaining;
    private static int secondsRemaining;
    private static boolean registered;

    private LauncherAlertClientState() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ticksRemaining = 0);
        registered = true;
    }

    public static void ping(int seconds) {
        ticksRemaining = EXPIRY_TICKS;
        secondsRemaining = seconds;
    }

    public static boolean active() {
        return ticksRemaining > 0;
    }

    public static int secondsRemaining() {
        return secondsRemaining;
    }
}
