package com.stormweapon.storm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Strict indoor test based on bounded air connectivity. Trees are weather-permeable; closed walls,
 * roofs, glass and doors enclose the search. Reaching the search boundary is conservatively outdoor.
 */
public final class IndoorShelter {
    private static final int HORIZONTAL_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 8;
    private static final int MAX_VISITED = 6_000;

    private IndoorShelter() {}

    public static boolean isIndoors(LevelReader level, BlockPos playerPos) {
        BlockPos origin = playerPos.above();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(origin);

        while (!queue.isEmpty() && visited.size() < MAX_VISITED) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current.asLong())) {
                continue;
            }
            int dx = Math.abs(current.getX() - origin.getX());
            int dy = Math.abs(current.getY() - origin.getY());
            int dz = Math.abs(current.getZ() - origin.getZ());
            if (dx >= HORIZONTAL_RADIUS || dz >= HORIZONTAL_RADIUS || dy >= VERTICAL_RADIUS) {
                return false;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next.asLong()) && weatherCanPass(level, next)) {
                    queue.addLast(next);
                }
            }
        }
        return queue.isEmpty();
    }

    private static boolean weatherCanPass(LevelReader level, BlockPos pos) {
        if (!level.isInsideBuildHeight(pos)) {
            return true;
        }
        var state = level.getBlockState(pos);
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }
}
