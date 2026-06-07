// play 진입 패킷들이 예외 없이 직렬화되고 올바른 id를 갖는지 스모크 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class PlayPacketsTest {

    @Test
    void loginPlay_writesWithoutError() {
        LoginPlay p = new LoginPlay(1, "minecraft:overworld", 0);
        assertEquals(0x2B, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertTrue(buf.readableBytes() > 0);
        buf.release();
    }

    @Test
    void syncPlayerPosition_writes_teleportId() {
        SyncPlayerPosition p = new SyncPlayerPosition(0, 64, 0, 42);
        assertEquals(0x40, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertTrue(buf.readableBytes() > 0);
        buf.release();
    }

    @Test
    void setDefaultSpawn_writes() {
        SetDefaultSpawnPosition p = new SetDefaultSpawnPosition(0, 64, 0);
        assertEquals(0x54, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertTrue(buf.readableBytes() > 0);
        buf.release();
    }
}
