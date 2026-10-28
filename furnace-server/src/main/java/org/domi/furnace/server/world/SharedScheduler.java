// region 틱 작업 스레드 풀. 등록된 Regionizer(차원)마다 잠금 없이 발행된 "큐 머리 마감"(headDue)을 보고 가장 이른 차원을 고르고,
// 꺼내기·끝내기만 그 차원 잠금 안에서 한다(tryTake/finishTick). 깨우기는 LockSupport.unpark — 풀에는 잠금이 없다.
// 지금(S2)은 차원마다 풀 하나(호환 경로)만 쓴다. 설계: docs/feature-parity/shared-scheduler-design-2026-10-28.md §2.1·§3
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
        this.spinMargin = Math.max(0L, spinMargin);
        this.workers = new Thread[Math.max(1, threads)];
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
        wake(rz.headDue());
    }

    void unregister(Regionizer rz) { dims.remove(rz); }

    public int threadCount() { return workers.length; }

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

    /** 등록된 차원 중 머리 마감이 가장 이른 것(없으면 null). 잠금 없음. */
    private Regionizer select() {
        Regionizer best = null;
        long bestDue = Long.MAX_VALUE;
        for (Regionizer rz : dims) {
            long d = rz.headDue();
            if (earlier(d, bestDue)) {
                best = rz;
                bestDue = d;
            }
        }
        return best;
    }

    private long earliestHeadDue() {
        long m = Long.MAX_VALUE;
        for (Regionizer rz : dims) {
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
    }

    // ------------------------------------------------------------------ 워커

    private void workerLoop() {
        Regionizer.markWorkerThread();
        while (running) {
            try {
                long now = System.nanoTime();
                Regionizer best = select();
                if (best == null) {
                    sleep(now + PARK_MAX, spinMargin);
                    continue;
                }
                long due = best.headDue();
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
                Region r = best.tryTake(now);
                if (r == null) continue;                    // 낡은 머리/닫힘 → 다시 고른다
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
