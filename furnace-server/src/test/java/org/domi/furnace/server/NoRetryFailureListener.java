// @NoRetry 가 붙은 시험(또는 그 안의 @BeforeAll 등)이 실패하면 이름을 파일에 적는다. CI 의 재시도 단계가 이 파일이 비어 있지 않으면
// 재시도하지 않고 바로 실패로 끝낸다. 파일 경로는 시스템 속성 furnace.noRetryReport(furnace-server/build.gradle.kts 가 시험 JVM 에 준다) —
// 이전 실행 기록은 gradle 이 시험 시작 전에 지운다. 속성이 없으면(IDE 등) 아무것도 하지 않는다. ServiceLoader 로 자동 등록된다(META-INF/services).
package org.domi.furnace.server;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class NoRetryFailureListener implements TestExecutionListener {

    static final String REPORT_PROPERTY = "furnace.noRetryReport";

    private static Path report() {
        String p = System.getProperty(REPORT_PROPERTY);
        return p == null || p.isBlank() ? null : Path.of(p);
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (result.getStatus() != TestExecutionResult.Status.FAILED) return;
        if (!id.getTags().contains(TestTag.create(NoRetry.TAG))) return;
        Path file = report();
        if (file == null) return;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, id.getUniqueId() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[NoRetryFailureListener] 실패 기록을 쓰지 못했다: " + e);
        }
    }
}
