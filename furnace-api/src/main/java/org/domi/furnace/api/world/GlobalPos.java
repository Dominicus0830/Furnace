// 차원 + 블록 좌표(바닐라 GlobalPos) — 예: 회복 나침반의 마지막 사망 위치.
package org.domi.furnace.api.world;

import java.util.Objects;

public record GlobalPos(String dimension, BlockPos pos) {

    public GlobalPos {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(pos, "pos");
        if (dimension.indexOf(':') < 0) dimension = "minecraft:" + dimension;
    }
}
