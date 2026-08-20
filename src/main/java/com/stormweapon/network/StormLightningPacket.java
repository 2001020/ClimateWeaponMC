package com.stormweapon.network;

import com.stormweapon.client.weather.StormLightningRenderer;
import net.minecraft.network.FriendlyByteBuf;

/** One sparse, server-authoritative physical strike visual. */
public record StormLightningPacket(double x, double y, double z, long seed, float strength) {
    public static void encode(StormLightningPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeLong(packet.seed);
        buffer.writeFloat(packet.strength);
    }

    public static StormLightningPacket decode(FriendlyByteBuf buffer) {
        return new StormLightningPacket(
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong(), buffer.readFloat()
        );
    }

    public static void handleClient(StormLightningPacket packet) {
        StormLightningRenderer.acceptPhysicalStrike(packet);
    }
}
