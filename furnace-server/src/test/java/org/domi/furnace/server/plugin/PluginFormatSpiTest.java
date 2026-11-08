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
        final List<String> events = new ArrayList<>();
        boolean throwAfterDisabled;

        @Override
        public void afterPluginsDisabled(org.domi.furnace.api.Furnace server) {
            events.add("after");
            if (throwAfterDisabled) throw new IllegalStateException("마무리 실패");
        }

        @Override public String id() { return "fake"; }

        @Override public List<String> descriptorNames() { return List.of("fake.cfg"); }

        @Override
        public PluginDescription readDescriptor(String fileName, String text) {
            String name = null, version = "1", main = null, api = null;
            List<PluginDescription.Library> libs = new ArrayList<>();
            for (String line : text.split("\n")) {
                String[] kv = line.split("=", 2);
                switch (kv[0]) {
                    case "name" -> name = kv[1];
                    case "version" -> version = kv[1];
                    case "main" -> main = kv[1];
                    case "api" -> api = kv[1];
                    case "lib" -> libs.add(new PluginDescription.Library(kv[1]));
                    case "boom" -> throw new IllegalArgumentException("읽을 수 없는 설명 파일");
                    default -> {}
                }
            }
            return new PluginDescription(name, version, main, "", List.of(), "", api, List.of(), List.of(), List.of(), null,
                    java.util.Map.of(), List.of(), null, libs);
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

    @Test
    void afterPluginsDisabledRunsOnceAfterEveryPluginIsDisabledAndAFailureDoesNotStopShutdown() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("a.jar"), "fake.cfg", "name=Alpha\nmain=org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        FakeFormat first = new FakeFormat(), second = new FakeFormat();
        first.throwAfterDisabled = true;   // 하나가 던져도 다음 형식과 종료는 계속된다
        load(dir, List.of(first, second));
        TestPlugins.LOG.clear();
        assertTrue(first.events.isEmpty() && second.events.isEmpty(), "켜는 동안·플러그인 하나를 끌 때는 안 불린다");
        host.pluginsImpl().disable(host.plugins().plugins().iterator().next());
        assertTrue(first.events.isEmpty(), "플러그인 하나 끄기(리로드 등)에서는 안 불린다");
        host.pluginsImpl().disableAll();
        assertEquals(List.of("after"), first.events);
        assertEquals(List.of("after"), second.events);
    }

    @Test
    void compatOptionsAreReadOnlyForPluginsCompatKeysAndStrict() {
        java.util.Properties pr = new java.util.Properties();
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("test");
        assertEquals(null, PluginSettings.from(pr, log).compatOption(PluginSettings.ASYNC_THREADS_KEY), "안 적으면 어댑터의 기본값(platform)");
        pr.setProperty(PluginSettings.ASYNC_THREADS_KEY, " Virtual ");
        assertEquals("virtual", PluginSettings.from(pr, log).compatOption(PluginSettings.ASYNC_THREADS_KEY));
        pr.setProperty(PluginSettings.ASYNC_THREADS_KEY, "platform");
        assertEquals(PluginSettings.DEFAULT, PluginSettings.from(pr, log), "기본값과 같게 적은 것은 안 적은 것과 같다");
        pr.setProperty(PluginSettings.ASYNC_THREADS_KEY, "fiber");
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(pr, log));
        assertTrue(e.getMessage().contains(PluginSettings.ASYNC_THREADS_KEY) && e.getMessage().contains("fiber"), e.getMessage());

        // 서버 쪽 문: plugins.compat. 로 시작하는 키만 보인다
        pr.setProperty(PluginSettings.ASYNC_THREADS_KEY, "virtual");
        pr.setProperty("rcon.password", "secret");
        host = new PluginHost(core, tmp.resolve("plugins"));
        host.pluginsImpl().settings(PluginSettings.from(pr, log));
        assertEquals("virtual", host.compatOption(PluginSettings.ASYNC_THREADS_KEY));
        assertEquals(null, host.compatOption("rcon.password"));
        assertEquals(null, host.compatOption(null));
    }
}
