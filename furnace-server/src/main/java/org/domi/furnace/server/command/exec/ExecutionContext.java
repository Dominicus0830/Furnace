// 바닐라 ExecutionContext(hs) — 한 최상위 명령(또는 함수)의 실행 큐. 덱 + 새 항목 버퍼(역순으로 덱 앞에) + 체인 예산 + 포크 한도 + 큐 상한.
// Furnace 차이: region 결과를 기다려야 하면 큐를 멈추고(suspend) future 완료 시 전역 스레드에서 이어 간다(resume). 블로킹 대기는 없다.
// 전역 스레드 전용 — 공개 메서드는 소유 스레드를 assert 한다.
package org.domi.furnace.server.command.exec;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExecutionContext {

    private static final Logger log = LoggerFactory.getLogger(ExecutionContext.class);
    public static final int MAX_QUEUE_DEPTH = 10_000_000;

    private final int commandLimit;
    private final int forkLimit;
    private final ThreadOwner owner;
    private final Consumer<Runnable> globalHop;
    private final Duration deferredTimeout;
    private final int maxQueueDepth;
    private int commandQuota;
    private boolean queueOverflow;
    private final Deque<CommandQueueEntry> commandQueue = new ArrayDeque<>();
    private final List<CommandQueueEntry> newTopCommands = new ArrayList<>();
    private int currentFrameDepth;

    // ---- Furnace: 지연 결과·멈춤/재개
    private CompletableFuture<Integer> pendingDefer;
    private boolean running;
    private boolean suspended;
    private boolean finished;
    private long generation;
    /** 재개 때 완료 처리(body)를 돌리고 큐를 이어 가는 쪽. 기본은 그 자리에서 body 후 run(); CommandQueue 가 바꾼다(예외 처리·다음 컨텍스트). */
    private Consumer<Runnable> driver = body -> {
        body.run();
        run();
    };
    private final CompletableFuture<Void> done = new CompletableFuture<>();

    /**
     * @param globalHop 아무 스레드에서 불려도 작업을 전역 스레드로 넘기는 함수(완료 홉). 거부되면 조용히 버려야 한다.
     * @param deferredTimeout 지연 결과 하나를 기다리는 벽시계 한도(설계 §2.2: 5초)
     */
    public ExecutionContext(int commandLimit, int forkLimit, ThreadOwner owner, Consumer<Runnable> globalHop, Duration deferredTimeout) {
        this(commandLimit, forkLimit, owner, globalHop, deferredTimeout, MAX_QUEUE_DEPTH);
    }

    /** 테스트용: 큐 상한을 바꿀 수 있는 생성자(바닐라 상한은 MAX_QUEUE_DEPTH). */
    public ExecutionContext(int commandLimit, int forkLimit, ThreadOwner owner, Consumer<Runnable> globalHop, Duration deferredTimeout,
                            int maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
        this.commandLimit = commandLimit;
        this.forkLimit = forkLimit;
        this.owner = owner;
        this.globalHop = globalHop;
        this.deferredTimeout = deferredTimeout;
        this.commandQuota = commandLimit;
    }

    private void checkThread() {
        assert owner.isOwnerThread() : "ExecutionContext 는 전역 스레드 전용: " + Thread.currentThread().getName();
    }

    private static Frame createTopFrame(ExecutionContext context, ResultCallback frameResult) {
        if (context.currentFrameDepth == 0) return new Frame(0, frameResult, context.commandQueue::clear);
        int reentrantFrameDepth = context.currentFrameDepth + 1;
        return new Frame(reentrantFrameDepth, frameResult, context.frameControlForDepth(reentrantFrameDepth));
    }

    /** 바닐라 queueInitialCommandExecution. */
    public static void queueInitialCommandExecution(ExecutionContext context, String commandInput, ContextChain<CommandSource> command,
                                                    CommandSource source, ResultCallback frameResult) {
        context.queueNext(new CommandQueueEntry(createTopFrame(context, frameResult), new BuildContexts.TopLevel(commandInput, command, source)));
    }

    /** 테스트·사용자 정의 실행기용: 임의 항목을 최상위 프레임으로 넣는다. */
    public static void queueInitialAction(ExecutionContext context, EntryAction action, ResultCallback frameResult) {
        context.queueNext(new CommandQueueEntry(createTopFrame(context, frameResult), action));
    }

    private void handleQueueOverflow() {
        queueOverflow = true;
        newTopCommands.clear();
        commandQueue.clear();
    }

    public void queueNext(CommandQueueEntry entry) {
        checkThread();
        if (newTopCommands.size() + commandQueue.size() > maxQueueDepth) handleQueueOverflow();
        if (!queueOverflow) newTopCommands.add(entry);
    }

    public void discardAtDepthOrHigher(int depth) {
        checkThread();
        while (!commandQueue.isEmpty() && commandQueue.peek().frame().depth() >= depth) commandQueue.removeFirst();
    }

    public Frame.FrameControl frameControlForDepth(int depth) { return () -> discardAtDepthOrHigher(depth); }

    /** 바닐라 runCommandQueue. 멈추면(suspend) 그 자리에서 돌아오고, 큐가 비거나 한도·상한으로 끝나면 컨텍스트를 끝낸다. */
    public void run() {
        checkThread();
        if (suspended || finished) return;
        running = true;
        try {
            pushNewCommands();
            while (true) {
                if (commandQuota <= 0) {
                    log.info("Command execution stopped due to limit (executed {} commands)", commandLimit);
                    break;
                }
                CommandQueueEntry entry = commandQueue.pollFirst();
                if (entry == null) break;
                currentFrameDepth = entry.frame().depth();
                entry.execute(this);
                if (queueOverflow) {
                    log.error("Command execution stopped due to command queue overflow (max {})", maxQueueDepth);
                    break;
                }
                if (suspended) return;   // 이 항목이 넣은 것은 재개 때 덱 앞에 넣는다
                pushNewCommands();
            }
            currentFrameDepth = 0;
            finish();
        } finally {
            running = false;
        }
    }

    private void pushNewCommands() {
        for (int i = newTopCommands.size() - 1; i >= 0; i--) commandQueue.addFirst(newTopCommands.get(i));
        newTopCommands.clear();
    }

    public int forkLimit() { return forkLimit; }

    /** 바닐라 incrementCost: 체인 예산 1 감소(수정자 단계·함수 호출·명령 실행 세 곳에서만). */
    public void incrementCost() { commandQuota--; }

    public int remainingQuota() { return commandQuota; }

    public boolean isQueueOverflowed() { return queueOverflow; }

    // ---------------------------------------------------------------- 지연 결과

    /** 명령 본문(CommandSource.defer)이 결과 future 를 맡긴다. 한 실행에 한 번. */
    public void defer(CompletableFuture<Integer> future) {
        checkThread();
        if (!running) throw new IllegalStateException("명령 본문 밖에서 defer — 중첩 헬퍼 안쪽은 …Async 판을 써야 한다");
        if (pendingDefer != null) throw new IllegalStateException("source.defer 는 명령 실행 하나에 한 번만 부를 수 있다");
        pendingDefer = future;
    }

    /** ExecuteCommand 가 명령 본문 직후 꺼낸다(없으면 null). */
    public CompletableFuture<Integer> takeDeferred() {
        CompletableFuture<Integer> f = pendingDefer;
        pendingDefer = null;
        return f;
    }

    /**
     * 큐를 멈추고 f 의 완료(또는 타임아웃)를 기다린다. 완료는 어느 스레드에서 오든 전역으로 넘겨 onDone(값, 예외) 을 부른 뒤 큐를 이어 간다.
     * 세대 토큰: 멈춤마다 새 토큰을 발급하고, 토큰이 다르거나 이미 재개된 완료(타임아웃 뒤 늦은 완료 등)는 버린다.
     * 반환값은 이번 멈춤의 토큰.
     */
    public <T> long suspend(CompletableFuture<T> f, BiConsumer<T, Throwable> onDone) {
        checkThread();
        if (suspended) throw new IllegalStateException("이미 멈춘 컨텍스트");
        long token = ++generation;
        suspended = true;
        CompletableFuture<T> watched = f.copy();
        watched.orTimeout(deferredTimeout.toNanos(), TimeUnit.NANOSECONDS);
        watched.whenComplete((v, ex) -> globalHop.accept(() -> complete(token, () -> onDone.accept(v, ex))));
        return token;
    }

    /** 멈춘 컨텍스트의 완료 처리(전역 스레드). 토큰이 지금 멈춤의 것이 아니면 아무것도 하지 않는다. */
    public void complete(long token, Runnable body) {
        checkThread();
        if (finished || !suspended || token != generation) return;
        suspended = false;
        driver.accept(body);
    }

    public boolean isSuspended() { return suspended; }

    public boolean isRunning() { return running; }

    public boolean isFinished() { return finished; }

    public long generation() { return generation; }

    /** 컨텍스트 종료 future(정상 종료·한도 중단·폐기 모두 완료). */
    public CompletableFuture<Void> onFinished() { return done; }

    public void setDriver(Consumer<Runnable> driver) { this.driver = driver; }

    private void finish() {
        if (finished) return;
        finished = true;
        done.complete(null);
    }

    /** 남은 항목을 버리고 끝낸다(실행 중 예외·서버 종료). 이후의 완료 홉은 무시된다. */
    public void abort() {
        newTopCommands.clear();
        commandQueue.clear();
        pendingDefer = null;
        suspended = false;
        finish();
    }
}
