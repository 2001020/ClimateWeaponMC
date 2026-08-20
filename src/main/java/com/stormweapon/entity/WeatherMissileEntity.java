package com.stormweapon.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import com.stormweapon.storm.FogEmberManager;
import com.stormweapon.storm.MissileKind;
import com.stormweapon.storm.StormManager;

/**
 * Server-authoritative guided weather missile. The initial skeleton is deliberately already a
 * distinct entity type; flight, persistence and detonation are added incrementally after the
 * current 26.2 entity signatures have compiled against the local MDK.
 */
public final class WeatherMissileEntity extends Entity {
    public static final int DETONATION_ALTITUDE = 300;
    private static final int IGNITION_TICKS = 18;
    private static final int BOOST_TICKS = 66;
    private static final EntityDataAccessor<Integer> TARGET_X = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_Z = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT_STATE = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT_AGE = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LAUNCH_YAW = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> STORM_SEED = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(WeatherMissileEntity.class, EntityDataSerializers.INT);

    public WeatherMissileEntity(EntityType<? extends WeatherMissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_X, 0);
        builder.define(TARGET_Z, 0);
        builder.define(FLIGHT_STATE, 0);
        builder.define(FLIGHT_AGE, 0);
        builder.define(LAUNCH_YAW, 0.0F);
        builder.define(STORM_SEED, 0L);
        builder.define(KIND, MissileKind.THUNDER.ordinal());
    }

    public void setTarget(int x, int z) {
        this.entityData.set(TARGET_X, x);
        this.entityData.set(TARGET_Z, z);
    }

    public int targetX() {
        return this.entityData.get(TARGET_X);
    }

    public int targetZ() {
        return this.entityData.get(TARGET_Z);
    }

    public int flightState() {
        return this.entityData.get(FLIGHT_STATE);
    }

    public int flightAge() {
        return this.entityData.get(FLIGHT_AGE);
    }

    public float launchYaw() {
        return this.entityData.get(LAUNCH_YAW);
    }

    public MissileKind kind() {
        return MissileKind.byOrdinal(this.entityData.get(KIND));
    }

    public boolean isFog() {
        return kind() == MissileKind.FOG;
    }

    /** Creates a real entity, rather than a particle or arrow substitute, and begins its staged flight. */
    public static WeatherMissileEntity launch(ServerLevel level, Vec3 launchPosition, float yaw, int targetX, int targetZ, long seed) {
        return launch(level, launchPosition, yaw, targetX, targetZ, seed, MissileKind.THUNDER);
    }

    /** Same staged flight as {@link #launch}, but {@code kind} selects what the burst turns into. */
    public static WeatherMissileEntity launch(ServerLevel level, Vec3 launchPosition, float yaw, int targetX, int targetZ, long seed, MissileKind kind) {
        WeatherMissileEntity missile = new WeatherMissileEntity(com.stormweapon.registry.ModContent.WEATHER_MISSILE_ENTITY.get(), level);
        missile.setPos(launchPosition);
        missile.setTarget(targetX, targetZ);
        missile.entityData.set(LAUNCH_YAW, yaw);
        missile.entityData.set(STORM_SEED, seed);
        missile.entityData.set(KIND, kind.ordinal());
        missile.setYRot(yaw);
        missile.setXRot(-75.0F);
        level.addFreshEntity(missile);
        return missile;
    }

    @Override
    public void tick() {
        super.tick();
        int age = this.entityData.get(FLIGHT_AGE) + 1;
        this.entityData.set(FLIGHT_AGE, age);

        if (this.level().isClientSide()) {
            return;
        }

        Vec3 velocity;
        if (age <= IGNITION_TICKS) {
            this.entityData.set(FLIGHT_STATE, 0);
            velocity = Vec3.ZERO;
        } else if (age <= IGNITION_TICKS + BOOST_TICKS) {
            this.entityData.set(FLIGHT_STATE, 1);
            float boostProgress = (age - IGNITION_TICKS) / (float)BOOST_TICKS;
            float speed = Mth.lerp(boostProgress, 0.10F, 0.92F);
            velocity = launchRail().scale(speed);
        } else {
            this.entityData.set(FLIGHT_STATE, 2);
            Vec3 target = new Vec3(targetX() + 0.5D, DETONATION_ALTITUDE, targetZ() + 0.5D);
            Vec3 toTarget = target.subtract(position());
            double distance = toTarget.length();
            if (distance <= 2.4D || age > 1_800) {
                detonate();
                return;
            }
            Vec3 desired = toTarget.normalize();
            double speed = Math.min(1.55D, 0.88D + (age - IGNITION_TICKS - BOOST_TICKS) * 0.006D);
            velocity = getDeltaMovement().scale(0.82D).add(desired.scale(speed * 0.24D));
            if (velocity.length() > speed) {
                velocity = velocity.normalize().scale(speed);
            }
        }

        setDeltaMovement(velocity);
        if (velocity.lengthSqr() > 1.0E-6D) {
            setPos(position().add(velocity));
            updateFlightRotation(velocity);
        }
    }

    private Vec3 launchRail() {
        double yawRadians = Math.toRadians(launchYaw());
        double horizontal = Math.cos(Math.toRadians(75.0D));
        return new Vec3(-Math.sin(yawRadians) * horizontal, Math.sin(Math.toRadians(75.0D)), Math.cos(yawRadians) * horizontal);
    }

    private void updateFlightRotation(Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = (float)(Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0D);
        float pitch = (float)-Math.toDegrees(Math.atan2(velocity.y, horizontal));
        setYRot(yaw);
        setXRot(pitch);
    }

    private void detonate() {
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 detonation = new Vec3(targetX() + 0.5D, DETONATION_ALTITUDE, targetZ() + 0.5D);
            boolean started = StormManager.startWeaponStorm(serverLevel, detonation, this.entityData.get(STORM_SEED), kind());
            // Two launchers can both be armed for the same missile kind before either detonates, so
            // the same-kind slot can still be occupied by the time this one reaches its target. When
            // that race rejects the deployment, skip the explosion/ember/damage payload too, instead
            // of dealing damage and spawning fire effects for a storm that never actually started.
            // The meteor payload deliberately has no airburst of its own: its whole visible
            // arrival is the sky going dark and the bombardment that follows.
            if (started && kind() == MissileKind.FOG) {
                spawnFogDetonationParticles(serverLevel, detonation);
                FogEmberManager.spawnBurst(serverLevel, detonation.x, detonation.z, detonation.y);
            }
        }
        discard();
    }

    /**
     * A high-altitude flame/smoke splash at the fog missile's burst point. This is a local visual
     * flourish only, distinct from the dimension-wide cloud/fog weather the detonation triggers.
     */
    private static void spawnFogDetonationParticles(ServerLevel serverLevel, Vec3 center) {
        // overrideLimiter/alwaysShow keep the burst visible to any nearby player up to 512 blocks,
        // instead of the default 32-block particle radius, since the detonation sits high overhead.
        serverLevel.sendParticles(ParticleTypes.FLAME, true, true,
            center.x, center.y, center.z, 220, 9.5D, 6.5D, 9.5D, 0.36D);
        serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, true, true,
            center.x, center.y, center.z, 150, 7.5D, 5.0D, 7.5D, 0.28D);
        serverLevel.sendParticles(ParticleTypes.LAVA, true, true,
            center.x, center.y, center.z, 34, 4.5D, 3.0D, 4.5D, 0.10D);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, true, true,
            center.x, center.y, center.z, 130, 10.5D, 6.5D, 10.5D, 0.28D);
        serverLevel.sendParticles(ParticleTypes.CLOUD, true, true,
            center.x, center.y, center.z, 190, 13.0D, 8.5D, 13.0D, 0.22D);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setTarget(input.getIntOr("TargetX", 0), input.getIntOr("TargetZ", 0));
        entityData.set(FLIGHT_STATE, input.getIntOr("FlightState", 0));
        entityData.set(FLIGHT_AGE, input.getIntOr("FlightAge", 0));
        entityData.set(LAUNCH_YAW, input.getFloatOr("LaunchYaw", 0.0F));
        entityData.set(STORM_SEED, input.getLongOr("StormSeed", 0L));
        // Older saves stored a plain IsFog boolean, before a third payload existed.
        entityData.set(KIND, input.getIntOr("Kind", input.getBooleanOr("IsFog", false) ? MissileKind.FOG.ordinal() : MissileKind.THUNDER.ordinal()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("TargetX", targetX());
        output.putInt("TargetZ", targetZ());
        output.putInt("FlightState", flightState());
        output.putInt("FlightAge", flightAge());
        output.putFloat("LaunchYaw", launchYaw());
        output.putLong("StormSeed", entityData.get(STORM_SEED));
        output.putInt("Kind", entityData.get(KIND));
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
