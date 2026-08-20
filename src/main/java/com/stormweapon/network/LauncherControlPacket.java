package com.stormweapon.network;

import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Server-validated edits made by the launcher control screen. */
public record LauncherControlPacket(BlockPos pos, int action, int slot, int targetX, int targetZ, int countdownSeconds) {
    public static final int SAVE_TARGET = 0;
    public static final int ARM_OR_LAUNCH = 1;

    public static void encode(LauncherControlPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.action);
        buffer.writeByte(packet.slot);
        buffer.writeInt(packet.targetX);
        buffer.writeInt(packet.targetZ);
        buffer.writeVarInt(packet.countdownSeconds);
    }
    public static LauncherControlPacket decode(FriendlyByteBuf buffer) {
        return new LauncherControlPacket(buffer.readBlockPos(), buffer.readByte(), buffer.readByte(), buffer.readInt(), buffer.readInt(), buffer.readVarInt());
    }
    public static void handleServer(LauncherControlPacket packet, ServerPlayer player) {
        if (player.level().getBlockEntity(packet.pos) instanceof WeatherLauncherBlockEntity launcher
            && player.distanceToSqr(packet.pos.getX() + 0.5D, packet.pos.getY() + 0.5D, packet.pos.getZ() + 0.5D) <= 64.0D) {
            if (packet.action == SAVE_TARGET) {
                launcher.setPreset(packet.slot + 1, packet.targetX, packet.targetZ);
                launcher.setCountdownSeconds(packet.countdownSeconds);
            }
            if (packet.action == ARM_OR_LAUNCH) launcher.armOrBeginCountdown(player);
        }
    }
}
