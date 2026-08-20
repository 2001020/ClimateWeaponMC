package com.stormweapon.storm;

import com.stormweapon.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.Set;

/** Server-authoritative accumulation and exposure penalties for a blizzard deployment. */
public final class BlizzardManager {
    private static final String DATA_KEY = "StormWeaponBlizzard";
    private static final String SPEED_KEY = "SpeedPercent";
    private static final String HEALTH_KEY = "MaxHealthHearts";
    private static final String EXPOSURE_KEY = "ExposureTicks";
    private static final String RECOVERY_KEY = "RecoveryTicks";
    private static final String INDOOR_KEY = "Indoors";
    private static final String SHELTER_CHECK_KEY = "ShelterCheckTicks";
    private static final int DAMAGE_INTERVAL = 100;
    private static final int MAX_HEALTH_INTERVAL = 300;
    private static final int SPEED_INTERVAL = 20;
    private static final int MAX_SPEED_PERCENT = 100;
    private static final int SNOW_INTERVAL = 10;
    private static final int SNOW_ATTEMPTS_PER_PLAYER = 12;
    private static final int SNOW_RADIUS = 32;
    // Refreshed well inside its own duration so the HUD icon never visibly flickers off between
    // the once-a-second updates below.
    private static final int EFFECT_DURATION_TICKS = 40;
    private static final int EFFECT_REFRESH_THRESHOLD = 20;

    private BlizzardManager() {}

    public static void tick(ServerLevel level, StormSnapshot snapshot) {
        if (snapshot.active() && level.getGameTime() % SNOW_INTERVAL == 0L) {
            Set<Long> claimedChunks = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                long chunk = ChunkPos.pack(player.getBlockX() >> 4, player.getBlockZ() >> 4);
                if (claimedChunks.add(chunk)) {
                    accumulateSnow(level, player.blockPosition());
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player, snapshot.active());
        }
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, boolean deploymentActive) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompoundOrEmpty(ServerPlayer.PERSISTED_NBT_TAG);
        CompoundTag data = persisted.getCompoundOrEmpty(DATA_KEY);
        int speedPercent = data.getIntOr(SPEED_KEY, 0);
        int healthHearts = data.getIntOr(HEALTH_KEY, 0);
        int exposureTicks = data.getIntOr(EXPOSURE_KEY, 0);
        int recoveryTicks = data.getIntOr(RECOVERY_KEY, 0);
        int shelterCheckTicks = data.getIntOr(SHELTER_CHECK_KEY, 0) - 1;
        boolean indoors = data.getBooleanOr(INDOOR_KEY, false);

        if (deploymentActive && shelterCheckTicks <= 0) {
            indoors = IndoorShelter.isIndoors(level, player.blockPosition());
            shelterCheckTicks = 20;
        }
        boolean exposed = deploymentActive && !indoors && !player.isCreative() && !player.isSpectator();
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (exposed) {
            exposureTicks++;
            recoveryTicks = 0;
            if (exposureTicks % SPEED_INTERVAL == 0) {
                speedPercent = Math.min(MAX_SPEED_PERCENT, speedPercent + 1);
            }
            if (exposureTicks % DAMAGE_INTERVAL == 0) {
                Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(ModDamageTypes.BLIZZARD);
                player.hurtServer(level, new DamageSource(type), 2.0F);
            }
            if (exposureTicks % MAX_HEALTH_INTERVAL == 0 && maxHealth != null) {
                int cap = Math.max(0, (int)Math.floor((maxHealth.getBaseValue() - 2.0D) / 2.0D));
                healthHearts = Math.min(cap, healthHearts + 1);
            }
        } else {
            exposureTicks = 0;
            if (speedPercent > 0 || healthHearts > 0) {
                recoveryTicks++;
                if (recoveryTicks % SPEED_INTERVAL == 0 && speedPercent > 0) {
                    speedPercent--;
                }
                if (recoveryTicks % MAX_HEALTH_INTERVAL == 0 && healthHearts > 0) {
                    healthHearts--;
                }
            } else {
                recoveryTicks = 0;
            }
        }

        syncLevelEffect(player, ModContent.BLIZZARD_CHILL, speedPercent);
        syncLevelEffect(player, ModContent.BLIZZARD_FRAILTY, healthHearts);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        data.putInt(SPEED_KEY, speedPercent);
        data.putInt(HEALTH_KEY, healthHearts);
        data.putInt(EXPOSURE_KEY, exposureTicks);
        data.putInt(RECOVERY_KEY, recoveryTicks);
        data.putInt(SHELTER_CHECK_KEY, shelterCheckTicks);
        data.putBoolean(INDOOR_KEY, indoors);
        persisted.put(DATA_KEY, data);
        root.put(ServerPlayer.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * Applies (or clears) a vanilla-style {@link MobEffectInstance} whose amplifier encodes the
     * manager's own 1-based level, so it shows up in the effect HUD/inventory screen like any other
     * potion effect. The underlying attribute modifier is managed automatically by the effect
     * itself once added -- no separate {@code AttributeInstance} bookkeeping is needed here.
     */
    private static void syncLevelEffect(ServerPlayer player, RegistryObject<MobEffect> effect, int level) {
        Holder<MobEffect> holder = effect.getHolder().orElseThrow();
        if (level <= 0) {
            if (player.hasEffect(holder)) {
                player.removeEffect(holder);
            }
            return;
        }
        int amplifier = level - 1;
        MobEffectInstance current = player.getEffect(holder);
        if (current == null || current.getAmplifier() != amplifier || current.getDuration() <= EFFECT_REFRESH_THRESHOLD) {
            player.addEffect(new MobEffectInstance(holder, EFFECT_DURATION_TICKS, amplifier, false, false, true), null);
        }
    }

    /** Forces vanilla snow layers to form in loaded terrain regardless of biome temperature. */
    private static void accumulateSnow(ServerLevel level, BlockPos center) {
        int maxLayers = Math.min(SnowLayerBlock.MAX_HEIGHT,
            level.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT));
        if (maxLayers <= 0) {
            return;
        }
        for (int attempt = 0; attempt < SNOW_ATTEMPTS_PER_PLAYER; attempt++) {
            int x = center.getX() + level.getRandom().nextInt(SNOW_RADIUS * 2 + 1) - SNOW_RADIUS;
            int z = center.getZ() + level.getRandom().nextInt(SNOW_RADIUS * 2 + 1) - SNOW_RADIUS;
            BlockPos probe = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(probe)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.SNOW) && level.getBlockState(pos.below()).is(Blocks.SNOW)) {
                pos = pos.below();
                state = level.getBlockState(pos);
            }
            if (level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
                continue;
            }
            if (state.is(Blocks.SNOW)) {
                int layers = state.getValue(SnowLayerBlock.LAYERS);
                if (layers < maxLayers) {
                    BlockState next = state.setValue(SnowLayerBlock.LAYERS, layers + 1);
                    Block.pushEntitiesUp(state, next, level, pos);
                    level.setBlockAndUpdate(pos, next);
                }
                continue;
            }
            if (!state.isAir()) {
                continue;
            }
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (snow.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, snow);
            }
        }
    }
}
