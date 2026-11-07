// 차원 + 블록 좌표(집·일터·만남 장소 같은 기억 값). dimension 은 차원 키(예: minecraft:overworld).
package org.domi.furnace.api.entity.ai.brain;

import org.domi.furnace.api.world.BlockPos;

public record GlobalBlockPos(String dimension, BlockPos pos) {
    public GlobalBlockPos {
        if (dimension == null || pos == null) throw new NullPointerException("dimension·pos");
    }

    public static GlobalBlockPos of(String dimension, int x, int y, int z) { return new GlobalBlockPos(dimension, new BlockPos(x, y, z)); }
}
