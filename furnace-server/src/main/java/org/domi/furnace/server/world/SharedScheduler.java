// region 틱 작업 스레드 풀. 등록된 Regionizer(차원)마다 잠금 없이 발행된 "큐 머리 마감"(headDue)을 보고 가장 이른 차원을 고르고,
// 꺼내기·끝내기만 그 차원 잠금 안에서 한다(tryTake/finishTick). 깨우기는 LockSupport.unpark — 풀에는 잠금이 없다.
// 서버 전체 하나(S4, 기본)이거나 shared-region-pool=false 면 차원마다 풀 하나(이전 방식).
// 공정성(§4.1): EDF + 머리가 2틱 넘게 밀린 차원 먼저 + 다른 차원에 할 일이 있으면 한 차원은 T-1 워커까지.
// 설계: docs/feature-parity/shared-scheduler-design-2026-10-28.md §2.1·§3·§4
package org.domi.furnace.server.world;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.sched.PreciseWait;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SharedScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SharedScheduler.class);

    /** 자는 상한(안전망). 깨우기를 놓쳐도 이만큼 뒤에는 다시 고른다 — 지금 코드의 await(50ms) 와 같다. */
    static final long PARK_MAX = 50_000_000L;

    private final CopyOnWriteArrayList<Regionizer> dims = new CopyOnWriteArrayList<>();
    private final Thread[] workers;
    private volatile boolean running = true;
    /** 자는 워커. until 은 갱신될 수 있다(N2). */
    private final ConcurrentLinkedQueue<Parked> parked = new ConcurrentLinkedQueue<>();
    private final AtomicLong wakeups = new AtomicLong();
    /** 계측: PARK 만료로 깼는데 깰 시각(until)보다 1ms 넘게 이른, 이미 지난 머리가 있었던 횟수(깨우기 유실 의심) / 만료 복귀 수 / tryTake 실패 수. */
    private final java.util.concurrent.atomic.LongAdder missedWakeups = new java.util.concurrent.atomic.LongAdder(),
            timeouts = new java.util.concurrent.atomic.LongAdder(), takeMisses = new java.util.concurrent.atomic.LongAdder();
    static final long MISSED_NANOS = 1_000_000L;
    private final AtomicLong waitNanos = new AtomicLong();
    /** 거친 타이머 플랫폼에서 마지막 구간을 돌며 기다릴 길이(ns). 0(리눅스 기본)이면 스핀 경로가 없다. */
    private final long spinMargin;
    /** 스핀 자리(서버 전체 1개, §5). 쓰기는 항상 min(earlier), 끝날 때만 MAX. */
    private final AtomicBoolean spinning = new AtomicBoolean();
    private volatile Thread spinner;
    private final AtomicLong spinDeadline = new AtomicLong(Long.MAX_VALUE);
    /** 스핀 자리를 잡을 남은 거친 구간 상한: 기다림 - spinMargin 이 이 이하이면 돈다. */
    static final long SPIN_HANDOFF = 1_000_000L;

    /** 시험 전용 훅(프로덕션은 null — 동작 불변). sleep ① 등록 직후 / park 복귀 직후·자기 제거 직전에 불린다. */
    volatile java.util.function.Consumer<Parked> afterRegisterHook;
    volatile java.util.function.Consumer<Parked> afterParkHook;

    /** 시험 전용: 깨우는 쪽이 p 를 뽑은 것처럼 목록에서 빼고 깨운다. */
    boolean pickForTest(Parked p) {
        boolean r = parked.remove(p);
        if (r) LockSupport.unpark(p.t);
        return r;
    }

    /** 시험 전용: p 가 아직 자는 목록에 있나(없으면 누가 뽑아 깨웠다). */
    boolean isParkedForTest(Parked p) { return parked.contains(p); }

    /** 시험 전용: 지금 자는(등록된) 워커 수. */
    int parkedCount() { return parked.size(); }

    /** 한 차원의 동시 실행 상한(다른 차원 큐에 할 일이 있을 때만). 워커 1개면 상한 없음. */
    private final int perDimCap;
    /** 굶음 판정: 머리 지연 >= 이 배수 × 그 차원 틱 주기. */
    private final int starveTicks;
    /** 같은 클래스에서 마감 차가 이 이하면 동률 — rr 로 번갈아 고른다. */
    static final long TIE_NANOS = 1_000L;
    private final java.util.concurrent.atomic.AtomicInteger rr = new java.util.concurrent.atomic.AtomicInteger();

    /** 서버 전체 공유 풀을 만들고 시작한다(시험·벤치 — 상한·굶음 값은 -Dfurnace.sched.* 또는 기본값). */
    public static SharedScheduler startShared(int threads, String name) {
        SharedScheduler p = new SharedScheduler(threads, name);
        p.start();
        return p;
    }

    /**
     * 서버 전체 공유 풀(FurnaceMain, shared-region-pool=true). dimCap 0 이면 워커 수−1, starveTicks 는 굶음 판정 틱 배수
     * (furnace.properties 의 region-pool-dim-cap·region-pool-starve-ticks).
     */
    public static SharedScheduler startShared(int threads, String name, int dimCap, int starveTicks) {
        SharedScheduler p = new SharedScheduler(threads, name, PreciseWait.spinMargin(), dimCap, starveTicks);
        p.start();
        return p;
    }

    /**
     * 시험 전용(-Dfurnace.test.sharedRegionPool=true): int 생성자로 만든 월드도 워커 수별 프로세스 공용 풀에 건다 — 기존 시험을
     * 공유 풀 모드로 다시 돌리기 위한 것. 운영 경로(FurnaceMain)는 이 값을 보지 않는다.
     */
    static final boolean TEST_SHARED = Boolean.getBoolean("furnace.test.sharedRegionPool");
    private static final java.util.concurrent.ConcurrentHashMap<Integer, SharedScheduler> TEST_POOLS = new java.util.concurrent.ConcurrentHashMap<>();

    static SharedScheduler testShared(int threads) {
        return TEST_POOLS.computeIfAbsent(Math.max(1, threads), t -> startShared(t, "furnace-region-"));
    }

    static final class Parked {
        final Thread t;
        volatile long until;

        Parked(Thread t, long until) {
            this.t = t;
            this.until = until;
        }
    }

    /** threads 개 워커를 만들고 시작한다. 이름은 name + 번호. */
    SharedScheduler(int threads, String name) { this(threads, name, PreciseWait.spinMargin()); }

    /** spinMargin 을 직접 준다(시험 — 리눅스에서도 스핀 경로를 돈다). */
    SharedScheduler(int threads, String name, long spinMargin) {
        this(threads, name, spinMargin, Integer.getInteger("furnace.sched.dimCap", 0), Integer.getInteger("furnace.sched.starveTicks", 2));
    }

    /** dimCap 0(이하) 이면 워커 수−1. */
    private SharedScheduler(int threads, String name, long spinMargin, int dimCap, int starveTicks) {
        this.spinMargin = Math.max(0L, spinMargin);
        this.workers = new Thread[Math.max(1, threads)];
        int cap = dimCap > 0 ? dimCap : workers.length - 1;
        this.perDimCap = workers.length <= 1 || cap <= 0 ? Integer.MAX_VALUE : cap;
        this.starveTicks = Math.max(1, starveTicks);
        for (int i = 0; i < workers.length; i++) {
            Thread t = new Thread(this::workerLoop, name + i);
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY - 1);   // 틱 스레드는 백그라운드 생성·저장보다 먼저 CPU 를 받는다
            workers[i] = t;
        }
    }

    /** 워커 시작(필드 할당이 끝난 뒤 — Thread.start 의 happens-before). */
    void start() {
        for (Thread t : workers) t.start();
    }

    /** 잠금 없음. 부르는 쪽은 차원 잠금을 쥐지 않는다. */
    void register(Regionizer rz) {
        dims.addIfAbsent(rz);
        snapshot = dims.toArray(new Regionizer[0]);
        wake(rz.headDue());
    }

    void unregister(Regionizer rz) {
        dims.remove(rz);
        snapshot = dims.toArray(new Regionizer[0]);
    }

    /** 선택기가 읽는 차원 배열(등록/해제 때만 바뀐다 — select 마다 할당하지 않는다). */
    private volatile Regionizer[] snapshot = new Regionizer[0];

    public int threadCount() { return workers.length; }

    /** 관측: 워커마다 {이름, 스레드 상태}. */
    public java.util.List<String[]> workerStates() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (Thread t : workers) out.add(new String[] {t.getName(), t.getState().name()});
        return out;
    }

    /** 등록된 차원 수. */
    public int dimensionCount() { return dims.size(); }

    int perDimCap() { return perDimCap; }

    public long missedWakeups() { return missedWakeups.sum(); }

    public long timeouts() { return timeouts.sum(); }

    public long takeMisses() { return takeMisses.sum(); }

    /** 계측: 워커가 park 에서 돌아온 횟수. */
    public long wakeups() { return wakeups.get(); }

    /** 계측: 워커들이 park 로 기다린 시간 합(ns). */
    public long waitNanos() { return waitNanos.get(); }

    // ------------------------------------------------------------------ 시간 비교(N4: MAX 먼저 거르고 차이로만)

    /** 마감 due 가 now 기준 때가 됐나. MAX(후보 없음)는 언제나 거짓. */
    static boolean isDue(long due, long now) { return due != Long.MAX_VALUE && now - due >= 0; }

    /** a 가 b 보다 이른가(MAX 는 가장 늦다). 둘 다 MAX 면 거짓. */
    static boolean earlier(long a, long b) {
        if (a == Long.MAX_VALUE) return false;
        if (b == Long.MAX_VALUE) return true;
        return a - b < 0;
    }

    /** select 의 결과를 워커가 받는 자리(워커 스레드마다 하나 — 할당 없음). */
    private static final class Pick {
        Regionizer best;
        long due;
        int maxRunning;
        /** 서버 전체 도는 틱 수 상한(쉬는 차원 몫을 남길 때 T−1, 아니면 없음) — tryTake 가 {@link #busy} 로 원자적으로 잡는다. */
        int globalMax;
    }

    /** 모든 차원에서 지금 도는 틱(drain 차례 포함) 수. Regionizer 가 꺼낼 때 올리고 끝낼 때 내린다. */
    final java.util.concurrent.atomic.AtomicInteger busy = new java.util.concurrent.atomic.AtomicInteger();

    /** 도는 틱 수가 globalMax 보다 적으면 하나 올리고 참(원자적 — 선택기의 잠금 없는 판단이 두 워커에서 겹쳐도 넘지 않게). */
    boolean reserveSlot(int globalMax) {
        while (true) {
            int b = busy.get();
            if (b >= globalMax) return false;
            if (busy.compareAndSet(b, b + 1)) return true;
        }
    }

    private static final ThreadLocal<Pick> PICK = ThreadLocal.withInitial(Pick::new);

    /**
     * 선택기(§4.1, 잠금 없음): 차원마다 키 = (capped ? 2 : starved ? 0 : 1, headDue), 사전식 최소. 같은 클래스에서 마감 차 ≤ TIE 면
     * 시작 위치를 rr 로 돌려 번갈아 고른다. maxRunning = 다른 차원에 할 일(큐 머리)이 있으면 perDimCap, 없으면 상한 없음(N3) —
     * 상한은 tryTake 가 잠금 안에서 다시 확인한다. 차원이 하나면 순수 EDF(호환 경로와 같은 동작).
     */
    private Pick select(long now, Pick out) {
        out.best = null;
        out.due = Long.MAX_VALUE;
        out.maxRunning = Integer.MAX_VALUE;
        out.globalMax = Integer.MAX_VALUE;
        Regionizer[] ds = snapshot;
        int n = ds.length;
        if (n == 0) return out;
        if (n == 1) {
            Regionizer rz = ds[0];
            long d = rz.headDue();
            if (d != Long.MAX_VALUE) { out.best = rz; out.due = d; }
            return out;
        }
        // 상한은 다른 차원에 "할 일이 있으면"(큐가 비지 않았으면) 건다 — "지금 때가 됐을 때만" 걸면, 무거운 차원이 가벼운 차원의
        // 틱 사이에 마지막 워커까지 긴 틱(40~80ms)으로 채워 가벼운 차원이 그만큼 늦는다(2026-11-07 과부하 벤치 asym).
        // 서버 전체 몫(A03-04): 틱을 돌리는 차원끼리 함께 쓰는 워커를 합쳐 T−1 개로 묶는다(아래 reserve).
        // 차원마다 T−1 상한만 두면 무거운 차원 둘이 워커 T 개를 다 채워 셋째(가벼운) 차원이 매 틱 늦었다(60ms 틱 → 약 16.7 TPS).
        int pendingCount = 0, totalActive = 0;
        long min1 = Long.MAX_VALUE, min2 = Long.MAX_VALUE;   // 할 일이 있는 차원의 평균 틱 시간 가장 작은 둘
        for (Regionizer o : ds) {
            totalActive += o.activeTicks();
            if (o.headDue() != Long.MAX_VALUE) {
                pendingCount++;
                long a = o.avgTickNanos();
                if (a < min1) { min2 = min1; min1 = a; }
                else if (a < min2) min2 = a;
            }
        }
        int start = Math.floorMod(rr.getAndIncrement(), n);
        int bestClass = 3;
        int bestMax = Integer.MAX_VALUE, bestGlobal = Integer.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            Regionizer rz = ds[(start + k) % n];
            long d = rz.headDue();
            if (d == Long.MAX_VALUE) continue;                       // N4: MAX 먼저 거른다
            boolean due = now - d >= 0;
            boolean othersPending = pendingCount > 1;   // 자기 자신(d != MAX)을 빼고도 할 일이 있는 차원이 있다
            int active = rz.activeTicks();
            // 나보다 틱이 훨씬 가벼운(평균 절반 미만) 차원에 할 일이 있으면, 무거운 쪽은 워커를 서버 전체 합 T−1 개까지만 쓴다 — 마지막 하나는
            // 가벼운 차원의 다음 틱 몫이다. 무거운 차원이 둘이면 둘이 합쳐 T−1, 가벼운 차원은 언제나 남은 하나를 받는다. 비슷한 차원끼리는 걸지 않는다.
            long avg = rz.avgTickNanos(), lightest = avg == min1 ? min2 : min1;
            boolean reserve = othersPending && workers.length > 1 && lightest != Long.MAX_VALUE && lightest < avg / 2;
            int max = othersPending ? perDimCap : Integer.MAX_VALUE;
            if (reserve) max = Math.min(max, active + Math.max(0, workers.length - 1 - totalActive));
            int cls;
            if (othersPending && active >= max) cls = 2;
            else if (due && now - d >= starveTicks * rz.tickNanos()) cls = 0;
            else cls = 1;
            if (cls < bestClass || (cls == bestClass && d - out.due < -TIE_NANOS)) {
                bestClass = cls;
                out.best = rz;
                out.due = d;
                bestMax = max;
                bestGlobal = reserve ? workers.length - 1 : Integer.MAX_VALUE;
            }
        }
        out.maxRunning = bestMax;
        out.globalMax = bestGlobal;
        return out;
    }

    private long earliestHeadDue() {
        long m = Long.MAX_VALUE;
        for (Regionizer rz : snapshot) {
            long d = rz.headDue();
            if (earlier(d, m)) m = d;
        }
        return m;
    }

    // ------------------------------------------------------------------ 깨우기(§3.3)

    /** 어떤 차원이 머리를 due 로 앞당겼다. due 보다 늦게 깰 자는 워커 하나만 깨운다. 잠금 없음. */
    void wake(long due) {
        if (due == Long.MAX_VALUE) return;
        long prev = spinDeadline.getAndAccumulate(due, PreciseWait::earlier);   // 스피너 목표 앞당김(원자적 min)
        if (earlier(due, prev)) {
            Thread s = spinner;
            if (s != null) LockSupport.unpark(s);
        }
        for (Parked p : parked) {
            if (p.until - due > 0 && parked.remove(p)) {
                LockSupport.unpark(p.t);
                return;
            }
        }
    }

    private void passBaton() {
        Parked p = parked.poll();
        if (p != null) LockSupport.unpark(p.t);
    }

    /** until 까지(또는 깨우기까지) 잔다. N1·N2 규칙. margin: 머리보다 이만큼 일찍 깬다(스핀 구간). */
    private void sleep(long until, long margin) {
        Thread self = Thread.currentThread();
        Parked me = new Parked(self, until);
        parked.add(me);                                    // ① 먼저 "잔다"고 등록
        var h1 = afterRegisterHook;
        if (h1 != null) h1.accept(me);
        long d = earliestHeadDue();                        // ② 그 다음 머리를 다시 본다
        if (d != Long.MAX_VALUE) {
            long target = d - margin;
            long now = System.nanoTime();
            if (target - now <= 0) {                       // 이미 때가 됨 → 안 잔다
                if (!parked.remove(me)) passBaton();       // (N1-②) 누가 나를 뽑았으면 그 깨우기를 넘긴다
                return;
            }
            if (target - me.until < 0) me.until = target;  // (N2) 더 이른 머리를 알았으면 until 갱신
        }
        long t0 = System.nanoTime();
        long left = me.until - t0;
        if (left > 0 && running) LockSupport.parkNanos(this, left);
        long t1 = System.nanoTime();
        waitNanos.addAndGet(t1 - t0);
        wakeups.incrementAndGet();
        var h2 = afterParkHook;
        if (h2 != null) h2.accept(me);
        boolean picked = !parked.remove(me);
        if (picked && t1 - me.until >= 0) passBaton();     // (N1-③) 뽑혔는데 타임아웃과 겹친 경우만 넘긴다
        if (!picked && t1 - me.until >= 0) {
            timeouts.increment();
            long hd = earliestHeadDue();
            if (hd != Long.MAX_VALUE && me.until - hd > MISSED_NANOS && t1 - hd > MISSED_NANOS) {   // 깰 시각보다 이른 머리를 아무도 알려 주지 않았다
                missedWakeups.increment();
                if (log.isDebugEnabled()) log.debug("[SCHED] 만료 복귀 때 머리가 {}µs 밀려 있었다(until 보다 {}µs 늦음)", (t1 - hd) / 1000, (t1 - me.until) / 1000);
            }
        }
    }

    // ------------------------------------------------------------------ 워커

    private void workerLoop() {
        Regionizer.markWorkerThread();
        while (running) {
            try {
                long now = System.nanoTime();
                Pick pick = select(now, PICK.get());
                Regionizer best = pick.best;
                if (best == null) {
                    sleep(now + PARK_MAX, spinMargin);
                    continue;
                }
                long due = pick.due;
                if (!isDue(due, now)) {
                    if (due == Long.MAX_VALUE) continue;   // 그 사이 비었다 — 다시 고른다
                    long wait = due - now;
                    if (spinMargin > 0L && wait - spinMargin <= SPIN_HANDOFF) {
                        // 대기 해상도가 거친 플랫폼: 마지막 구간은 돌며 기다려 틱 시작이 흔들리지 않게 한다. 도는 워커는 서버 전체 1개,
                        // 자리를 못 잡은 워커는 반드시 잔다(바쁜 재시도 금지 — D7)
                        if (spinning.compareAndSet(false, true)) spinUntil(due);
                        else sleep(now + Math.min(wait, PARK_MAX), 0L);
                        continue;
                    }
                    sleep(now + Math.min(wait, PARK_MAX), spinMargin);   // 머리보다 spinMargin 일찍 깬다
                    continue;
                }
                Region r = best.tryTake(now, pick.maxRunning, pick.globalMax);
                if (r == null) {                            // 낡은 머리/닫힘/상한 → 다시 고른다
                    takeMisses.increment();
                    continue;
                }
                best.runOne(r);
            } catch (Throwable t) {
                log.error("[TICK] region 워커 루프 오류", t);
            }
        }
    }

    /** 스핀 자리를 쥔 워커: due(또는 그 사이 앞당겨진 머리)까지 거친 구간은 자고 마지막만 돈다. */
    private void spinUntil(long due) {
        long t0 = System.nanoTime();
        try {
            spinner = Thread.currentThread();
            spinDeadline.accumulateAndGet(due, PreciseWait::earlier);
            long target = PreciseWait.earlier(spinDeadline.get(), earliestHeadDue());
            PreciseWait.parkUntilInterruptible(target, spinDeadline::get, spinMargin);
        } finally {
            spinner = null;
            spinDeadline.set(Long.MAX_VALUE);
            spinning.set(false);
            waitNanos.addAndGet(System.nanoTime() - t0);
            wakeups.incrementAndGet();
        }
    }

    /** 계측·시험: 지금 스핀 자리를 쥔 워커가 있나. */
    boolean spinningNow() { return spinning.get(); }

    @Override
    public void close() {
        running = false;
        for (Thread t : workers) LockSupport.unpark(t);
        for (Thread t : workers) {
            if (t == Thread.currentThread()) continue;
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
