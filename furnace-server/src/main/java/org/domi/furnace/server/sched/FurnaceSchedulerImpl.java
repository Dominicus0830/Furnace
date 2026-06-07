// FurnaceScheduler 파사드 구현. M3b: 전역 owner 1개 + region owner N개(고정 그리드).
package org.domi.furnace.server.sched;

import java.util.function.IntConsumer;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.server.world.RegionGrid;

public final class FurnaceSchedulerImpl implements FurnaceScheduler, AutoCloseable {

    private final GlobalThread global;
    private final RegionThread[] regions;
    private volatile IntConsumer regionTick = i -> {};

    /** 백호환: region 스레드 1개. */
    public FurnaceSchedulerImpl(long tickNanos) {
        this(tickNanos, 1);
    }

    public FurnaceSchedulerImpl(long tickNanos, int regionThreads) {
        this.global = new GlobalThread("furnace-global");
        this.regions = new RegionThread[regionThreads];
        for (int i = 0; i < regionThreads; i++) {
            int idx = i;
            this.regions[i] = new RegionThread("furnace-region-" + i, tickNanos,
                    () -> regionTick.accept(idx));
        }
    }

    @Override
    public GlobalScheduler global() {
        return global;
    }

    @Override
    public TaskScheduler region(RegionPos pos) {
        return regions[RegionGrid.threadIndex(pos, regions.length)];
    }

    /** region 틱 콜백(스레드 인덱스 전달). World.tickThread 연결용. */
    public void setRegionTick(IntConsumer tick) {
        this.regionTick = tick;
    }

    public int regionThreadCount() {
        return regions.length;
    }

    /** M0/테스트용 — 첫 region 스레드 접근. */
    public RegionThread regionThread() {
        return regions[0];
    }

    @Override
    public void close() {
        for (RegionThread r : regions) r.close();
        global.close();
    }
}
