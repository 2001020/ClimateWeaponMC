package com.stormweapon.entity;

import com.stormweapon.registry.ModContent;
import net.minecraft.core.BlockPos;
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

/**
 * A single falling meteor from a meteor missile's bombardment.
 *
 * <p>Movement is integrated by hand with {@code noPhysics}, the same approach
 * {@link WeatherMissileEntity} uses: these spawn high above the terrain and fall fast enough that
 * the stock collision sweep is the wrong tool, and the impact test only needs to answer "is the
 * block under me solid". The blast itself is a stock TNT-strength explosion with fire enabled, so
 * it carries vanilla's explosion sound and block damage and additionally leaves the crater
 * burning.</p>
 */
public final class MeteorEntity extends Entity {
    /** Roughly TNT-sized. Kept at TNT's own power so the destruction reads as familiar. */
    private static final float EXPLOSION_POWER = 4.0F;
    private static final double FALL_SPEED = 1.35D;
    /** Radius of the entity's visible rock body, shared with the renderer. */
    public static final float BODY_RADIUS = 0.85F;
    /**
     * Wall-clock lifetime, measured against the level's own game time rather than a tick counter.
     *
     * <p>A tick counter only advances while the entity is actually ticking, which stops the moment
     * its chunk falls out of the simulation distance. A meteor stranded that way used to resume
     * its fall whenever a player wandered back into range -- meteors kept raining in areas the
     * player flew to long after the bombardment had ended. An absolute deadline expires those
     * stragglers the instant they resume instead, no matter how long they sat frozen.</p>
     *
     * <p>Generous compared to the ~3.5 seconds a meteor needs to fall from its spawn altitude, so
     * this only ever culls genuinely stuck ones.</p>
     */
    private static final int LIFETIME_TICKS = 200;

    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DRIFT_X = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DRIFT_Z = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    /** Absolute level game time after which this meteor removes itself without detonating. */
    private long expireGameTime = Long.MAX_VALUE;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPIN, 0.0F);
        builder.define(DRIFT_X, 0.0F);
        builder.define(DRIFT_Z, 0.0F);
    }

    public float spin() {
        return this.entityData.get(SPIN);
    }

    /** Spawns one meteor on a slanted descent toward the ground below {@code position}. */
    public static MeteorEntity spawn(ServerLevel level, Vec3 position, float driftX, float driftZ) {
        MeteorEntity meteor = new MeteorEntity(ModContent.METEOR_ENTITY.get(), level);
        meteor.setPos(position);
        meteor.entityData.set(SPIN, level.getRandom().nextFloat() * 360.0F);
        meteor.entityData.set(DRIFT_X, driftX);
        meteor.entityData.set(DRIFT_Z, driftZ);
        meteor.expireGameTime = level.getGameTime() + LIFETIME_TICKS;
        level.addFreshEntity(meteor);
        return meteor;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = new Vec3(this.entityData.get(DRIFT_X), -FALL_SPEED, this.entityData.get(DRIFT_Z));
        setDeltaMovement(velocity);
        setPos(position().add(velocity));

        if (this.level().isClientSide()) {
            return;
        }
        if (this.level().getGameTime() >= expireGameTime || position().y < this.level().getMinY() - 8) {
            // Silent removal, not explode(): a straggler detonating here would crater somewhere
            // unrelated, long after the bombardment the player watched end.
            discard();
            return;
        }
        // The step per tick is larger than one block, so testing only the destination would let a
        // meteor tunnel straight through thin terrain (a roof, a bridge) without ever registering
        // a hit. Walk the segment instead and detonate at the first solid block along it.
        Vec3 from = position().subtract(velocity);
        int steps = Math.max(1, (int) Math.ceil(velocity.length()));
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = from.add(velocity.scale(i / (double) steps));
            BlockPos pos = BlockPos.containing(sample);
            if (!this.level().getBlockState(pos).isAir() || !this.level().getFluidState(pos).isEmpty()) {
                explode(sample);
                return;
            }
        }
    }

    private void explode(Vec3 at) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LAVA, true, true, at.x, at.y, at.z, 24, 1.2D, 0.8D, 1.2D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 40, 2.0D, 1.4D, 2.0D, 0.18D);
            // fire=true is the one deliberate departure from plain TNT: it leaves the blast area
            // alight instead of only cratering it. Vanilla supplies the explosion sound itself.
            this.level().explode(this, at.x, at.y, at.z, EXPLOSION_POWER, true, Level.ExplosionInteraction.TNT);
        }
        discard();
    }

    /**
     * Meteors are never written to disk. They belong to a 15-second bombardment, so a saved one
     * could only ever come back wrong: resuming its fall in a reloaded chunk, or raining out of a
     * clear sky after a server restart. Not saving them means a chunk unload disposes of them
     * outright, and {@link #LIFETIME_TICKS} covers the case where the chunk stays loaded but stops
     * being ticked.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(SPIN, input.getFloatOr("Spin", 0.0F));
        entityData.set(DRIFT_X, input.getFloatOr("DriftX", 0.0F));
        entityData.set(DRIFT_Z, input.getFloatOr("DriftZ", 0.0F));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("Spin", entityData.get(SPIN));
        output.putFloat("DriftX", entityData.get(DRIFT_X));
        output.putFloat("DriftZ", entityData.get(DRIFT_Z));
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
