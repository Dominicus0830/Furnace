// 호환 어댑터(PluginFormat)는 Furnace 설명 파일이 없는 jar 를 처음 만났을 때에야 만든다 —
// Bukkit·Paper 플러그인이 없으면 호환 모듈 클래스는 JVM 에 올라오지 않는다는 약속의 서버 쪽 근거.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.plugin.PluginFormat;
import org.domi.furnace.server.plugin.library.ServerLibraries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginFormatsLazyTest {

    /** ServiceLoader 가 만들 때마다 센다 — 만들었는지(= 호환 모듈 클래스를 올렸는지)의 대리 지표. */
    public static final class CountingFormat implements PluginFormat {
        static final AtomicInteger CONSTRUCTED = new AtomicInteger();
        private final PluginFormatSpiTest.FakeFormat fake = new PluginFormatSpiTest.FakeFormat();

        public CountingFormat() { CONSTRUCTED.incrementAndGet(); }

        @Override public String id() { return fake.id(); }
        @Override public List<String> descriptorNames() { return fake.descriptorNames(); }
        @Override public PluginDescription readDescriptor(String fileName, String text) { return fake.readDescriptor(fileName, text); }
        @Override public FurnacePlugin create(PluginContext context, Class<?> main) throws ReflectiveOperationException { return fake.create(context, main); }
    }

    @TempDir Path tmp;
    private URLClassLoader withService;
    private PluginBootstrapper boot;

    @BeforeEach
    void setUp() throws Exception {
        CountingFormat.CONSTRUCTED.set(0);
        TestPlugins.LOG.clear();
        // 호환 모듈이 클래스패스에 있는 것처럼: 서비스 자원 파일만 가진 로더(클래스는 시험 클래스패스에서 부모가 준다)
        Path res = tmp.resolve("svc");
        Files.createDirectories(res.resolve("META-INF/services"));
        Files.writeString(res.resolve("META-INF/services/" + PluginFormat.class.getName()), CountingFormat.class.getName() + "\n");
        withService = new URLClassLoader(new URL[] {res.toUri().toURL()}, getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (boot != null) boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
        withService.close();
    }

    private void furnaceJar(Path dir) throws Exception {
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml",
                "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
    }

    @Test
    void serverModuleClasspathHasNoCompatModule() {
        assertFalse(PluginFormats.discover(getClass().getClassLoader()).available(), "서버 모듈 단독 클래스패스에는 어댑터 자원이 없다");
        assertFalse(PluginFormats.none().available());
    }

    @Test
    void discoveringDoesNotLoadTheAdapter() {
        PluginFormats f = PluginFormats.discover(withService);
        assertTrue(f.available());
        assertFalse(f.loaded());
        assertEquals(0, CountingFormat.CONSTRUCTED.get());
    }

    @Test
    void furnaceOnlyPluginsNeverLoadTheAdapter() throws Exception {
        Path dir = tmp.resolve("plugins");
        furnaceJar(dir);
        Files.createDirectories(dir.resolve(PluginBootstrapper.BUKKIT_FOLDER));   // 빈 bukkit-plugins 폴더
        PluginFormats f = PluginFormats.discover(withService);
        boot = PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT, f, ServerLibraries.DEFAULT);
        assertEquals(List.of("Alpha"), boot.prepared().stream().map(p -> p.description().name()).toList());
        assertFalse(f.loaded(), "Furnace 설명 파일 jar 만 있으면 어댑터를 만들지 않는다");
        assertEquals(0, CountingFormat.CONSTRUCTED.get());
    }

    @Test
    void aForeignDescriptorJarLoadsTheAdapterOnceAndOnlyThen() throws Exception {
        Path dir = tmp.resolve("plugins");
        furnaceJar(dir);
        PluginSystemTest.jar(dir.resolve(PluginBootstrapper.BUKKIT_FOLDER).resolve("b.jar"), "fake.cfg",
                "name=Beta\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        PluginSystemTest.jar(dir.resolve(PluginBootstrapper.BUKKIT_FOLDER).resolve("c.jar"), "fake.cfg",
                "name=Gamma\nmain=org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        PluginFormats f = PluginFormats.discover(withService);
        boot = PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT, f, ServerLibraries.DEFAULT);
        assertEquals(List.of("Alpha", "Beta", "Gamma"), boot.prepared().stream().map(p -> p.description().name()).sorted().toList());
        assertTrue(f.loaded());
        assertEquals(1, CountingFormat.CONSTRUCTED.get(), "어댑터는 한 번만 만든다");
    }

    @Test
    void compatSettingOffNeverScansTheBukkitFolderNorLoadsTheAdapter() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve(PluginBootstrapper.BUKKIT_FOLDER).resolve("b.jar"), "fake.cfg",
                "name=Beta\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        boot = PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT.withCompat(false), PluginFormats.none(), ServerLibraries.DEFAULT);
        assertTrue(boot.prepared().isEmpty());
        assertEquals(0, CountingFormat.CONSTRUCTED.get());
    }
}
