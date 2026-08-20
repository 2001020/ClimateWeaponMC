package com.stormweapon.client;

import com.stormweapon.storm.StormSnapshot;

/**
 * Pure synchronized data holder for both independent storm slots. It intentionally imports no
 * client-only Minecraft classes, so loading the packet registration on a dedicated server remains
 * safe.
 */
public final class StormClientState {
    private static volatile StormSnapshot thunder = StormSnapshot.CLEAR;
    private static volatile StormSnapshot fog = StormSnapshot.CLEAR;
    private static volatile StormSnapshot blizzard = StormSnapshot.CLEAR;
    private static volatile StormSnapshot cherry = StormSnapshot.CLEAR;
    private static volatile boolean meteorActive;
    private static volatile long meteorStartGameTime;

    private StormClientState() {}

    public static StormSnapshot thunder() {
        return thunder;
    }

    public static StormSnapshot fog() {
        return fog;
    }

    public static StormSnapshot blizzard() {
        return blizzard;
    }

    public static StormSnapshot cherry() {
        return cherry;
    }

    public static boolean meteorActive() {
        return meteorActive;
    }

    public static long meteorStartGameTime() {
        return meteorStartGameTime;
    }

    public static void accept(StormSnapshot nextThunder, StormSnapshot nextFog, StormSnapshot nextBlizzard,
                              StormSnapshot nextCherry, boolean nextMeteorActive, long nextMeteorStart) {
        thunder = nextThunder;
        fog = nextFog;
        blizzard = nextBlizzard;
        cherry = nextCherry;
        meteorActive = nextMeteorActive;
        meteorStartGameTime = nextMeteorStart;
    }

    public static void clear() {
        thunder = StormSnapshot.CLEAR;
        fog = StormSnapshot.CLEAR;
        blizzard = StormSnapshot.CLEAR;
        cherry = StormSnapshot.CLEAR;
        meteorActive = false;
        meteorStartGameTime = 0L;
    }
}
