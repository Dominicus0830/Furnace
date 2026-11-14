// 청크 생성 대기열(T-170). furnace-gen-wait 의 FIFO(newFixedThreadPool) 를 대신한다.
//  ① 생성 요청은 그 차원 플레이어들과의 지금 체비셰프 거리 순(50ms 마다 다시 잰다), 같으면 넣은 순. 플레이어가 없는 차원은 넣은 순.
//  ② 플레이어 미리 불러오기로 걸린 요청은 시작 전에 어느 플레이어 추적 범위(바닐라 ChunkTrackingView, 바깥 한 겹 포함)에도 없으면 취소한다
//     — 취소된 요청은 실패로 끝나 플레이어 자리(chunkPrefetches)가 돌아간다. 이미 시작한 생성(원형·장식 중간 상태)은 건드리지 않는다.
//  ③ 플레이어당 생성 칸 = clamp(전역 예산 ÷ 생성 칸을 쓰는 플레이어 수, 1, 상한) — 인원이 적으면 한 사람이 다 쓰고, 많으면 나눠 쓴다.
//     (Region.sendQueuedChunksAuto 가 디스크에 없는 청크에만 이 칸을 쓴다. 디스크 청크는 예전 상한 그대로.)
//  ④ 차례(turnOwners>0): 생성 칸을 쓰는 사람을 줄 세워 앞의 K 명만 큰 칸(turnQuota)을 받고 그 사람들 요청을 먼저 뽑는다.
//     한 사람이 turnQuota 개를 시작하면(또는 오래 걸리거나 더 원하지 않으면) 줄 뒤로 가고 다음 사람이 받는다. 생성은 이웃 원형(7×7)을
//     함께 쓰므로 한 사람 둘레를 몰아 만드는 쪽이 같은 일로 청크를 더 많이 끝낸다 — 몰아 주되 돌아가며 몰아 줘서 아무도 굶지 않는다.
//  시간 티켓(순간 이동 뒤·포털, World.addTicket)으로 걸린 요청은 티켓이 살아 있는 동안은 시야 밖이어도 남기고, 끝나면 시야 밖일 때 취소한다
//  (바닐라: 티켓이 빠지면 대기 작업이 내려간다). 그 밖의 요청(플러그인·스폰 청크·영구 티켓 등, "고정")은 취소하지 않고, 오래 기다리면(pinnedMaxWait) 거리와 상관없이 먼저 뽑는다.
//  위치 표시가 없는 작업(재생성 등)은 늘 먼저 넣은 순.
// 생성 결과는 생성 순서에만 기대고(바닐라도 순서 의존 — 1차·B2 기준과 같음), 무엇이 만들어지는지는 바뀌지 않는다.
// -Dfurnace.gen.order=fifo 면 예전과 같은 동작(넣은 순, 취소 없음, 생성 칸 없음) — 비교 측정·되돌리기용.
package org.domi.furnace.server.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.domi.furnace.server.entity.Player;

public final class GenQueue extends AbstractExecutorService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GenQueue.class);

    /** 운영 손잡이(시스템 속성). 기본값은 T-170 측정으로 정했다(docs/feature-parity/context-notes/2026-11-14-t170-gen-queue.md). */
    public record Settings(boolean distanceOrder, int budget, int fixedPlayerCap, int maxPlayerCap, long cancelDelayNanos,
                           int cancelMargin, long pinnedMaxWaitNanos, boolean strictBudget, int turnOwners, int turnQuota, long turnMaxNanos) {
        /** 차례 없이(시험·예전 판). */
        public Settings(boolean distanceOrder, int budget, int fixedPlayerCap, int maxPlayerCap, long cancelDelayNanos,
                        int cancelMargin, long pinnedMaxWaitNanos, boolean strictBudget) {
            this(distanceOrder, budget, fixedPlayerCap, maxPlayerCap, cancelDelayNanos, cancelMargin, pinnedMaxWaitNanos, strictBudget, 0, 0, 0);
        }

        public static Settings fromSystem(int threads, int maxPlayerCap) {
            boolean fifo = "fifo".equalsIgnoreCase(System.getProperty("furnace.gen.order", "distance"));
            int budget = Math.max(1, Integer.getInteger("furnace.gen.budget", threads * DEFAULT_BUDGET_PER_THREAD));
            int quota = Math.max(1, Integer.getInteger("furnace.gen.turnQuota", maxPlayerCap));
            // 차례 인원 기본 = 예산을 차례 칸으로 나눈 수(적어도 2 — 한 사람 차례가 끝나 다음 사람이 걸기 전까지 스레드가 놀지 않게)
            int owners = Integer.getInteger("furnace.gen.turnOwners", Math.max(2, budget / quota));
            return new Settings(!fifo, budget, Math.max(0, Integer.getInteger("furnace.gen.playerCap", 0)), maxPlayerCap,
                    TimeUnit.MILLISECONDS.toNanos(Math.max(0, Long.getLong("furnace.gen.cancelDelayMs", 0L))),
                    Math.max(0, Integer.getInteger("furnace.gen.cancelMargin", 0)),
                    TimeUnit.MILLISECONDS.toNanos(Math.max(0, Long.getLong("furnace.gen.pinnedMaxWaitMs", 3000L))),
                    Boolean.getBoolean("furnace.gen.strictBudget"), Math.max(0, owners), quota,
                    TimeUnit.MILLISECONDS.toNanos(Math.max(1, Long.getLong("furnace.gen.turnMaxMs", 2000L))));
        }

        boolean turns() { return distanceOrder && fixedPlayerCap == 0 && turnOwners > 0; }
    }

    /** 전역 예산 = 생성 스레드 × 이 값(4코어 16). */
    static final int DEFAULT_BUDGET_PER_THREAD = 4;
    /** 생성 칸을 원한 지 이만큼 안이면 "탐험 중"으로 센다. */
    static final long WANT_WINDOW_NANOS = 1_000_000_000L;
    /** 거리를 다시 재는 간격. */
    static final long REPRIO_NANOS = 50_000_000L;
    /** 대기열 길이·시야 밖 수를 ChunkTrace 에 남기는 간격. */
    private static final long METRIC_NANOS = 1_000_000_000L;
    /** 플레이어가 없는 차원의 요청 — 플레이어 가까운 요청 뒤에 넣은 순. */
    static final int PRIO_NO_PLAYERS = Integer.MAX_VALUE - 2;
    /** 미리 생성(T-175) 같은 뒤로 미룬 작업 — 맨 뒤. */
    static final int PRIO_BACKGROUND = Integer.MAX_VALUE - 1;

    /**
     * 생성 요청 하나의 상태. 취소는 대기열만, 고정(pin)은 다른 요청자가 같은 진행 중 로드를 기다릴 때 Region 이 건다 —
     * 둘이 엇갈려도 한쪽만 이긴다(같은 원자 값 하나에 CAS). 고정된 요청은 취소되지 않는다.
     */
    public static final class Ticket {
        static final int QUEUED = 0, PINNED = 1, STARTED = 2, CANCELLED = 3;
        private final AtomicInteger state;
        /** 시야 밖이어도 살려 둘 조건(시간 티켓 — 바닐라처럼 티켓이 살아 있는 동안만 올린다). null 이면 시야만 본다. 생성 스레드에서 부른다. */
        final java.util.function.BooleanSupplier keepAlive;
        /** 이 요청을 건 플레이어(entityId, 차례 계산용). 없으면 -1. */
        final int owner;

        public Ticket(boolean cancellable) { this(cancellable, null, -1); }

        public Ticket(boolean cancellable, java.util.function.BooleanSupplier keepAlive) { this(cancellable, keepAlive, -1); }

        public Ticket(boolean cancellable, java.util.function.BooleanSupplier keepAlive, int owner) {
            state = new AtomicInteger(cancellable ? QUEUED : PINNED);
            this.keepAlive = keepAlive;
            this.owner = owner;
        }

        /** 다른 요청자가 붙는다: 취소 못 하게. 이미 취소됐으면 거짓(그 로드는 실패로 끝난다 — 다시 걸어야 한다). */
        public boolean pin() {
            while (true) {
                int s = state.get();
                if (s == CANCELLED) return false;
                if (s != QUEUED || state.compareAndSet(QUEUED, PINNED)) return true;
            }
        }

        /** 생성 직전에 부른다. 거짓이면 취소된 요청 — 만들지 않는다. */
        public boolean tryStart() {
            while (true) {
                int s = state.get();
                if (s == CANCELLED) return false;
                if (s == STARTED || state.compareAndSet(s, STARTED)) return true;
            }
        }

        boolean tryCancel() { return state.compareAndSet(QUEUED, CANCELLED); }
        boolean cancellable() { return state.get() == QUEUED; }
        public boolean cancelled() { return state.get() == CANCELLED; }
    }

    private static final class Task {
        final Runnable r;
        final World w;
        final int cx, cz;
        final Ticket ticket;
        final boolean background;
        final long seq, t0;
        int prio;
        long staleSince;

        Task(Runnable r, World w, int cx, int cz, Ticket ticket, boolean background, long seq, long t0) {
            this.r = r; this.w = w; this.cx = cx; this.cz = cz; this.ticket = ticket; this.background = background; this.seq = seq; this.t0 = t0;
        }
    }

    private final Settings settings;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition ready = lock.newCondition();
    /** 위치 없는 작업(먼저, 넣은 순). */
    private final ArrayDeque<Task> plain = new ArrayDeque<>();
    /** 생성 요청(거리 순으로 고른다). 순서는 seq 로 따로 지키므로 빼낼 때 끝 원소로 메운다. */
    private final ArrayList<Task> gen = new ArrayList<>();
    private final CopyOnWriteArrayList<World> worlds = new CopyOnWriteArrayList<>();
    private final CountDownLatch done;
    private long seq;
    private long lastReprio = Long.MIN_VALUE / 2, lastMetric;
    private volatile boolean shutdown;
    private volatile int playerCap;
    /** strictBudget: 디스크에 없는 미리 불러오기 전체 수(전역 토큰). */
    private final AtomicInteger outstanding = new AtomicInteger();

    public GenQueue(int threads, Settings settings) {
        this.settings = settings;
        this.playerCap = settings.distanceOrder() ? initialCap(settings) : Integer.MAX_VALUE;
        this.done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::work, "furnace-gen-wait");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
    }

    private static int initialCap(Settings s) {
        return s.fixedPlayerCap() > 0 ? s.fixedPlayerCap() : Math.clamp(s.budget(), 1, s.maxPlayerCap());
    }

    public Settings settings() { return settings; }

    /** 플레이어 수를 셀 차원(World 생성자가 부른다). */
    void addWorld(World w) { worlds.addIfAbsent(w); }

    /** 내린 차원(WorldManager.unload). */
    public void removeWorld(World w) { worlds.remove(w); }

    /**
     * 플레이어 한 명이 동시에 걸 수 있는 "디스크에 없는" 미리 불러오기 수. FIFO 모드는 제한 없음(예전 동시 진행 상한만).
     * 50ms 마다 다시 잰다 — 생성 칸을 쓰는 사람 수가 바뀌면 다음 계산에서 따라간다.
     */
    public int playerCap() { return playerCap; }

    /** 생성 칸을 쓰는 사람 수(50ms 마다 다시 세고, 그 사이 새로 원하기 시작한 사람은 capFor 가 바로 더한다). */
    private final AtomicInteger exploringCount = new AtomicInteger();

    private int capOf(int exploring) {
        return Math.clamp(settings.budget() / Math.max(1, exploring), 1, settings.maxPlayerCap());
    }

    /**
     * 플레이어가 지금 쓸 생성 칸. newcomer = 방금 디스크에 없는 청크를 원하기 시작했다(1초 안에 원한 적도, 걸어 둔 생성도 없음) —
     * 그 사람을 곧바로 인원에 더해 나눈다. 모두가 한꺼번에 순간 이동하거나 접속해도 첫 사람들만 조금 더 받고(합 ≈ 예산 × ln 인원)
     * 예산 전부를 저마다 걸지 않는다.
     */
    public int capFor(boolean newcomer) { return capFor(-1, newcomer); }

    /** 차례가 켜져 있으면 차례인 사람은 turnQuota, 줄 선 사람은 1(시야 밖 취소로 자리가 돌아오고, 차례가 오면 바로 시작할 하나). */
    public int capFor(int owner, boolean newcomer) {
        if (!settings.distanceOrder()) return Integer.MAX_VALUE;
        if (settings.fixedPlayerCap() > 0) return settings.fixedPlayerCap();
        if (settings.turns() && owner >= 0) {
            lock.lock();
            try {
                Turn t = turnActive.get(owner);
                if (t != null) return settings.turnQuota();
                if (turnQueued.add(owner)) {
                    turnWait.addLast(owner);
                    fillTurnsLocked(System.nanoTime());
                    if (turnActive.containsKey(owner)) return settings.turnQuota();
                }
                return 1;
            } finally {
                lock.unlock();
            }
        }
        return capOf(newcomer ? exploringCount.incrementAndGet() : Math.max(1, exploringCount.get()));
    }

    /** 차례 하나: 시작한 수와 받은 때, 마지막으로 대기열에 요청이 있던 때. */
    private static final class Turn {
        int starts;
        final long since;
        long lastQueued;
        Turn(long now) { since = now; lastQueued = now; }
    }

    /** 차례인 사람(entityId → 차례). */
    private final java.util.HashMap<Integer, Turn> turnActive = new java.util.HashMap<>();
    /** 줄(차례 기다리는 사람, 앞이 먼저). turnQueued 는 줄 또는 차례에 있는 사람 전부. */
    private final ArrayDeque<Integer> turnWait = new ArrayDeque<>();
    private final java.util.HashSet<Integer> turnQueued = new java.util.HashSet<>();
    /** 요청이 없어 차례를 넘기기까지 기다리는 시간(다음 틱 요청을 걸 틈). */
    static final long TURN_IDLE_NANOS = 150_000_000L;

    /** 잠금 안: 빈 차례 자리를 줄 앞사람으로 채운다. */
    private void fillTurnsLocked(long now) {
        while (turnActive.size() < settings.turnOwners() && !turnWait.isEmpty()) turnActive.put(turnWait.pollFirst(), new Turn(now));
    }

    /** 잠금 안: 차례를 끝내 줄 뒤로 보낸다. */
    private void endTurnLocked(int owner, long now) {
        if (turnActive.remove(owner) != null) {
            turnWait.addLast(owner);
            fillTurnsLocked(now);
        }
    }

    /** 잠금 안: 지금 차례인 사람의 요청인가(차례가 없거나 주인 없는 요청은 차례인 것과 같게 본다). */
    private boolean onTurn(Task t) {
        int o = t.ticket == null ? -1 : t.ticket.owner;
        return o < 0 || turnActive.containsKey(o);
    }

    /** strictBudget 일 때 전역 토큰 하나(없으면 거짓). 아니면 늘 참. */
    public boolean tryAcquireGenToken() {
        if (!settings.strictBudget() || !settings.distanceOrder()) return true;
        while (true) {
            int n = outstanding.get();
            if (n >= settings.budget()) return false;
            if (outstanding.compareAndSet(n, n + 1)) return true;
        }
    }

    public void releaseGenToken() {
        if (settings.strictBudget() && settings.distanceOrder()) outstanding.decrementAndGet();
    }

    /** 위치를 단 실행기: 이 실행기로 낸 작업은 생성 요청으로 거리 순 대기열에 선다. ticket 은 null 이면 고정. */
    public Executor tagged(World w, int cx, int cz, Ticket ticket) {
        return r -> submit(r, w, cx, cz, ticket, false);
    }

    /** 맨 뒤 작업(미리 생성 등). 플레이어 요청이 하나라도 있으면 그 뒤. */
    public Executor background(World w, int cx, int cz) {
        return r -> submit(r, w, cx, cz, null, true);
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown) throw new RejectedExecutionException("생성 대기열이 닫혔다");
        lock.lock();
        try {
            plain.add(new Task(command, null, 0, 0, null, false, seq++, System.nanoTime()));
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    static final int TRACE_PINNED = Integer.getInteger("furnace.gen.tracePinned", 0);
    static final AtomicInteger tracedPinned = new AtomicInteger();

    private void submit(Runnable r, World w, int cx, int cz, Ticket ticket, boolean background) {
        if (shutdown) throw new RejectedExecutionException("생성 대기열이 닫혔다");
        long now = System.nanoTime();
        int prio = background ? PRIO_BACKGROUND : settings.distanceOrder() ? nearestNow(w, cx, cz) : 0;
        lock.lock();
        try {
            Task t = new Task(r, w, cx, cz, ticket, background, seq++, now);
            t.prio = prio;
            gen.add(t);
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    /** 넣는 순간의 거리(대기열 잠금 밖). */
    private static int nearestNow(World w, int cx, int cz) {
        if (w == null) return PRIO_NO_PLAYERS;
        int best = PRIO_NO_PLAYERS;
        for (Player p : w.playerIndex().toArray()) {
            ChunkPos c = p.chunkPos();
            int d = Math.max(Math.abs(c.x() - cx), Math.abs(c.z() - cz));
            if (d < best) best = d;
        }
        return best;
    }

    /** 한 차원의 플레이어 위치 사본(다시 재는 동안만). */
    private static final class Snap {
        final int[] x, z, keep;
        Snap(Player[] ps, int serverVd, int margin) {
            x = new int[ps.length]; z = new int[ps.length]; keep = new int[ps.length];
            for (int i = 0; i < ps.length; i++) {
                ChunkPos c = ps[i].chunkPos();
                x[i] = c.x(); z[i] = c.z();
                // Region.trackingDistance 와 같은 값 + 바깥 한 겹(ChunkTrackingView.contains 는 축 방향으로 시야+1 칸까지 넣는다)
                keep[i] = Math.clamp(ps[i].viewDistance(), 2, Math.max(2, serverVd)) + 1 + margin;
            }
        }

        boolean keeps(int cx, int cz) {
            for (int k = 0; k < x.length; k++) if (Math.max(Math.abs(x[k] - cx), Math.abs(z[k] - cz)) <= keep[k]) return true;
            return false;
        }
    }

    /** 잠금 안: 거리를 다시 재고, 취소할 것은 대기열에서 빼 돌려준다. 생성 칸 계산과 지표도 여기서. */
    private List<Task> reprioLocked(long now) {
        if (now - lastReprio < REPRIO_NANOS) return null;
        lastReprio = now;
        if (!settings.distanceOrder()) {   // 넣은 순(예전): 취소·칸 없이 비교용 지표만
            if (ChunkTrace.ON && now - lastMetric >= METRIC_NANOS) {
                lastMetric = now;
                IdentityHashMap<World, Snap> snaps = new IdentityHashMap<>();
                int stale = 0;
                for (Task t : gen) {
                    if (t.background || t.w == null) continue;
                    Snap s = snaps.computeIfAbsent(t.w, w -> new Snap(w.playerIndex().toArray(), w.viewDistance(), settings.cancelMargin()));
                    if (!s.keeps(t.cx, t.cz)) stale++;
                }
                ChunkTrace.value("batch.gqlen", gen.size());
                ChunkTrace.value("batch.gqstale", stale);
            }
            return null;
        }
        IdentityHashMap<World, Snap> snaps = new IdentityHashMap<>();
        List<Task> cancelled = null;
        int stale = 0;
        for (int i = gen.size() - 1; i >= 0; i--) {
            Task t = gen.get(i);
            if (t.background || t.w == null) continue;
            Snap s = snaps.computeIfAbsent(t.w, w -> new Snap(w.playerIndex().toArray(), w.viewDistance(), settings.cancelMargin()));
            int best = PRIO_NO_PLAYERS;
            boolean kept = false;
            for (int k = 0; k < s.x.length; k++) {
                int d = Math.max(Math.abs(s.x[k] - t.cx), Math.abs(s.z[k] - t.cz));
                if (d < best) best = d;
                if (d <= s.keep[k]) kept = true;
            }
            t.prio = best;
            if (!kept && t.ticket != null && t.ticket.keepAlive != null && t.ticket.cancellable()) kept = t.ticket.keepAlive.getAsBoolean();
            if (kept) { t.staleSince = 0; continue; }
            if (t.staleSince == 0) t.staleSince = now;
            if (now - t.staleSince >= settings.cancelDelayNanos() && t.ticket != null && t.ticket.tryCancel()) {
                removeAt(i);
                if (cancelled == null) cancelled = new ArrayList<>();
                cancelled.add(t);
            } else if (t.ticket == null || !t.ticket.cancelled()) {
                stale++;
            }
        }
        // 생성 칸: 지금 디스크에 없는 청크를 기다리는 플레이어 수로 전역 예산을 나눈다
        if (settings.fixedPlayerCap() > 0) {
            playerCap = settings.fixedPlayerCap();
        } else {
            // 생성 칸을 쓰는 사람 = 지금 걸어 둔 생성이 있거나, 최근 1초 안에 디스크에 없는 청크를 원했던 사람(칸이 차서 못 건 사람 포함).
            // 걸어 둔 것만 세면 한꺼번에 순간 이동한 직후(옛 요청이 모두 취소돼 0명) 모두가 예산 전부를 한 번에 걸어 버린다.
            int exploring = 0;
            java.util.HashSet<Integer> ids = settings.turns() ? new java.util.HashSet<>() : null;
            for (World w : worlds) for (Player p : w.playerIndex().toArray()) {
                long want = p.genWantNanos();
                if (!p.genPrefetches().isEmpty() || (want != 0 && now - want < WANT_WINDOW_NANOS)) {
                    exploring++;
                    if (ids != null) ids.add(p.entityId());
                }
            }
            exploringCount.set(exploring);
            playerCap = capOf(exploring);
            if (ids != null) rotateLocked(ids, now);
        }
        if (ChunkTrace.ON && now - lastMetric >= METRIC_NANOS) {
            lastMetric = now;
            ChunkTrace.value("batch.gqlen", gen.size());
            ChunkTrace.value("batch.gqstale", stale);
            ChunkTrace.value("batch.gqcap", playerCap);
            int pinned = 0, noOwner = 0, maxPer = 0;
            java.util.HashMap<Integer, Integer> per = new java.util.HashMap<>();
            for (Task t : gen) {
                if (t.ticket == null || !t.ticket.cancellable()) pinned++;
                if (t.ticket == null || t.ticket.owner < 0) noOwner++;
                else maxPer = Math.max(maxPer, per.merge(t.ticket.owner, 1, Integer::sum));
            }
            ChunkTrace.value("batch.gqpin", pinned);
            ChunkTrace.value("batch.gqnoown", noOwner);
            ChunkTrace.value("batch.gqowners", per.size());
            ChunkTrace.value("batch.gqmaxper", maxPer);
        }
        return cancelled;
    }

    /** 잠금 안: 더는 생성 칸을 쓰지 않는 사람을 빼고, 오래 걸렸거나 요청이 없는 차례를 넘긴다. */
    private void rotateLocked(java.util.Set<Integer> exploring, long now) {
        turnQueued.retainAll(exploring);
        turnWait.removeIf(o -> !turnQueued.contains(o));
        turnActive.keySet().removeIf(o -> !turnQueued.contains(o));
        for (Task t : gen) {
            if (t.ticket == null || t.ticket.owner < 0) continue;
            Turn turn = turnActive.get(t.ticket.owner);
            if (turn != null) turn.lastQueued = now;
        }
        List<Integer> done = null;
        for (var e : turnActive.entrySet()) {
            Turn turn = e.getValue();
            if (now - turn.since >= settings.turnMaxNanos() || now - turn.lastQueued >= TURN_IDLE_NANOS) {
                if (done == null) done = new ArrayList<>();
                done.add(e.getKey());
            }
        }
        if (done != null) for (int o : done) endTurnLocked(o, now);
        fillTurnsLocked(now);
    }

    private void removeAt(int i) {
        Task last = gen.remove(gen.size() - 1);
        if (i < gen.size()) gen.set(i, last);
    }

    /** 잠금 안: 다음 작업. 위치 없는 작업 먼저, 그다음 오래 기다린 고정 요청, 그다음 가까운 순(같으면 넣은 순). */
    private Task pickLocked(long now) {
        Task p = plain.poll();
        if (p != null) return p;
        int bi = -1;
        boolean turns = settings.turns();
        long bestKey = Long.MAX_VALUE, bestSeq = Long.MAX_VALUE;
        for (int i = 0, n = gen.size(); i < n; i++) {
            Task t = gen.get(i);
            long key;
            if (!settings.distanceOrder()) key = 0;
            else if (!t.background && (t.ticket == null || !t.ticket.cancellable()) && now - t.t0 >= settings.pinnedMaxWaitNanos()) key = -1;
            else if (turns && !t.background && !onTurn(t)) key = (1L << 32) + t.prio;   // 줄 선 사람 요청은 차례인 요청 뒤
            else key = t.prio;
            if (t.background && settings.distanceOrder()) key = (2L << 32) + key;   // 미리 생성은 줄 선 요청보다도 뒤
            if (key < bestKey || (key == bestKey && t.seq < bestSeq)) {
                bestKey = key; bestSeq = t.seq; bi = i;
            }
        }
        if (bi < 0) return null;
        Task t = gen.get(bi);
        removeAt(bi);
        if (turns && t.ticket != null && t.ticket.owner >= 0) {
            Turn turn = turnActive.get(t.ticket.owner);
            if (turn != null && ++turn.starts >= settings.turnQuota()) endTurnLocked(t.ticket.owner, now);
        }
        return t;
    }

    private void work() {
        try {
            while (true) {
                Task t;
                List<Task> cancelled;
                lock.lock();
                try {
                    while (!shutdown && plain.isEmpty() && gen.isEmpty()) ready.awaitUninterruptibly();
                    if (shutdown && plain.isEmpty() && gen.isEmpty()) return;
                    long now = System.nanoTime();
                    cancelled = reprioLocked(now);
                    t = pickLocked(now);
                } finally {
                    lock.unlock();
                }
                if (cancelled != null) for (Task c : cancelled) runCancelled(c);
                if (t == null) continue;
                run(t);
            }
        } finally {
            done.countDown();
        }
    }

    /** 취소된 요청을 돌린다: 시작 관문(Ticket.tryStart)이 거짓이라 만들지 않고 실패로 끝난다 → 기다리던 쪽이 자리를 돌려준다. */
    private static void runCancelled(Task c) {
        if (ChunkTrace.ON) ChunkTrace.nanos("gen.cancel", System.nanoTime() - c.t0);
        try {
            c.r.run();
        } catch (Throwable e) {
            LOG.warn("[GEN] 취소한 생성 요청 정리 실패 {},{}", c.cx, c.cz, e);
        }
    }

    private static void run(Task t) {
        boolean genTask = t.w != null || t.background;
        long t1 = ChunkTrace.ON ? System.nanoTime() : 0L;
        if (ChunkTrace.ON && genTask) ChunkTrace.nanos("gen.queue", t1 - t.t0);
        try {
            t.r.run();
        } catch (Throwable e) {
            LOG.warn("[GEN] 생성 작업 실패 {},{}", t.cx, t.cz, e);
        }
        if (ChunkTrace.ON && genTask && (t.ticket == null || !t.ticket.cancelled())) ChunkTrace.nanos("gen.run", System.nanoTime() - t1);
    }

    /** 시험용: 대기열 안 생성 요청 수. */
    int queuedGen() {
        lock.lock();
        try { return gen.size(); } finally { lock.unlock(); }
    }

    /** 시험용: 지금 바로 거리를 다시 잰다(간격 무시). 취소된 것은 이 스레드에서 정리한다. */
    void reprioNow() {
        List<Task> cancelled;
        lock.lock();
        try {
            lastReprio = Long.MIN_VALUE / 2;
            cancelled = reprioLocked(System.nanoTime());
        } finally {
            lock.unlock();
        }
        if (cancelled != null) for (Task c : cancelled) runCancelled(c);
    }

    @Override
    public void shutdown() {
        List<Task> cancelled = new ArrayList<>();
        lock.lock();
        try {
            if (shutdown) return;
            shutdown = true;
            // 남은 미리 불러오기는 아무도 기다리지 않는다 — 닫는 동안 수백 개를 만들지 않게 취소한다(고정 요청은 끝까지 돈다)
            for (int i = gen.size() - 1; i >= 0; i--) {
                Task t = gen.get(i);
                if (t.ticket != null && t.ticket.tryCancel()) {
                    removeAt(i);
                    cancelled.add(t);
                }
            }
            ready.signalAll();
        } finally {
            lock.unlock();
        }
        for (Task c : cancelled) runCancelled(c);
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return List.of();
    }

    @Override
    public boolean isShutdown() { return shutdown; }

    @Override
    public boolean isTerminated() { return shutdown && done.getCount() == 0; }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return done.await(timeout, unit);
    }
}
