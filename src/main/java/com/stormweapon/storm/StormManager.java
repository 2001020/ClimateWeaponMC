package com.stormweapon.storm;

import com.stormweapon.StormWeaponMod;
import com.stormweapon.config.StormSettings;
import com.stormweapon.config.StormSettingsState;
import com.stormweapon.network.StormNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class StormManager {
    private static boolean registered;

    private StormManager() {}

    public static synchronized void registerEvents() {
        if (registered) {
            return;
        }
        TickEvent.ServerTickEvent.Post.BUS.addListener(StormManager::onServerTick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(StormManager::onPlayerLogin);
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(StormManager::onPlayerChangedDimension);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(StormManager::onPlayerRespawn);
        registered = true;
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        // The overworld's SavedData is the authority for the shared settings; mirror it into the
        // shared holder so server-side readers (the meteor spawner) see the persisted values after
        // a restart without every read site having to reach for the SavedData itself.
        StormSettings persisted = StormSavedData.get(event.server().overworld()).settings();
        if (StormSettingsState.current() != persisted) {
            StormSettingsState.set(persisted);
        }
        for (ServerLevel level : event.server().getAllLevels()) {
            StormSavedData data = StormSavedData.get(level);
            long gameTime = level.getGameTime();
            // Lightning only ever comes from the thunder slot (a fog deployment's lightningIntensity
            // is always 0), and the ground haze/slowdown only ever comes from the fog slot, so each
            // subsystem is fed its own independent snapshot rather than "the" single storm state.
            StormLightningManager.tick(level, data.snapshot(gameTime, false));
            FogEmberManager.tick(level);
            FogSlownessManager.tick(level, data.snapshot(gameTime, true));
            FogDamageManager.tick(level, data.snapshot(gameTime, true));
            BlizzardManager.tick(level, data.snapshot(gameTime, MissileKind.BLIZZARD));
            CherryBlossomManager.tick(level, data.snapshot(gameTime, MissileKind.CHERRY));
            MeteorStormManager.tick(level, data);
            if (data.tick(level)) {
                StormNetwork.syncLevel(level);
            }
        }
    }

    /** Public server-side hand-off from the weather missile at its atmospheric detonation point. */
    public static boolean startWeaponStorm(ServerLevel level, Vec3 detonation, long seed) {
        return startWeaponStorm(level, detonation, seed, MissileKind.THUNDER);
    }

    /**
     * Same hand-off, but {@code kind} selects which payload the burst becomes. Only one deployment
     * of any kind may be active at a time: launching while any deployment is already running is
     * rejected. See {@link StormSavedData#isOccupied} for why that restriction exists.
     */
    public static boolean startWeaponStorm(ServerLevel level, Vec3 detonation, long seed, MissileKind kind) {
        StormSavedData data = StormSavedData.get(level);
        if (data.isOccupied()) {
            StormWeaponMod.LOGGER.warn("Storm Weapon launch rejected: a deployment is already active");
            return false;
        }
        if (kind == MissileKind.METEOR) {
            // The meteor payload runs on its own short clock and has no weather phases, so it
            // skips the storm state machine entirely -- and skips sendDetonation, which drives the
            // airburst shockwave visual this payload deliberately does not have.
            data.startMeteor(level);
            StormNetwork.syncLevel(level);
            StormWeaponMod.LOGGER.info("Storm Weapon meteor bombardment started at {}, {}", detonation.x, detonation.z);
            return true;
        }
        data.startWeapon(level, detonation.x, detonation.z, detonation.y, seed, kind);
        StormNetwork.syncLevel(level);
        StormNetwork.sendDetonation(level, detonation);
        StormWeaponMod.LOGGER.info("Storm Weapon atmospheric detonation at {}, {}, {} (kind={})", detonation.x, detonation.y, detonation.z, kind);
        return true;
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StormNetwork.syncPlayer(player);
            // Settings are shared server-wide, so a joining client must be told the current values
            // before it renders anything, not left on its local defaults.
            StormNetwork.sendSettings(player, false);
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Weather state is dimension-local. Replace the snapshot from the dimension the
            // player just left immediately instead of waiting for the next phase transition.
            StormNetwork.syncPlayer(player);
        }
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Respawn replaces the client player entity, so replay the current deployment even
            // when the weather state itself has not changed since the death.
            StormNetwork.syncPlayer(player);
        }
    }
}
