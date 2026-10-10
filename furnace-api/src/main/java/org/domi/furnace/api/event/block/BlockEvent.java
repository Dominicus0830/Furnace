// 블록이 주체인 이벤트의 바탕 — 좌표와 블록 종류(레지스트리 키, minecraft:stone).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Event;
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
}
