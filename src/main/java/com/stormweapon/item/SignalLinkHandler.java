package com.stormweapon.item;

import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.item.SignalConnectorItem.Endpoint;
import com.stormweapon.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Drives the signal connector's pairing interaction off Forge's {@code RightClickBlock} event.
 *
 * <p>Using this event rather than the item's own {@code useOn} is deliberate: vanilla resolves
 * {@code BlockState.useItemOn} first, so a lever or button would actuate and consume the
 * interaction before the held item was ever consulted. Cancelling this earlier event is therefore
 * both how the pairing gets a chance to run at all and how the button/lever is kept from firing
 * while the connector is in hand.</p>
 */
public final class SignalLinkHandler {
    private static boolean registered;

    private SignalLinkHandler() {}

    public static synchronized void registerEvents() {
        if (registered) {
            return;
        }
        PlayerInteractEvent.RightClickBlock.BUS.addListener(SignalLinkHandler::onRightClickBlock);
        registered = true;
    }

    /** @return true to cancel, suppressing both the block's own use and any item behaviour. */
    private static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != ModContent.SIGNAL_CONNECTOR.get()) {
            return false;
        }
        // Cancelling client-side does not hide the click from the server: MultiPlayerGameMode sends
        // ServerboundUseItemOnPacket unconditionally from its startPrediction lambda, so the server
        // still fires this same event and runs the pairing below. The client cancel only suppresses
        // the local prediction, which is exactly what stops the lever/button from visually flipping.
        //
        // The result is set to SUCCESS rather than left at the default PASS so the interaction reads
        // as handled: on PASS the client would fall through and try the off-hand item on the same
        // block, letting whatever is in the other hand act on the lever the connector just blocked.
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide()) {
            handle(event.getLevel(), event.getEntity(), stack, event.getPos());
        }
        return true;
    }

    private static void handle(Level level, Player player, ItemStack stack, BlockPos clicked) {
        Endpoint clickedKind = SignalConnectorItem.endpointAt(level, clicked);
        if (clickedKind == null) {
            player.sendSystemMessage(Component.translatable("message.stormweapon.connector.invalid"));
            return;
        }

        // Sneak is an unambiguous "undo": drop any half-made selection and unlink the launcher
        // itself if that is what was clicked, rather than overloading a plain click with a meaning
        // that depends on hidden state.
        if (player.isShiftKeyDown()) {
            SignalConnectorItem.clearAnchor(stack);
            // Unlinking works from either end, so a control can be freed without first hunting down
            // whichever launcher happens to own it.
            WeatherLauncherBlockEntity owner = clickedKind == Endpoint.LAUNCHER
                ? (level.getBlockEntity(clicked) instanceof WeatherLauncherBlockEntity launcher ? launcher : null)
                : WeatherLauncherBlockEntity.launcherLinkedTo(level, clicked);
            if (owner != null && owner.linkedSignalPos() != null) {
                owner.setLinkedSignal(null);
                player.sendSystemMessage(Component.translatable("message.stormweapon.connector.unlinked"));
                return;
            }
            player.sendSystemMessage(Component.translatable("message.stormweapon.connector.cancelled"));
            return;
        }

        BlockPos anchor = SignalConnectorItem.anchor(stack);
        if (anchor == null) {
            select(player, stack, clicked, clickedKind);
            return;
        }
        if (anchor.equals(clicked)) {
            SignalConnectorItem.clearAnchor(stack);
            player.sendSystemMessage(Component.translatable("message.stormweapon.connector.cancelled"));
            return;
        }

        Endpoint anchorKind = SignalConnectorItem.endpointAt(level, anchor);
        // Either the anchored block is gone/replaced since it was picked, or this click is the same
        // kind again (two launchers, two levers). Both mean "this click is the new first pick".
        if (anchorKind == null || anchorKind == clickedKind) {
            select(player, stack, clicked, clickedKind);
            return;
        }

        BlockPos launcherPos = anchorKind == Endpoint.LAUNCHER ? anchor : clicked;
        BlockPos signalPos = anchorKind == Endpoint.SIGNAL ? anchor : clicked;
        if (!(level.getBlockEntity(launcherPos) instanceof WeatherLauncherBlockEntity launcher)) {
            player.sendSystemMessage(Component.translatable("message.stormweapon.connector.invalid"));
            SignalConnectorItem.clearAnchor(stack);
            return;
        }
        // Pairing is one-to-one in both directions. An existing link is reported rather than
        // silently replaced, so re-wiring is always a deliberate unlink-then-link, and the message
        // names the other endpoint's coordinates to make it findable. The pending selection is
        // dropped either way so a refused attempt never leaves the tool half-armed.
        if (launcher.linkedSignalPos() != null) {
            SignalConnectorItem.clearAnchor(stack);
            player.sendSystemMessage(Component.translatable("message.stormweapon.connector.launcher_taken",
                format(launcher.linkedSignalPos())));
            return;
        }
        WeatherLauncherBlockEntity signalOwner = WeatherLauncherBlockEntity.launcherLinkedTo(level, signalPos);
        if (signalOwner != null) {
            SignalConnectorItem.clearAnchor(stack);
            player.sendSystemMessage(Component.translatable("message.stormweapon.connector.signal_taken",
                format(signalOwner.getBlockPos())));
            return;
        }
        launcher.setLinkedSignal(signalPos);
        SignalConnectorItem.clearAnchor(stack);
        player.sendSystemMessage(Component.translatable("message.stormweapon.connector.linked",
            format(launcherPos), format(signalPos)));
    }

    private static void select(Player player, ItemStack stack, BlockPos pos, Endpoint kind) {
        SignalConnectorItem.setAnchor(stack, pos);
        player.sendSystemMessage(Component.translatable(kind == Endpoint.LAUNCHER
            ? "message.stormweapon.connector.selected_launcher"
            : "message.stormweapon.connector.selected_signal", format(pos)));
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
