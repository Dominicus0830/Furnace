// 블록이 주체인 이벤트의 바탕 — 좌표와 블록 종류(레지스트리 키, minecraft:stone).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.world.Block;
import org.domi.furnace.api.world.BlockPos;

public abstract class BlockEvent extends Event {

    private final String world;
    private final BlockPos pos;

    protected BlockEvent(String world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    public String world() { return world; }

    public BlockPos pos() { return pos; }

    /** 이 칸의 살아 있는 손잡이(읽지 않는다). 처리기는 그 칸을 가진 region 스레드에서 돌므로 바로 읽고 쓸 수 있다. */
    public Block liveBlock() {
        var w = org.domi.furnace.api.Furnace.server().world(world);
        if (w == null) throw new IllegalStateException("없는 차원: " + world);
        return w.block(pos);
    }
}
