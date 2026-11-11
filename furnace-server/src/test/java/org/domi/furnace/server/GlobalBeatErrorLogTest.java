// A03-05 회귀(2차 점검 재현 시험): 전역 박동(FurnaceMain.FixedRateBeat → ServerCore.scheduledTick)이 던진 예외는 GlobalThread.execute 가 돌려준 future 에만 담기고
// 박동은 그 future 를 버려 예외가 로그 없이 사라졌다. 이제는 "[TICK] 전역 틱 실패" 로 남기고(같은 곳은 10초에 한 번 묶어서) 박동을 계속 돈다.
// 바닐라·Paper 는 tickServer 의 예외를 크래시 리포트로 남기고 서버를 멈춘다. Furnace region 틱은 "[TICK] region 실패" 로 로그를 남긴다.
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GlobalBeatErrorLogTest {

    @Test
    void exceptionInGlobalBeatIsLoggedAndThrottled() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        root.addAppender(app);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            AtomicInteger calls = new AtomicInteger(), after = new AtomicInteger();
            new FurnaceMain.FixedRateBeat(sched, () -> {
                calls.incrementAndGet();
                if (true) throw new IllegalStateException("boom-in-global-tick");
                after.incrementAndGet();   // 시간·날씨 진행 자리
            }, 50_000_000L).start();
            Thread.sleep(600);
            long errors = app.list.stream().filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN)
                    && (String.valueOf(e.getFormattedMessage()).contains("boom") || (e.getThrowableProxy() != null && e.getThrowableProxy().getMessage().contains("boom")))).count();
            String summary = "A03-BEAT 박동 " + calls.get() + "번 모두 예외, 남은 단계 " + after.get() + "번, 경고 이상 로그 " + errors + "건";
            System.out.println(summary);
            assertTrue(calls.get() >= 5, "박동은 계속 돈다: " + summary);
            assertTrue(errors > 0, "전역 틱 예외가 로그에 남지 않는다: " + summary);
            assertTrue(errors < calls.get(), "같은 예외는 묶어서 남긴다(박동마다 찍지 않는다): " + summary);
        } finally {
            root.detachAppender(app);
            sched.close();
        }
    }
}
