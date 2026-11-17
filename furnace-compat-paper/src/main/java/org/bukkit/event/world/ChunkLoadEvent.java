package org.bukkit.event.world;

import org.bukkit.Chunk;
import org.bukkit.event.HandlerList;

public class ChunkLoadEvent extends ChunkEvent {

    private final boolean newChunk;

    public ChunkLoadEvent(Chunk chunk) { this(chunk, false); }

    public ChunkLoadEvent(Chunk chunk, boolean newChunk) {
        super(chunk);
        this.newChunk = newChunk;
    }

    /** 이번에 생성기가 처음 만든 청크인가(저장된 청크를 읽은 것이면 거짓) — 서버 ChunkLoadEvent 의 newChunk 를 그대로 잇는다(A15-04). */
    public boolean isNewChunk() { return newChunk; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
