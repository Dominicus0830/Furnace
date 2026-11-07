// 콘솔 Ctrl+C(SIGINT) 확인 — confirm-interrupt=true 면 첫 입력은 안내만 하고, 제한 시간 안의 두 번째 입력에서 종료한다.
package org.domi.furnace.server;

import java.util.function.LongSupplier;
import org.domi.furnace.server.config.OperatorOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InterruptConfirm {

    private static final Logger log = LoggerFactory.getLogger(InterruptConfirm.class);

    private final long windowNanos;
    private final LongSupplier nanoClock;
    private long firstAt;
    private boolean armed;

    public InterruptConfirm(int seconds, LongSupplier nanoClock) {
        this.windowNanos = seconds * 1_000_000_000L;
        this.nanoClock = nanoClock;
    }

    /** 신호가 올 때마다 부른다. true 면 종료해야 한다. */
    public synchronized boolean onInterrupt() {
        long now = nanoClock.getAsLong();
        if (armed && now - firstAt <= windowNanos) {
            armed = false;
            return true;
        }
        armed = true;
        firstAt = now;
        return false;
    }

    /** 지금 켜져 있는 확인기(없으면 Ctrl+C 한 번에 바로 종료). */
    private static volatile InterruptConfirm active;
    private static volatile int activeSeconds;

    /** 콘솔 편집기(JLine)가 Ctrl+C 를 받았을 때 — 터미널이 SIGINT 를 만들지 않으므로 같은 규칙을 여기서 적용한다. */
    public static void onConsoleInterrupt() {
        handle(active, activeSeconds);
    }

    private static void handle(InterruptConfirm confirm, int seconds) {
        if (confirm == null) {
            log.info("Ctrl+C — 서버를 종료한다");
            Runtime.getRuntime().exit(130);
        } else if (confirm.onInterrupt()) {
            log.info("Ctrl+C 를 한 번 더 받았다 — 서버를 종료한다");
            Runtime.getRuntime().exit(130);
        } else {
            log.warn("Ctrl+C: {}초 안에 한 번 더 누르면 서버를 저장하고 종료한다", seconds);
        }
    }

    /** confirm-interrupt 가 켜져 있으면 SIGINT 를 가로챈다. SIGTERM(kill)·/stop 은 그대로 바로 종료한다. 신호 API 가 없는 JVM 이면 경고만 하고 넘어간다. */
    public static void installIfEnabled(OperatorOptions o) {
        if (!o.confirmInterrupt()) return;
        InterruptConfirm confirm = new InterruptConfirm(o.interruptConfirmSeconds(), System::nanoTime);
        active = confirm;
        activeSeconds = o.interruptConfirmSeconds();
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> handle(confirm, o.interruptConfirmSeconds()));
        } catch (Throwable t) {
            log.warn("confirm-interrupt: SIGINT 를 가로챌 수 없어 켜지 않는다: {}", t.toString());
        }
    }
}
