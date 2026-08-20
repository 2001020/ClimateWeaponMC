package com.stormweapon.blockentity;

import com.stormweapon.config.StormConfig;
import com.stormweapon.entity.WeatherMissileEntity;
import com.stormweapon.network.StormNetwork;
import com.stormweapon.registry.ModContent;
import com.stormweapon.storm.MissileKind;
import com.stormweapon.storm.StormSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent server-authoritative control computer for a single launcher installation. */
public final class WeatherLauncherBlockEntity extends BlockEntity {
    public enum LaunchState { SAFE, ARMED, COUNTDOWN, IGNITION, COOLDOWN }

    private static final int PRESET_COUNT = 3;
    private static final int IGNITION_TICKS = 18;
    private static final int MIN_COUNTDOWN_SECONDS = 3;
    private static final int MAX_COUNTDOWN_SECONDS = 30;

    /**
     * Every currently loaded launcher, per dimension. Used to enforce that only one launcher may
     * be counting down (or igniting) at a time: checked live off each launcher's own {@link #state()}
     * rather than a separately maintained lock flag, so there is nothing to leak or leave stuck if a
     * launcher is removed, unloaded, or the server crashes mid-countdown -- the same class of bug
     * already fixed once for the cooldown/state desync above.
     */
    private static final Map<ResourceKey<Level>, Set<WeatherLauncherBlockEntity>> LOADED_LAUNCHERS = new HashMap<>();

    private final int[] presetX = new int[PRESET_COUNT];
    private final int[] presetZ = new int[PRESET_COUNT];
    private boolean hasMissile;
    private int missileKindOrdinal;
    private int activePreset;
    private int stateOrdinal;
    private int sequenceTicks;
    private int cooldownTicks;
    private int countdownSeconds = 5;
    /** Button/lever paired by the signal connector, or null when this launcher is unlinked. */
    private BlockPos linkedSignalPos;
    /**
     * Last observed powered state of {@link #linkedSignalPos}. Persisted rather than recomputed on
     * load: defaulting it to false would make a lever left in the ON position read as a fresh
     * off-to-on edge the first tick after every reload, firing the launcher on its own.
     */
    private boolean linkedSignalPowered;

    public WeatherLauncherBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.WEATHER_LAUNCHER_BLOCK_ENTITY.get(), pos, state);
        for (int index = 0; index < PRESET_COUNT; index++) {
            presetX[index] = pos.getX();
            presetZ[index] = pos.getZ();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED_LAUNCHERS.computeIfAbsent(level.dimension(), key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            Set<WeatherLauncherBlockEntity> launchers = LOADED_LAUNCHERS.get(level.dimension());
            if (launchers != null) {
                launchers.remove(this);
            }
        }
    }

    /** True if some other loaded launcher in this dimension is currently counting down or igniting. */
    private boolean anotherLauncherIsCountingDown(ServerLevel serverLevel) {
        Set<WeatherLauncherBlockEntity> launchers = LOADED_LAUNCHERS.get(serverLevel.dimension());
        if (launchers == null) {
            return false;
        }
        for (WeatherLauncherBlockEntity other : launchers) {
            if (other != this && (other.state() == LaunchState.COUNTDOWN || other.state() == LaunchState.IGNITION)) {
                return true;
            }
        }
        return false;
    }

    /** The button/lever paired to this launcher by the signal connector, or null. */
    public BlockPos linkedSignalPos() {
        return linkedSignalPos;
    }

    /**
     * The launcher already paired to {@code signalPos}, or null if that control is free. Backs the
     * rule that a button/lever drives at most one launcher.
     *
     * <p>This only sees launchers whose chunks are currently loaded, which is the same set that can
     * actually respond to that control: an unloaded launcher does not tick and so cannot fire. A
     * launcher parked in an unloaded chunk could therefore still hold a stale claim on this control
     * that is invisible here, which is accepted rather than backed by a second, level-wide index
     * that would duplicate (and could drift from) the block entity's own saved link.</p>
     */
    public static WeatherLauncherBlockEntity launcherLinkedTo(Level level, BlockPos signalPos) {
        Set<WeatherLauncherBlockEntity> launchers = LOADED_LAUNCHERS.get(level.dimension());
        if (launchers == null) {
            return null;
        }
        for (WeatherLauncherBlockEntity launcher : launchers) {
            if (signalPos.equals(launcher.linkedSignalPos)) {
                return launcher;
            }
        }
        return null;
    }

    /**
     * Pairs (or, with null, unpairs) a button/lever. The edge tracker is seeded from that control's
     * current state so linking to a lever that is already ON does not read as an immediate
     * off-to-on edge and fire the launcher the moment the link is made.
     */
    public void setLinkedSignal(BlockPos pos) {
        linkedSignalPos = pos == null ? null : pos.immutable();
        linkedSignalPowered = linkedSignalPos != null && level != null
            && level.hasChunkAt(linkedSignalPos) && isPowered(level.getBlockState(linkedSignalPos));
        sync();
    }

    private static boolean isPowered(BlockState state) {
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    private void pollLinkedSignal(Level level) {
        if (linkedSignalPos == null || !level.hasChunkAt(linkedSignalPos)) {
            // Never force-load the paired control's chunk just to poll it; while it is unloaded no
            // player is there to flip it anyway, and the tracked state simply stays as it was.
            return;
        }
        BlockState signalState = level.getBlockState(linkedSignalPos);
        if (!signalState.hasProperty(BlockStateProperties.POWERED)) {
            // The paired control was broken or replaced, so this link now points at an unrelated
            // block. Drop it rather than polling that block forever.
            linkedSignalPos = null;
            linkedSignalPowered = false;
            sync();
            return;
        }
        boolean powered = signalState.getValue(BlockStateProperties.POWERED);
        if (powered == linkedSignalPowered) {
            return;
        }
        linkedSignalPowered = powered;
        setChanged();
        if (powered && level instanceof ServerLevel serverLevel) {
            triggerRemoteLaunch(serverLevel);
        }
    }

    /**
     * Remote fire from a paired button/lever: goes straight from SAFE into the countdown, skipping
     * the manual ARM step. Every other guard the manual path applies still holds, so a remote
     * trigger can never bypass the one-deployment-at-a-time or one-countdown-at-a-time rules.
     */
    private void triggerRemoteLaunch(ServerLevel serverLevel) {
        if (!hasMissile || state() != LaunchState.SAFE) {
            return;
        }
        if (StormSavedData.get(serverLevel).isOccupied() || anotherLauncherIsCountingDown(serverLevel)) {
            return;
        }
        stateOrdinal = LaunchState.COUNTDOWN.ordinal();
        sequenceTicks = countdownSeconds * 20;
        broadcastCountdownStart(serverLevel, countdownSeconds);
        sync();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WeatherLauncherBlockEntity launcher) {
        if (level.isClientSide()) {
            return;
        }
        // Polled before any of the early returns below so the edge tracker keeps following the
        // lever even while this launcher is busy or cooling down; otherwise a toggle during that
        // window would be missed and the *next* toggle back would read as the rising edge.
        launcher.pollLinkedSignal(level);
        if (launcher.cooldownTicks > 0) {
            launcher.cooldownTicks--;
            if (launcher.cooldownTicks == 0) {
                launcher.stateOrdinal = LaunchState.SAFE.ordinal();
                launcher.sync();
            }
            return;
        }
        // cooldownTicks is already 0 here. COOLDOWN only ever advances back to SAFE through the
        // decrement above, so if a save/reload (or any other desync) leaves the state stuck at
        // COOLDOWN with nothing left counting down, nothing would otherwise ever move it forward
        // again -- the launcher stayed permanently non-interactive until the block was broken and
        // replaced. Force it back to SAFE directly instead.
        if (launcher.state() == LaunchState.COOLDOWN) {
            launcher.stateOrdinal = LaunchState.SAFE.ordinal();
            launcher.sync();
        }
        LaunchState launchState = launcher.state();
        if (launchState != LaunchState.COUNTDOWN && launchState != LaunchState.IGNITION) {
            return;
        }
        if (--launcher.sequenceTicks > 0) {
            if (launcher.sequenceTicks % 20 == 0) {
                if (launchState == LaunchState.COUNTDOWN) {
                    launcher.broadcastCountdownTick((ServerLevel) level, launcher.sequenceTicks / 20);
                }
                launcher.sync();
            }
            return;
        }
        if (launchState == LaunchState.COUNTDOWN) {
            launcher.stateOrdinal = LaunchState.IGNITION.ordinal();
            launcher.sequenceTicks = IGNITION_TICKS;
            launcher.sync();
            return;
        }
        launcher.spawnMissile((ServerLevel) level);
    }

    public boolean tryInstall(Player player, ItemStack stack, MissileKind kind) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (hasMissile || state() != LaunchState.SAFE) {
            // Previously this just silently failed to load, with no feedback at all telling the
            // player why -- most commonly still-running cooldown after a save/reload, which reads
            // as "the launcher is broken" rather than "wait a bit longer" without an explanation.
            if (!level.isClientSide()) {
                player.sendSystemMessage(cannotLoadReason());
            }
            return false;
        }
        hasMissile = true;
        missileKindOrdinal = kind.ordinal();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.sendSystemMessage(Component.translatable(switch (kind) {
            case FOG -> "message.stormweapon.launcher.loaded_fog";
            case METEOR -> "message.stormweapon.launcher.loaded_meteor";
            case BLIZZARD -> "message.stormweapon.launcher.loaded_blizzard";
            case CHERRY -> "message.stormweapon.launcher.loaded_cherry";
            case THUNDER -> "message.stormweapon.launcher.loaded";
        }));
        sync();
        return true;
    }

    /** Explains why {@link #tryInstall} just refused to load a missile. */
    private Component cannotLoadReason() {
        if (hasMissile) {
            return Component.translatable("message.stormweapon.launcher.already_loaded");
        }
        return switch (state()) {
            case COOLDOWN -> Component.translatable("message.stormweapon.launcher.cooling_down", Mth.ceil(cooldownTicks / 20.0F));
            case ARMED, COUNTDOWN, IGNITION -> Component.translatable("message.stormweapon.launcher.not_ready");
            case SAFE -> Component.translatable("message.stormweapon.launcher.not_ready");
        };
    }

    public void cyclePreset(Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        activePreset = (activePreset + 1) % PRESET_COUNT;
        player.sendSystemMessage(Component.translatable("message.stormweapon.launcher.preset", activePreset + 1, presetX[activePreset], presetZ[activePreset]));
        sync();
    }

    public void armOrBeginCountdown(Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!hasMissile) {
            player.sendSystemMessage(Component.translatable("message.stormweapon.launcher.no_missile"));
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // Only one deployment, of either kind, may be active at a time.
            if (StormSavedData.get(serverLevel).isOccupied()) {
                player.sendSystemMessage(Component.translatable("message.stormweapon.launcher.target_busy"));
                return;
            }
        }
        if (state() == LaunchState.SAFE) {
            stateOrdinal = LaunchState.ARMED.ordinal();
            player.sendSystemMessage(Component.translatable("message.stormweapon.launcher.armed"));
            sync();
            return;
        }
        if (state() == LaunchState.ARMED) {
            if (level instanceof ServerLevel serverLevel && anotherLauncherIsCountingDown(serverLevel)) {
                player.sendSystemMessage(Component.translatable("message.stormweapon.launcher.countdown_busy"));
                return;
            }
            stateOrdinal = LaunchState.COUNTDOWN.ordinal();
            sequenceTicks = countdownSeconds * 20;
            broadcastCountdownStart((ServerLevel) level, countdownSeconds);
            sync();
        }
    }

    public boolean setPreset(int oneBasedSlot, int x, int z) {
        int slot = oneBasedSlot - 1;
        if (slot < 0 || slot >= PRESET_COUNT || level == null) {
            return false;
        }
        presetX[slot] = x;
        presetZ[slot] = z;
        sync();
        return true;
    }

    public void setCountdownSeconds(int seconds) {
        if (level == null || state() == LaunchState.COUNTDOWN || state() == LaunchState.IGNITION) return;
        countdownSeconds = Mth.clamp(seconds, MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS);
        sync();
    }

    /**
     * Custom full-screen title/subtitle overlay instead of a chat message, so the countdown reads
     * as an urgent, hard-to-miss alert rather than one more line scrolling past in chat. This is
     * rendered client-side by {@code LauncherAlertOverlay} rather than vanilla's title system,
     * which draws its title/subtitle at a fixed, non-configurable scale -- the custom overlay lets
     * the alert text be sized independently of that vanilla default.
     */
    private void broadcastCountdownStart(ServerLevel serverLevel, int seconds) {
        for (ServerPlayer nearby : nearbyPlayers(serverLevel)) {
            StormNetwork.sendLauncherAlert(nearby, seconds);
        }
    }

    /** Per-second refresh during an already-started countdown: live seconds and the alert ping. */
    private void broadcastCountdownTick(ServerLevel serverLevel, int seconds) {
        for (ServerPlayer nearby : nearbyPlayers(serverLevel)) {
            StormNetwork.sendLauncherAlert(nearby, seconds);
        }
    }

    private Iterable<ServerPlayer> nearbyPlayers(ServerLevel serverLevel) {
        List<ServerPlayer> nearby = new ArrayList<>();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 16384.0D) {
                nearby.add(player);
            }
        }
        return nearby;
    }

    private void spawnMissile(ServerLevel serverLevel) {
        float yaw = getBlockState().getValue(com.stormweapon.block.WeatherLauncherBlock.FACING).toYRot();
        // Match WeatherLauncherRenderer's mounted missile center exactly. The old fixed Y+1 spawn
        // was several blocks below the visible model, so the missile snapped downward when the
        // block-entity model handed off to the flying entity.
        double elevation = Math.toRadians(75.0D);
        Vec3 railForward = new Vec3(0.0D, Math.sin(elevation), Math.cos(elevation));
        Vec3 railUp = new Vec3(0.0D, Math.cos(elevation), -Math.sin(elevation));
        Vec3 localCenter = new Vec3(0.0D, 1.18D, -0.12D)
            .add(railForward.scale(-0.94D + 3.40D))
            .add(railUp.scale(0.645D));
        double yawRadians = Math.toRadians(yaw);
        Vec3 launchPoint = new Vec3(
            worldPosition.getX() + 0.5D - Math.sin(yawRadians) * localCenter.z,
            worldPosition.getY() + localCenter.y,
            worldPosition.getZ() + 0.5D + Math.cos(yawRadians) * localCenter.z
        );
        long seed = serverLevel.getRandom().nextLong();
        WeatherMissileEntity.launch(serverLevel, launchPoint, yaw, presetX[activePreset], presetZ[activePreset], seed, missileKind());
        hasMissile = false;
        missileKindOrdinal = MissileKind.THUNDER.ordinal();
        stateOrdinal = LaunchState.COOLDOWN.ordinal();
        cooldownTicks = StormConfig.LAUNCHER_COOLDOWN_SECONDS.get() * 20;
        sequenceTicks = 0;
        sync();
    }

    public LaunchState state() {
        return LaunchState.values()[Mth.clamp(stateOrdinal, 0, LaunchState.values().length - 1)];
    }

    public boolean hasMissile() { return hasMissile; }
    public MissileKind missileKind() { return MissileKind.byOrdinal(missileKindOrdinal); }
    public boolean missileFog() { return missileKind() == MissileKind.FOG; }
    public int activePreset() { return activePreset; }
    public int presetX() { return presetX[activePreset]; }
    public int presetZ() { return presetZ[activePreset]; }
    public int presetX(int slot) { return presetX[Mth.clamp(slot, 0, PRESET_COUNT - 1)]; }
    public int presetZ(int slot) { return presetZ[Mth.clamp(slot, 0, PRESET_COUNT - 1)]; }
    public int sequenceTicks() { return sequenceTicks; }
    public int cooldownTicks() { return cooldownTicks; }
    public int countdownSeconds() { return countdownSeconds; }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4.5D, 7.5D, 4.5D);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hasMissile = input.getBooleanOr("HasMissile", false);
        // Older saves stored a plain MissileFog boolean, before a third payload existed.
        missileKindOrdinal = input.getIntOr("MissileKind",
            input.getBooleanOr("MissileFog", false) ? MissileKind.FOG.ordinal() : MissileKind.THUNDER.ordinal());
        activePreset = Mth.clamp(input.getIntOr("ActivePreset", 0), 0, PRESET_COUNT - 1);
        stateOrdinal = Mth.clamp(input.getIntOr("LaunchState", 0), 0, LaunchState.values().length - 1);
        sequenceTicks = Math.max(0, input.getIntOr("SequenceTicks", 0));
        cooldownTicks = Math.max(0, input.getIntOr("CooldownTicks", 0));
        countdownSeconds = Mth.clamp(input.getIntOr("CountdownSeconds", 5), MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS);
        long linked = input.getLongOr("LinkedSignal", Long.MIN_VALUE);
        linkedSignalPos = linked == Long.MIN_VALUE ? null : BlockPos.of(linked);
        linkedSignalPowered = input.getBooleanOr("LinkedSignalPowered", false);
        for (int index = 0; index < PRESET_COUNT; index++) {
            presetX[index] = input.getIntOr("PresetX" + index, worldPosition.getX());
            presetZ[index] = input.getIntOr("PresetZ" + index, worldPosition.getZ());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("HasMissile", hasMissile);
        output.putInt("MissileKind", missileKindOrdinal);
        output.putInt("ActivePreset", activePreset);
        output.putInt("LaunchState", stateOrdinal);
        output.putInt("SequenceTicks", sequenceTicks);
        output.putInt("CooldownTicks", cooldownTicks);
        output.putInt("CountdownSeconds", countdownSeconds);
        if (linkedSignalPos != null) {
            output.putLong("LinkedSignal", linkedSignalPos.asLong());
        }
        output.putBoolean("LinkedSignalPowered", linkedSignalPowered);
        for (int index = 0; index < PRESET_COUNT; index++) {
            output.putInt("PresetX" + index, presetX[index]);
            output.putInt("PresetZ" + index, presetZ[index]);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
