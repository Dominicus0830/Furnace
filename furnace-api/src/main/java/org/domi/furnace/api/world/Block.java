// 읽은 시점의 블록 스냅샷(살아 있는 칸이 아니다). 불변.
package org.domi.furnace.api.world;

import java.util.Objects;

public record Block(String world, BlockPos pos, BlockData data) {

    public Block {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(data, "data");
    }

    public String type() { return data.type(); }
}
