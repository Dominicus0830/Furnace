// furnace-api만 의존해 컴파일되는 샘플. API/구현 분리 증명용(M0).
package org.domi.furnace.plugintest;

import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.RegionPos;

public final class SamplePlugin {

    private final FurnaceScheduler scheduler;

    public SamplePlugin(FurnaceScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** region (0,0)에서 비동기 작업 예약. 구현 클래스는 전혀 모른다. */
    public void scheduleHello() {
        scheduler.region(new RegionPos(0, 0))
                .execute(() -> System.out.println("[SamplePlugin] hello from region thread"));
    }
}
