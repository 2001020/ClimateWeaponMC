package com.stormweapon.item;

import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pairs a weather missile launcher with a vanilla button or lever, so flipping that control fires
 * the launcher directly.
 *
 * <p>The interaction itself is driven from {@link SignalLinkHandler} through
 * Forge's {@code RightClickBlock} event rather than {@code useOn} here: vanilla resolves
 * {@code BlockState.useItemOn} before the held item's own {@code useOn}, so a lever would toggle
 * itself and swallow the interaction before this item ever saw it. The event fires earlier and is
 * cancellable, which is also exactly what suppresses the button/lever from actuating while the
 * connector is held.</p>
 */
public final class SignalConnectorItem extends Item {
    public SignalConnectorItem(Properties properties) {
        super(properties);
    }

    /** Endpoint kinds this tool can pair. */
    public enum Endpoint { LAUNCHER, SIGNAL }

    /** Which endpoint kind the block at {@code pos} is, or {@code null} if it is neither. */
    public static Endpoint endpointAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WeatherLauncherBlockEntity) {
            return Endpoint.LAUNCHER;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LEVER) || state.is(BlockTags.BUTTONS)) {
            return Endpoint.SIGNAL;
        }
        return null;
    }

    /** The half-finished selection stored on this connector, or {@code null} when idle. */
    public static BlockPos anchor(ItemStack stack) {
        return stack.get(ModContent.LINK_ANCHOR.get());
    }

    public static void setAnchor(ItemStack stack, BlockPos pos) {
        stack.set(ModContent.LINK_ANCHOR.get(), pos.immutable());
    }

    public static void clearAnchor(ItemStack stack) {
        stack.remove(ModContent.LINK_ANCHOR.get());
    }

    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().getItem() == ModContent.SIGNAL_CONNECTOR.get()
            || player.getOffhandItem().getItem() == ModContent.SIGNAL_CONNECTOR.get();
    }

    /** The connector stack the player is holding, preferring the main hand, or {@link ItemStack#EMPTY}. */
    public static ItemStack heldBy(Player player) {
        if (player.getMainHandItem().getItem() == ModContent.SIGNAL_CONNECTOR.get()) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() == ModContent.SIGNAL_CONNECTOR.get()) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }
}
