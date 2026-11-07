// enable-jmx-monitoring: 바닐라 MinecraftServerStatistics 와 같은 이름·속성의 MBean.
package org.domi.furnace.server.profiling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.domi.furnace.server.sched.TickTimeRing;
import org.junit.jupiter.api.Test;

class JmxMonitoringTest {

    @Test
    void registersVanillaNameAndAttributes() throws Exception {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        TickTimeRing ring = new TickTimeRing(512);
        for (int i = 0; i < 130; i++) ring.record(10_000_000L + i);   // 10ms 남짓
        JmxMonitoring bean = JmxMonitoring.register(server, () ->
                new JmxMonitoring.Snapshot(ring.recentByTickNumber(JmxMonitoring.SAMPLES), ring.count()));
        assertNotNull(bean);
        ObjectName name = new ObjectName("net.minecraft.server:type=Server");
        assertTrue(server.isRegistered(name));

        long[] times = (long[]) server.getAttribute(name, "tickTimes");
        assertEquals(100, times.length);
        // 130 번째 틱(번호 129)은 [29] 자리, 번호 100 은 [0] 자리 — 바닐라 tickTimesNanos[tickCount % 100]
        assertEquals(10_000_000L + 129, times[29]);
        assertEquals(10_000_000L + 100, times[0]);
        assertEquals(10_000_000L + 99, times[99]);

        float avg = (Float) server.getAttribute(name, "averageTickTime");
        assertEquals(10.0F, avg, 0.01F, "지수 평활한 틱 소요(ms)");
        assertThrows(javax.management.AttributeNotFoundException.class, () -> server.getAttribute(name, "nope"));
        assertFalse(server.getMBeanInfo(name).getAttributes()[0].isWritable());

        bean.close();
        assertFalse(server.isRegistered(name));
    }

    @Test
    void ringKeepsTickNumberSlots() {
        TickTimeRing ring = new TickTimeRing(16);
        for (int i = 1; i <= 5; i++) ring.record(i);
        assertArrayEquals(new long[]{1, 2, 3, 4, 5, 0, 0, 0}, ring.recentByTickNumber(8));
        for (int i = 6; i <= 12; i++) ring.record(i);
        assertArrayEquals(new long[]{9, 10, 11, 12, 5, 6, 7, 8}, ring.recentByTickNumber(8));
        assertEquals(12, ring.count());
    }

    @Test
    void smoothingFollowsVanillaFormula() {
        long[] t = new long[100];
        t[0] = 50_000_000L;   // 번호 0: 50ms
        t[1] = 0;             // 번호 1: 0ms
        float expected = 0.0F;
        expected = expected * 0.8F + 50.0F * 0.19999999F;
        expected = expected * 0.8F + 0.0F * 0.19999999F;
        assertEquals(expected, JmxMonitoring.smoothedMillis(t, 2), 1e-4F);
    }
}
