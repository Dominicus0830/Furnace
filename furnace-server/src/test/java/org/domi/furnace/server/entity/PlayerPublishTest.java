// 위치 발행/드레인 라운드트립 + regionPos + 퇴장 플래그 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.api.sched.RegionPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class PlayerPublishTest {

    @Test
    void publishThenDrain_appliesPosition() {
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        p.publishPosition(100, -60, 200, 90f, 10f, true);
        assertEquals(0, p.x()); // 드레인 전엔 미적용
        p.drainPending();
        assertEquals(100, p.x());
        assertEquals(90f, p.yaw());
    }

    @Test
    void disconnect_flag() {
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        assertFalse(p.isDisconnected());
        p.markDisconnected();
        assertTrue(p.isDisconnected());
    }
}
