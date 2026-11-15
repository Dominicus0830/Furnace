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
// 생성 조절기(T-172, furnace.properties worldgen.tick-guard, 기본 꺼짐 — 결정 W-2): 켜면 가장 느린 region 의 최근 1초 TPS 가
//  TICK_GUARD_LOW 아래면 동시에 도는 생성 요청 수를 하나 줄이고(1 까지), TICK_GUARD_HIGH 이상이면 하나 늘린다(스레드 수까지).
//  위치 없는 작업(빛 미리 계산 등 이어 하기)은 세지 않는다. 끄면 이 갈래는 아무것도 하지 않는다(예전과 같다).
package org.domi.furnace.server.world;

import org.domi.furnace.server.i18n.Lang;
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

        public boolean turns() { return distanceOrder && fixedPlayerCap == 0 && turnOwners > 0; }
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
        /** 미리 생성(T-175)이 건 요청 — 맨 뒤. 다른 요청자가 붙으면 promote 로 내린다(그 뒤로는 고정 요청과 같다). */
        private volatile boolean background;

        /** 미리 생성 요청: 맨 뒤에 서고, 시야 밖 취소·차례와 무관하다. 미리 생성을 멈추면 시작 전에 cancelBackground 로 취소한다. */
        public static Ticket forBackground() {
            Ticket t = new Ticket(true, null, -1);
            t.background = true;
            return t;
        }

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

        /** 미리 생성 요청인가(다른 요청자가 붙어 내려오기 전). */
        public boolean isBackground() { return background; }

        /**
         * 다른 요청자(플레이어·플러그인 등)가 미리 생성 요청에 붙는다: 고정하고 맨 뒤에서 꺼낸다 — 붙은 사람이 미리 생성 차례를 기다리지 않게.
         * 이미 취소됐으면(미리 생성 멈춤) 거짓 — 그 로드는 실패로 끝난다(다시 걸어야 한다).
         */
        public boolean promote() {
            if (!pin()) return false;
            background = false;
            return true;
        }

        /** 미리 생성을 멈출 때: 아직 시작 안 했고 아무도 안 붙은 미리 생성 요청만 취소한다. */
        public boolean cancelBackground() { return background && state.compareAndSet(QUEUED, CANCELLED); }

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
        /** 조절기가 세는 생성 요청으로 시작했다(끝날 때 genRunning 을 돌려준다). */
        boolean counted;

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

    /** 생성 조절기 문턱(가장 느린 region 의 1초 TPS): 아래면 줄이고, 이상이면 늘린다. W60 시제품 값. */
    static final double TICK_GUARD_LOW = 19.0, TICK_GUARD_HIGH = 19.8;
    /** 조절기가 TPS 를 보는 간격. */
    static final long TICK_GUARD_PERIOD_MS = 250;
    private static final long TICK_GUARD_WINDOW = 1_000_000_000L;
    /** 생성 조절기를 켰나(T-172). 꺼져 있으면 genLimit·genRunning 을 쓰지 않는다. */
    private final boolean tickGuard;
    private final int threads;
    /** 동시에 도는 생성 요청 상한(조절기가 바꾼다, 1..threads). 잠금 안에서 쓰고 읽는다. */
    private int genLimit;
    /** 지금 도는 생성 요청 수(조절기를 켰을 때만 센다). 잠금 안. */
    private int genRunning;
    /** 조절기 스레드(닫을 때 깨운다). 없으면 null. */
    private Thread guard;
    /** 상한 변경 로그 빈도 제한(마지막으로 남긴 때). */
    private long lastLimitLog = Long.MIN_VALUE / 2;
    private static final long LIMIT_LOG_NANOS = 10_000_000_000L;

    public GenQueue(int threads, Settings settings) { this(threads, settings, false); }

    /** tickGuard = 생성 조절기(furnace.properties worldgen.tick-guard). */
    public GenQueue(int threads, Settings settings, boolean tickGuard) { this(threads, settings, tickGuard, true); }

    /** 시험용: guardThread=false 면 조절기 스레드 없이 adjustLimit 을 손으로 부른다. */
    GenQueue(int threads, Settings settings, boolean tickGuard, boolean guardThread) {
        this.settings = settings;
        this.tickGuard = tickGuard;
        this.threads = threads;
        this.genLimit = threads;
        this.playerCap = settings.distanceOrder() ? initialCap(settings) : Integer.MAX_VALUE;
        this.done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::work, "furnace-gen-wait");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        if (tickGuard && guardThread) {
            Thread c = new Thread(this::guardLoop, "furnace-gen-throttle");
            c.setDaemon(true);
            guard = c;
            c.start();
        }
    }

    /** 생성 조절기를 켰나. */
    public boolean tickGuard() { return tickGuard; }

    /** 지금 동시 생성 상한(조절기가 꺼져 있으면 스레드 수 — 일꾼 수가 곧 상한). */
    public int genLimit() {
        lock.lock();
        try { return genLimit; } finally { lock.unlock(); }
    }

    /** 조절기 스레드: 가장 느린 region 의 1초 TPS 를 보고 상한을 고친다. */
    private void guardLoop() {
        while (!shutdown) {
            try {
                Thread.sleep(TICK_GUARD_PERIOD_MS);
            } catch (InterruptedException e) {
                return;
            }
            try {
                adjustLimit(worstTps(System.nanoTime()));
            } catch (RuntimeException e) {   // 조절기가 죽어 상한이 낮은 채로 굳지 않게 — 이번만 건너뛴다
                LOG.warn(Lang.t("gen.throttle-check-failed"), e);
            }
        }
    }

    /** 모든 차원에서 가장 느린 region 의 최근 1초 TPS(잴 수 있는 region 이 없으면 20). */
    private double worstTps(long now) {
        List<Regionizer.RegionRate> rates = new ArrayList<>(worlds.size());
        for (World w : worlds) {
            Regionizer rz = w.regionizer();
            if (rz != null) rates.add(rz.slowestRegion(now, TICK_GUARD_WINDOW, TICK_GUARD_WINDOW));
        }
        return worstOf(rates);
    }

    /** 차원별 가장 느린 region 중 최저 TPS(null = 잴 region 없음). 모두 없으면 20. */
    static double worstOf(List<Regionizer.RegionRate> perDimension) {
        double worst = 20;
        for (Regionizer.RegionRate r : perDimension) if (r != null && r.tps() < worst) worst = r.tps();
        return worst;
    }

    /** 한 번 조절한다: worstTps 가 낮으면 하나 줄이고 높으면 하나 늘린다. 바뀐 상한을 돌려준다. 조절기가 꺼져 있으면 아무것도 안 한다. */
    int adjustLimit(double worstTps) {
        if (!tickGuard) return threads;
        lock.lock();
        try {
            int p = genLimit;
            if (worstTps < TICK_GUARD_LOW && p > 1) p--;
            else if (worstTps >= TICK_GUARD_HIGH && p < threads) p++;
            if (p != genLimit) {
                boolean up = p > genLimit;
                genLimit = p;
                if (up) ready.signalAll();   // 상한에 막혀 기다리던 일꾼을 깨운다
                if (ChunkTrace.ON) ChunkTrace.value("gen.limit", p);
                long now = System.nanoTime();
                if (now - lastLimitLog >= LIMIT_LOG_NANOS) {   // 오르내림이 잦아도 10초에 한 줄
                    lastLimitLog = now;
                    LOG.info(Lang.t("gen.throttle-changed", up ? p - 1 : p + 1, p, String.format("%.1f", worstTps)));
                }
            }
            return p;
        } finally {
            lock.unlock();
        }
    }

    /** 잠금 안: 조절기 상한에 막혀 생성 요청을 지금 시작하면 안 되나. 닫는 중에는 막지 않는다(남은 고정 요청을 끝내야 한다). */
    private boolean genBlockedLocked() {
        return tickGuard && !shutdown && genRunning >= genLimit;
    }

    private static int initialCap(Settings s) {
        return s.fixedPlayerCap() > 0 ? s.fixedPlayerCap() : Math.clamp(s.budget(), 1, s.maxPlayerCap());
    }

    public Settings settings() { return settings; }

    /** 생성 스레드 수. */
    public int threads() { return threads; }

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

    /**
     * 잠금 안: 차례를 끝내 줄 뒤로 보낸다. 그 사람이 걸어 두고 아직 시작 안 한 요청은 취소한다 — 다음 차례(수십 초 뒤)에는 이미 지나친
     * 땅이라 그때 만들면 차례를 헛쓴다. 자리가 돌아오면 그 사람은 칸 하나로 지금 가장 가까운 청크를 다시 건다.
     */
    private void endTurnLocked(int owner, long now) {
        if (turnWait.isEmpty() && turnActive.containsKey(owner)) {   // 기다리는 사람이 없으면 차례를 새로 줄 뿐 — 시야 안 요청을 취소하지 않는다(검토 R3)
            turnActive.put(owner, new Turn(now));
            return;
        }
        if (turnActive.remove(owner) != null) {
            turnWait.addLast(owner);
            for (int i = gen.size() - 1; i >= 0; i--) {
                Task t = gen.get(i);
                if (t.ticket != null && t.ticket.owner == owner && t.ticket.keepAlive == null && t.ticket.tryCancel()) {   // 시간 티켓 로드는 owner −1 이라 지금은 늘 참(검토 R11)
                    removeAt(i);
                    turnCancelled.add(t);
                }
            }
            fillTurnsLocked(now);
        }
    }

    /** 시간 티켓이 살아 있나(던지면 끝난 것으로 — 대기열 일꾼이 죽지 않게). */
    private static boolean alive(Ticket t) {
        try {
            return t.keepAlive.getAsBoolean();
        } catch (RuntimeException e) {
            LOG.warn(Lang.t("gen.time-ticket-failed"), e);
            return false;
        }
    }

    /** 취소해 대기열에서 뺀 요청(차례 끝·시야 밖, 잠금 밖에서 정리한다). 예외가 나도 잃지 않게 필드에 모은다. */
    private final ArrayList<Task> turnCancelled = new ArrayList<>();

    /** 잠금 안: 차례 취소분을 꺼낸다. */
    private List<Task> drainTurnCancelledLocked(List<Task> into) {
        if (turnCancelled.isEmpty()) return into;
        if (into == null) into = new ArrayList<>();
        into.addAll(turnCancelled);
        turnCancelled.clear();
        return into;
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
        return r -> submit(r, w, cx, cz, ticket, ticket != null && ticket.isBackground());
    }

    /** 맨 뒤 작업(고정 — 취소·내리기 없음). 플레이어 요청이 하나라도 있으면 그 뒤. 미리 생성은 Ticket.forBackground() 로 tagged 를 쓴다. */
    public Executor background(World w, int cx, int cz) {
        return r -> submit(r, w, cx, cz, null, true);
    }

    /** 잠금 안: 지금 맨 뒤 작업인가(미리 생성 요청에 다른 요청자가 붙어 내려왔으면 아니다). */
    private static boolean bg(Task t) {
        return t.background && (t.ticket == null || t.ticket.isBackground());
    }

    @Override
    public void execute(Runnable command) {
        lock.lock();
        try {
            if (shutdown) throw new RejectedExecutionException("생성 대기열이 닫혔다");
            plain.add(new Task(command, null, 0, 0, null, false, seq++, System.nanoTime()));
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    private void submit(Runnable r, World w, int cx, int cz, Ticket ticket, boolean background) {
        long now = System.nanoTime();
        int prio = background ? PRIO_BACKGROUND : settings.distanceOrder() ? nearestNow(w, cx, cz) : 0;
        lock.lock();
        try {
            if (shutdown) throw new RejectedExecutionException("생성 대기열이 닫혔다");
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
                // Region.trackingDistance 와 같은 값 + 바깥 한 겹(ChunkTrackingView.contains 는 축 방향으로 시야+1 칸까지 넣는다).
                // 바닐라 플레이어 티켓은 서버 시야 정사각(Region.collectCornerLoads 의 모서리) — 클라 시야가 작아도 그만큼은 남긴다(검토 R1)
                int track = Math.clamp(ps[i].viewDistance(), 2, Math.max(2, serverVd));
                keep[i] = Math.max(track, Math.clamp(serverVd, 2, 32)) + 1 + margin;
            }
        }

        boolean keeps(int cx, int cz) {
            for (int k = 0; k < x.length; k++) if (Math.max(Math.abs(x[k] - cx), Math.abs(z[k] - cz)) <= keep[k]) return true;
            return false;
        }
    }

    /** 잠금 안: 거리를 다시 재고, 취소할 것은 대기열에서 빼 turnCancelled 에 모은다. 생성 칸 계산과 지표도 여기서. */
    private void reprioLocked(long now) {
        if (now - lastReprio < REPRIO_NANOS) return;
        lastReprio = now;
        if (!settings.distanceOrder()) {   // 넣은 순(예전): 취소·칸 없이 비교용 지표만
            if (ChunkTrace.ON && now - lastMetric >= METRIC_NANOS) {
                lastMetric = now;
                IdentityHashMap<World, Snap> snaps = new IdentityHashMap<>();
                int stale = 0;
                for (Task t : gen) {
                    if (bg(t) || t.w == null) continue;
                    Snap s = snaps.computeIfAbsent(t.w, w -> new Snap(w.playerIndex().toArray(), w.viewDistance(), settings.cancelMargin()));
                    if (!s.keeps(t.cx, t.cz)) stale++;
                }
                ChunkTrace.value("batch.gqlen", gen.size());
                ChunkTrace.value("batch.gqstale", stale);
            }
            return;
        }
        IdentityHashMap<World, Snap> snaps = new IdentityHashMap<>();
        int stale = 0;
        for (int i = gen.size() - 1; i >= 0; i--) {
            Task t = gen.get(i);
            if (bg(t) || t.w == null) continue;
            Snap s = snaps.computeIfAbsent(t.w, w -> new Snap(w.playerIndex().toArray(), w.viewDistance(), settings.cancelMargin()));
            int best = PRIO_NO_PLAYERS;
            boolean kept = false;
            for (int k = 0; k < s.x.length; k++) {
                int d = Math.max(Math.abs(s.x[k] - t.cx), Math.abs(s.z[k] - t.cz));
                if (d < best) best = d;
                if (d <= s.keep[k]) kept = true;
            }
            t.prio = best;
            if (!kept && t.ticket != null && t.ticket.keepAlive != null && t.ticket.cancellable()) kept = alive(t.ticket);
            if (kept) { t.staleSince = 0; continue; }
            if (t.staleSince == 0) t.staleSince = now;
            if (now - t.staleSince >= settings.cancelDelayNanos() && t.ticket != null && t.ticket.tryCancel()) {
                removeAt(i);
                turnCancelled.add(t);
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
        return;
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
        if (genBlockedLocked()) return null;
        int bi = -1;
        boolean turns = settings.turns();
        long bestKey = Long.MAX_VALUE, bestSeq = Long.MAX_VALUE;
        for (int i = 0, n = gen.size(); i < n; i++) {
            Task t = gen.get(i);
            long key;
            boolean back = bg(t);
            if (t.ticket != null && t.ticket.cancelled()) key = Long.MIN_VALUE;   // 취소된 요청(멈춘 미리 생성 등)은 만들지 않고 바로 끝낸다 — 붙은 요청자가 맨 뒤까지 기다리지 않게
            else if (!settings.distanceOrder()) key = 0;
            else if (!back && (t.ticket == null || !t.ticket.cancellable()) && now - t.t0 >= settings.pinnedMaxWaitNanos()) key = -1;
            else if (t.ticket != null && t.ticket.keepAlive != null && t.ticket.cancellable() && alive(t.ticket)) key = 0;   // 살아 있는 시간 티켓(바닐라 티켓 레벨이 플레이어 것보다 높다)
            else if (turns && !back && !onTurn(t)) key = (1L << 32) + t.prio;   // 줄 선 사람 요청은 차례인 요청 뒤
            else key = t.prio;
            if (back && settings.distanceOrder() && key != Long.MIN_VALUE) key = (2L << 32) + key;   // 미리 생성은 줄 선 요청보다도 뒤
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
        if (tickGuard) {
            genRunning++;
            t.counted = true;
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
                    while (!shutdown && plain.isEmpty() && (gen.isEmpty() || genBlockedLocked())) {
                        if (gen.isEmpty()) {
                            ready.awaitUninterruptibly();
                            continue;
                        }
                        // 조절기 상한에 막혀 기다리는 동안에도 거리 다시 재기·시야 밖 취소·차례 넘기기는 REPRIO 간격으로 돈다
                        // (도는 생성이 끝나야 다시 재면 상한 1 일 때 플레이어 자리가 생성 한 개 시간만큼 묶인다)
                        try { ready.awaitNanos(REPRIO_NANOS); } catch (InterruptedException ignored) { }   // 다른 대기처럼 끼어들기는 무시(다시 세우면 바쁜 대기)
                        if (!shutdown && plain.isEmpty() && !gen.isEmpty() && genBlockedLocked()) {
                            try { reprioLocked(System.nanoTime()); } catch (RuntimeException e) { LOG.warn(Lang.t("gen.queue-cleanup-failed"), e); }
                            if (!turnCancelled.isEmpty()) break;   // 취소분은 아래에서 잠금 밖으로 정리한다
                        }
                    }
                    if (shutdown && plain.isEmpty() && gen.isEmpty()) return;
                    long now = System.nanoTime();
                    t = null;
                    try {
                        reprioLocked(now);
                        t = pickLocked(now);
                    } catch (RuntimeException e) {   // 일꾼이 죽으면 대기열이 영영 멈춘다 — 이번 차례만 버리고 계속
                        LOG.warn(Lang.t("gen.queue-cleanup-failed"), e);
                    }
                    cancelled = drainTurnCancelledLocked(null);
                } finally {
                    lock.unlock();
                }
                if (cancelled != null) for (Task c : cancelled) runCancelled(c);
                if (t == null) continue;
                try {
                    run(t);
                } finally {
                    if (t.counted) {
                        lock.lock();
                        try {
                            genRunning--;
                            ready.signal();   // 상한에 막혀 기다리던 일꾼 하나가 이어 받는다
                        } finally {
                            lock.unlock();
                        }
                    }
                }
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
            LOG.warn(Lang.t("gen.cancelled-cleanup-failed", c.cx, c.cz), e);
        }
    }

    private static void run(Task t) {
        boolean genTask = t.w != null || t.background;
        long t1 = ChunkTrace.ON ? System.nanoTime() : 0L;
        if (ChunkTrace.ON && genTask) ChunkTrace.nanos("gen.queue", t1 - t.t0);
        try {
            t.r.run();
        } catch (Throwable e) {
            LOG.warn(Lang.t("gen.task-failed", t.cx, t.cz), e);
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
            reprioLocked(System.nanoTime());
            cancelled = drainTurnCancelledLocked(null);
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
            if (guard != null) guard.interrupt();   // 조절기 스레드가 잠든 채 최대 250ms 남지 않게
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
