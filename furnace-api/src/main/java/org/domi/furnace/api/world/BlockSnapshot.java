// 읽은 시점의 블록 복사본(살아 있는 칸이 아니다). 불변이라 아무 스레드에서나 읽는다.
// 살아 있는 칸은 {@link Block} — World.block(pos) 로 얻는다.
package org.domi.furnace.api.world;

import java.util.Objects;

public record BlockSnapshot(String world, BlockPos pos, BlockData data) {

    public BlockSnapshot {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(data, "data");
    }

    public String type() { return data.type(); }

    /** 같은 칸의 살아 있는 손잡이(읽지 않는다). 그 차원이 서버에 없으면 IllegalStateException. */
    public Block live() {
        World w = org.domi.furnace.api.Furnace.server().world(world);
        if (w == null) throw new IllegalStateException("없는 차원: " + world);
        return w.block(pos);
    }
}
