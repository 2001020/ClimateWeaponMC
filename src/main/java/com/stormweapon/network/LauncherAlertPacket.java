package com.stormweapon.network;

import com.stormweapon.client.LauncherAlertClientState;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Payload-less "still counting down" ping for the full-screen red border. Sent once when a
 * countdown starts and again with every per-second subtitle refresh; the client extends a short
 * expiry window on each ping and lets the border fade on its own once the pings stop, rather than
 * relying on an explicit "turn it off" packet that could be missed and leave the border stuck on.
 */
public record LauncherAlertPacket() {
    public static void encode(LauncherAlertPacket packet, FriendlyByteBuf buffer) {}

    public static LauncherAlertPacket decode(FriendlyByteBuf buffer) {
        return new LauncherAlertPacket();
    }

    public static void handleClient(LauncherAlertPacket packet) {
        LauncherAlertClientState.ping();
    }
}
