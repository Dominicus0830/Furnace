// 첫 틱서 플레이어 주변 청크 로드, 이동 후 diff(새 로드+언로드) 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class WorldChunkTest {

    private static World world(int viewDistance) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                viewDistance, reg.directBits(), 7);
    }

    @Test
    void firstTick_loadsChunksAroundPlayer() {
        World w = world(1); // 3x3 = 9 청크
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "Dom", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        w.addPlayer(p);
        w.tick();
        assertEquals(9, p.loadedChunks().size());
        assertEquals(new ChunkPos(0, 0), p.lastCenter());
        ch.finishAndReleaseAll();
    }

    @Test
    void moveCrossingChunk_loadsNewUnloadsOld() {
        World w = world(1);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "Dom", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        w.addPlayer(p);
        w.tick();                       // 센터 (0,0), 9 청크
        p.applyMove(16, -60, 0, 0, 0, true); // 센터 (1,0)
        w.tick();
        // 여전히 3x3=9 청크, 센터만 이동
        assertEquals(9, p.loadedChunks().size());
        assertEquals(new ChunkPos(1, 0), p.lastCenter());
        assertTrue(p.loadedChunks().contains(new ChunkPos(2, 0)));   // 새로 로드
        assertFalse(p.loadedChunks().contains(new ChunkPos(-1, 0))); // 언로드됨
        ch.finishAndReleaseAll();
    }
}
