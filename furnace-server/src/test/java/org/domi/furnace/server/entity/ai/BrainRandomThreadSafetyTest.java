// O06 C3: Sensor 의 시작 틱 난수는 여러 region 스레드가 함께 쓰므로 스레드 안전 난수여야 하고, ShufflingList 는 바닐라처럼 목록마다 난수를 가진다.
package org.domi.furnace.server.entity.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.ThreadSafeLegacyRandomSource;
import org.domi.furnace.server.entity.ai.behavior.ShufflingList;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.junit.jupiter.api.Test;

class BrainRandomThreadSafetyTest {

    @Test
    void sensorSharedRandomIsThreadSafe() throws Exception {
        Field f = Sensor.class.getDeclaredField("RANDOM");
        f.setAccessible(true);
        assertTrue(Modifier.isStatic(f.getModifiers()));
        assertInstanceOf(ThreadSafeLegacyRandomSource.class, f.get(null));
    }

    @Test
    void shufflingListHasItsOwnRandomPerList() throws Exception {
        Field f = ShufflingList.class.getDeclaredField("random");
        f.setAccessible(true);
        assertFalse(Modifier.isStatic(f.getModifiers()), "목록마다 난수 — 정적으로 공유하면 region 스레드끼리 상태를 밟는다");
        var a = new ShufflingList<String>();
        var b = new ShufflingList<String>();
        assertNotSame(f.get(a), f.get(b));
        assertInstanceOf(RandomSource.class, f.get(a));
    }

    @Test
    void shufflingStillOrdersByWeightedKey() {
        // 가중치가 큰 항목이 앞에 올 확률이 높다(바닐라 -pow(난수, 1/가중치) 키)
        int heavyFirst = 0;
        for (int i = 0; i < 2000; i++) {
            var l = new ShufflingList<String>().add("light", 1).add("heavy", 20);
            List<String> out = l.shuffle().asList();
            if (out.get(0).equals("heavy")) heavyFirst++;
        }
        assertTrue(heavyFirst > 1700, "heavyFirst=" + heavyFirst);
    }
}
