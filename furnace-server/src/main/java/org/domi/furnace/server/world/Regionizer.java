// 동적 region(Folia ThreadedRegionizer 방식): 청크를 구역(2^shift 청크 정사각형)으로 묶고, 반경 안의 구역을 가진 region 은 합친다.
// 멀어지면(죽은 구역이 늘면) 연결 성분으로 나눈다. region 마다 다음 틱 시각을 갖고 작업 스레드 풀이 가장 이른 region 부터 돈다.
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
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
    private final Condition wake = lock.newCondition();
    /** 구역 키 → 구역. 쓰기는 잠금 안, 읽기(주인 조회)는 어디서나. */
    private final ConcurrentHashMap<Long, Section> sections = new ConcurrentHashMap<>();
    /** 살아 있는 region(잠금 안). */
    private final Set<Region> regions = new LinkedHashSet<>();
    /** 틱 대기열(잠금 안, 지연 삭제 — 꺼낼 때 상태를 다시 본다). */
    private final PriorityQueue<Region> queue = new PriorityQueue<>((a, b) -> Long.compare(a.nextTickNanos, b.nextTickNanos));
    private final Thread[] workers;
    private volatile boolean running = true;
    /** 거짓이면 region 틱은 작업만 처리하고 게임 틱은 건너뛴다(서버가 다 준비되기 전·테스트). */
    private volatile boolean gameTicks;
    private int nextId;

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
        this.workers = new Thread[Math.max(1, threads)];
        for (int i = 0; i < workers.length; i++) {
            // 차원마다 작업 스레드가 따로다(오버월드 furnace-region-N, 네더 furnace-region-the_nether-N …)
            String dim = world.dimensionType() == DimensionType.OVERWORLD ? "" : world.dimension().substring(world.dimension().indexOf(':') + 1) + "-";
            Thread t = new Thread(this::workerLoop, "furnace-region-" + dim + i);
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY - 1);   // 틱 스레드는 백그라운드 생성·저장보다 먼저 CPU 를 받는다
            workers[i] = t;
            t.start();
        }
    }

    public int shift() { return shift; }

    public int radius() { return radius; }

    public int threadCount() { return workers.length; }

    void setTickNanos(long nanos) { this.tickNanos = Math.max(1L, nanos); }

    long tickNanos() { return tickNanos; }

    void gameTicks(boolean on) { this.gameTicks = on; }

    boolean gameTicks() { return gameTicks; }

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
        Region r = ownerForTask(chunkX(chunkKey), chunkZ(chunkKey));
        r.inbox.add(new Region.Task(chunkKey, task));
        if (!r.alive) reroute(r);
    }

    /** 주인 region 이 이미 있을 때만 작업을 넣는다(조회성 — region 을 만들지 않는다). 주인이 없으면 false. */
    boolean enqueueIfOwned(long chunkKey, java.util.function.Consumer<Region> task) {
        Region r = ownerOf(chunkX(chunkKey), chunkZ(chunkKey));
        if (r == null) return false;
        r.inbox.add(new Region.Task(chunkKey, task));
        if (!r.alive) reroute(r);
        return true;
    }

    /** 죽은 region 의 남은 작업을 지금 주인에게. */
    void reroute(Region dead) {
        Region.Task t;
        while ((t = dead.inbox.poll()) != null) enqueue(t.chunkKey(), t.run());
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
                r.inbox.add(new Region.Task(chunkKey(chunkX(sk) << shift, chunkZ(sk) << shift), w));
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
    private final java.util.concurrent.ConcurrentHashMap<Integer, long[]> tickStats = new java.util.concurrent.ConcurrentHashMap<>();

    private long[] lastInstall = new long[4];

    private void recordTick(Region r, long start, long end) {
        long[] st = tickStats.computeIfAbsent(r.id, k -> new long[2 + 200]);   // [0]=직전 시작, [1]=개수, 이후 (간격, 소요) 쌍
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
            log.info("[TICKSTATS] region #{} 간격 p50={}ms p95={}ms max={}ms / 소요 p50={}ms p95={}ms max={}ms", r.id,
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
            long[] is = Region.INSTALL_STATS.clone();
            long[] d = new long[4];
            for (int i = 0; i < 4; i++) d[i] = is[i] - lastInstall[i];
            lastInstall = is;
            if (d[0] > 0) {
                log.info("[TICKSTATS]   청크 설치 {}개: 넣기 {}ms 빛 {}ms 엔티티·예약틱 {}ms (개당 평균 {}µs)", d[0], d[1] / 1_000_000, d[2] / 1_000_000,
                        d[3] / 1_000_000, (d[1] + d[2] + d[3]) / d[0] / 1000);
            }
        }
    }

    /** 지금 스레드가 (어느 차원이든) region 틱 워커인가. */
    private static final ThreadLocal<Boolean> WORKER = ThreadLocal.withInitial(() -> false);

    public static boolean isRegionWorkerThread() { return WORKER.get(); }

    private void workerLoop() {
        WORKER.set(true);
        while (running) {
            Region r;
            lock.lock();
            try {
                r = takeDue();
                if (r == null) continue;
                r.ticking = true;
                r.tickThread = Thread.currentThread();
            } catch (InterruptedException e) {
                return;
            } finally {
                lock.unlock();
            }
            long t0 = STATS ? System.nanoTime() : 0L;
            try {
                boolean game = gameTicks;
                long tickStart = game ? System.nanoTime() : 0L;
                r.runTick(game);
                if (game) world.tickState().record(System.nanoTime() - tickStart);   // /tick query 의 틱 시간 표본
            } catch (Throwable t) {
                log.error("[TICK] region #{} 실패", r.id, t);
            }
            if (STATS) recordTick(r, t0, System.nanoTime());
            lock.lock();
            try {
                r.ticking = false;
                r.tickThread = null;
                afterTick(r);
            } catch (Throwable t) {
                log.error("[REGION] region #{} 틱 뒤 정리 실패", r.id, t);
            } finally {
                lock.unlock();
            }
        }
    }

    /** 다음에 돌 region(잠금 안). 아직 때가 안 됐으면 기다린다. 기다리다 깨면 null(다시 고른다). */
    private Region takeDue() throws InterruptedException {
        while (true) {
            Region head = queue.peek();
            if (head == null) {
                wake.await(50, TimeUnit.MILLISECONDS);
                return null;
            }
            if (!head.alive || head.yielded || head.ticking || !head.queued) {
                queue.poll();
                head.queued = false;
                continue;
            }
            long wait = head.nextTickNanos - System.nanoTime();
            if (wait > 0) {
                long spin = org.domi.furnace.server.sched.PreciseWait.spinMargin();
                if (spin > 0L && wait <= spin) {
                    // 대기 해상도가 거친 플랫폼: 마지막 구간은 잠금을 놓고 돌며 기다려 틱 시작이 흔들리지 않게 한다
                    long due = head.nextTickNanos;
                    lock.unlock();
                    try {
                        org.domi.furnace.server.sched.PreciseWait.parkUntil(due);
                    } finally {
                        lock.lock();
                    }
                    return null;
                }
                wake.awaitNanos(spin > 0L ? wait - spin : wait);
                return null;
            }
            queue.poll();
            head.queued = false;
            return head;
        }
    }

    /** 틱 끝(잠금 안): 합치기 → 나누기 → 빈 region 정리 → 다음 틱 예약. */
    private void afterTick(Region r) {
        updateSprintSummary(r);
        resolveMerges(r);
        if (!r.alive || r.yielded) return;
        r.ticksSinceRecalc++;
        if ((r.deadSections > 0 || r.sections.isEmpty()) && r.ticksSinceRecalc >= RECALC_INTERVAL_TICKS) {
            r.ticksSinceRecalc = 0;
            split(r);
        }
        if (!r.alive) return;
        long now = System.nanoTime();
        updateSprintSummary(r);   // 병합으로 이어받은 상태까지
        if (r.sprintPending) {
            // /tick sprint: 기다리지 않고 곧바로 다음 틱(같은 풀의 다른 region 과는 마감 순서로 번갈아). 전역을 기다린 틱이면 잠깐 뒤에.
            r.nextTickNanos = r.sprintHeld() ? now + SPRINT_HOLD_NANOS : now;
        } else {
            r.nextTickNanos += tickNanos;
            if (r.nextTickNanos < now) r.nextTickNanos = now + tickNanos;   // 너무 밀렸으면 재동기(따라잡기 폭주 방지) — sprint 끝도 여기로 돌아온다
        }
        schedule(r);
    }

    private void schedule(Region r) {
        if (r.queued || !r.alive || r.yielded) return;
        r.queued = true;
        queue.add(r);
        wake.signalAll();
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

    /** 미룬 병합 상대 목록(없으면 null)을 돌려준다. */
    private List<Region> resolveMerges(Region r, List<Region> deferred) {
        while (!r.pendingMerges.isEmpty()) {
            var it = r.pendingMerges.iterator();
            Region o = resolve(it.next());
            it.remove();
            if (o == null || o == r || !o.alive) continue;
            if (!sprintCompatible(r, o)) {   // 참여 region 끼리 마친 틱이 다르면 한 틱 미룬다
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
            n.inheritSteps(r.stepGeneration(), r.stepsLeft());   // 나뉜 조각은 부모의 남은 걸음을 물려받는다
            n.inheritSprint(r.sprintGeneration(), r.sprintLeft(), r.sprintJoined());   // sprint 틱도
            // 방금 돈 틱의 다음 — 나뉜 조각이 곧바로 한 번 더 돌지 않게. sprint 중이면 부모처럼 곧바로
            n.nextTickNanos = r.sprintLeft() > 0 ? r.nextTickNanos : r.nextTickNanos + tickNanos;
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
                enqueue(task.chunkKey(), task.run());
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

    void close() {
        running = false;
        lock.lock();
        try {
            wake.signalAll();
        } finally {
            lock.unlock();
        }
        for (Thread t : workers) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
