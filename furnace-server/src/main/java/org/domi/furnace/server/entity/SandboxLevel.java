package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkColumn;

/**
 * 어느 월드에도 속하지 않은 빈 레벨(엔티티 데이터 설계 3.1·3.2): 엔티티를 만들어 칸을 읽고 버릴 때 쓴다. 청크가 없고(블록 없음) 저장·방송을 하지 않으며,
 * 엔티티 번호를 자기 안에서 센다 — 전역 엔티티 번호 발급기와 월드 난수를 건드리지 않는다. 레벨 난수도 이 레벨 것이다. 한 번 쓰고 버린다.
 */
public final class SandboxLevel extends ChunkLevel {
    private int ids;

    public SandboxLevel() { super(1); }

    @Override protected ChunkColumn chunk(int chunkX, int chunkZ) { return null; }
    @Override protected void markDirty(int chunkX, int chunkZ) {}
    @Override protected void broadcastToChunk(int chunkX, int chunkZ, Packet packet) {}
    @Override protected LevelServices services() { return LevelServices.DEFAULT; }
    @Override public int nextEntityId() { return ++ids; }
    @Override public int nextEntityIds(int count) { int first = ids + 1; ids += count; return first; }
}
