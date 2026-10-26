// S→C 청크 데이터+라이트(0x27). body는 ChunkSerializer가 채운 ByteBuf를 그대로 싣는다.
// shared 는 World 본문 캐시의 byte[](청크 버전마다 하나) — 있으면 압축 프레임을 플레이어끼리 공유한다(SharedChunkFrames).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.protocol.Packet;

public record LevelChunkWithLight(ByteBuf body, byte[] shared) implements Packet {

    public static final int ID = 0x27;

    public LevelChunkWithLight(ByteBuf body) {
        this(body, null);
    }

    /** 캐시된 본문 배열로 만든다(배열은 바꾸지 않는다는 약속). */
    public static LevelChunkWithLight ofShared(byte[] body) {
        return new LevelChunkWithLight(Unpooled.wrappedBuffer(body), body);
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(body);
        body.release();
    }
}
