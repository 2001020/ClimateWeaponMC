package com.stormweapon.storm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stormweapon.StormWeaponMod;
import com.stormweapon.config.StormConfig;
import com.stormweapon.config.StormSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent deployment state. Atmospheric payloads each retain their own slot so their seed,
 * detonation origin and timing survive save/reload; the global occupancy guard still permits only
 * one active weapon deployment at a time.
 */
public final class StormSavedData extends SavedData {
    private static final Codec<StormPhase> PHASE_CODEC = Codec.STRING.xmap(StormPhase::parse, StormPhase::name);

    /** Per-kind phase state machine. Mutated in place by {@link StormSavedData}. */
    public static final class Instance {
        private boolean active;
        private StormPhase phase;
        private double centerX;
        private double centerZ;
        private int coreRadius;
        private int transitionRadius;
        private long seed;
        private long startGameTime;
        private long phaseStartGameTime;
        private double detonationY;
        private long waveStartGameTime;
        private boolean weaponDeployment;

        Instance() {
            this(false, StormPhase.CLEAR, 0.0D, 0.0D, 768, 1024, 0L, 0L, 0L, 0.0D, 0L, false);
        }

        private Instance(
            boolean active,
            StormPhase phase,
            double centerX,
            double centerZ,
            int coreRadius,
            int transitionRadius,
            long seed,
            long startGameTime,
            long phaseStartGameTime,
            double detonationY,
            long waveStartGameTime,
            boolean weaponDeployment
        ) {
            this.active = active;
            this.phase = phase;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.coreRadius = coreRadius;
            this.transitionRadius = transitionRadius;
            this.seed = seed;
            this.startGameTime = startGameTime;
            this.phaseStartGameTime = phaseStartGameTime;
            this.detonationY = detonationY;
            this.waveStartGameTime = waveStartGameTime;
            this.weaponDeployment = weaponDeployment;
        }

        private Instance copy() {
            return new Instance(active, phase, centerX, centerZ, coreRadius, transitionRadius, seed,
                startGameTime, phaseStartGameTime, detonationY, waveStartGameTime, weaponDeployment);
        }

        static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(i -> i.active),
            PHASE_CODEC.optionalFieldOf("phase", StormPhase.CLEAR).forGetter(i -> i.phase),
            Codec.DOUBLE.optionalFieldOf("centerX", 0.0D).forGetter(i -> i.centerX),
            Codec.DOUBLE.optionalFieldOf("centerZ", 0.0D).forGetter(i -> i.centerZ),
            Codec.INT.optionalFieldOf("coreRadius", 768).forGetter(i -> i.coreRadius),
            Codec.INT.optionalFieldOf("transitionRadius", 1024).forGetter(i -> i.transitionRadius),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(i -> i.seed),
            Codec.LONG.optionalFieldOf("startGameTime", 0L).forGetter(i -> i.startGameTime),
            Codec.LONG.optionalFieldOf("phaseStartGameTime", 0L).forGetter(i -> i.phaseStartGameTime),
            Codec.DOUBLE.optionalFieldOf("detonationY", 0.0D).forGetter(i -> i.detonationY),
            Codec.LONG.optionalFieldOf("waveStartGameTime", 0L).forGetter(i -> i.waveStartGameTime),
            Codec.BOOL.optionalFieldOf("weaponDeployment", false).forGetter(i -> i.weaponDeployment)
        ).apply(instance, Instance::new));
    }

    public static final Codec<StormSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Instance.CODEC.optionalFieldOf("thunder", new Instance()).forGetter(data -> data.thunderState),
        Instance.CODEC.optionalFieldOf("fog", new Instance()).forGetter(data -> data.fogState),
        Instance.CODEC.optionalFieldOf("blizzard", new Instance()).forGetter(data -> data.blizzardState),
        Instance.CODEC.optionalFieldOf("cherry", new Instance()).forGetter(data -> data.cherryState),
        Codec.BOOL.optionalFieldOf("debug", false).forGetter(data -> data.debug),
        Codec.BOOL.optionalFieldOf("meteorActive", false).forGetter(data -> data.meteorActive),
        Codec.LONG.optionalFieldOf("meteorStartGameTime", 0L).forGetter(data -> data.meteorStartGameTime),
        StormSettings.CODEC.optionalFieldOf("settings", StormSettings.DEFAULT).forGetter(data -> data.settings)
    ).apply(instance, StormSavedData::new));

    public static final SavedDataType<StormSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "storm_state"),
        StormSavedData::new,
        CODEC,
        null
    );

    private final Instance thunderState;
    private final Instance fogState;
    private final Instance blizzardState;
    private final Instance cherryState;
    private boolean debug;
    /**
     * The meteor bombardment deliberately does not reuse {@link Instance}. It has no multi-phase
     * weather timeline at all -- just "on, from this tick, for a fixed short window" -- so an
     * active flag plus a start time is the whole state, and forcing it through the phase machine
     * would mean inventing phases that never do anything.
     */
    private boolean meteorActive;
    private long meteorStartGameTime;
    /**
     * Storm Controller settings live here so they inherit this class's existing persistence. They
     * are read and written through the overworld's instance only (see
     * {@link com.stormweapon.config.StormSettingsSync}), which makes them one server-wide value
     * rather than something that could differ per dimension.
     */
    private StormSettings settings;

    public StormSavedData() {
        this(new Instance(), new Instance(), new Instance(), new Instance(), false, false, 0L, StormSettings.DEFAULT);
    }

    private StormSavedData(Instance thunderState, Instance fogState, Instance blizzardState,
                           Instance cherryState, boolean debug,
                           boolean meteorActive, long meteorStartGameTime, StormSettings settings) {
        // Codec optional defaults are object instances; copy them so an older save missing a newly
        // added slot can never share one mutable default deployment between dimensions/worlds.
        this.thunderState = thunderState.copy();
        this.fogState = fogState.copy();
        this.blizzardState = blizzardState.copy();
        this.cherryState = cherryState.copy();
        this.debug = debug;
        this.meteorActive = meteorActive;
        this.meteorStartGameTime = meteorStartGameTime;
        this.settings = settings.sanitized();
    }

    public static StormSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private Instance instanceFor(boolean fog) {
        return fog ? fogState : thunderState;
    }

    private Instance instanceFor(MissileKind kind) {
        return switch (kind) {
            case FOG -> fogState;
            case BLIZZARD -> blizzardState;
            case CHERRY -> cherryState;
            case THUNDER, METEOR -> thunderState;
        };
    }

    public StormSettings settings() {
        return settings;
    }

    public void setSettings(StormSettings next) {
        this.settings = next.sanitized();
        setDirty();
    }

    public boolean meteorActive() {
        return meteorActive;
    }

    public long meteorStartGameTime() {
        return meteorStartGameTime;
    }

    /** Begins the meteor bombardment window. Sky darkening and spawning both run off this tick. */
    public void startMeteor(ServerLevel level) {
        meteorActive = true;
        meteorStartGameTime = level.getGameTime();
        setDirty();
    }

    /** @return true when the bombardment just expired and clients need a resync. */
    private boolean tickMeteor(ServerLevel level) {
        if (!meteorActive) {
            return false;
        }
        long duration = StormConfig.METEOR_ACTIVE_SECONDS.get() * 20L;
        if (level.getGameTime() - meteorStartGameTime < duration) {
            return false;
        }
        meteorActive = false;
        setDirty();
        return true;
    }

    /**
     * True while any deployment -- thunder or fog, regardless of the kind being requested -- is
     * already active. Thunder and fog missiles used to occupy independent slots so both could run
     * at once, but a fog deployment's visibility cut requires flooring the render distance option,
     * which reports to the server and shrinks how large a radius of chunks/entities it keeps
     * tracking for that client; while a thunder deployment was also active, its physical lightning
     * strikes (up to ~96 blocks away) regularly fell outside that shrunken radius and silenced
     * themselves, sound included. Restricting the mod to one deployment at a time removes that
     * conflict entirely instead of trying to keep the render distance floor loose enough to avoid it.
     */
    public boolean isOccupied(boolean ignoredKind) {
        return isOccupied();
    }

    public boolean isOccupied() {
        return thunderState.active || fogState.active || blizzardState.active || cherryState.active || meteorActive;
    }

    /** Debug/testing entry point, unrelated to missile launches. Always targets the thunder slot. */
    public void start(ServerLevel level, double x, double z) {
        long now = level.getGameTime();
        Instance inst = thunderState;
        inst.active = true;
        inst.phase = StormPhase.SEEDING;
        inst.centerX = x;
        inst.centerZ = z;
        inst.coreRadius = -1;
        inst.transitionRadius = -1;
        inst.seed = level.getRandom().nextLong();
        inst.startGameTime = now;
        inst.phaseStartGameTime = now;
        inst.detonationY = 0.0D;
        inst.waveStartGameTime = 0L;
        inst.weaponDeployment = false;
        setDirty();
    }

    /** Starts the weapon-specific storm from a non-destructive atmospheric detonation. */
    public void startWeapon(ServerLevel level, double x, double z, double y, long weaponSeed) {
        startWeapon(level, x, z, y, weaponSeed, false);
    }

    /**
     * Same weapon start, but {@code fog} selects which independent slot (thunder or fog missile)
     * is armed. The other slot, if already active, is left running untouched.
     */
    public void startWeapon(ServerLevel level, double x, double z, double y, long weaponSeed, boolean fog) {
        startWeapon(level, x, z, y, weaponSeed, fog ? MissileKind.FOG : MissileKind.THUNDER);
    }

    /** Starts one atmospheric payload without disturbing the persisted slots of other kinds. */
    public void startWeapon(ServerLevel level, double x, double z, double y, long weaponSeed, MissileKind kind) {
        long now = level.getGameTime();
        Instance inst = instanceFor(kind);
        inst.active = true;
        inst.phase = StormPhase.ATMOSPHERIC_WAVE;
        inst.centerX = x;
        inst.centerZ = z;
        // Negative radius is the synchronized sentinel for a dimension-wide deployment. The
        // target remains the atmospheric detonation and shockwave origin.
        inst.coreRadius = -1;
        inst.transitionRadius = -1;
        inst.seed = weaponSeed == 0L ? level.getRandom().nextLong() : weaponSeed;
        inst.startGameTime = now;
        inst.phaseStartGameTime = now;
        inst.detonationY = y;
        inst.waveStartGameTime = now;
        inst.weaponDeployment = true;
        setDirty();
    }

    /** Stops every active deployment -- thunder, fog and meteor -- matching the debug command's scope. */
    public void stop(ServerLevel level) {
        stopInstance(thunderState, level);
        stopInstance(fogState, level);
        stopInstance(blizzardState, level);
        stopInstance(cherryState, level);
        meteorActive = false;
        setDirty();
    }

    private static void stopInstance(Instance inst, ServerLevel level) {
        inst.active = false;
        inst.phase = StormPhase.CLEAR;
        inst.phaseStartGameTime = level.getGameTime();
        inst.weaponDeployment = false;
    }

    /** Debug-only phase override. Always targets the thunder slot. */
    public void forcePhase(ServerLevel level, StormPhase phase) {
        if (phase == StormPhase.CLEAR) {
            stop(level);
            return;
        }
        long now = level.getGameTime();
        Instance inst = thunderState;
        inst.active = true;
        inst.phase = phase;
        inst.phaseStartGameTime = now;
        if (inst.startGameTime == 0L) {
            inst.startGameTime = now;
        }
        setDirty();
    }

    /** Ticks every slot independently; returns {@code true} if any of them needs a resync. */
    public boolean tick(ServerLevel level) {
        boolean thunderChanged = tickInstance(thunderState, level);
        boolean fogChanged = tickInstance(fogState, level);
        boolean blizzardChanged = tickInstance(blizzardState, level);
        boolean cherryChanged = tickInstance(cherryState, level);
        boolean meteorChanged = tickMeteor(level);
        return thunderChanged || fogChanged || blizzardChanged || cherryChanged || meteorChanged;
    }

    private boolean tickInstance(Instance inst, ServerLevel level) {
        if (!inst.active || inst.phase == StormPhase.CLEAR) {
            return false;
        }
        long waveDurationTicks = StormConfig.ATMOSPHERIC_WAVE_SECONDS.get() * 20L;
        boolean syncWave = inst.weaponDeployment && level.getGameTime() - inst.waveStartGameTime <= waveDurationTicks
            && level.getGameTime() % 4L == 0L;
        // A deployed weapon remains in its severe state for five minutes measured from the
        // high-altitude burst, then enters the normal gentle recovery phases. Debug storms keep
        // the independently configurable phase sequence.
        if (inst.weaponDeployment && inst.phase == StormPhase.PEAK_STORM
            && level.getGameTime() - inst.startGameTime >= StormConfig.WEAPON_ACTIVE_SECONDS.get() * 20L) {
            if (inst == blizzardState || inst == cherryState) {
                // Seasonal payloads are specified as an exact five-minute deployment. Their
                // client visuals still ease out locally after this authoritative stop.
                stopInstance(inst, level);
            } else {
                inst.phase = StormPhase.DECAY;
                inst.phaseStartGameTime = level.getGameTime();
            }
            setDirty();
            return true;
        }
        int duration = inst.phase.durationTicks();
        if (inst.weaponDeployment && inst.phase == StormPhase.PEAK_STORM) {
            duration = Integer.MAX_VALUE;
        }
        if (duration > 0 && level.getGameTime() - inst.phaseStartGameTime >= duration) {
            StormPhase next = inst.phase.next();
            if (next == StormPhase.CLEAR) {
                stopInstance(inst, level);
            } else {
                inst.phase = next;
                inst.phaseStartGameTime = level.getGameTime();
            }
            setDirty();
            return true;
        }
        return syncWave;
    }

    public boolean toggleDebug() {
        this.debug = !this.debug;
        setDirty();
        return this.debug;
    }

    public StormSnapshot snapshot(long gameTime, boolean fog) {
        return snapshot(gameTime, fog ? MissileKind.FOG : MissileKind.THUNDER);
    }

    public StormSnapshot snapshot(long gameTime, MissileKind kind) {
        Instance inst = instanceFor(kind);
        return new StormSnapshot(
            inst.active,
            inst.phase,
            inst.centerX,
            inst.centerZ,
            inst.coreRadius,
            inst.transitionRadius,
            inst.seed,
            inst.startGameTime,
            inst.phaseStartGameTime,
            inst.phase.durationTicks(),
            inst.detonationY,
            waveRadius(inst, gameTime),
            inst.weaponDeployment ? StormConfig.ATMOSPHERIC_WAVE_RADIUS.get().floatValue() : 0.0F,
            waveProgress(inst, gameTime),
            debug,
            kind == MissileKind.FOG
        );
    }

    /** Current wave radius is always horizontal; the sky height is only carried for the renderer. */
    private float waveRadius(Instance inst, long gameTime) {
        if (!inst.weaponDeployment) {
            return 0.0F;
        }
        return StormConfig.ATMOSPHERIC_WAVE_RADIUS.get().floatValue() * waveProgress(inst, gameTime);
    }

    private float waveProgress(Instance inst, long gameTime) {
        if (!inst.weaponDeployment) {
            return 1.0F;
        }
        long duration = StormConfig.ATMOSPHERIC_WAVE_SECONDS.get() * 20L;
        return Mth.clamp((gameTime - inst.waveStartGameTime) / (float)duration, 0.0F, 1.0F);
    }

    /** Status for the {@code /stormweapon status} command: whichever slot is active, thunder first. */
    public StormStatus status(ServerLevel level) {
        MissileKind kind = thunderState.active ? MissileKind.THUNDER
            : fogState.active ? MissileKind.FOG
            : blizzardState.active ? MissileKind.BLIZZARD
            : cherryState.active ? MissileKind.CHERRY
            : MissileKind.THUNDER;
        Instance inst = instanceFor(kind);
        StormSnapshot snapshot = snapshot(level.getGameTime(), kind);
        long remaining;
        if (inst.weaponDeployment && inst.phase == StormPhase.PEAK_STORM) {
            // A weapon deployment deliberately holds PEAK until the five-minute deployment clock
            // expires, rather than using PEAK_STORM's ordinary debug-storm duration.
            remaining = Math.max(0L, StormConfig.WEAPON_ACTIVE_SECONDS.get() * 20L
                - (level.getGameTime() - inst.startGameTime));
        } else {
            remaining = Math.max(0L, (long) inst.phase.durationTicks() - (level.getGameTime() - inst.phaseStartGameTime));
        }
        return new StormStatus(snapshot, remaining, inst.weaponDeployment);
    }

    public record StormStatus(StormSnapshot snapshot, long phaseRemainingTicks, boolean weaponDeployment) {}
}
