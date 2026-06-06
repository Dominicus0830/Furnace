// RegionThread가 실제로 ~20 TPS를 유지하는지 실시간 측정으로 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RegionThreadTpsTest {

    @Test
    void maintainsAbout20Tps() throws Exception {
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, () -> {})) {
            Thread.sleep(1500); // measuredTps가 최소 한 번 갱신되도록 1초 이상
            double tps = r.measuredTps();
            assertTrue(tps >= 17.0 && tps <= 23.0, "measuredTps=" + tps);
        }
    }
}
