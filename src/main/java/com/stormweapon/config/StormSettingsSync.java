package com.stormweapon.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Server-side ownership rules for the Storm Controller's settings.
 *
 * <p>Storage lives on the overworld's storm SavedData so it persists with the world and is one
 * value for the whole server rather than one per dimension.</p>
 */
public final class StormSettingsSync {
    private StormSettingsSync() {}

    /**
     * Whether this player may open and change the shared settings.
     *
     * <p>Operator level is the multiplayer gate. The singleplayer owner is admitted regardless,
     * because a normal singleplayer world without cheats grants its own player no permission level
     * at all -- gating on operator alone would lock the item out of singleplayer entirely.</p>
     */
    public static boolean mayEdit(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null && !server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId())) {
            return true;
        }
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
