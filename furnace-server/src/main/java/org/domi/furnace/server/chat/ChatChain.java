// 연결별 채팅 순서 사슬(T-035, Paper 의 chatMessageChain 을 채팅 실행기에 둔 것과 같은 꼴).
// 한 연결의 chat·chat_command·chat_command_signed·chat_session_update 를 도착 순서대로 한 번에 하나씩 처리한다.
// - 채팅 단계(append)는 채팅 실행기 스레드에서 돈다: AsyncChatEvent 처리기가 오래 막혀도 전역 틱(시간·명령·RCON·#tick)이 멈추지 않고,
//   처리기 안의 callSyncMethod(...).get() 같은 "전역에 맡기고 기다리기"가 교착하지 않는다(Paper 는 채팅 실행기 스레드라 다음 틱에 풀린다).
// - 전역 단계(appendGlobal)는 전역 스레드에 넘기고, 그 람다가 돌아온 뒤(future 완료 콜백)에 다음 단계를 잇는다 — 실행기 스레드를
//   붙잡고 기다리지 않는다. 명령이 region 에 넘긴 꼬리(CommandFuture)까지는 기다리지 않는다(전역 큐의 지금 순서 보장과 같다).
// - 단계 사이에는 happens-before 가 있다(사슬 잠금·실행기 넘김·future 완료). 그래서 서명 디코더처럼 동기화 없는 상태를 채팅 단계와
//   전역 단계가 번갈아 써도 된다.
package org.domi.furnace.server.chat;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatChain {

    private static final Logger log = LoggerFactory.getLogger(ChatChain.class);

    /** 한 연결에 쌓일 수 있는 처리 안 된 단계 수. 바닐라 스팸 감지(메시지 10개 남짓)보다 훨씬 크다 — 넘치면 append 가 false. */
    public static final int MAX_PENDING = 256;

    private static final AtomicInteger THREAD_NO = new AtomicInteger();

    /**
     * 채팅 실행기: Paper 의 chatExecutor 처럼 필요할 때 늘고 쉬면 줄어드는 플랫폼 스레드 풀(데몬, "Async Chat Thread - #n").
     * 가상 스레드를 쓰지 않는다 — 처리기가 synchronized 안에서 막히면 운반 스레드를 붙잡는다(결정 D1, T-034 와 같은 이유).
     * 서버마다 따로 두지 않는다: 사슬이 하나씩만 돌리므로 스레드 수는 "동시에 처리 중인 연결 수"를 넘지 않는다.
     */
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "Async Chat Thread - #" + THREAD_NO.getAndIncrement());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((th, e) -> log.error("채팅 실행기 예외", e));
                return t;
            });

    /** 단계 하나. 다음 단계까지 기다릴 future 를 돌려주거나(전역에 넘긴 일), 바로 끝났으면 null. */
    @FunctionalInterface
    private interface Step {
        CompletableFuture<?> run();
    }

    private final Executor executor;
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    /** 지금 단계를 돌리는 중이거나 돌리기로 예약됐다(큐를 비울 주인이 있다). this 잠금 아래에서만. */
    private boolean running;

    public ChatChain() { this(EXECUTOR); }

    /** 시험용: 실행기를 바꿔 끼운다. */
    ChatChain(Executor executor) { this.executor = executor; }

    /** 채팅 단계: 채팅 실행기 스레드에서 돈다. 큐가 넘쳤으면 넣지 않고 false. */
    public boolean append(Runnable task) {
        return enqueue(() -> {
            task.run();
            return null;
        });
    }

    /**
     * 채팅 실행기 스레드에서 돌고, 돌려준 future(보통 전역에 넘긴 일)가 끝난 뒤에 다음 단계를 잇는다. null 이면 바로 잇는다.
     * 채팅 입력 가로채기처럼 "앞부분은 사슬에서, 뒷부분은 전역에서" 인 경우에 쓴다.
     */
    public boolean appendThen(java.util.function.Supplier<CompletableFuture<?>> task) {
        return enqueue(task::get);
    }

    /** 전역 단계: 전역 스레드에서 돌리고, 그 람다가 돌아온 뒤 다음 단계를 잇는다. */
    public boolean appendGlobal(GlobalScheduler global, Runnable task) {
        return enqueue(() -> {
            try {
                return global.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException e) {   // 서버가 닫히는 중 — 전역에 못 넘긴 단계는 버린다
                return null;
            }
        });
    }

    /** 지금까지 넣은 단계가 모두 끝나면 완료되는 future(시험 장벽용). 큐가 넘쳐도 넣는다. */
    public CompletableFuture<Void> barrier() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Step s = () -> {
            done.complete(null);
            return null;
        };
        boolean start;
        synchronized (this) {
            queue.add(s);
            start = !running;
            running = true;
        }
        if (start) schedule();
        return done;
    }

    /** 처리 안 된 단계 수(지금 도는 단계는 빼고). */
    public synchronized int pending() { return queue.size(); }

    private boolean enqueue(Step s) {
        boolean start;
        synchronized (this) {
            if (queue.size() >= MAX_PENDING) return false;
            queue.add(s);
            start = !running;
            running = true;
        }
        if (start) schedule();
        return true;
    }

    private void schedule() {
        try {
            executor.execute(this::drain);
        } catch (java.util.concurrent.RejectedExecutionException e) {   // 시험용 실행기가 닫혔다 — 그 자리에서 돈다
            drain();
        }
    }

    /** 큐를 비운다. 단계가 future 를 돌려주면 그 완료 콜백에서 실행기로 다시 들어와 잇는다(스레드를 붙잡고 기다리지 않는다). */
    private void drain() {
        while (true) {
            Step s;
            synchronized (this) {
                s = queue.poll();
                if (s == null) {
                    running = false;
                    return;
                }
            }
            CompletableFuture<?> wait;
            try {
                wait = s.run();
            } catch (Throwable t) {
                log.error("채팅 처리 실패", t);
                wait = null;
            }
            if (wait == null) continue;
            if (!wait.isDone()) {
                wait.whenComplete((v, t) -> {
                    logFailure(t);
                    schedule();
                });
                return;
            }
            wait.handle((v, t) -> {
                logFailure(t);
                return null;
            });
        }
    }

    private static void logFailure(Throwable t) {
        if (t != null) log.error("채팅 처리 실패(전역)", unwrap(t));
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof java.util.concurrent.CompletionException && t.getCause() != null ? t.getCause() : t;
    }
}
