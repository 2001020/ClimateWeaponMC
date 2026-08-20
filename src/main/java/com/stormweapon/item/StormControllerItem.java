package com.stormweapon.item;

import com.stormweapon.config.StormSettingsSync;
import com.stormweapon.network.StormNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Opens the shared Storm Weapon settings.
 *
 * <p>The screen is opened by the <em>server</em>, not locally on right-click: these settings are
 * now one shared, world-persisted value that only operators may change, so the permission check
 * and the current values both have to come from the server. The client opens the editor when the
 * resulting settings packet arrives, which also guarantees it is editing the live values rather
 * than a stale local copy.</p>
 */
public final class StormControllerItem extends Item {
    public StormControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (!StormSettingsSync.mayEdit(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.stormweapon.settings.denied"));
                return InteractionResult.SUCCESS_SERVER;
            }
            StormNetwork.sendSettings(serverPlayer, true);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
