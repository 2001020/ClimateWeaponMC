package com.stormweapon.storm;

import com.stormweapon.StormWeaponMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Keys into the data-driven damage type registry. The actual {@link DamageType} definitions live
 * in {@code data/stormweapon/damage_type/*.json}, matching how vanilla's own {@code DamageTypes}
 * keys are backed by {@code data/minecraft/damage_type/*.json}.
 */
public interface ModDamageTypes {
    ResourceKey<DamageType> FOG = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "fog"));
    ResourceKey<DamageType> BLIZZARD = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "blizzard"));
}
