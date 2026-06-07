// S→C 청크 언로드(플레이어 시야 벗어남).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record UnloadChunk(int chunkX, int chunkZ) implements Packet {

    public static final int ID = 0x21;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        // forget_level_chunk = ChunkPos를 단일 long으로: (x & 0xFFFFFFFF) | (z << 32).
        // 로드 패킷(Int X,Z)과 포맷이 다르다 — 두 Int로 쓰면 클라가 (z,x)로 전치 해석해 엉뚱한 청크를 언로드.
        buf.writeLong(((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32));
    }
}
