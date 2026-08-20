package com.stormweapon.network;

import com.stormweapon.config.StormSettings;
import com.stormweapon.config.StormSettingsState;
import com.stormweapon.config.StormSettingsSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.stormweapon.storm.StormSavedData;

/** Client to server: an operator's edited settings, to be adopted server-wide. */
public record StormSettingsUpdatePacket(StormSettings settings) {
    public static void encode(StormSettingsUpdatePacket packet, FriendlyByteBuf buffer) {
        StormSettings.encode(packet.settings, buffer);
    }

    public static StormSettingsUpdatePacket decode(FriendlyByteBuf buffer) {
        return new StormSettingsUpdatePacket(StormSettings.decode(buffer));
    }

    public static void handleServer(StormSettingsUpdatePacket packet, ServerPlayer player) {
        // Re-checked here rather than trusting that the sender was allowed to open the screen: the
        // packet is just bytes and could arrive from anyone, at any time.
        if (!StormSettingsSync.mayEdit(player)) {
            player.sendSystemMessage(Component.translatable("message.stormweapon.settings.denied"));
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        StormSavedData.get(overworld).setSettings(packet.settings);
        StormSettingsState.set(packet.settings);
        // Broadcast to everyone, not just the editor: these are shared values, and the next
        // operator to open the screen must start from what this one just set.
        StormNetwork.broadcastSettings(server);
    }
}
