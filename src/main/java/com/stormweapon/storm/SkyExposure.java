package com.stormweapon.storm;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Stricter stand-in for {@code LevelReader#canSeeSky}, shared by both the client-side fog tint
 * and the server-side fog slowdown so the two stay consistent.
 *
 * <p>The quick heightmap rejects obviously open columns, then the exact vertical scan ignores both
 * leaves and logs. Ignoring logs matters for broad branches: a trunk/branch above the player is
 * still a tree, not an indoor roof. Any other collision-bearing block (including glass, slabs and
 * ordinary building blocks) is treated as genuine shelter.</p>
 */
public final class SkyExposure {
    private SkyExposure() {}

    public static boolean exposed(LevelReader level, BlockPos pos) {
        int startY = pos.getY() + 2;
        int columnTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (columnTop <= startY) {
            return true;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), startY, pos.getZ());
        for (int y = startY; y < Math.min(columnTop, level.getMaxY()); y++) {
            cursor.setY(y);
            var state = level.getBlockState(cursor);
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                continue;
            }
            if (!state.getCollisionShape(level, cursor).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
