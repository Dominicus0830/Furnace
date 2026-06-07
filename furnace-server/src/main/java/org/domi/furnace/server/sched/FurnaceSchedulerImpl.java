// FurnaceScheduler 파사드 구현. M0는 전역 owner 1개 + region owner 1개(월드 전체).
package org.domi.furnace.server.sched;

import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;

public final class FurnaceSchedulerImpl implements FurnaceScheduler, AutoCloseable {

    private final GlobalThread global;
    private final RegionThread region; // M0: 모든 RegionPos가 이 하나에 매핑
    private volatile Runnable regionTick = () -> {};

    public FurnaceSchedulerImpl(long tickNanos) {
        this.global = new GlobalThread("furnace-global");
        this.region = new RegionThread("furnace-region-0", tickNanos, () -> regionTick.run());
    }

    /** region 틱마다 실행할 콜백 등록(World.tick 연결용). */
    public void setRegionTick(Runnable tick) {
        this.regionTick = tick;
    }

    @Override
    public GlobalScheduler global() {
        return global;
    }

    @Override
    public TaskScheduler region(RegionPos pos) {
        return region; // M0: pos 무시, 단일 region
    }

    /** M0 내부/테스트용 — 단일 region 스레드 직접 접근. */
    public RegionThread regionThread() {
        return region;
    }

    @Override
    public void close() {
        region.close();
        global.close();
    }
}
