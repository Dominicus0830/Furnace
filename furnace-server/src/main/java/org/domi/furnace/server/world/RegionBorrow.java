// 전역 스레드의 region 빌림 구간 — 옛 "주 스레드 하나가 모든 월드를 만진다" 코드(Bukkit 동기 작업·명령)가 region 상태를 동기로 만지게 한다.
// 구간 안에서 소유 스레드 전용 API 가 남의 region 을 만지면 그 region 이 틱 사이가 되기를 기다렸다가 구간이 닫힐 때까지 빌린다(Regionizer.borrow).
// 빌린 region 은 틱을 쉬고 이 스레드가 그 소유 스레드가 된다(Region.tickThread) — 그래서 그 뒤의 소유 검사·assertOwns 가 그대로 통과한다.
// 만지지 않은 region 은 계속 병렬로 돈다. 전역 스레드에서만 연다: region 스레드가 남의 틱을 기다리면 둘이 서로 기다리는 교착이 된다
// region 이 전역을 기다리는 길(명령 블록 awaitUrgent 는 예산이 있다, 전역 결과를 막고 기다리는 GlobalWait, 전역이 쥔 잠금)은 빌리는 쪽이 알아채고 곧바로 포기한다.
// 한 구간에서 못 빌린 region 은 다시 기다리지 않고, 구간 전체의 기다림도 한도 하나를 넘지 않는다. 닫지 않은 구간은 전역 작업이 끝날 때 서버가 닫는다.
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.domi.furnace.api.sched.GlobalScheduler.RegionAccess;
import org.domi.furnace.server.sched.GlobalThread;

public final class RegionBorrow {

    /** 빌리러 기다리는 한도(틱이 멈춘 region 을 끝없이 기다리지 않게). 넘으면 예전처럼 소유 스레드가 아니라고 답한다. */
    static final long WAIT_NANOS = Long.getLong("furnace.regionBorrowWaitMillis", 1_000L) * 1_000_000L;

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    /** 열린 구간 수(서버 전체) — 0 이면 소유 검사가 ThreadLocal 을 보지 않는다. */
    private static final AtomicInteger OPEN = new AtomicInteger();

    // 관측(/furnace·시험): 빌린 횟수, 기다린 시간 합(ns), 못 빌린 횟수(한도 초과·서로 기다림)
    private static final AtomicLong BORROWS = new AtomicLong();
    private static final AtomicLong WAIT_SUM = new AtomicLong();
    private static final AtomicLong TIMEOUTS = new AtomicLong();

    private RegionBorrow() {}

    /** 빌림 구간을 연다. 전역 스레드가 아니면 빈 손잡이. 겹쳐 열면 가장 바깥 손잡이가 닫힐 때 빌린 region 을 모두 돌려준다. */
    public static RegionAccess open() {
        if (!GlobalThread.isGlobalThread()) return RegionAccess.NONE;
        Scope s = CURRENT.get();
        if (s == null) {
            s = new Scope(Thread.currentThread());
            CURRENT.set(s);
            OPEN.incrementAndGet();
        }
        s.depth++;
        return new Handle(s);
    }

    /** 지금 스레드에 열린 빌림 구간이 있는가. */
    public static boolean active() { return OPEN.get() > 0 && CURRENT.get() != null; }

    /**
     * 전역 작업 하나가 끝날 때(GlobalThread.afterExecute): 닫지 않은 구간이 남았으면 경고하고 닫는다(빌린 region 을 돌려준다).
     * 그 구간의 손잡이는 이제 닫힌 것으로 본다(isOpen false, close 는 아무것도 안 한다).
     */
    public static void closeLeaked() {
        if (OPEN.get() == 0) return;
        Scope s = CURRENT.get();
        if (s == null) return;
        org.slf4j.LoggerFactory.getLogger(RegionBorrow.class).warn(
                "[BORROW] 전역 작업이 region 빌림 구간을 닫지 않고 끝났다(빌린 region {}개) — 서버가 닫는다. openRegionAccess() 는 try-with-resources 로 닫는다", s.held.size());
        s.depth = 0;
        s.close();
    }

    /**
     * 지금 스레드가 그 청크를 가진 region 의 소유 스레드인가 — 빌림 구간 안이면 빌려서라도 그렇게 만든다.
     * 주인 region 이 없으면(로드 안 된 곳) false.
     */
    public static boolean claimChunk(World w, int cx, int cz) {
        if (w == null) return false;
        Regionizer rz = w.regionizer();
        Region r = rz.ownerOf(cx, cz);
        if (r == null) return false;
        if (r.isOwnerThread()) return true;
        Scope s = scope();
        if (s == null) return false;
        for (int i = 0; i < 4; i++) {   // 기다리는 사이 나뉘어 그 청크가 다른 region 으로 갔으면 새 주인을 다시 빌린다
            if (s.borrow(rz, r) == null) return false;
            Region now = rz.ownerOf(cx, cz);
            if (now == null) return false;
            if (now.isOwnerThread()) return true;
            r = now;
        }
        return false;
    }

    /** 지금 스레드가 current 가 알려 주는 region 의 소유 스레드인가 — 빌림 구간 안이면 빌려서라도. current 는 기다린 뒤 다시 부른다. */
    public static boolean claim(Supplier<Region> current) {
        Region r = current.get();
        if (r == null) return false;
        if (r.isOwnerThread()) return true;
        Scope s = scope();
        if (s == null) return false;
        for (int i = 0; i < 4; i++) {
            if (s.borrow(r.world().regionizer(), r) == null) return false;
            Region now = current.get();
            if (now == null) return false;
            if (now.isOwnerThread()) return true;
            r = now;
        }
        return false;
    }

    /** 엔티티를 가진 region(엔티티의 레벨)을 빌려서라도 소유한다. region 레벨에 있지 않은 엔티티는 false. */
    public static boolean claimEntity(org.domi.furnace.server.entity.Entity e) {
        return claim(() -> e.level() instanceof RegionLevel rl ? rl.region() : null);
    }

    /** 관측: (빌린 횟수, 기다린 시간 합 ns, 못 빌린 횟수 — 한도 초과·서로 기다림). */
    public static long[] stats() { return new long[] {BORROWS.get(), WAIT_SUM.get(), TIMEOUTS.get()}; }

    private static Scope scope() {
        if (OPEN.get() == 0) return null;
        return CURRENT.get();
    }

    /** 한 스레드의 빌림 구간. 그 스레드만 만진다. */
    private static final class Scope {
        final Thread owner;
        int depth;
        boolean closed;
        final List<Region> held = new ArrayList<>();
        /** 이번 구간에서 못 빌린 region — 다시 만져도 기다리지 않는다(멈춘 region 의 칸을 여럿 읽을 때 한도를 거듭 기다리지 않게). */
        final java.util.Set<Region> failed = new java.util.HashSet<>();
        /** 이번 구간에서 빌리러 기다린 시간 합 — 한도 하나를 넘으면 그 뒤로는 쉬는 region 만 곧바로 쥔다. */
        long waited;

        Scope(Thread owner) { this.owner = owner; }

        Region borrow(Regionizer rz, Region r) {
            if (failed.contains(r)) return null;
            long budget = Math.max(0L, WAIT_NANOS - waited);
            long t0 = System.nanoTime();
            String[] why = new String[1];
            Region got = rz.borrow(r, budget, why);
            long took = System.nanoTime() - t0;
            waited += took;
            WAIT_SUM.addAndGet(took);
            if (got == null) {
                failed.add(r);
                if (budget == 0 && why[0] != null) why[0] = "이번 구간의 기다림 한도(" + WAIT_NANOS / 1_000_000L + "ms)를 이미 다 썼다";
                if (why[0] != null) {
                    TIMEOUTS.incrementAndGet();
                    org.slf4j.LoggerFactory.getLogger(RegionBorrow.class).warn("[BORROW] region #{} 을 빌리지 못했다 — {}", r.id(), why[0]);
                }
                return null;
            }
            if (got.borrowed && got.tickThread == owner && !held.contains(got)) {
                held.add(got);
                BORROWS.incrementAndGet();
            }
            return got;
        }

        void close() {
            if (closed) return;
            closed = true;
            if (CURRENT.get() == this) CURRENT.remove();
            OPEN.decrementAndGet();
            // 늦게 빌린 것부터 돌려준다. 빌린 사이 다른 region 을 흡수했을 수 있으나 돌려주는 단위는 쥔 region 그대로다
            for (int i = held.size() - 1; i >= 0; i--) {
                Region r = held.get(i);
                r.world().regionizer().giveBack(r);
            }
            held.clear();
            failed.clear();
        }
    }

    /** 열 때마다 하나 — 두 번 닫아도 한 번만 센다. */
    private static final class Handle implements RegionAccess {
        private Scope scope;

        Handle(Scope scope) { this.scope = scope; }

        @Override
        public void close() {
            Scope s = scope;
            if (s == null || Thread.currentThread() != s.owner) return;
            scope = null;
            if (s.closed) return;   // 서버가 이미 닫았다(closeLeaked)
            if (--s.depth == 0) s.close();
        }

        @Override
        public boolean isOpen() {
            Scope s = scope;
            return s != null && !s.closed;
        }
    }
}
