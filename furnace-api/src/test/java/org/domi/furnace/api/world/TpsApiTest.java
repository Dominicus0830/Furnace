package org.domi.furnace.api.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** TPS API 의 기본 구현(ServerTps 모양, World.region(id)·tpsAt 이 regionAt/regions 위에서 도는 것). */
class TpsApiTest {

    private static RegionInfo info(int id, double tps, int players) {
        return new RegionInfo(id, "minecraft:overworld", 4, 0, 0, 15, 15, null, tps, 1_000_000L, 2_000_000L, players);
    }

    @Test
    void serverTpsPaperShapeAndUnknown() {
        var t = new ServerTps(19.0, 18.0, 17.0, 16.0, 2, 3, 15.0, 14.0, 20, 20, 20, 20);
        assertEquals(19.0, t.current());
        assertEquals(List.of(18.0, 17.0, 16.0), java.util.Arrays.stream(t.oneFiveFifteen()).boxed().toList());
        assertTrue(Double.isNaN(ServerTps.UNKNOWN.current()));
        assertEquals(0, ServerTps.UNKNOWN.activeRegions());
    }

    @Test
    void regionInfoActiveMeansPlayersPresent() {
        assertTrue(info(1, 20, 2).active());
        assertFalse(info(2, 20, 0).active());
    }

    @Test
    void regionByIdAndTpsAtUseRegionAtAndRegions() {
        World w = worldWith(List.of(info(1, 20.0, 1), info(2, Double.NaN, 0)));
        assertEquals(1, w.region(1).orElseThrow().id());
        assertTrue(w.region(9).isEmpty());
        assertEquals(20.0, w.tpsAt(0, 0).orElseThrow());
        assertTrue(w.tpsAt(100, 100).isEmpty(), "region 이 없는 위치");
        assertTrue(w.tpsAt(1, 1).isEmpty(), "아직 잴 수 없는(NaN) region");
    }

    /** regionAt/regions 만 구현한 월드: (0,0) 청크는 1번, (1,1) 청크는 2번 region, 나머지는 없음. default 메서드는 그대로 돈다. */
    private static World worldWith(List<RegionInfo> list) {
        return (World) java.lang.reflect.Proxy.newProxyInstance(TpsApiTest.class.getClassLoader(), new Class<?>[] {World.class}, (proxy, m, args) -> {
            if (m.getName().equals("regions")) return list;
            if (m.getName().equals("regionAt") && args.length == 2) {
                int x = (Integer) args[0], z = (Integer) args[1];
                return x == 0 && z == 0 ? Optional.of(list.get(0)) : x == 1 && z == 1 ? Optional.of(list.get(1)) : Optional.empty();
            }
            if (m.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, args);
            throw new UnsupportedOperationException(m.getName());
        });
    }
}
