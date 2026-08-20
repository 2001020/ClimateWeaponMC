package com.stormweapon.storm;

import com.stormweapon.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;

/** Restorative player effects and vanilla pink-petal accumulation for a cherry deployment. */
public final class CherryBlossomManager {
    private static final int PLAYER_INTERVAL = 20;
    private static final int PETAL_INTERVAL = 20;
    private static final int PETAL_ATTEMPTS_PER_PLAYER = 5;
    private static final int PETAL_RADIUS = 28;

    // Refreshed well inside its own duration so the HUD icon never visibly flickers off between ticks.
    private static final int EFFECT_DURATION_TICKS = 40;
    private static final int EFFECT_REFRESH_THRESHOLD = 20;

    private CherryBlossomManager() {}

    public static void tick(ServerLevel level, StormSnapshot snapshot) {
        if (!snapshot.active()) {
            return;
        }
        long elapsed = Math.max(0L, level.getGameTime() - snapshot.startGameTime());
        boolean healTick = elapsed > 0L && elapsed % PLAYER_INTERVAL == 0L;
        Holder<MobEffect> holder = ModContent.CHERRY_BLOSSOM_BLESSING.getHolder().orElseThrow();
        for (ServerPlayer player : level.players()) {
            if (!SkyExposure.exposed(level, player.blockPosition())) {
                if (player.hasEffect(holder)) {
                    player.removeEffect(holder);
                }
                continue;
            }
            MobEffectInstance current = player.getEffect(holder);
            if (current == null || current.getDuration() <= EFFECT_REFRESH_THRESHOLD) {
                player.addEffect(new MobEffectInstance(holder, EFFECT_DURATION_TICKS, 0, false, false, true), null);
            }
            if (healTick) {
                player.heal(4.0F);
                removeOneHarmfulEffect(player);
            }
        }
        if (level.getGameTime() % PETAL_INTERVAL == 0L) {
            for (ServerPlayer player : level.players()) {
                accumulatePetals(level, player.blockPosition());
            }
        }
    }

    /** One effect per second makes cleansing visible and gradual while guaranteeing eventual removal. */
    private static void removeOneHarmfulEffect(ServerPlayer player) {
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
                return;
            }
        }
    }

    /** Adds/increments the vanilla pink-petals block wherever that vanilla block can survive. */
    private static void accumulatePetals(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < PETAL_ATTEMPTS_PER_PLAYER; attempt++) {
            int x = center.getX() + level.getRandom().nextInt(PETAL_RADIUS * 2 + 1) - PETAL_RADIUS;
            int z = center.getZ() + level.getRandom().nextInt(PETAL_RADIUS * 2 + 1) - PETAL_RADIUS;
            BlockPos probe = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(probe)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.PINK_PETALS)) {
                int amount = state.getValue(FlowerBedBlock.AMOUNT);
                if (amount < 4) {
                    level.setBlockAndUpdate(pos, state.setValue(FlowerBedBlock.AMOUNT, amount + 1));
                }
                continue;
            }
            if (!state.isAir()) {
                continue;
            }
            BlockState petals = Blocks.PINK_PETALS.defaultBlockState();
            if (petals.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, petals);
            }
        }
    }
}
