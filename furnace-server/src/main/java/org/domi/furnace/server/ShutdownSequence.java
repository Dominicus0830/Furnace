// 서버 종료 순서 계약 — 단계는 Step 의 선언 순서로만 돈다. 한 단계가 실패해도 다음 단계는 돈다(저장·닫기가 빠지지 않게).
// 핵심: region(스케줄러) 정지는 모든 플러그인의 onDisable 이 끝난 뒤다(Folia #353 — region 틱이 먼저 꺼져 플러그인이 월드를 정리하지
// 못하는 문제). 플러그인 끄기 동안에는 접속자·네트워크·region·스케줄러가 모두 살아 있다. 문서: docs/api/lifecycle.md.
package org.domi.furnace.server;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShutdownSequence {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 종료 단계(선언 순서 = 실행 순서). 순서를 바꾸면 플러그인 계약이 바뀐다 — docs/api/lifecycle.md 도 같이 고칠 것. */
    public enum Step {
        /** 종료 중 표시 + ServerShutdownEvent. 모든 것이 아직 살아 있다. */
        SHUTDOWN_EVENT,
        /** 플러그인 onDisable(켠 순서의 반대). 접속자·region·스케줄러 살아 있음. 끝나면 플러그인 작업은 취소된다. */
        DISABLE_PLUGINS,
        /** RCON·Query·워치독. */
        CLOSE_REMOTE_SERVICES,
        /** 새 접속을 막고 접속자를 내보낸다(퇴장 저장은 region 이 한다). */
        STOP_NETWORK,
        /** region 스레드에서 dirty 청크·접속 플레이어 저장, 플레이어 데이터 실행기 비우기. */
        FLUSH_WORLDS,
        /** 청크 IO 풀·리전 파일·level.dat·저장소 닫기. */
        CLOSE_STORAGE,
        /** 청크 작업 풀·스케줄러(region 스레드) 정지. */
        STOP_SCHEDULER,
        /** 플러그인 클래스로더·비동기 실행기 닫기. */
        CLOSE_PLUGINS
    }

    private final Map<Step, Runnable> steps = new EnumMap<>(Step.class);
    private final AtomicBoolean ran = new AtomicBoolean();
    private final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
    /** 지금 단계를 도는 스레드(아직 안 돌았으면 null). */
    private volatile Thread runner;

    /** 단계에 할 일을 정한다(같은 단계에 다시 부르면 이어 붙인다). */
    public synchronized ShutdownSequence on(Step step, Runnable action) {
        Runnable prev = steps.get(step);
        steps.put(step, prev == null ? action : () -> {
            prev.run();
            action.run();
        });
        return this;
    }

    /**
     * 단계를 순서대로 한 번만 돈다. 다른 스레드가 이미 돌고 있으면 그것이 끝날 때까지 기다린다 — /stop 이 도는 중에 JVM 종료(Ctrl+C·kill)가
     * 와도 종료 훅이 먼저 끝나 JVM 이 저장 도중에 멈추지 않게. 단, 도는 스레드가 단계 안에서 System.exit 를 불러(종료 훅을 기다리며) 멈췄으면
     * 기다리지 않는다(서로 기다리는 교착).
     */
    public void run() {
        if (!ran.compareAndSet(false, true)) {
            awaitOther();
            return;
        }
        runner = Thread.currentThread();
        try {
            runSteps();
        } finally {
            done.countDown();
        }
    }

    /**
     * /stop: 단계를 다 돈 뒤 JVM 을 끝낸다(seq 가 아직 없으면 바로 끝낸다 — 그때는 종료 훅도 할 일이 없다). Paper 도 /stop 은 서버 스레드가
     * stopServer(플러그인 끄기·저장)를 마친 뒤 JVM 이 끝나고, JVM 종료 훅(ServerShutdownThread)은 신호로 끝날 때만 그 일을 한다.
     */
    public static void runThenExit(ShutdownSequence seq, java.util.function.IntConsumer exit) {
        try {
            if (seq != null) seq.run();
        } finally {
            exit.accept(0);
        }
    }

    /** 단계가 모두 끝났는가. */
    public boolean finished() { return done.getCount() == 0; }

    private void awaitOther() {
        if (Thread.currentThread() == runner) return;   // 단계 안에서 다시 부름
        try {
            while (!done.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Thread r = runner;
                if (r != null && inSystemExit(r)) {
                    log.warn("[STOP] 종료 단계 안에서 System.exit 가 불렸다 — 남은 단계를 기다리지 않는다");
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** t 가 Runtime.exit/halt 안에서 종료 훅을 기다리는 중인가. */
    static boolean inSystemExit(Thread t) {
        for (StackTraceElement e : t.getStackTrace()) {
            if ("java.lang.Shutdown".equals(e.getClassName())) return true;
        }
        return false;
    }

    private void runSteps() {
        Map<Step, Runnable> snapshot;
        synchronized (this) {
            snapshot = new EnumMap<>(steps);
        }
        for (Step s : Step.values()) {
            Runnable r = snapshot.get(s);
            if (r == null) continue;
            try {
                r.run();
            } catch (Throwable t) {
                log.error("[STOP] {} 단계 실패", s, t);
            }
        }
    }
}
