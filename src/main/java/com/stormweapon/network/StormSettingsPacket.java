package com.stormweapon.network;

import com.stormweapon.config.StormSettings;
import com.stormweapon.config.StormSettingsState;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client: the shared Storm Controller settings now in force.
 *
 * <p>{@code openScreen} rides along rather than being its own packet so that opening the editor is
 * inherently atomic with receiving the values it is about to display -- a client can never render
 * the screen against a stale copy of the settings it is meant to be editing.</p>
 */
public record StormSettingsPacket(StormSettings settings, boolean openScreen) {
    public static void encode(StormSettingsPacket packet, FriendlyByteBuf buffer) {
        StormSettings.encode(packet.settings, buffer);
        buffer.writeBoolean(packet.openScreen);
    }

    public static StormSettingsPacket decode(FriendlyByteBuf buffer) {
        return new StormSettingsPacket(StormSettings.decode(buffer), buffer.readBoolean());
    }

    public static void handleClient(StormSettingsPacket packet) {
        StormSettingsState.set(packet.settings);
        if (packet.openScreen) {
            com.stormweapon.client.gui.StormClientScreens.openSettings();
        }
    }
}
