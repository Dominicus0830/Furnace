// JUnit 확장: 시험마다 새 서버(앞 시험의 틱·상태가 남지 않는다), 인자로 서버를 받는다.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FurnaceTestExtensionTest {

    @RegisterExtension
    final FurnaceTestExtension furnace = FurnaceTestExtension.with(b -> b.plugin(CounterPlugin.class));

    @Test
    @Order(1)
    void first(FurnaceTestServer server) {
        assertSame(server, furnace.server());
        server.tick(7);
        assertEquals(7, server.plugin(CounterPlugin.class).globalTicks.get());
    }

    @Test
    @Order(2)
    void secondGetsAFreshServer(FurnaceTestServer server) {
        assertEquals(0, server.currentTick());
        assertEquals(0, server.plugin(CounterPlugin.class).globalTicks.get());
    }
}
