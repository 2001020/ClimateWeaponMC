package com.stormweapon.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class StormClientScreens {
    private StormClientScreens() {}
    public static void openLauncher(BlockPos pos) {
        Minecraft.getInstance().setScreenAndShow(new LauncherControlScreen(pos));
    }
    public static void openSettings() { Minecraft.getInstance().setScreenAndShow(new StormSettingsScreen()); }
}
