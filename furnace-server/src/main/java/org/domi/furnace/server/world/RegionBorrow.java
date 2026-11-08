// 전역 스레드의 region 빌림 구간 — 옛 "주 스레드 하나가 모든 월드를 만진다" 코드(Bukkit 동기 작업·명령)가 region 상태를 동기로 만지게 한다.
// 구간 안에서 소유 스레드 전용 API 가 남의 region 을 만지면 그 region 이 틱 사이가 되기를 기다렸다가 구간이 닫힐 때까지 빌린다(Regionizer.borrow).
// 빌린 region 은 틱을 쉬고 이 스레드가 그 소유 스레드가 된다(Region.tickThread) — 그래서 그 뒤의 소유 검사·assertOwns 가 그대로 통과한다.
// 만지지 않은 region 은 계속 병렬로 돈다. 전역 스레드에서만 연다: region 스레드가 남의 틱을 기다리면 둘이 서로 기다리는 교착이 된다
// (region 이 전역을 기다리는 길은 명령 블록의 awaitUrgent 뿐이고 예산이 있어 풀린다).
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
    static final long WAIT_NANOS = Long.getLong("furnace.regionBorrowWaitMillis", 5_000L) * 1_000_000L;

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    /** 열린 구간 수(서버 전체) — 0 이면 소유 검사가 ThreadLocal 을 보지 않는다. */
    private static final AtomicInteger OPEN = new AtomicInteger();

    // 관측(/furnace·시험): 빌린 횟수, 기다린 시간 합(ns), 한도 초과 횟수
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

    /** 관측: (빌린 횟수, 기다린 시간 합 ns, 한도 초과 횟수). */
    public static long[] stats() { return new long[] {BORROWS.get(), WAIT_SUM.get(), TIMEOUTS.get()}; }

    private static Scope scope() {
        if (OPEN.get() == 0) return null;
        return CURRENT.get();
    }

    /** 한 스레드의 빌림 구간. 그 스레드만 만진다. */
    private static final class Scope {
        final Thread owner;
        int depth;
        final List<Region> held = new ArrayList<>();

        Scope(Thread owner) { this.owner = owner; }

        Region borrow(Regionizer rz, Region r) {
            long t0 = System.nanoTime();
            Region got = rz.borrow(r, WAIT_NANOS);
            WAIT_SUM.addAndGet(System.nanoTime() - t0);
            if (got == null) {
                if (System.nanoTime() - t0 >= WAIT_NANOS) {
                    TIMEOUTS.incrementAndGet();
                    org.slf4j.LoggerFactory.getLogger(RegionBorrow.class).warn(
                            "[BORROW] region #{} 을 {}ms 안에 빌리지 못했다 — 틱이 멈췄거나 너무 길다", r.id(), WAIT_NANOS / 1_000_000L);
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
            CURRENT.remove();
            OPEN.decrementAndGet();
            // 늦게 빌린 것부터 돌려준다. 빌린 사이 다른 region 을 흡수했을 수 있으나 돌려주는 단위는 쥔 region 그대로다
            for (int i = held.size() - 1; i >= 0; i--) {
                Region r = held.get(i);
                r.world().regionizer().giveBack(r);
            }
            held.clear();
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
            if (--s.depth == 0) s.close();
        }
    }
}
