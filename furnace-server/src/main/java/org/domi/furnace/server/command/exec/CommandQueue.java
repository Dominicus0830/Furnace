// Furnace 전용 최상위 명령 큐(전역 스레드). 한 번에 하나의 ExecutionContext 만 돌린다 — 앞 컨텍스트가 region 결과를 기다리며 멈춰 있으면
// 뒤 명령(채팅·콘솔·tick 함수 등)은 FIFO 로 기다린다(바닐라와 같은 직렬화; 대가는 head-of-line 지연 — 지표·경고로 관측한다).
// 같은 컨텍스트 안에서 재진입(실행 중인 명령이 또 명령을 돌림)하면 바닐라 executeCommandInContext 처럼 그 컨텍스트에 합친다.
package org.domi.furnace.server.command.exec;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandQueue {

    private static final Logger log = LoggerFactory.getLogger(CommandQueue.class);
    /** 지연 결과 하나를 기다리는 벽시계 한도(설계 §2.2). */
    public static final Duration DEFAULT_DEFERRED_TIMEOUT = Duration.ofSeconds(5);
    /** 한 컨텍스트가 이 전역 틱 수를 넘기면 경고 1회(head-of-line 관측). */
    public static final int SLOW_CONTEXT_TICKS = 100;

    private record Pending(CommandSource source, String input, Consumer<ExecutionContext> init, CompletableFuture<Void> done) {}

    private final TaskScheduler global;
    private final ThreadOwner owner;
    private final IntSupplier chainLimit;
    private final IntSupplier forkLimit;
    private volatile Duration deferredTimeout;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private ExecutionContext current;
    private Pending currentEntry;
    private long currentTicks;
    private boolean slowWarned;
    private boolean pumping;
    /** 지금 drive(완료 처리 body + run) 중인 컨텍스트 — 재개 body 안에서 들어온 명령도 그 컨텍스트에 합친다(S0 검토 S-3). */
    private ExecutionContext driving;
    private volatile boolean closed;
    /** 지금 컨텍스트가 region 즉시 처리 문(CommandDrainGate)을 열어 두었는가 — 컨텍스트가 도는 동안만 열린다. */
    private boolean gateOpen;

    public CommandQueue(TaskScheduler global, ThreadOwner owner, IntSupplier chainLimit, IntSupplier forkLimit, Duration deferredTimeout) {
        this.global = global;
        this.owner = owner;
        this.chainLimit = chainLimit;
        this.forkLimit = forkLimit;
        this.deferredTimeout = deferredTimeout;
    }

    private void checkThread() {
        assert owner.isOwnerThread() : "CommandQueue 는 전역 스레드 전용: " + Thread.currentThread().getName();
    }

    public ThreadOwner owner() { return owner; }

    /**
     * 최상위 실행을 넣는다(전역 스레드). init 은 새(또는 재진입이면 지금) 컨텍스트에 첫 항목을 넣는다.
     * 돌려주는 future 는 그 컨텍스트가 끝날 때 완료된다(종료로 버려져도 완료).
     */
    public CompletableFuture<Void> enqueue(CommandSource source, String input, Consumer<ExecutionContext> init) {
        checkThread();
        if (closed) return CompletableFuture.completedFuture(null);
        if (current != null && (current.isRunning() || driving == current)) {
            init.accept(current);   // 같은 컨텍스트 안의 재진입(재개 body 포함): 바닐라처럼 합친다
            return current.onFinished();
        }
        Pending p = new Pending(source, input, init, new CompletableFuture<>());
        pending.add(p);
        pump();
        return p.done();
    }

    private void pump() {
        if (pumping) return;
        pumping = true;
        try {
            while (current == null && !closed && !pending.isEmpty()) begin(pending.poll());
        } finally {
            pumping = false;
        }
    }

    private void begin(Pending p) {
        ExecutionContext ctx = new ExecutionContext(Math.max(1, chainLimit.getAsInt()), forkLimit.getAsInt(), owner, this::hop, deferredTimeout);
        ctx.setDriver(body -> drive(ctx, p, body));
        current = ctx;
        currentEntry = p;
        currentTicks = 0;
        slowWarned = false;
        openGate();
        drive(ctx, p, () -> p.init().accept(ctx));
    }

    /** 완료 처리(body) 뒤 큐를 이어 간다. 항목 실행 중 예외는 바닐라 performCommand 의 catch 처럼 command.failed 를 보내고 컨텍스트를 끝낸다. */
    private void drive(ExecutionContext ctx, Pending p, Runnable body) {
        boolean handled = false;
        ExecutionContext prevDriving = driving;
        driving = ctx;
        try {
            if (body != null) body.run();
            ctx.run();
            handled = true;
        } catch (Exception e) {
            handled = true;
            reportException(p, e);
            ctx.abort();
        } finally {
            driving = prevDriving;
            if (!handled) ctx.abort();   // Error: 컨텍스트를 끝내고 그대로 던진다(큐가 막히지 않게)
            if (ctx.isFinished() && current == ctx) {
                current = null;
                currentEntry = null;
                closeGate();
                p.done().complete(null);
                pump();
            }
        }
    }

    private void openGate() {
        if (gateOpen) return;
        gateOpen = true;
        org.domi.furnace.server.world.CommandDrainGate.enter();
    }

    private void closeGate() {
        if (!gateOpen) return;
        gateOpen = false;
        org.domi.furnace.server.world.CommandDrainGate.exit();
    }

    private static void reportException(Pending p, Exception e) {
        Text detail = Text.text(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        log.error("Command exception: /{}", p.input(), e);
        p.source().sendFailure(Text.translatable("command.failed").hoverText(detail));
    }

    /** 아무 스레드에서 와도 전역으로 넘긴다. 종료 뒤·실행기 거부는 조용히 버린다(설계 §5 I6). */
    private void hop(Runnable task) {
        if (closed) return;
        try {
            global.execute(() -> {
                if (closed) return;
                try {
                    task.run();
                } catch (Throwable t) {
                    log.error("명령 완료 처리 실패", t);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // 전역 스레드가 이미 닫혔다
        }
    }

    /** 전역 틱마다(정지 중에도) 부른다: 지금 컨텍스트의 경과 틱을 세고, 너무 길면 한 번 경고한다. */
    public void tick() {
        if (!owner.isOwnerThread()) return;   // 지표 전용: 테스트가 틱을 다른 스레드에서 직접 돌릴 때는 세지 않는다
        if (current == null) return;
        currentTicks++;
        if (currentTicks > SLOW_CONTEXT_TICKS && !slowWarned) {
            slowWarned = true;
            log.warn("명령 컨텍스트 /{} 이 {} 틱째 실행 중, 대기 {}", currentEntry == null ? "?" : currentEntry.input(), currentTicks, pending.size());
        }
    }

    /** 테스트용: 이후 시작하는 컨텍스트의 지연 결과 타임아웃을 바꾼다. */
    public void deferredTimeout(Duration timeout) { this.deferredTimeout = timeout; }

    /** 지표: 기다리는 최상위 실행 수. */
    public int pendingCount() { return pending.size(); }

    /** 지표: 지금 컨텍스트의 경과 전역 틱(없으면 -1). */
    public long currentContextTicks() { return current == null ? -1 : currentTicks; }

    public boolean isBusy() { return current != null; }

    public boolean isClosed() { return closed; }

    /**
     * 종료: 기다리는 실행과 지금 컨텍스트를 버린다. GlobalThread.close() 보다 먼저 불려야 한다(스케줄러 close 훅).
     * 전역 스레드가 아니면 전역에서 정리하도록 넘기고 잠깐 기다린다(못 하면 closed 표시만으로 이후 홉·enqueue 를 막는다).
     */
    public void close() {
        if (closed) return;
        if (owner.isOwnerThread()) {
            closeOnOwner();
            return;
        }
        try {
            global.execute(this::closeOnOwner).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 전역에서 정리하지 못했다: closed 로 이후 홉·enqueue 를 막고, 기다리는 쪽이 매달리지 않게 future 만 완료한다(자료구조는 건드리지 않음)
            closed = true;
            try {
                if (currentEntry != null) currentEntry.done().complete(null);
                for (Pending p : pending.toArray(new Pending[0])) p.done().complete(null);
            } catch (RuntimeException ignored) {
                // 전역 스레드와 경쟁 — 최선만 다한다
            }
        }
    }

    private void closeOnOwner() {
        if (closed) return;
        closed = true;
        int n = pending.size() + (current != null ? 1 : 0);
        if (current != null) current.abort();
        if (currentEntry != null) currentEntry.done().complete(null);
        for (Pending p : pending) p.done().complete(null);
        pending.clear();
        current = null;
        currentEntry = null;
        closeGate();
        if (n > 0) log.warn("종료: 미완료 명령 컨텍스트 {}개 버림", n);
    }
}
