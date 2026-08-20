package com.stormweapon.network;

import com.stormweapon.StormWeaponMod;
import com.stormweapon.client.StormClientState;
import com.stormweapon.config.StormSettings;
import com.stormweapon.storm.StormSavedData;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class StormNetwork {
    private static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "main"))
        .networkProtocolVersion(2)
        .simpleChannel();
    private static boolean initialized;

    private StormNetwork() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        CHANNEL.messageBuilder(StormSyncPacket.class, 0)
            .direction(PacketFlow.CLIENTBOUND)
            .encoder(StormSyncPacket::encode)
            .decoder(StormSyncPacket::decode)
            .consumerMainThread((packet, context) -> StormClientState.accept(
                packet.thunder(), packet.fog(), packet.blizzard(), packet.cherry(),
                packet.meteorActive(), packet.meteorStartGameTime()))
            .add()
            .messageBuilder(StormLightningPacket.class, 1)
            .direction(PacketFlow.CLIENTBOUND)
            .encoder(StormLightningPacket::encode)
            .decoder(StormLightningPacket::decode)
            .consumerMainThread((packet, context) -> StormLightningPacket.handleClient(packet))
            .add()
            .messageBuilder(LauncherControlPacket.class, 2)
            .direction(PacketFlow.SERVERBOUND)
            .encoder(LauncherControlPacket::encode)
            .decoder(LauncherControlPacket::decode)
            .consumerMainThread((packet, context) -> {
                ServerPlayer player = context.getSender();
                if (player != null) LauncherControlPacket.handleServer(packet, player);
            })
            .add()
            .messageBuilder(StormDetonationPacket.class, 3)
            .direction(PacketFlow.CLIENTBOUND)
            .encoder(StormDetonationPacket::encode)
            .decoder(StormDetonationPacket::decode)
            .consumerMainThread((packet, context) -> StormDetonationPacket.handleClient(packet))
            .add()
            .messageBuilder(LauncherAlertPacket.class, 4)
            .direction(PacketFlow.CLIENTBOUND)
            .encoder(LauncherAlertPacket::encode)
            .decoder(LauncherAlertPacket::decode)
            .consumerMainThread((packet, context) -> LauncherAlertPacket.handleClient(packet))
            .add()
            .messageBuilder(StormSettingsPacket.class, 5)
            .direction(PacketFlow.CLIENTBOUND)
            .encoder(StormSettingsPacket::encode)
            .decoder(StormSettingsPacket::decode)
            .consumerMainThread((packet, context) -> StormSettingsPacket.handleClient(packet))
            .add()
            .messageBuilder(StormSettingsUpdatePacket.class, 6)
            .direction(PacketFlow.SERVERBOUND)
            .encoder(StormSettingsUpdatePacket::encode)
            .decoder(StormSettingsUpdatePacket::decode)
            .consumerMainThread((packet, context) -> {
                ServerPlayer player = context.getSender();
                if (player != null) StormSettingsUpdatePacket.handleServer(packet, player);
            })
            .add()
            .build();
        initialized = true;
    }

    public static void syncLevel(ServerLevel level) {
        StormSavedData data = StormSavedData.get(level);
        long gameTime = level.getGameTime();
        StormSyncPacket packet = new StormSyncPacket(
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.THUNDER),
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.FOG),
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.BLIZZARD),
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.CHERRY),
            data.meteorActive(), data.meteorStartGameTime());
        CHANNEL.send(packet, PacketDistributor.DIMENSION.with(level.dimension()));
    }

    public static void syncPlayer(ServerPlayer player) {
        StormSavedData data = StormSavedData.get(player.level());
        long gameTime = player.level().getGameTime();
        StormSyncPacket packet = new StormSyncPacket(
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.THUNDER),
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.FOG),
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.BLIZZARD),
            data.snapshot(gameTime, com.stormweapon.storm.MissileKind.CHERRY),
            data.meteorActive(), data.meteorStartGameTime());
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    public static void sendLightning(ServerLevel level, StormLightningPacket packet) {
        CHANNEL.send(packet, PacketDistributor.DIMENSION.with(level.dimension()));
    }

    public static void sendDetonation(ServerLevel level, Vec3 position) {
        CHANNEL.send(new StormDetonationPacket(position.x, position.y, position.z),
            PacketDistributor.DIMENSION.with(level.dimension()));
    }

    public static void sendLauncherControl(LauncherControlPacket packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    /** Client to server: an operator's edited shared settings. */
    public static void sendLauncherSettingsUpdate(StormSettingsUpdatePacket packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    public static void sendLauncherAlert(ServerPlayer player, int secondsRemaining) {
        CHANNEL.send(new LauncherAlertPacket(secondsRemaining), PacketDistributor.PLAYER.with(player));
    }

    /** Pushes the shared settings to one player, optionally opening the editor for them. */
    public static void sendSettings(ServerPlayer player, boolean openScreen) {
        StormSettings settings = StormSavedData.get(player.level().getServer().overworld()).settings();
        CHANNEL.send(new StormSettingsPacket(settings, openScreen), PacketDistributor.PLAYER.with(player));
    }

    /** Pushes the shared settings to everyone after an operator changed them. */
    public static void broadcastSettings(MinecraftServer server) {
        StormSettings settings = StormSavedData.get(server.overworld()).settings();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(new StormSettingsPacket(settings, false), PacketDistributor.PLAYER.with(player));
        }
    }
}
