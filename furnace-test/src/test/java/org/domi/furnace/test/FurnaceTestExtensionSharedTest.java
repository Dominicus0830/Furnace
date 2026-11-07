// JUnit 확장을 static 필드로: 클래스의 시험이 서버 하나를 함께 쓰고, @Nested 클래스도 바깥 서버를 그대로 쓴다(닫지 않는다).
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FurnaceTestExtensionSharedTest {

    @RegisterExtension
    static final FurnaceTestExtension furnace = FurnaceTestExtension.create();

    static FurnaceTestServer first;

    @Test
    @Order(1)
    void outerTicks(FurnaceTestServer server) {
        first = server;
        server.tick(3);
        assertEquals(3, server.currentTick());
    }

    @Nested
    class Inner {
        @Test
        void sharesOuterServer(FurnaceTestServer server) {
            assertSame(first, server, "@Nested 도 바깥 클래스가 연 서버");
            assertEquals(3, server.currentTick(), "앞 시험의 틱이 남는다");
            server.tick();
        }
    }
}
