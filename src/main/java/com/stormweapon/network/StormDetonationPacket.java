package com.stormweapon.network;

import com.stormweapon.client.CameraShakeManager;
import net.minecraft.network.FriendlyByteBuf;

/** Sparse detonation cue used for the delayed client camera shake response. */
public record StormDetonationPacket(double x, double y, double z) {
    public static void encode(StormDetonationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
    }

    public static StormDetonationPacket decode(FriendlyByteBuf buffer) {
        return new StormDetonationPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handleClient(StormDetonationPacket packet) {
        CameraShakeManager.trigger(packet.x, packet.y, packet.z);
    }
}
