package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * D1 검토 R4(c): 설정 키 plugins.compat.nms.missing-class-log 가 실제 플러그인 로더를 거쳐 먹는다 — 기본(summary)은 켜는 동안의 실패가 켜기 끝에 한 줄,
 * each 는 이름마다 한 줄(켜는 중에도).
 */
class NmsMissingClassLogPluginTest {

    private static final String YML = "name: MissLogUser\nversion: '1'\nmain: misslog.P\napi-version: '1.21'\n";

    private static final String PLUGIN = """
            package misslog;
            public final class P extends org.bukkit.plugin.java.JavaPlugin {
                @Override public void onEnable() {
                    for (String n : new String[] {"net.minecraft.world.level.FurnaceProbeAlpha", "net.minecraft.world.level.FurnaceProbeBeta"}) {
                        try { Class.forName(n); } catch (ClassNotFoundException e) { /* 기능 감지 — 없으면 다른 길 */ }
                    }
                }
            }
            """;

    @TempDir
    Path tmp;

    private Logger furnace;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        org.domi.furnace.compat.nms.NmsReflect.resetWarnings();
        furnace = (Logger) LoggerFactory.getLogger("Furnace");
        logs = new ListAppender<>();
        logs.start();
        furnace.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        furnace.detachAppender(logs);
        org.domi.furnace.compat.nms.NmsReflect.resetWarnings();
    }

    private List<String> probeWarnings() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("MissLogUser") && m.contains("FurnaceProbe")).toList();
    }

    private FurnaceTestServer start(FurnaceTestServer.Builder b) throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("misslog.jar"), YML, Map.of("misslog.P", PLUGIN), Set.of());
        return b.failFast(false).pluginJar(jar).start();
    }

    @Test
    void defaultSummarisesStartupMissesInOneLine() throws Exception {
        try (FurnaceTestServer server = start(FurnaceTestServer.builder())) {
            List<String> w = probeWarnings();
            assertEquals(1, w.size(), () -> String.join("\n", w));
            assertTrue(w.get(0).contains("켜지는 동안") && w.get(0).contains("2개") && w.get(0).contains("FurnaceProbeAlpha"), w.get(0));
        }
    }

    @Test
    void eachSettingWarnsPerName() throws Exception {
        try (FurnaceTestServer server = start(FurnaceTestServer.builder().compatNmsMissingClassLog("each"))) {
            List<String> w = probeWarnings();
            assertEquals(2, w.size(), () -> String.join("\n", w));
            assertTrue(w.get(0).contains("FurnaceProbeAlpha") && w.get(1).contains("FurnaceProbeBeta"), String.join("\n", w));
            assertTrue(w.stream().noneMatch(m -> m.contains("켜지는 동안")), "요약 줄이 없다");
        }
    }
}
