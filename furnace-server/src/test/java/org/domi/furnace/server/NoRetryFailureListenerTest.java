// @NoRetry 시험의 실패만 보고 파일에 남는지(= CI 가 재시도하지 않는지), 그리고 시험 기본 제한시간 설정이 살아 있는지 본다.
package org.domi.furnace.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class NoRetryFailureListenerTest {

    /** 평소에는 꺼져 있고(속성 없음) 아래 시험이 속성을 켜고 런처로 직접 돌릴 때만 실행된다. */
    @EnabledIfSystemProperty(named = "furnace.noRetryFixture", matches = "true")
    static class Fixture {
        @NoRetry @Test void noRetryFails() { throw new AssertionError("의도된 실패(재시도 금지 표시)"); }
        @NoRetry @Test void noRetryPasses() { }
        @Test void plainFails() { throw new AssertionError("의도된 실패(표시 없음)"); }
    }

    @Test
    void onlyNoRetryFailuresAreReported(@TempDir Path dir) throws Exception {
        Path report = dir.resolve("no-retry-failures.txt");
        String oldReport = System.getProperty(NoRetryFailureListener.REPORT_PROPERTY);
        System.setProperty(NoRetryFailureListener.REPORT_PROPERTY, report.toString());
        System.setProperty("furnace.noRetryFixture", "true");
        try {
            Launcher launcher = LauncherFactory.create();
            launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(Fixture.class)).build());
        } finally {
            System.clearProperty("furnace.noRetryFixture");
            if (oldReport == null) System.clearProperty(NoRetryFailureListener.REPORT_PROPERTY);
            else System.setProperty(NoRetryFailureListener.REPORT_PROPERTY, oldReport);
        }
        List<String> lines = Files.readAllLines(report);
        assertEquals(1, lines.size(), "재시도 금지 표시가 붙은 실패 하나만 남아야 한다: " + lines);
        assertTrue(lines.get(0).contains("noRetryFails"), lines.get(0));
    }

    @Test
    void defaultTimeoutIsConfigured() throws Exception {
        // 멈춘 시험이 CI 작업 제한(35분)까지 가지 않게 하는 기본 제한시간(A19-01). 지우면 이 시험이 먼저 알려 준다.
        Properties p = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("junit-platform.properties")) {
            assertNotNull(in);
            p.load(in);
        }
        String t = p.getProperty("junit.jupiter.execution.timeout.default");
        assertNotNull(t, "junit.jupiter.execution.timeout.default 가 없다");
        assertFalse(t.isBlank());
    }
}
