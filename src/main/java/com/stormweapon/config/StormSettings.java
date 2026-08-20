package com.stormweapon.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * The full set of values behind the Storm Controller screen, as one immutable snapshot.
 *
 * <p>These used to be per-client Forge config entries. They are now a single server-owned value
 * that is persisted with the world and broadcast to every client, so an operator's change applies
 * to everyone and the next operator edits on top of it rather than from their own private copy.
 * Bundling them into one record (instead of syncing each field separately) is what makes that
 * "edit on top of the current value" behaviour automatic: a client always sends back the state it
 * was just showing, which is the state the server last broadcast.</p>
 */
public record StormSettings(
    StormQuality quality,
    int cloudQuality,
    double rainDensity,
    double cameraShake,
    double lightningFlash,
    boolean stormFog,
    double meteorDensity
) {
    public static final StormSettings DEFAULT = new StormSettings(
        StormQuality.HIGH, 5, 0.55D, 0.6D, 0.85D, true, 1.0D
    );

    public static final Codec<StormSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("quality", StormQuality.HIGH.name())
            .xmap(StormQuality::parse, StormQuality::name).forGetter(StormSettings::quality),
        Codec.INT.optionalFieldOf("cloudQuality", 5).forGetter(StormSettings::cloudQuality),
        Codec.DOUBLE.optionalFieldOf("rainDensity", 0.55D).forGetter(StormSettings::rainDensity),
        Codec.DOUBLE.optionalFieldOf("cameraShake", 0.6D).forGetter(StormSettings::cameraShake),
        Codec.DOUBLE.optionalFieldOf("lightningFlash", 0.85D).forGetter(StormSettings::lightningFlash),
        Codec.BOOL.optionalFieldOf("stormFog", true).forGetter(StormSettings::stormFog),
        Codec.DOUBLE.optionalFieldOf("meteorDensity", 1.0D).forGetter(StormSettings::meteorDensity)
    ).apply(instance, StormSettings::new));

    /**
     * Clamped rebuild. Applied to anything arriving from a client packet or an old save, so a
     * malformed or out-of-date value can never drive the renderers or the meteor spawner out of
     * their supported ranges.
     */
    public StormSettings sanitized() {
        return new StormSettings(
            quality == null ? StormQuality.HIGH : quality,
            Mth.clamp(cloudQuality, 1, 5),
            Mth.clamp(rainDensity, 0.1D, 2.0D),
            Mth.clamp(cameraShake, 0.0D, 1.0D),
            Mth.clamp(lightningFlash, 0.0D, 1.0D),
            stormFog,
            Mth.clamp(meteorDensity, 0.2D, 3.0D)
        );
    }

    public static void encode(StormSettings settings, FriendlyByteBuf buffer) {
        buffer.writeEnum(settings.quality);
        buffer.writeInt(settings.cloudQuality);
        buffer.writeDouble(settings.rainDensity);
        buffer.writeDouble(settings.cameraShake);
        buffer.writeDouble(settings.lightningFlash);
        buffer.writeBoolean(settings.stormFog);
        buffer.writeDouble(settings.meteorDensity);
    }

    public static StormSettings decode(FriendlyByteBuf buffer) {
        return new StormSettings(
            buffer.readEnum(StormQuality.class),
            buffer.readInt(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readBoolean(),
            buffer.readDouble()
        ).sanitized();
    }
}
