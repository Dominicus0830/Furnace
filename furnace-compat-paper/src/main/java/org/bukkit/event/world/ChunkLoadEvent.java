package org.bukkit.event.world;

import org.bukkit.Chunk;
import org.bukkit.event.HandlerList;

public class ChunkLoadEvent extends ChunkEvent {

    public ChunkLoadEvent(Chunk chunk) { super(chunk); }

    /** 처음 만들어진 청크인가 — Furnace 의 청크 불러오기 이벤트는 이 구분을 알리지 않는다(H3 에서 더한다). */
    public boolean isNewChunk() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("ChunkLoadEvent#isNewChunk", "청크 이벤트가 새로 만든 청크인지 알려 주지 않는다");
    }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
