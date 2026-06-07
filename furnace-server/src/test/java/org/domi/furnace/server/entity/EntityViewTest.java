// EntityView가 Player 상태를 불변 스냅샷으로 담는지 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class EntityViewTest {

    @Test
    void ofPlayer_capturesState() {
        UUID id = UUID.randomUUID();
        Player p = new Player(7, id, "Dom", new EmbeddedChannel());
        p.applyMove(10, -60, 20, 90f, 5f, true);
        EntityView v = EntityView.of(p);
        assertEquals(7, v.entityId());
        assertEquals(id, v.uuid());
        assertEquals("Dom", v.name());
        assertEquals(10, v.x());
        assertEquals(90f, v.yaw());
        assertTrue(v.onGround());
    }
}
