// N-lib-1: 서버 jar 에 통째로 실린 호환 모듈 jar 를 꺼내 서버 제공 라이브러리 로더 아래의 자기 로더에 올린다.
// 로더 순서: 호환 플러그인 로더 → 호환 모듈 로더(furnace-compat) → 공유 로더(furnace-server-libraries) → 서버 로더.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.domi.furnace.api.plugin.PluginFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompatJarsTest {

    @TempDir Path tmp;
    private URLClassLoader source;
    private PluginBootstrapper boot;

    @BeforeEach
    void setUp() { PluginFormatsLazyTest.CountingFormat.CONSTRUCTED.set(0); }

    @AfterEach
    void tearDown() throws Exception {
        if (boot != null) boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
        if (source != null) source.close();
    }

    /** 서버 jar 흉내: META-INF/furnace/compat/ 에 색인과, 서비스 자원만 든 "호환 모듈" jar. 어댑터 클래스는 시험 클래스패스(부모)에서 온다. */
    private URLClassLoader serverJar(String index, String serviceLine) throws Exception {
        Path res = tmp.resolve("server-res-" + System.nanoTime());
        Path dir = res.resolve("META-INF/furnace/compat");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.txt"), index);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(dir.resolve("compat.jar")))) {
            out.putNextEntry(new JarEntry("META-INF/services/" + PluginFormat.class.getName()));
            out.write(serviceLine.getBytes());
            out.closeEntry();
        }
        source = new URLClassLoader(new URL[] {res.toUri().toURL()}, getClass().getClassLoader());
        return source;
    }

    @Test
    void noIndexMeansNotEmbedded() throws Exception {
        try (URLClassLoader empty = new URLClassLoader(new URL[] {tmp.toUri().toURL()}, null)) {
            assertNull(CompatJars.find(empty));
        }
    }

    @Test
    void indexRejectsPathsAndSkipsComments() throws Exception {
        assertEquals(List.of("compat.jar"), CompatJars.find(serverJar("# 주석\n\ncompat.jar\n", "x")).names());
        source.close();
        assertThrows(IllegalStateException.class, () -> CompatJars.find(serverJar("../evil.jar\n", "x")));
    }

    @Test
    void extractIsContentAddressedAndIdempotent() throws Exception {
        CompatJars jars = CompatJars.find(serverJar("compat.jar\n", "a\n"));
        Path out = tmp.resolve("cache");
        Path first = jars.extract(out).get(0);
        long mtime = Files.getLastModifiedTime(first).toMillis();
        Path again = jars.extract(out).get(0);
        assertEquals(first, again, "같은 내용이면 같은 파일");
        assertEquals(mtime, Files.getLastModifiedTime(again).toMillis(), "다시 쓰지 않는다");
        source.close();
        Path changed = CompatJars.find(serverJar("compat.jar\n", "b-다른-내용\n")).extract(out).get(0);
        assertNotEquals(first, changed, "판이 바뀌면 새 파일(열린 옛 파일을 덮어쓰지 않는다)");
        assertTrue(Files.exists(first));
    }

    @Test
    void compatJarIsTheFirstEntryOfTheSharedLibraryLoader() throws Exception {
        ClassLoader src = serverJar("compat.jar\n", PluginFormatsLazyTest.CountingFormat.class.getName() + "\n");
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve(PluginBootstrapper.BUKKIT_FOLDER).resolve("b.jar"), "fake.cfg",
                "name=Beta\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        boot = PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT, src);
        assertEquals(List.of("Beta"), boot.prepared().stream().map(p -> p.description().name()).toList());
        assertEquals(1, PluginFormatsLazyTest.CountingFormat.CONSTRUCTED.get());
        ClassLoader shared = boot.sharedLibraryLoader();
        assertNotNull(shared);
        assertSame(shared, boot.prepared().get(0).loader().getParent(), "호환 플러그인의 부모는 공유 로더");
        URL[] urls = ((URLClassLoader) shared).getURLs();
        assertEquals(boot.compatJarFiles().get(0).toUri().toURL(), urls[0], "호환 jar 가 공유 로더의 첫 항목 — 호환 계층과 라이브러리가 서로 본다");
        assertNotNull(shared.getResource("META-INF/services/" + PluginFormat.class.getName()));
        assertTrue(boot.compatJarFiles().get(0).getFileName().toString().startsWith("compat-"));
        assertEquals(java.util.Set.of(), boot.compatCoreGroups(), "이 흉내 jar 는 라이브러리 표지가 없다");
    }

    @Test
    void bothPathsAtOnceRefuseToStart() throws Exception {
        Path res = tmp.resolve("both");
        Files.createDirectories(res.resolve("META-INF/services"));
        Files.writeString(res.resolve("META-INF/services/" + PluginFormat.class.getName()), PluginFormatsLazyTest.CountingFormat.class.getName() + "\n");
        ClassLoader embedded = serverJar("compat.jar\n", "x\n");
        try (URLClassLoader both = new URLClassLoader(new URL[] {res.toUri().toURL()}, embedded)) {
            assertThrows(IllegalStateException.class, () -> PluginBootstrapper.bootstrap(tmp.resolve("p"), PluginSettings.DEFAULT, both));
        }
    }

    @Test
    void furnaceOnlyPluginsNeverExtractTheCompatJar() throws Exception {
        ClassLoader src = serverJar("compat.jar\n", PluginFormatsLazyTest.CountingFormat.class.getName() + "\n");
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml",
                "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        boot = PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT, src);
        assertNull(boot.compatJarFiles(), "Bukkit 플러그인이 없으면 호환 모듈을 꺼내지도 올리지도 않는다");
        assertFalse(Files.exists(dir.resolve(".libraries/compat")));
        assertEquals(0, PluginFormatsLazyTest.CountingFormat.CONSTRUCTED.get());
    }
}
