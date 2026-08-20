package com.stormweapon.network;

import com.stormweapon.storm.StormPhase;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Carries every storm slot in one sync: the thunder and fog deployments as full snapshots, plus
 * the meteor bombardment, which needs only an active flag and its start tick because it has no
 * phase timeline of its own.
 */
public record StormSyncPacket(
    StormSnapshot thunder,
    StormSnapshot fog,
    StormSnapshot blizzard,
    StormSnapshot cherry,
    boolean meteorActive,
    long meteorStartGameTime
) {
    public static void encode(StormSyncPacket packet, FriendlyByteBuf buffer) {
        writeSnapshot(packet.thunder, buffer);
        writeSnapshot(packet.fog, buffer);
        writeSnapshot(packet.blizzard, buffer);
        writeSnapshot(packet.cherry, buffer);
        buffer.writeBoolean(packet.meteorActive);
        buffer.writeLong(packet.meteorStartGameTime);
    }

    public static StormSyncPacket decode(FriendlyByteBuf buffer) {
        return new StormSyncPacket(
            readSnapshot(buffer), readSnapshot(buffer), readSnapshot(buffer), readSnapshot(buffer),
            buffer.readBoolean(), buffer.readLong()
        );
    }

    private static void writeSnapshot(StormSnapshot state, FriendlyByteBuf buffer) {
        buffer.writeBoolean(state.active());
        buffer.writeEnum(state.phase());
        buffer.writeDouble(state.centerX());
        buffer.writeDouble(state.centerZ());
        buffer.writeInt(state.coreRadius());
        buffer.writeInt(state.transitionRadius());
        buffer.writeLong(state.seed());
        buffer.writeLong(state.startGameTime());
        buffer.writeLong(state.phaseStartGameTime());
        buffer.writeInt(state.phaseDurationTicks());
        buffer.writeDouble(state.detonationY());
        buffer.writeFloat(state.waveRadius());
        buffer.writeFloat(state.waveMaxRadius());
        buffer.writeFloat(state.waveProgress());
        buffer.writeBoolean(state.debug());
        buffer.writeBoolean(state.fog());
    }

    private static StormSnapshot readSnapshot(FriendlyByteBuf buffer) {
        return new StormSnapshot(
            buffer.readBoolean(),
            buffer.readEnum(StormPhase.class),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readInt(),
            buffer.readDouble(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readBoolean(),
            buffer.readBoolean()
        );
    }
}
