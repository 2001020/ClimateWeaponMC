package com.stormweapon;

import com.mojang.logging.LogUtils;
import com.stormweapon.command.StormWeaponCommands;
import com.stormweapon.config.StormConfig;
import com.stormweapon.network.StormNetwork;
import com.stormweapon.registry.ModContent;
import com.stormweapon.storm.StormManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(StormWeaponMod.MOD_ID)
public final class StormWeaponMod {
    public static final String MOD_ID = "stormweapon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StormWeaponMod(FMLJavaModLoadingContext context) {
        ModContent.register(context.getModBusGroup());
        // Only COMMON remains: the Storm Controller's values moved out of per-client config into
        // one server-owned, world-persisted value (see StormSettings).
        context.registerConfig(ModConfig.Type.COMMON, StormConfig.COMMON_SPEC);

        StormNetwork.init();
        StormManager.registerEvents();
        StormWeaponCommands.registerEvents();
        com.stormweapon.item.SignalLinkHandler.registerEvents();

        LOGGER.info("Storm Weapon common systems initialized for Minecraft 26.2 / Forge 65.1.1");

        // The client bootstrap is only resolved inside this branch, so a dedicated server never
        // loads any client-only class.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.stormweapon.client.StormClientBootstrap.init();
        }
    }
}
