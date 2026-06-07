// join 흐름 패킷(GameEvent, ChunkBatchStart/Finished) id·와이어 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class JoinFlowPacketsTest {

    @Test
    void gameEvent_writesByteAndFloat() {
        GameEvent p = new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f);
        assertEquals(0x22, p.id());
        assertEquals(13, GameEvent.START_WAITING_FOR_CHUNKS);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(13, buf.readByte());
        assertEquals(0f, buf.readFloat());
        buf.release();
    }

    @Test
    void chunkBatchStart_hasNoBody() {
        ChunkBatchStart p = new ChunkBatchStart();
        assertEquals(0x0D, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void chunkBatchFinished_writesBatchSize() {
        ChunkBatchFinished p = new ChunkBatchFinished(121);
        assertEquals(0x0C, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(121, Types.readVarInt(buf));
        buf.release();
    }
}
