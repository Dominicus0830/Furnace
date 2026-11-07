// 동적 region(Folia ThreadedRegionizer 방식): 청크를 구역(2^shift 청크 정사각형)으로 묶고, 반경 안의 구역을 가진 region 은 합친다.
// 멀어지면(죽은 구역이 늘면) 연결 성분으로 나눈다. region 마다 다음 틱 시각을 갖고 작업 스레드 풀(SharedScheduler)이 가장 이른 region 부터 돈다.
// 풀은 잠금 없이 headDue(큐 머리 마감)만 읽고, 꺼내기(tryTake)·끝내기(finishTick)는 이 차원 잠금 안에서 한다.
// 구조 변경(구역 추가·제거, 합치기·나누기, 틱 시작·끝)은 한 잠금 아래에서만. 합치기·나누기는 region 이 쉬는 동안(틱 사이)만 한다.
// 설계: docs/superpowers/specs/2026-10-17-dynamic-regionizer.md
package org.domi.furnace.server.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Regionizer {

    private static final Logger log = LoggerFactory.getLogger(Regionizer.class);

    /** 죽은 구역이 있으면 이 틱마다 한 번 나누기를 다시 계산한다. */
    private static final int RECALC_INTERVAL_TICKS = 20;
    /**
     * 구역은 이만큼 계속 비어 있어야 지운다. 막 생긴 구역(다리 구역에 보낸 작업이 아직 안 돈 상태 등)을 바로 지우면
     * 합치기 → 나누기 → 다시 합치기가 끝없이 돈다.
     */
    private static final long SECTION_DEAD_GRACE_NANOS = 1_000_000_000L;

    private final World world;
    private final int shift;
    private final int radius;
    /** region 틱 주기. /tick rate 가 바꾼다. */
    private volatile long tickNanos;

    final ReentrantLock lock = new ReentrantLock();
    /** 구역 키 → 구역. 쓰기는 잠금 안, 읽기(주인 조회)는 어디서나. */
    private final ConcurrentHashMap<Long, Section> sections = new ConcurrentHashMap<>();
    /** 살아 있는 region(잠금 안). */
    private final Set<Region> regions = new LinkedHashSet<>();
    /** 틱 대기열(잠금 안, 지연 삭제 — 꺼낼 때 상태를 다시 본다). */
    private final PriorityQueue<Region> queue = new PriorityQueue<>((a, b) -> compareDue(a.nextTickNanos, b.nextTickNanos));   // nanoTime 오버플로에 안전한 차이 비교
    /** 작업 스레드 풀. 서버 전체 공유(S4, 기본) 또는 차원마다 하나(ownsPool, shared-region-pool=false). */
    private final SharedScheduler pool;
    private final boolean ownsPool;
    /** 큐 머리의 마감(비면 Long.MAX_VALUE). 쓰기는 잠금 안(publishHead), 읽기는 어디서나 — 힌트일 뿐 결정은 tryTake 가 다시 한다. */
    private volatile long headDue = Long.MAX_VALUE;
    /** close 뒤 참: tryTake 는 null, schedule 은 넣지 않는다. */
    private volatile boolean closed;
    /** 거짓이면 region 틱은 작업만 처리하고 게임 틱은 건너뛴다(서버가 다 준비되기 전·테스트). */
    private volatile boolean gameTicks;
    private int nextId;

    // ------------------------------------------------------------------ 관측(워치독·/furnace 명령 — 동작에 영향 없음)
    /** 지금 틱 중인 region → 틱 시작 시각(nanoTime). tryTake 가 넣고 finishTick 이 뺀다. 읽기는 잠금 없이. */
    private final ConcurrentHashMap<Region, Long> running = new ConcurrentHashMap<>();
    /** 최근 region 틱 소요 시간(ns) 분포(잠금 없이 기록). */
    private final org.domi.furnace.server.sched.TickTimeRing tickRing = new org.domi.furnace.server.sched.TickTimeRing(512);
    /** 이 차원 region 틱 전부의 시간 창별 소요 시간(/mspt). */
    private final org.domi.furnace.server.sched.TickWindows tickWindows = new org.domi.furnace.server.sched.TickWindows();

    // ------------------------------------------------------------------ 스케줄 계측(동작에 영향 없음)
    /** 지금 틱 중인 region 수(= 바쁜 워커 수)와 그 최대. */
    private final java.util.concurrent.atomic.AtomicInteger activeTicks = new java.util.concurrent.atomic.AtomicInteger();
    private volatile int peakActiveTicks;
    /** 꺼낸 틱 수, 큐 지연 합·최대(ns), 지연 분포(2의 거듭제곱 µs 칸: 0=<1µs, i=[2^(i-1),2^i)µs). 잠금 안에서만. */
    private long schedTicks, schedDelaySum, schedDelayMax;
    private final long[] schedDelayHist = new long[DELAY_BUCKETS];
    static final int DELAY_BUCKETS = 32;

    /** 구역: 그 안의 "있는 청크" 수와 주인 region. */
    static final class Section {
        final long key;
        volatile Region owner;
        int present;
        /** 비게 된 시각(nanoTime). 있는 청크가 있으면 의미 없다. */
        long deadSince;

        Section(long key, Region owner) {
            this.key = key;
            this.owner = owner;
            this.deadSince = System.nanoTime();
        }

        boolean removable(long now) { return present == 0 && now - deadSince >= SECTION_DEAD_GRACE_NANOS; }
    }

    Regionizer(World world, int threads, int shift, int radius, long tickNanos) {
        this.world = world;
        this.shift = shift;
        this.radius = radius;
        this.tickNanos = tickNanos;
        if (STATS) log.info("[TICKSTATS] 틱 계측 켜짐 — region 틱 100번마다 간격/소요 시간을 기록합니다");
        // 차원마다 작업 스레드가 따로다(오버월드 furnace-region-N, 네더 furnace-region-the_nether-N …)
        String dim = world.dimensionType() == DimensionType.OVERWORLD ? "" : world.dimension().substring(world.dimension().indexOf(':') + 1) + "-";
        this.pool = new SharedScheduler(threads, "furnace-region-" + dim);
        this.ownsPool = true;
        pool.register(this);
        pool.start();
        LIVE.add(this);
    }

    /** 이미 있는 풀에 건다(ownsPool=false — close 는 이 차원만 떼고 풀은 멈추지 않는다). */
    Regionizer(World world, SharedScheduler pool, int shift, int radius, long tickNanos) {
        this.world = world;
        this.shift = shift;
        this.radius = radius;
        this.tickNanos = tickNanos;
        if (STATS) log.info("[TICKSTATS] 틱 계측 켜짐 — region 틱 100번마다 간격/소요 시간을 기록합니다");
        this.pool = pool;
        this.ownsPool = false;
        pool.register(this);
        LIVE.add(this);
    }

    public int shift() { return shift; }

    public int radius() { return radius; }

    public int threadCount() { return pool.threadCount(); }

    /** 큐 머리 마감(잠금 없음, 힌트). */
    long headDue() { return headDue; }

    /** 지금 이 차원에서 틱 중인 region 수(잠금 없음). */
    int activeTicks() { return activeTicks.get(); }

    /** 시험 전용: 머리 마감을 직접 발행한다(깨우기 경합 재현). 프로덕션은 부르지 않는다. */
    void setHeadDueForTest(long due) { headDue = due; }

    SharedScheduler pool() { return pool; }

    void setTickNanos(long nanos) { this.tickNanos = Math.max(1L, nanos); }

    long tickNanos() { return tickNanos; }

    void gameTicks(boolean on) { this.gameTicks = on; }

    boolean gameTicks() { return gameTicks; }

    // ------------------------------------------------------------------ 수동 시계(헤드리스 시험 서버, furnace-test)
    // 켜면 region 은 벽시계로 게임 틱을 하지 않는다. 시험이 manualAdvance 로 낸 틱 수(manualTarget)까지만 한 틱씩 돌고, 그 사이에는
    // 작업 큐만 비운다(지연 작업 시계도 멈춘다). 마친 틱 수가 다른 region 끼리는 합치지 않는다(sprint lockstep 과 같은 까닭).
    // 나뉜 조각은 부모의 수를, 새로 생긴 region 은 지금 목표를 이어받는다(생긴 뒤의 틱부터 돈다). 프로덕션 서버는 켜지 않는다.

    /** 수동 시계일 때 틱 사이 작업 큐를 비우러 도는 간격. */
    static final long MANUAL_POLL_NANOS = 1_000_000L;
    private volatile boolean manualClock;
    private volatile long manualTarget;

    /** 수동 시계를 켠다(게임 틱 시작 전에 한 번). 되돌리지 않는다. */
    void enableManualClock() { this.manualClock = true; }

    boolean isManualClock() { return manualClock; }

    /** 시험 틱 하나를 낸다: 목표를 1 올리고 쉬는 region 을 곧바로 깨운다. 새 목표를 돌려준다. */
    long manualAdvance() {
        lock.lock();
        try {
            long target = ++manualTarget;
            long now = System.nanoTime();
            List<Region> wake = new ArrayList<>();
            for (Region r : regions) if (r.queued && !r.ticking && r.alive && !r.yielded) wake.add(r);
            for (Region r : wake) {
                queue.remove(r);
                r.nextTickNanos = now;
                queue.add(r);
            }
            publishHead();
            pool.wake(headDue);
            return target;
        } finally {
            lock.unlock();
        }
    }

    /** 살아 있는 모든 region 이 target 번째 시험 틱까지 마쳤는가(합쳐지기를 기다리는 region 은 흡수하는 쪽이 센다). */
    boolean manualCaughtUp(long target) {
        lock.lock();
        try {
            for (Region r : regions) if (r.alive && !r.yielded && r.manualDone < target) return false;
            return true;
        } finally {
            lock.unlock();
        }
    }

    long manualTarget() { return manualTarget; }

    // ------------------------------------------------------------------ 키

    public static long chunkKey(int cx, int cz) { return (long) cx << 32 | (cz & 0xFFFFFFFFL); }

    public static int chunkX(long key) { return (int) (key >> 32); }

    public static int chunkZ(long key) { return (int) key; }

    long sectionKeyOfChunk(int cx, int cz) { return chunkKey(cx >> shift, cz >> shift); }

    long sectionKeyOfChunk(long chunkKey) { return sectionKeyOfChunk(chunkX(chunkKey), chunkZ(chunkKey)); }

    // ------------------------------------------------------------------ 주인 찾기

    /** 합쳐져 죽은 region 이면 흡수한 쪽을 따라간다. */
    private static Region resolve(Region r) {
        while (r != null && !r.alive && r.mergedInto != null) r = r.mergedInto;
        return r;
    }

    /** 그 청크 구역의 지금 주인(없으면 null). 잠금 없이 — 결과는 곧 낡을 수 있다(작업 보내기가 다시 확인한다). */
    public Region ownerOf(int cx, int cz) {
        Section s = sections.get(sectionKeyOfChunk(cx, cz));
        return s == null ? null : resolve(s.owner);
    }

    /** 그 청크가 이 region 것인가(틱 중인 region 이 자기 청크만 만지게). */
    boolean owns(Region r, int cx, int cz) {
        Section s = sections.get(sectionKeyOfChunk(cx, cz));
        return s != null && resolve(s.owner) == r;
    }

    /** 작업 보내기용 주인: 구역이 없으면 빈(죽은) 구역을 만들어 맡긴다 — 나중에 나누기가 지운다. */
    Region ownerForTask(int cx, int cz) {
        Region r = ownerOf(cx, cz);
        if (r != null && r.alive) return r;
        lock.lock();
        try {
            return sectionFor(sectionKeyOfChunk(cx, cz), null).owner;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 청크를 "있음"으로 표시한다(로드·로드 중·플레이어 시야). requester 는 그것을 원하는 region(틱 중인 자기 자신, 없으면 null).
     * 구역이 다른 region 것이면 두 region 을 합칠 대상으로 잇는다. 돌려주는 값은 그 청크의 주인.
     */
    Region want(int cx, int cz, Region requester) {
        lock.lock();
        try {
            Section s = sectionFor(sectionKeyOfChunk(cx, cz), requester);
            Region owner = resolve(s.owner);
            if (owner.present.add(chunkKey(cx, cz))) {
                if (s.present++ == 0) owner.deadSections--;
            }
            if (requester != null && requester.alive && owner != requester) link(owner, requester);
            return owner;
        } finally {
            lock.unlock();
        }
    }

    /** a 와 b 가 합쳐지기를 기다리는 중인가. */
    boolean merging(Region a, Region b) {
        lock.lock();
        try {
            return a.pendingMerges.contains(b) || b.pendingMerges.contains(a);
        } finally {
            lock.unlock();
        }
    }

    /** "있음" 표시를 뗀다(청크 퇴출). 주인 스레드에서. */
    void release(Region owner, int cx, int cz) {
        lock.lock();
        try {
            if (!owner.present.remove(chunkKey(cx, cz))) return;
            Section s = sections.get(sectionKeyOfChunk(cx, cz));
            if (s != null && --s.present == 0) {
                s.deadSince = System.nanoTime();
                resolve(s.owner).deadSections++;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 구역을 찾거나 만든다(잠금 안). 새 구역의 주인: requester → 반경 안 이웃 → 새 region. 반경 안 다른 region 은 합칠 대상. */
    private Section sectionFor(long key, Region requester) {
        Section s = sections.get(key);
        if (s != null) {
            Region o = resolve(s.owner);
            if (o != null && o.alive) {
                s.owner = o;
                return s;
            }
            sections.remove(key);   // 주인이 사라진 구역(없앤 region) — 새로 만든다
        }
        int sx = chunkX(key), sz = chunkZ(key);
        Set<Region> near = new LinkedHashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Section n = sections.get(chunkKey(sx + dx, sz + dz));
                if (n != null) {
                    Region r = resolve(n.owner);
                    if (r != null && r.alive) near.add(r);
                }
            }
        }
        Region owner;
        if (requester != null && requester.alive) owner = requester;
        else if (!near.isEmpty()) owner = pickIdle(near);
        else owner = newRegion(System.nanoTime());
        s = new Section(key, owner);
        sections.put(key, s);
        owner.sections.add(key);
        owner.deadSections++;   // 아직 있는 청크가 없다
        for (Region r : near) if (r != owner) link(owner, r);
        return s;
    }

    private static Region pickIdle(Set<Region> near) {
        Region best = null;
        for (Region r : near) {
            if (!r.ticking && (best == null || r.sections.size() > best.sections.size())) best = r;
        }
        return best != null ? best : near.iterator().next();
    }

    private static void link(Region a, Region b) {
        a.pendingMerges.add(b);
        b.pendingMerges.add(a);
    }

    private Region newRegion(long firstTick) {
        Region r = new Region(++nextId, world);
        r.manualDone = manualTarget;   // 수동 시계: 생긴 뒤의 시험 틱부터 돈다
        r.inheritSteps(world.tickState().generation(), 0);   // 진행 중인 step 은 새 region 에 다시 주지 않는다
        r.inheritSprint(world.tickState().sprintGeneration(), 0, false);   // sprint 도 같다(참여하지 않는다)
        regions.add(r);
        r.nextTickNanos = firstTick;
        schedule(r);
        return r;
    }

    // ------------------------------------------------------------------ 작업 보내기

    /** 그 청크의 주인 region 큐에 작업을 넣는다. 그 사이 주인이 죽었으면(합쳐짐·나뉨) 죽은 큐를 비워 다시 보낸다. */
    void enqueue(long chunkKey, java.util.function.Consumer<Region> task) {
        enqueue(chunkKey, task, org.domi.furnace.server.sched.GlobalThread.isGlobalThread());
    }

    /** urgent: 전역(명령) 스레드가 보낸 작업 — 명령 블록이 결과를 기다리는 동안 골라 돌린다(Region.awaitUrgent). */
    void enqueue(long chunkKey, java.util.function.Consumer<Region> task, boolean urgent) {
        Region r = ownerForTask(chunkX(chunkKey), chunkZ(chunkKey));
        r.inbox.add(new Region.Task(chunkKey, task, urgent));
        if (!r.alive) reroute(r);
        else if (urgent) urgentArrived(r);
    }

    /** 주인 region 이 이미 있을 때만 작업을 넣는다(조회성 — region 을 만들지 않는다). 주인이 없으면 false. */
    boolean enqueueIfOwned(long chunkKey, java.util.function.Consumer<Region> task) {
        Region r = ownerOf(chunkX(chunkKey), chunkZ(chunkKey));
        if (r == null) return false;
        boolean urgent = org.domi.furnace.server.sched.GlobalThread.isGlobalThread();
        r.inbox.add(new Region.Task(chunkKey, task, urgent));
        if (!r.alive) reroute(r);
        else if (urgent) urgentArrived(r);
        return true;
    }

    /** 죽은 region 의 남은 작업을 지금 주인에게. */
    void reroute(Region dead) {
        Region.Task t;
        while ((t = dead.inbox.poll()) != null) enqueue(t.chunkKey(), t.run(), t.urgent());
    }

    // ------------------------------------------------------------------ 명령 블록 기다림(전역 스레드가 보낸 작업을 빨리 돌리기)

    /**
     * 급한 작업이 r 에 들어왔다: r 이 명령 결과를 기다리는 중이면 깨우고, 어딘가에서 명령 블록이 기다리는 중이고 r 이 쉬고 있으면
     * 다음 틱을 기다리지 않고 급한 작업만 돌리는 차례(drain)를 곧바로 준다 — 그래야 @e 처럼 모든 region 을 도는 명령이 한 틱 안에 끝난다.
     */
    private void urgentArrived(Region r) {
        Thread w = r.urgentWaiter;
        if (w != null) {
            java.util.concurrent.locks.LockSupport.unpark(w);
            return;
        }
        if (Region.URGENT_WAITERS.get() == 0) return;
        lock.lock();
        try {
            if (closed || !r.queued || r.ticking || !r.alive || r.yielded || r.drainPass) return;
            long now = System.nanoTime();
            // 틱 시각이 이미 됐어도 drain 차례로 표시한다: 워커가 모두 기다리는 스레드에 잡혀 있으면 이 틱은 돌 수 없고,
            // 기다리는 스레드가 helpDrainAny 로 대신 돌릴 수 있는 것은 drain 차례뿐이다(부하가 걸린 서버에서 틱이 밀릴 때).
            queue.remove(r);
            r.savedNextTickNanos = r.nextTickNanos;
            r.drainPass = true;
            PENDING_DRAINS.incrementAndGet();
            if (now - r.nextTickNanos < 0) r.nextTickNanos = now;
            queue.add(r);
            long prev = headDue;
            publishHead();
            if (SharedScheduler.earlier(headDue, prev)) pool.wake(headDue);
        } finally {
            lock.unlock();
        }
    }

    /** 살아 있는 Regionizer(서버 전체) — 기다리는 region 스레드가 남의 drain 차례를 대신 돌릴 때 찾는다. */
    private static final java.util.concurrent.CopyOnWriteArrayList<Regionizer> LIVE = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 아직 돌지 않은 drain 차례 수(서버 전체, 힌트). */
    private static final java.util.concurrent.atomic.AtomicInteger PENDING_DRAINS = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 명령 결과를 기다리는 region 스레드가 부른다: 워커가 모자라(기다리는 스레드가 워커를 쥐고 있다) 아직 못 돈 남의 drain 차례가 있으면
     * 하나를 이 스레드에서 대신 돌린다. 돌렸으면 참. 그 동안 이 스레드는 두 region 을 함께 쥔다(남의 급한 작업만 돈다).
     */
    static boolean helpDrainAny() {
        if (PENDING_DRAINS.get() <= 0) return false;
        for (Regionizer rz : LIVE) {
            Region r = rz.takeDrain();
            if (r != null) {
                rz.runDrainPass(r);
                return true;
            }
        }
        return false;
    }

    /** 대기열에서 drain 차례인 쉬는 region 하나를 꺼내 이 스레드가 쥔다(없으면 null). */
    private Region takeDrain() {
        lock.lock();
        try {
            if (closed) return null;
            for (Region r : queue) {
                if (!r.drainPass || !r.queued || r.ticking || !r.alive || r.yielded) continue;
                queue.remove(r);
                r.queued = false;
                r.ticking = true;
                r.tickThread = Thread.currentThread();
                running.put(r, System.nanoTime());
                activeTicks.incrementAndGet();
                publishHead();
                return r;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /** drain 차례(워커, 잠금 밖): 급한 작업만 돌리고 원래 다음 틱 시각으로 돌려 놓는다. 틱 수·계측·나누기는 건드리지 않는다. */
    private void runDrainPass(Region r) {
        try {
            r.runUrgentDrain();
        } catch (Throwable t) {
            log.error("[TICK] region #{} 급한 작업 처리 실패", r.id, t);
        }
        lock.lock();
        try {
            r.ticking = false;
            r.tickThread = null;
            running.remove(r);
            activeTicks.decrementAndGet();
            r.drainPass = false;
            PENDING_DRAINS.decrementAndGet();
            r.nextTickNanos = r.savedNextTickNanos;
            resolveMerges(r);   // 이 차례 동안 양보하고 기다리는 상대가 있으면 흡수(틱 뒤와 같다)
            if (r.alive && !r.yielded) schedule(r);
        } catch (Throwable t) {
            log.error("[REGION] region #{} drain 뒤 정리 실패", r.id, t);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 살아 있는 region 마다 한 번씩 작업(저장·종료 정리). 그 region 큐에 바로 넣는다 — 합쳐지면 흡수한 쪽에서 돌고,
     * 돌기 전에 나뉘면 나뉜 조각마다 한 번씩 돈다(future 는 모든 조각이 끝나야 완료).
     */
    List<java.util.concurrent.CompletableFuture<Void>> forEachRegion(java.util.function.Consumer<Region> task) {
        List<java.util.concurrent.CompletableFuture<Void>> out = new ArrayList<>();
        lock.lock();
        try {
            for (Region r : regions) {
                long sk = r.sections.isEmpty() ? 0 : r.sections.iterator().next();
                RegionWide w = new RegionWide(task);
                boolean urgent = org.domi.furnace.server.sched.GlobalThread.isGlobalThread();
                r.inbox.add(new Region.Task(chunkKey(chunkX(sk) << shift, chunkZ(sk) << shift), w, urgent));
                if (urgent) urgentArrived(r);
                out.add(w.done);
            }
        } finally {
            lock.unlock();
        }
        return out;
    }

    /** region 단위 작업: 청크 주인을 따지지 않고 받은 region 에서 돈다. 나누기 때 조각마다 복제된다. */
    static final class RegionWide implements java.util.function.Consumer<Region> {
        final java.util.function.Consumer<Region> task;
        final java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
        private final java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(1);
        private volatile Throwable error;

        RegionWide(java.util.function.Consumer<Region> task) { this.task = task; }

        void fork() { left.incrementAndGet(); }

        @Override
        public void accept(Region r) {
            try {
                task.accept(r);
            } catch (Throwable t) {
                error = t;
            }
            if (left.decrementAndGet() == 0) {
                Throwable e = error;
                if (e == null) done.complete(null);
                else done.completeExceptionally(e);
            }
        }
    }

    // ------------------------------------------------------------------ 틱 스케줄

    /** -Dfurnace.tickstats=true: region 틱 간격·소요 시간을 100틱마다 p50/p95/최대로 기록한다(끊김 진단용). */
    static final boolean STATS = Boolean.getBoolean("furnace.tickstats") || System.getenv("FURNACE_TICKSTATS") != null;

    /** 틱 뒤·끝내기 앞(region 이 아직 RUNNING — 다른 워커가 못 꺼낸다)에 부른다. 표는 region 인스턴스 필드다. */
    private void recordTick(Region r, long start, long end) {
        long[] st = r.tickStat;   // [0]=직전 시작, [1]=개수, 이후 (간격, 소요) 쌍
        int n = (int) st[1];
        if (st[0] != 0L) {
            st[2 + n * 2] = start - st[0];
            st[3 + n * 2] = end - start;
            st[1] = ++n;
        }
        st[0] = start;
        if (n >= 100) {
            long[] gap = new long[n], dur = new long[n];
            for (int i = 0; i < n; i++) {
                gap[i] = st[2 + i * 2];
                dur[i] = st[3 + i * 2];
            }
            java.util.Arrays.sort(gap);
            java.util.Arrays.sort(dur);
            log.info("[TICKSTATS] region {}#{} 간격 p50={}ms p95={}ms max={}ms / 소요 p50={}ms p95={}ms max={}ms", world.dimension(), r.id,
                    gap[n / 2] / 1_000_000, gap[n * 95 / 100] / 1_000_000, gap[n - 1] / 1_000_000,
                    dur[n / 2] / 1_000_000, dur[n * 95 / 100] / 1_000_000, dur[n - 1] / 1_000_000);
            st[1] = 0;
            String[] names = {"블록틱", "조명", "플레이어", "보임", "정리·저장"};
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                b.append(String.format(" %s 평균 %.1f/최대 %.0fms", names[i], r.phaseSum[i] / 1e8, r.phaseMax[i] / 1e6));
                r.phaseSum[i] = 0;
                r.phaseMax[i] = 0;
            }
            log.info("[TICKSTATS]   단계별:{}", b);
            long[] d = new long[4];
            for (int i = 0; i < 4; i++) {   // 누적값과 지난 출력 사본의 차이 — installStats 는 리셋하지 않는다
                d[i] = r.installStats[i] - r.installStatsLogged[i];
                r.installStatsLogged[i] = r.installStats[i];
            }
            if (d[0] > 0) {
                log.info("[TICKSTATS]   청크 설치 {}개: 넣기 {}ms 빛 {}ms 엔티티·예약틱 {}ms (개당 평균 {}µs)", d[0], d[1] / 1_000_000, d[2] / 1_000_000,
                        d[3] / 1_000_000, (d[1] + d[2] + d[3]) / d[0] / 1000);
            }
        }
    }

    /** 마감 비교(nanoTime 은 넘칠 수 있어 값이 아니라 차이로 본다). 두 값이 2^63 ns(약 292년) 안일 때만 유효. */
    static int compareDue(long a, long b) {
        long d = a - b;
        return d < 0 ? -1 : d > 0 ? 1 : 0;
    }

    /** 지금 스레드가 (어느 차원이든) region 틱 워커인가. */
    private static final ThreadLocal<Boolean> WORKER = ThreadLocal.withInitial(() -> false);

    public static boolean isRegionWorkerThread() { return WORKER.get(); }

    /** 풀 워커가 시작할 때 부른다. */
    static void markWorkerThread() { WORKER.set(true); }

    /**
     * 꺼내기(워커, 잠금 안): 머리의 낡은 항목(죽음·양보·틱 중·큐 밖)을 지우고, 때가 된 머리를 RUNNING 으로 꺼낸다.
     * 머리가 없거나 아직 때가 아니거나 닫혔으면 null(워커가 다시 고른다).
     */
    Region tryTake(long now) { return tryTake(now, Integer.MAX_VALUE); }

    /** maxRunning: 이 차원에서 동시에 도는 region 수 상한(§4.1 N3 — 선택기가 정하고 여기서 잠금 안에서 다시 확인한다). */
    Region tryTake(long now, int maxRunning) {
        lock.lock();
        try {
            if (closed) return null;
            while (true) {
                Region head = queue.peek();
                if (head == null) {
                    publishHead();
                    return null;
                }
                if (!head.alive || head.yielded || head.ticking || !head.queued) {
                    queue.poll();
                    head.queued = false;
                    continue;
                }
                long late = now - head.nextTickNanos;
                if (late < 0) {
                    publishHead();
                    return null;
                }
                if (activeTicks.get() >= maxRunning) {   // 상한: 다른 차원 몫으로 남긴다
                    publishHead();
                    return null;
                }
                queue.poll();
                head.queued = false;
                head.ticking = true;
                // 수동 시계: 이번 차례가 시험 틱인지 잠금 안에서 정한다 — 병합 판정(resolveMerges)이 틱 중인 상대의 수를 +1 로 본다
                head.manualPass = manualClock && head.manualDone < manualTarget;
                head.tickThread = Thread.currentThread();
                running.put(head, now);
                noteTaken(head, late);
                publishHead();
                // 새 머리가 자는 워커의 깰 시각보다 이르면 하나 깨운다(지금까지의 signalAll 과 같은 효과 — 같은 마감 region 을 다른 워커가 받는다)
                pool.wake(headDue);
                return head;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 틱 하나(워커, 잠금 밖): 본문 → /tick 표본 → 계측 → 끝내기. 순서는 예전 workerLoop 와 같다. */
    void runOne(Region r) {
        if (r.drainPass) {
            runDrainPass(r);
            return;
        }
        long t0 = STATS ? System.nanoTime() : 0L;
        try {
            // 수동 시계: 목표에 닿은 region 은 작업 큐만 비운다(manualPass 는 tryTake 가 잠금 안에서 정했다)
            boolean hold = manualClock && !r.manualPass;
            boolean game = gameTicks && !hold;
            long tickStart = game ? System.nanoTime() : 0L;
            r.runTick(gameTicks, hold);
            if (game && r.lastRunTicked) {
                long took = System.nanoTime() - tickStart;
                world.tickState().record(took);   // /tick query 의 틱 시간 표본
                noteTickTime(r, tickStart, took);
            }
        } catch (Throwable t) {
            log.error("[TICK] region #{} 실패", r.id, t);
        }
        if (STATS) recordTick(r, t0, System.nanoTime());
        finishTick(r);
    }

    /** 틱 소요 시간을 관측용으로 남긴다(잠금 없음). */
    private void noteTickTime(Region r, long start, long took) {
        tickRing.record(took);
        tickWindows.record(start, took);
        r.tickStarts.record(start);
        r.observedPlayers = r.players().size();
        r.lastTickNanos = took;
        if (took > r.maxTickNanos) r.maxTickNanos = took;
    }

    /** 끝내기(워커, 잠금 안): RUNNING → IDLE/QUEUED. */
    void finishTick(Region r) {
        lock.lock();
        try {
            r.ticking = false;
            r.tickThread = null;
            running.remove(r);
            activeTicks.decrementAndGet();
            afterTick(r);
            // 틱 중에 들어온 급한 작업(다른 region 의 명령 블록이 기다린다)은 다음 틱까지 미루지 않는다
            if (Region.URGENT_WAITERS.get() > 0 && r.alive && r.hasUrgentTask()) urgentArrived(r);
        } catch (Throwable t) {
            log.error("[REGION] region #{} 틱 뒤 정리 실패", r.id, t);
        } finally {
            lock.unlock();
        }
    }

    /** 큐 머리 마감을 발행한다(잠금 안, 큐가 바뀐 자리 끝). 낡은 머리를 발행해도 앞당길 뿐 늦추지 않는다. */
    private void publishHead() {
        Region h = queue.peek();
        headDue = h == null ? Long.MAX_VALUE : h.nextTickNanos;
    }

    /** 꺼낸 region 의 큐 지연을 남긴다(잠금 안). */
    private void noteTaken(Region r, long delay) {
        if (delay < 0) delay = 0;
        schedTicks++;
        schedDelaySum += delay;
        if (delay > schedDelayMax) schedDelayMax = delay;
        long us = delay / 1000;
        schedDelayHist[Math.min(DELAY_BUCKETS - 1, 64 - Long.numberOfLeadingZeros(us))]++;
        r.schedTicks++;
        r.schedDelaySum += delay;
        if (delay > r.schedDelayMax) r.schedDelayMax = delay;
        int a = activeTicks.incrementAndGet();
        if (a > peakActiveTicks) peakActiveTicks = a;
    }

    /** region 별 스케줄 계측. */
    public record RegionSchedStats(int id, long ticks, long queueDelaySumNanos, long queueDelayMaxNanos) {}

    /**
     * 차원(이 Regionizer) 스케줄 계측 사본. queueDelay = 꺼낸 시각 - 예정 마감. waitNanos/wakeups = 이 차원 풀의 워커가 기다린 시간·횟수
     * (풀을 공유하면 풀 전체 값).
     * activeWorkers = 지금 틱 중인 워커 수, peakActiveWorkers = 그 최대.
     */
    public record SchedStats(String dimension, int threads, long ticks, long queueDelaySumNanos, long queueDelayMaxNanos,
                             long queueDelayP50Nanos, long queueDelayP95Nanos, long queueDelayP99Nanos,
                             long waitNanos, long wakeups, int activeWorkers, int peakActiveWorkers,
                             List<RegionSchedStats> regions) {
        public double queueDelayAvgNanos() { return ticks == 0 ? 0 : (double) queueDelaySumNanos / ticks; }
    }

    public SchedStats schedStats() {
        lock.lock();
        try {
            List<RegionSchedStats> rs = new ArrayList<>();
            for (Region r : regions) rs.add(new RegionSchedStats(r.id, r.schedTicks, r.schedDelaySum, r.schedDelayMax));
            return new SchedStats(world.dimension(), threadCount(), schedTicks, schedDelaySum, schedDelayMax,
                    delayQuantile(0.50), delayQuantile(0.95), delayQuantile(0.99),
                    pool.waitNanos(), pool.wakeups(), activeTicks.get(), peakActiveTicks, rs);
        } finally {
            lock.unlock();
        }
    }

    /** 지연 분포 칸의 위 경계(ns)로 본 분위수(잠금 안). */
    private long delayQuantile(double q) {
        if (schedTicks == 0) return 0;
        long need = (long) Math.ceil(schedTicks * q), acc = 0;
        for (int i = 0; i < DELAY_BUCKETS; i++) {
            acc += schedDelayHist[i];
            if (acc >= need) return i == 0 ? 1_000L : (1L << i) * 1_000L;
        }
        return schedDelayMax;
    }

    /** 틱 끝(잠금 안): 합치기 → 나누기 → 빈 region 정리 → 다음 틱 예약. */
    private void afterTick(Region r) {
        boolean counted = !manualClock;
        if (r.manualPass) {
            r.manualPass = false;
            if (!r.sprintHeld()) {   // sprint lockstep 으로 기다린 차례는 틱을 돌지 않았다 — 세지 않고 곧 다시 돈다
                r.manualDone++;
                counted = true;
            }
        }
        updateSprintSummary(r);
        resolveMerges(r);
        if (!r.alive || r.yielded) return;
        if (counted) r.ticksSinceRecalc++;   // 수동 시계: 나누기 재계산 주기는 쉬는 차례가 아니라 시험 틱으로 센다
        if ((r.deadSections > 0 || r.sections.isEmpty()) && r.ticksSinceRecalc >= RECALC_INTERVAL_TICKS) {
            r.ticksSinceRecalc = 0;
            split(r);
        }
        if (!r.alive) return;
        long now = System.nanoTime();
        updateSprintSummary(r);   // 병합으로 이어받은 상태까지
        if (manualClock) {
            // 수동 시계: 낸 틱이 남았으면 곧바로, 아니면 작업 큐를 비우러 잠깐 뒤에(manualAdvance 가 앞당긴다)
            r.nextTickNanos = r.manualDone < manualTarget ? now : now + MANUAL_POLL_NANOS;
        } else if (r.sprintPending) {
            // /tick sprint: 기다리지 않고 곧바로 다음 틱(같은 풀의 다른 region 과는 마감 순서로 번갈아). 전역을 기다린 틱이면 잠깐 뒤에.
            r.nextTickNanos = r.sprintHeld() ? now + SPRINT_HOLD_NANOS : now;
        } else {
            r.nextTickNanos += tickNanos;
            if (r.nextTickNanos - now < 0) r.nextTickNanos = now + tickNanos;   // 너무 밀렸으면 재동기(따라잡기 폭주 방지) — sprint 끝도 여기로 돌아온다
        }
        schedule(r);
    }

    private void schedule(Region r) {
        if (closed || r.queued || !r.alive || r.yielded) return;
        r.queued = true;
        queue.add(r);
        long prev = headDue;
        publishHead();
        if (SharedScheduler.earlier(headDue, prev)) pool.wake(headDue);   // 머리가 앞당겨졌다 — 늦게 깰 워커 하나를 깨운다(잠금 없음)
    }

    /** 합칠 대상이 쉬면 흡수, 틱 중이면 양보(다음 틱을 잡지 않고 상대가 끝날 때 흡수된다). */
    /** 틱 뒤 요약(잠금 안): 본 세대, 남은 sprint 틱 여부, 참여 여부, 마친 틱 수 — 다른 스레드는 이 요약만 읽는다. */
    private void updateSprintSummary(Region r) {
        r.sprintSeen = r.sprintGeneration();
        r.sprintPending = r.sprintLeft() > 0;
        r.sprintJoinedSummary = r.sprintJoined();
        r.sprintDoneSummary = r.sprintJoined() ? world.tickState().sprintTicks() - r.sprintLeft() : 0;
    }

    /**
     * sprint 중 병합 조건(잠금 안 요약으로): 둘 다 참여 중인데 마친 틱 수가 다르면 잠깐 미룬다. lockstep 이라 차이는 1 이하이고
     * 뒤진 쪽은 전역에 막히지 않으므로 다음 한 틱 안에 같아진다(소유권이 겹치는 구간은 평소 병합 대기와 같은 한 틱).
     * 한쪽만 참여하면 미루지 않고 곧바로 합치며, 합친 region 은 참여 쪽 상태를 따른다(absorb).
     */
    private boolean sprintCompatible(Region r, Region o) {
        var ts = world.tickState();
        if (!ts.sprinting()) return true;
        int gen = ts.sprintGeneration();
        boolean jr = r.sprintSeen == gen && r.sprintJoinedSummary, jo = o.sprintSeen == gen && o.sprintJoinedSummary;
        if (!jr || !jo) return true;
        return r.sprintDoneSummary == o.sprintDoneSummary;
    }

    /** sprint 가 끝난 뒤 남은 region 이 미룬 병합을 다시 보게 깨우는 주기(나노초) — 전역을 기다린 region 의 재시도 간격도 같다. */
    static final long SPRINT_HOLD_NANOS = 100_000L;

    private void resolveMerges(Region r) {
        if (r.pendingMerges.isEmpty()) return;
        List<Region> deferred = null;
        try {
            deferred = resolveMerges(r, null);
        } finally {
            if (deferred != null) r.pendingMerges.addAll(deferred);
        }
    }

    /**
     * 수동 시계 병합 조건(잠금 안): 마친 시험 틱 수가 같아야 한다. 틱 중인 상대는 지금 도는 시험 틱까지 센다 — 그렇지 않으면
     * 쉬던 쪽이 양보한 뒤 상대가 틱을 마쳐 수가 달라지고, 양보한 region 은 다시 돌지 않아 영영 흡수되지 않는다.
     */
    private boolean manualCompatible(Region r, Region o) {
        if (!manualClock) return true;
        long ro = r.manualDone + (r.ticking && r.manualPass ? 1 : 0);
        long oo = o.manualDone + (o.ticking && o.manualPass ? 1 : 0);
        return ro == oo;
    }

    /** 미룬 병합 상대 목록(없으면 null)을 돌려준다. */
    private List<Region> resolveMerges(Region r, List<Region> deferred) {
        while (!r.pendingMerges.isEmpty()) {
            var it = r.pendingMerges.iterator();
            Region o = resolve(it.next());
            it.remove();
            if (o == null || o == r || !o.alive) continue;
            if (!sprintCompatible(r, o) || !manualCompatible(r, o)) {   // 참여 region 끼리(수동 시계면 모두) 마친 틱이 다르면 한 틱 미룬다
                if (deferred == null) deferred = new ArrayList<>();
                deferred.add(o);
                continue;
            }
            if (o.ticking) {
                r.yielded = true;
                o.pendingMerges.addAll(r.pendingMerges);
                o.pendingMerges.remove(o);
                o.pendingMerges.add(r);
                r.pendingMerges.clear();
                return deferred;
            }
            absorb(r, o);
        }
        return deferred;
    }

    /** o 를 r 로 흡수(둘 다 쉬는 중, 잠금 안). */
    private void absorb(Region r, Region o) {
        o.alive = false;
        o.mergedInto = r;
        regions.remove(o);
        for (long sk : o.sections) {
            Section s = sections.get(sk);
            if (s != null) s.owner = r;
            r.sections.add(sk);
        }
        r.deadSections += o.deadSections;
        o.sections.clear();
        for (Region p : o.pendingMerges) if (resolve(p) != r) r.pendingMerges.add(p);
        o.pendingMerges.clear();
        r.absorbData(o);
        r.adoptSprintFrom(o);   // 참여하지 않던 쪽이 흡수하면 참여 쪽 sprint 상태를 이어받는다
        reroute(o);
        if (o.yielded) o.yielded = false;
    }

    /** 죽은 구역을 지우고 남은 구역을 반경 인접으로 묶어, 둘 이상이면 나눈다(잠금 안, r 은 쉬는 중). */
    private void split(Region r) {
        long now = System.nanoTime();
        int stillDead = 0;
        List<Long> live = new ArrayList<>();
        Set<Long> anchored = new HashSet<>();   // 플레이어가 선 구역은 청크가 없어도 남긴다(텔레포트 직후 등)
        for (var p : r.players().values()) {
            ChunkPos c = p.chunkPos();
            anchored.add(sectionKeyOfChunk(c.x(), c.z()));
        }
        for (long sk : r.sections) {
            Section s = sections.get(sk);
            if (s == null) continue;
            if (s.removable(now) && !anchored.contains(sk)) {
                sections.remove(sk);
            } else {
                live.add(sk);
                if (s.present == 0) stillDead++;
            }
        }
        r.sections.retainAll(new HashSet<>(live));
        r.deadSections = stillDead;   // 아직 유예 중인 빈 구역은 다음 계산 때 다시 본다
        if (live.isEmpty()) {
            if (r.isIdleEmpty()) kill(r);   // 아니면(지연 작업·ack 가 남음) 다음 계산 때 다시 본다
            return;
        }
        // 연결 성분(반경 안 = 인접)
        Set<Long> left = new HashSet<>(live);
        List<Set<Long>> parts = new ArrayList<>();
        while (!left.isEmpty()) {
            long start = left.iterator().next();
            Set<Long> part = new HashSet<>();
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(start);
            left.remove(start);
            while (!q.isEmpty()) {
                long k = q.poll();
                part.add(k);
                int sx = chunkX(k), sz = chunkZ(k);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        long n = chunkKey(sx + dx, sz + dz);
                        if (left.remove(n)) q.add(n);
                    }
                }
            }
            parts.add(part);
        }
        if (parts.size() == 1) return;
        parts.sort((a, b) -> Integer.compare(b.size(), a.size()));   // 가장 큰 성분은 원래 region 이 갖는다
        Map<Long, Region> target = new HashMap<>();
        List<Region> created = new ArrayList<>();
        for (int i = 1; i < parts.size(); i++) {
            Region n = new Region(++nextId, world);
            n.manualDone = r.manualDone;   // 수동 시계: 부모가 마친 시험 틱 수
            n.inheritSteps(r.stepGeneration(), r.stepsLeft());   // 나뉜 조각은 부모의 남은 걸음을 물려받는다
            n.inheritSprint(r.sprintGeneration(), r.sprintLeft(), r.sprintJoined());   // sprint 틱도
            // 방금 돈 틱의 다음 — 나뉜 조각이 곧바로 한 번 더 돌지 않게. sprint 중이면 부모처럼 곧바로
            n.nextTickNanos = r.sprintLeft() > 0 || manualClock ? r.nextTickNanos : r.nextTickNanos + tickNanos;
            regions.add(n);
            created.add(n);
            for (long sk : parts.get(i)) {
                target.put(sk, n);
                r.sections.remove(sk);
                n.sections.add(sk);
                sections.get(sk).owner = n;
            }
        }
        r.deadSections = countDead(r);
        for (Region n : created) n.deadSections = countDead(n);
        r.splitData(created, chunkKey -> target.get(sectionKeyOfChunk(chunkKey)));
        // 큐에 남은 작업도 구역을 따라 보낸다(먼저 모두 꺼낸 뒤 — r 에 남을 작업이 다시 들어와도 무한히 돌지 않게).
        // region 단위 작업은 조각마다 한 번씩.
        List<Region.Task> pending = new ArrayList<>();
        Region.Task t;
        while ((t = r.inbox.poll()) != null) pending.add(t);
        for (Region.Task task : pending) {
            if (task.run() instanceof RegionWide w) {
                r.inbox.add(task);
                for (Region n : created) {
                    w.fork();
                    n.inbox.add(task);
                }
            } else {
                enqueue(task.chunkKey(), task.run(), task.urgent());
            }
        }
        for (Region n : created) schedule(n);
        log.debug("[REGION] #{} 를 {} 개로 나눔", r.id, parts.size());
    }

    private int countDead(Region r) {
        int n = 0;
        for (long sk : r.sections) {
            Section s = sections.get(sk);
            if (s != null && s.present == 0) n++;
        }
        return n;
    }

    private void kill(Region r) {
        if (r.drainPass) {
            r.drainPass = false;
            PENDING_DRAINS.decrementAndGet();
        }
        r.alive = false;
        regions.remove(r);
        reroute(r);
    }

    // ------------------------------------------------------------------ 상태

    /**
     * /tick sprint 끝 판정(전역 스레드): 살아 있는 모든 region 이 그 sprint 세대를 보고 sprint 틱을 다 돌았으면 참.
     * 게임 틱을 돌지 않는 월드(시작 전)는 참. 세대 요약은 틱 뒤 잠금 안에서 남긴 것을 읽는다.
     */
    /**
     * lockstep(전역 스레드): 이 sprint 에 참여하는 region 들이 마친 sprint 틱 수의 최솟값. 아직 세대를 못 본 region 은 0,
     * 참여하지 않는 region·흡수 대기 region 은 빼고, 없으면 Long.MAX_VALUE. 게임 틱을 돌지 않는 월드도 MAX.
     */
    public long sprintMinDone(int generation) {
        if (!gameTicks) return Long.MAX_VALUE;
        lock.lock();
        try {
            long min = Long.MAX_VALUE;
            for (Region r : regions) {
                if (!r.alive || r.yielded) continue;
                if (r.sprintSeen != generation) min = 0;
                else if (r.sprintJoinedSummary) min = Math.min(min, r.sprintDoneSummary);
            }
            return min;
        } finally {
            lock.unlock();
        }
    }

    public boolean sprintDone(int generation) {
        if (!gameTicks) return true;
        lock.lock();
        try {
            for (Region r : regions) {
                if (r.alive && !r.yielded && (r.sprintSeen != generation || r.sprintPending)) return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ 관측 조회(/furnace, 워치독)

    /** 틱 소요 시간 분포(최근 표본 최대 512개 + 가동 이후 최대). */
    public org.domi.furnace.server.sched.TickTimeRing.Stats tickTimes() { return tickRing.stats(); }

    /** region 한 개의 관측 사본. thread 는 지금 틱 중인 워커 이름(쉬면 null), runningNanos 는 그 틱이 돈 시간. */
    public record RegionInfo(int id, int sections, int chunks, int inbox, String thread, long runningNanos,
                             long lastTickNanos, long maxTickNanos, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                             int players) {}

    /** 살아 있는 region 들의 관측 사본(잠금 안에서 값만 복사한다 — region 상태는 만지지 않는다). */
    public List<RegionInfo> regionInfos() {
        long now = System.nanoTime();
        List<RegionInfo> out = new ArrayList<>();
        lock.lock();
        try {
            for (Region r : regions) out.add(info(r, now));
        } finally {
            lock.unlock();
        }
        return out;
    }

    /** region 하나의 관측 사본(잠금 안). */
    private RegionInfo info(Region r, long now) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : r.sections) {
            Section sec = sections.get(key);
            if (sec == null) continue;
            int sx = (int) (key >> 32) << shift, sz = (int) key << shift;
            minX = Math.min(minX, sx);
            minZ = Math.min(minZ, sz);
            maxX = Math.max(maxX, sx + (1 << shift) - 1);
            maxZ = Math.max(maxZ, sz + (1 << shift) - 1);
        }
        Thread t = r.tickThread;
        Long started = running.get(r);
        return new RegionInfo(r.id, r.sections.size(), r.present.size(), r.inbox.size(), t == null ? null : t.getName(),
                started == null ? 0 : now - started, r.lastTickNanos, r.maxTickNanos, minX, minZ, maxX, maxZ, r.observedPlayers);
    }

    /** region 틱 시간 창별 소요 시간(/mspt). */
    public org.domi.furnace.server.sched.TickWindows tickWindows() { return tickWindows; }

    /** /tps 용: region 하나의 최근 틱 속도. tps 는 TickStartRing.rate 값. */
    public record RegionRate(RegionInfo region, double tps) {}

    /**
     * 최근 windowNanos 동안 틱 속도가 가장 낮은 region(같으면 id 가 작은 쪽). 기록이 minHistoryNanos 보다 짧은 region(막 생긴 region)은
     * 빼고, 잴 수 있는 region 이 없으면 null. 잠금 안에서 각 region 의 시작 시각 고리만 읽는다.
     */
    public RegionRate slowestRegion(long now, long windowNanos, long minHistoryNanos) {
        lock.lock();
        try {
            Region worst = null;
            double worstTps = Double.NaN;
            for (Region r : regions) {
                if (!r.alive || r.yielded) continue;
                double tps = r.tickStarts.rate(now, windowNanos, minHistoryNanos);
                if (Double.isNaN(tps)) continue;
                if (worst == null || tps < worstTps || (tps == worstTps && r.id < worst.id)) {
                    worst = r;
                    worstTps = tps;
                }
            }
            return worst == null ? null : new RegionRate(info(worst, now), worstTps);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 활성 region(플레이어가 있는 region) 요약 — /tps·getTPS 용. 잴 수 있는(기록 minHistory 이상) region 만 센다.
     * weightedSum = Σ(플레이어 수 × TPS), 평균은 weightedSum / activePlayers. slowestAny 는 플레이어 없는 region 까지 포함한 최저.
     */
    public record ActiveSummary(int activeRegions, int activePlayers, double weightedSum, double slowestActive, double slowestAny) {
        public static final ActiveSummary NONE = new ActiveSummary(0, 0, 0, Double.NaN, Double.NaN);

        /** 여러 차원의 요약을 합친다. */
        public ActiveSummary plus(ActiveSummary o) {
            return new ActiveSummary(activeRegions + o.activeRegions, activePlayers + o.activePlayers, weightedSum + o.weightedSum,
                    minNaN(slowestActive, o.slowestActive), minNaN(slowestAny, o.slowestAny));
        }

        /** 플레이어 가중 평균(활성 region 이 없으면 NaN). */
        public double weightedAverage() { return activePlayers == 0 ? Double.NaN : weightedSum / activePlayers; }

        private static double minNaN(double a, double b) { return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.min(a, b); }
    }

    /** 활성 region 요약(잠금 안에서 각 region 의 시작 시각 고리·플레이어 수만 읽는다). */
    public ActiveSummary activeSummary(long now, long windowNanos, long minHistoryNanos) {
        lock.lock();
        try {
            int regionsN = 0, playersN = 0;
            double sum = 0, slowActive = Double.NaN, slowAny = Double.NaN;
            for (Region r : regions) {
                if (!r.alive || r.yielded) continue;
                double tps = r.tickStarts.rate(now, windowNanos, minHistoryNanos);
                if (Double.isNaN(tps)) continue;
                slowAny = Double.isNaN(slowAny) ? tps : Math.min(slowAny, tps);
                int p = r.observedPlayers;
                if (p <= 0) continue;
                regionsN++;
                playersN += p;
                sum += p * tps;
                slowActive = Double.isNaN(slowActive) ? tps : Math.min(slowActive, tps);
            }
            return new ActiveSummary(regionsN, playersN, sum, slowActive, slowAny);
        } finally {
            lock.unlock();
        }
    }

    /** 플러그인 API 용: 관측 사본에 최근 틱 속도(window 동안)를 더한 값. */
    public record RegionView(RegionInfo info, double tps) {}

    /** 모든 살아 있는 region 의 보기(id 순, 잠금 안에서 값만 복사). */
    public List<RegionView> regionViews(long windowNanos, long minHistoryNanos) {
        long now = System.nanoTime();
        List<RegionView> out = new ArrayList<>();
        lock.lock();
        try {
            for (Region r : regions) out.add(new RegionView(info(r, now), r.tickStarts.rate(now, windowNanos, minHistoryNanos)));
        } finally {
            lock.unlock();
        }
        out.sort(java.util.Comparator.comparingInt(v -> v.info().id()));
        return out;
    }

    /** 그 청크 구역의 주인 region 보기(없으면 null). */
    public RegionView regionViewAt(int cx, int cz, long windowNanos, long minHistoryNanos) {
        long now = System.nanoTime();
        lock.lock();
        try {
            Section s = sections.get(sectionKeyOfChunk(cx, cz));
            Region r = s == null ? null : resolve(s.owner);
            if (r == null || !r.alive) return null;
            return new RegionView(info(r, now), r.tickStarts.rate(now, windowNanos, minHistoryNanos));
        } finally {
            lock.unlock();
        }
    }

    /** 대기열에 든 region 수(틱 차례를 기다리는 것 — 잠금 안). */
    public int queuedRegions() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /** 이 차원 풀의 워커 스레드 이름·상태. */
    public List<String[]> workerStates() { return pool.workerStates(); }

    /**
     * 워치독용(잠금 없음): 지금 틱 중인 region 중 가장 오래 돈 시간과, 큐 머리가 마감을 넘긴 시간 중 큰 값. 아무 문제 없으면 0 이하.
     * 큐 머리 지연은 워커가 모두 막혔을 때 드러난다.
     */
    public long stalledNanos(long now) {
        long worst = 0;
        for (Long started : running.values()) worst = Math.max(worst, now - started);
        long hd = headDue;
        if (hd != Long.MAX_VALUE) worst = Math.max(worst, now - hd);
        return worst;
    }

    /** 워치독용: 가장 오래 틱 중인 region 의 워커 스레드(없으면 null). */
    public Thread longestRunningThread(long now) {
        long worst = -1;
        Thread t = null;
        for (var e : running.entrySet()) {
            long d = now - e.getValue();
            if (d > worst) {
                worst = d;
                t = e.getKey().tickThread;
            }
        }
        return t;
    }

    public String dimension() { return world.dimension(); }

    /** 살아 있는 region 수(잠금 안 — 테스트·모니터링). */
    public int regionCount() {
        lock.lock();
        try {
            return regions.size();
        } finally {
            lock.unlock();
        }
    }

    /** 살아 있는 region 목록 사본(테스트·모니터링). */
    List<Region> regionsSnapshot() {
        lock.lock();
        try {
            return new ArrayList<>(regions);
        } finally {
            lock.unlock();
        }
    }

    /** 진단용 상태 문자열. */
    String debugState() {
        lock.lock();
        try {
            StringBuilder b = new StringBuilder();
            for (Region r : regions) {
                b.append("#").append(r.id).append(" alive=").append(r.alive).append(" ticking=").append(r.ticking)
                        .append(" yielded=").append(r.yielded).append(" queued=").append(r.queued).append(" inbox=").append(r.inbox.size())
                        .append(" sections=").append(r.sections).append(" pending=");
                for (Region p : r.pendingMerges) b.append(p.id).append(p.alive ? "" : "(dead)").append(',');
                b.append('\n');
            }
            b.append("queue=").append(queue.size()).append(" sections=").append(sections.keySet());
            return b.toString();
        } finally {
            lock.unlock();
        }
    }

    /** 이 차원을 풀에서 뗀다. 돌던 틱은 끝까지 돌고(다음 예약은 거절) 풀이 이 차원 것이면 워커도 멈춘다(join 1초 상한). */
    void close() {
        lock.lock();
        try {
            closed = true;
            headDue = Long.MAX_VALUE;
        } finally {
            lock.unlock();
        }
        LIVE.remove(this);
        pool.unregister(this);   // 잠금을 놓은 뒤(R1)
        if (ownsPool) pool.close();
    }
}
