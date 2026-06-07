// 두 플레이어 가시성: 등록 시 서로 추적, 먼 플레이어 미추적
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class WorldVisibilityTest {

    private static World world() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                4, reg.directBits(), 7);
    }

    @Test
    void twoNearbyPlayers_trackEachOther() {
        World w = world();
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        b.applyMove(5, -60, 5, 0, 0, true);
        w.addPlayer(a); w.addPlayer(b);
        w.tick();
        assertTrue(a.tracked().containsKey(2)); // A가 B 추적
        assertTrue(b.tracked().containsKey(1)); // B가 A 추적
    }

    @Test
    void farPlayer_notTracked() {
        World w = world(); // viewDistance 4 → 64블록
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        b.applyMove(1000, -60, 1000, 0, 0, true);
        w.addPlayer(a); w.addPlayer(b);
        w.tick();
        assertFalse(a.tracked().containsKey(2));
    }
}
