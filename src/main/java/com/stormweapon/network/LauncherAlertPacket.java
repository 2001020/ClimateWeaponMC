package com.stormweapon.network;

import com.stormweapon.client.LauncherAlertClientState;
import net.minecraft.network.FriendlyByteBuf;

/**
 * "Still counting down" ping for the full-screen red border and title/subtitle alert, carrying the
 * live countdown seconds. Sent once when a countdown starts and again with every per-second
 * refresh; the client extends a short expiry window on each ping and lets the alert fade on its
 * own once the pings stop, rather than relying on an explicit "turn it off" packet that could be
 * missed and leave the alert stuck on.
 */
public record LauncherAlertPacket(int secondsRemaining) {
    public static void encode(LauncherAlertPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.secondsRemaining());
    }

    public static LauncherAlertPacket decode(FriendlyByteBuf buffer) {
        return new LauncherAlertPacket(buffer.readVarInt());
    }

    public static void handleClient(LauncherAlertPacket packet) {
        LauncherAlertClientState.ping(packet.secondsRemaining());
    }
}
