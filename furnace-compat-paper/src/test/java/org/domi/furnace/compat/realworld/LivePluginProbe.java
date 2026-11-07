package org.domi.furnace.compat.realworld;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.slf4j.LoggerFactory;

/**
 * 실제 플러그인 jar 하나를 헤드리스 서버에 올려 결과를 기록한다(H4). 로드·활성 실패의 원인은 서버 로그의 예외에서 읽는다.
 */
public final class LivePluginProbe {

    /** 낮을수록 나쁨 — 기준표(realworld-baseline.properties)와 견줄 때 순서를 쓴다. */
    public enum Status {
        NOT_LOADED, LOADED_NOT_ENABLED, ENABLED_THEN_CRASHED, ENABLED
    }

    public record Result(Status status, String pluginName, String firstError, List<String> errors, int ticksSurvived) {}

    private LivePluginProbe() {}

    /** 플러그인 jar 를 올려 서버를 띄우고 몇 틱 돌린 뒤 결과를 돌려준다. {@code pluginName} 은 plugin.yml 의 name. */
    public static Result run(Path jar, String pluginName, int ticks, Path serverDir) {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        List<String> errors = new ArrayList<>();
        Status status = Status.NOT_LOADED;
        int survived = 0;
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).directory(serverDir).pluginJar(jar).start()) {
            FurnacePlugin p = server.plugin(pluginName);
            if (p != null) {
                status = p.isEnabled() ? Status.ENABLED : Status.LOADED_NOT_ENABLED;
                for (int i = 0; i < ticks; i++) {
                    server.tick();
                    survived++;
                }
                if (status == Status.ENABLED && !p.isEnabled()) status = Status.ENABLED_THEN_CRASHED;
            }
        } finally {
            root.detachAppender(appender);
        }
        for (ILoggingEvent e : appender.list) {
            if (e.getLevel().isGreaterOrEqual(Level.WARN) && e.getThrowableProxy() != null) errors.add(describe(e.getThrowableProxy()));
            else if (e.getLevel().isGreaterOrEqual(Level.ERROR)) errors.add(e.getFormattedMessage());
        }
        return new Result(status, pluginName, errors.isEmpty() ? null : errors.get(0), errors, survived);
    }

    /** 가장 안쪽 원인(보통 NoClassDefFoundError 등)을 한 줄로. */
    static String describe(IThrowableProxy t) {
        IThrowableProxy root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getClassName() + ": " + root.getMessage();
    }
}
