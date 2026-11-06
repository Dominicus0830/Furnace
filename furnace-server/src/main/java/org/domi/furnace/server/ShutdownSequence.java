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

    /** 단계에 할 일을 정한다(같은 단계에 다시 부르면 이어 붙인다). */
    public synchronized ShutdownSequence on(Step step, Runnable action) {
        Runnable prev = steps.get(step);
        steps.put(step, prev == null ? action : () -> {
            prev.run();
            action.run();
        });
        return this;
    }

    /** 단계를 순서대로 한 번만 돈다. 두 번째 호출은 아무것도 안 한다. */
    public void run() {
        if (!ran.compareAndSet(false, true)) return;
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
