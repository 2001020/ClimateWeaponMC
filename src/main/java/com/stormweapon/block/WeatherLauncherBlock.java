package com.stormweapon.block;

import com.mojang.serialization.MapCodec;
import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Compact logical footprint for the oversized, rendered launcher assembly.
 * The BlockEntity added by the launcher module owns its durable state and interaction.
 */
public final class WeatherLauncherBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape LOGICAL_BASE = box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);

    public WeatherLauncherBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        // Block codecs are used by the data-driven decoder as well as direct registry suppliers.
        return MapCodec.unit(() -> new WeatherLauncherBlock(BlockBehaviour.Properties.of()
            .strength(4.0F, 12.0F).noOcclusion()
            .setId(ModContent.BLOCKS.key("weather_missile_launcher"))));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LOGICAL_BASE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LOGICAL_BASE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherLauncherBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof WeatherLauncherBlockEntity launcher) {
            if (stack.getItem() == ModContent.WEATHER_MISSILE.get()) {
                return launcher.tryInstall(player, stack, com.stormweapon.storm.MissileKind.THUNDER) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
            }
            if (stack.getItem() == ModContent.FOG_MISSILE.get()) {
                return launcher.tryInstall(player, stack, com.stormweapon.storm.MissileKind.FOG) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
            }
            if (stack.getItem() == ModContent.METEOR_MISSILE.get()) {
                return launcher.tryInstall(player, stack, com.stormweapon.storm.MissileKind.METEOR) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
            }
            if (stack.getItem() == ModContent.BLIZZARD_MISSILE.get()) {
                return launcher.tryInstall(player, stack, com.stormweapon.storm.MissileKind.BLIZZARD) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
            }
            if (stack.getItem() == ModContent.CHERRY_MISSILE.get()) {
                return launcher.tryInstall(player, stack, com.stormweapon.storm.MissileKind.CHERRY) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            com.stormweapon.client.gui.StormClientScreens.openLauncher(pos);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof WeatherLauncherBlockEntity launcher) {
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.WEATHER_LAUNCHER_BLOCK_ENTITY.get(), WeatherLauncherBlockEntity::tick);
    }
}
