// FurnaceScheduler 파사드 구현: 전역 owner 1개 + region 스케줄러. region 은 월드의 동적 region(Folia 방식)이 맡는다 —
// 월드가 붙으면(installRouter) RegionPos(청크 좌표) 작업은 그 청크를 가진 region 에서 돈다. 월드 없이(스케줄러 단독 테스트)는
// 예전처럼 고정 스레드로 나눈다.
package org.domi.furnace.server.sched;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;

public final class FurnaceSchedulerImpl implements FurnaceScheduler, AutoCloseable {

    /** 청크 좌표 → 그 청크를 가진 region 의 스케줄러. */
    @FunctionalInterface
    public interface RegionRouter {
        TaskScheduler forChunk(int chunkX, int chunkZ);
    }

    private final GlobalThread global;
    private final long tickNanos;
    private final int regionThreads;
    private volatile RegionThread[] fallback;
    private volatile RegionRouter router;
    private final List<Runnable> closeHooks = new ArrayList<>();

    /** 백호환: region 스레드 1개. */
    public FurnaceSchedulerImpl(long tickNanos) {
        this(tickNanos, 1);
    }

    public FurnaceSchedulerImpl(long tickNanos, int regionThreads) {
        this.global = new GlobalThread("furnace-global");
        this.tickNanos = tickNanos;
        this.regionThreads = Math.max(1, regionThreads);
    }

    @Override
    public GlobalScheduler global() {
        return global;
    }

    @Override
    public boolean isTickThread() {
        return GlobalThread.isGlobalThread() || RegionThread.isRegionThread() || org.domi.furnace.server.world.Regionizer.isRegionWorkerThread();
    }

    /** RegionPos 는 청크 좌표 — 그 청크를 가진 region 에서 돈다. */
    @Override
    public TaskScheduler region(RegionPos pos) {
        RegionRouter r = router;
        if (r != null) return r.forChunk(pos.x(), pos.z());
        RegionThread[] f = fallback();
        return f[Math.floorMod(pos.x() * 31 + pos.z(), f.length)];
    }

    /** 월드가 region 라우팅을 맡는다. onClose 는 close() 때 부른다(region 작업 스레드 정리). */
    public synchronized void installRouter(RegionRouter router, Runnable onClose) {
        this.router = router;
        if (onClose != null) closeHooks.add(onClose);
    }

    /** 라우터 없이 종료 훅만(오버월드가 아닌 차원 월드 — 플러그인 RegionPos 는 오버월드 청크다). */
    public synchronized void addCloseHook(Runnable onClose) {
        if (onClose != null) closeHooks.add(onClose);
    }

    /** 종료 훅을 뗀다(실행 중에 내린 월드 — 이미 스스로 닫았다). 같은 객체를 넘겨야 한다. */
    public synchronized void removeCloseHook(Runnable onClose) {
        closeHooks.remove(onClose);
    }

    public long tickNanos() { return tickNanos; }

    /** region 작업 스레드 수(설정값). */
    public int regionThreadCount() {
        return regionThreads;
    }

    private RegionThread[] fallback() {
        RegionThread[] f = fallback;
        if (f == null) {
            synchronized (this) {
                if (fallback == null) {
                    RegionThread[] arr = new RegionThread[regionThreads];
                    for (int i = 0; i < arr.length; i++) arr[i] = new RegionThread("furnace-region-fallback-" + i, tickNanos, () -> {});
                    fallback = arr;
                }
                f = fallback;
            }
        }
        return f;
    }

    /** 월드 없는 고정 스레드(스케줄러 단독 테스트용). */
    public RegionThread regionThread(int index) {
        return fallback()[index];
    }

    public RegionThread regionThread() {
        return fallback()[0];
    }

    @Override
    public void close() {
        List<Runnable> hooks;
        synchronized (this) {
            hooks = new ArrayList<>(closeHooks);
        }
        for (Runnable h : hooks) {
            try { h.run(); } catch (Throwable ignored) { }
        }
        RegionThread[] f = fallback;
        if (f != null) for (RegionThread r : f) r.close();
        global.close();
    }
}
