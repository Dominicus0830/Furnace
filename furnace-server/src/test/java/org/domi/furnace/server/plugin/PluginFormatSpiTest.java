// 플러그인 형식 SPI(PluginFormat): 형식 어댑터가 있으면 그 설명 파일 jar 도 plugins/·plugins/bukkit-plugins/ 에서 읽어 Furnace 플러그인으로 만든다.
// 없으면 전과 같다(PluginSystemTest.loadsInDependencyOrderAndSkipsBadJars).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.plugin.PluginFormat;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginFormatSpiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    /** "name=…\nversion=…\nmain=…\napi=…" 한 줄씩인 가짜 형식. */
    static final class FakeFormat implements PluginFormat {
        final List<String> created = new ArrayList<>();

        @Override public String id() { return "fake"; }

        @Override public List<String> descriptorNames() { return List.of("fake.cfg"); }

        @Override
        public PluginDescription readDescriptor(String fileName, String text) {
            String name = null, version = "1", main = null, api = null;
            for (String line : text.split("\n")) {
                String[] kv = line.split("=", 2);
                switch (kv[0]) {
                    case "name" -> name = kv[1];
                    case "version" -> version = kv[1];
                    case "main" -> main = kv[1];
                    case "api" -> api = kv[1];
                    case "boom" -> throw new IllegalArgumentException("읽을 수 없는 설명 파일");
                    default -> {}
                }
            }
            return new PluginDescription(name, version, main, "", List.of(), "", api, List.of(), List.of(), List.of(), null,
                    java.util.Map.of(), List.of(), null, List.of());
        }

        @Override
        public FurnacePlugin create(PluginContext context, Class<?> main) throws ReflectiveOperationException {
            created.add(context.description().name());
            return (FurnacePlugin) main.getDeclaredConstructor().newInstance();
        }
    }

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        sched.close();
    }

    private PluginHost load(Path dir, List<PluginFormat> formats) {
        host = new PluginHost(core, dir);
        host.pluginsImpl().loadAll(PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT, formats));
        host.pluginsImpl().enableAll();
        return host;
    }

    @Test
    void formatJarsLoadFromBothFoldersAndWithoutFormatTheyAreSkipped() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("a.jar"), "fake.cfg", "name=Alpha\nmain=org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        PluginSystemTest.jar(dir.resolve("bukkit-plugins").resolve("b.jar"), "fake.cfg",
                "name=Beta\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);

        load(dir, List.of());
        assertEquals(0, host.plugins().plugins().size(), "형식이 없으면 낯선 설명 파일 jar 는 건너뛴다");
        host.pluginsImpl().disableAll();
        host.pluginsImpl().closeAll();
        host.close();
        TestPlugins.LOG.clear();

        FakeFormat fake = new FakeFormat();
        load(dir, List.of(fake));
        assertEquals(List.of("Alpha", "Beta"), host.plugins().plugins().stream().map(FurnacePlugin::name).sorted().toList());
        assertEquals(List.of("Alpha", "Beta"), fake.created.stream().sorted().toList());
        assertTrue(host.plugins().isEnabled("alpha"));
    }

    @Test
    void furnaceDescriptorWinsAndFormatApiVersionIsLeftToTheFormat() throws Exception {
        Path dir = tmp.resolve("plugins");
        // 두 설명 파일이 다 있으면 Furnace 것이 이긴다(형식 어댑터를 거치지 않는다)
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml", "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n",
                TestPlugins.Alpha.class);
        // 형식의 api 값은 서버가 해석하지 않는다(형식이 알아서 검사) — 터무니없는 값이어도 서버는 거르지 않는다
        PluginSystemTest.jar(dir.resolve("b.jar"), "fake.cfg",
                "name=Beta\napi=99.9\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        FakeFormat fake = new FakeFormat();
        load(dir, List.of(fake));
        assertEquals(List.of("Beta"), fake.created);
        assertEquals(2, host.plugins().plugins().size());
    }

    @Test
    void aFormatThatCannotReadItsDescriptorOnlyDropsThatJar() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("bad.jar"), "fake.cfg", "boom=1\n");
        PluginSystemTest.jar(dir.resolve("a.jar"), "fake.cfg", "name=Alpha\nmain=org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        load(dir, List.of(new FakeFormat()));
        assertEquals(List.of("Alpha"), host.plugins().plugins().stream().map(FurnacePlugin::name).toList());
    }
}
