// Player 위치 갱신·청크좌표 계산 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class PlayerTest {

    @Test
    void applyMove_updatesPositionAndChunk() {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "Dom", ch);
        p.applyMove(33.0, -60.0, -17.0, 90f, 0f, true);
        assertEquals(33.0, p.x());
        assertEquals(90f, p.yaw());
        assertEquals(new ChunkPos(2, -2), p.chunkPos()); // floor(33/16)=2, floor(-17/16)=-2
        ch.finishAndReleaseAll();
    }
}
