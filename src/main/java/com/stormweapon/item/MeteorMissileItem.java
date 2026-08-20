package com.stormweapon.item;

import net.minecraft.world.item.Item;

/**
 * Launcher payload that detonates into a short, violent meteor bombardment rather than a weather
 * deployment. Unlike the other two payloads this one destroys terrain, so its display name carries
 * an explicit high-destruction warning.
 */
public final class MeteorMissileItem extends Item {
    public MeteorMissileItem(Properties properties) {
        super(properties);
    }
}
