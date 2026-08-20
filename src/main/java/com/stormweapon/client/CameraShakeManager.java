package com.stormweapon.client;

import com.stormweapon.config.StormConfig;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.storm.StormPhase;
import com.stormweapon.storm.StormSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/** Delayed, accessibility-scaled atmospheric detonation camera impulse. */
public final class CameraShakeManager {
    private static final int DELAY_TICKS = 10;
    private static final int DURATION_TICKS = 60;
    private static int delayTicks;
    private static int remainingTicks;
    private static float appliedYaw;
    private static float appliedPitch;
    private static Object lastPlayer;
    private static float distanceScale;
    // Tracked separately per storm kind: observeSnapshot() is now called once for the thunder slot
    // and once for the fog slot every tick, and a single shared "last observed" value would let one
    // kind's detonation overwrite the other's, causing a missed or duplicate shake trigger.
    private static long observedThunderStart = Long.MIN_VALUE;
    private static long observedFogStart = Long.MIN_VALUE;
    private static boolean startLogged;
    private static boolean registered;

    private CameraShakeManager() {}

    public static synchronized void register() {
        if (registered) return;
        TickEvent.ClientTickEvent.Post.BUS.addListener(CameraShakeManager::onClientTick);
        registered = true;
    }

    public static void trigger(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        double distance = minecraft.player.position().distanceTo(new Vec3(x, y, z));
        distanceScale = Mth.clamp((float)(1.0D - distance / 2400.0D), 0.55F, 1.0F);
        delayTicks = DELAY_TICKS;
        remainingTicks = DURATION_TICKS;
        startLogged = false;
    }

    /**
     * Reliable fallback for a missed one-shot detonation cue. The synchronized weapon snapshot is
     * authoritative and arrives at the same burst, so observing a new wave start schedules the
     * exact same shake without retriggering on ordinary periodic sync packets.
     */
    public static void observeSnapshot(StormSnapshot snapshot, long gameTime) {
        if (!snapshot.active() || snapshot.phase() != StormPhase.ATMOSPHERIC_WAVE) return;
        long observed = snapshot.fog() ? observedFogStart : observedThunderStart;
        if (snapshot.startGameTime() == observed) return;
        if (snapshot.fog()) {
            observedFogStart = snapshot.startGameTime();
        } else {
            observedThunderStart = snapshot.startGameTime();
        }
        if (gameTime - snapshot.startGameTime() <= 20L) {
            trigger(snapshot.centerX(), snapshot.detonationY(), snapshot.centerZ());
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            appliedYaw = 0.0F;
            appliedPitch = 0.0F;
            lastPlayer = null;
            return;
        }
        if (lastPlayer != minecraft.player) {
            appliedYaw = 0.0F;
            appliedPitch = 0.0F;
            lastPlayer = minecraft.player;
        }

        // Remove last tick's offset first. Mouse movement made by the player between ticks is
        // therefore preserved, and the view returns exactly to its unshaken heading at the end.
        float baseYaw = minecraft.player.getYRot() - appliedYaw;
        float basePitch = minecraft.player.getXRot() - appliedPitch;
        appliedYaw = 0.0F;
        appliedPitch = 0.0F;

        if (delayTicks > 0) {
            delayTicks--;
            minecraft.player.setYRot(baseYaw);
            minecraft.player.setXRot(basePitch);
            return;
        }
        if (remainingTicks <= 0) {
            minecraft.player.setYRot(baseYaw);
            minecraft.player.setXRot(basePitch);
            return;
        }
        if (!startLogged) {
            StormWeaponMod.LOGGER.info("Storm Weapon detonation camera shake started");
            startLogged = true;
        }
        float elapsed = DURATION_TICKS - remainingTicks;
        float progress = elapsed / DURATION_TICKS;
        float decay = (1.0F - progress) * (1.0F - progress);
        float amplitude = decay * distanceScale * (float) StormConfig.cameraShake();
        float fast = elapsed * 0.82F;
        float slow = elapsed * 0.29F;
        appliedYaw = (Mth.sin(fast) * 3.25F + Mth.sin(slow) * 0.85F) * amplitude;
        appliedPitch = (Mth.cos(fast * 1.17F) * 2.35F + Mth.sin(slow * 1.31F) * 0.62F) * amplitude;
        minecraft.player.setYRot(baseYaw + appliedYaw);
        minecraft.player.setXRot(Mth.clamp(basePitch + appliedPitch, -90.0F, 90.0F));
        remainingTicks--;
    }
}
