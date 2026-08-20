package com.stormweapon.storm;

import com.stormweapon.StormWeaponMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Server-authoritative movement penalty for standing in the fog missile's ground haze. Gated by
 * the same {@link SkyExposure} shelter check the client-side tint uses, so a roof excuses the
 * player from both the visual haze and the slowdown together, exactly like rain.
 */
public final class FogSlownessManager {
    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "fog_slowness");
    private static final double SPEED_PENALTY = -0.20D;

    /** Matches the haze's own formation ramp: no penalty until the fog has actually started settling. */
    private static final float ACTIVATION_THRESHOLD = 0.1F;

    private FogSlownessManager() {}

    public static void tick(ServerLevel level, StormSnapshot snapshot) {
        boolean fogging = snapshot.active() && snapshot.fog()
            && snapshot.fogIntensity(level.getGameTime(), 1.0F) > ACTIVATION_THRESHOLD;
        for (ServerPlayer player : level.players()) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) {
                continue;
            }
            boolean shouldSlow = fogging && SkyExposure.exposed(level, player.blockPosition()) && !player.isCreative();
            boolean currentlySlowed = speed.getModifier(MODIFIER_ID) != null;
            if (shouldSlow && !currentlySlowed) {
                speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!shouldSlow && currentlySlowed) {
                speed.removeModifier(MODIFIER_ID);
            }
        }
    }
}
