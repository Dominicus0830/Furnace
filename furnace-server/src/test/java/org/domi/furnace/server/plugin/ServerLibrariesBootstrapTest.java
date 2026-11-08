// 호환 플러그인용 서버 제공 라이브러리(H4 묶음 B): 호환 형식 플러그인이 쓰는 묶음만 공유 로더에 붙고, 두 플러그인이 같은 Class 를 본다.
// 오프라인·Mojang 금지·off·all·Furnace 플러그인(영향 없음)·plugin.yml libraries(declared)를 본다. 네트워크 대신 offline + 미리 채운 캐시를 쓴다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.domi.furnace.api.plugin.PluginFormat;
import org.domi.furnace.server.plugin.library.LibrarySettings;
import org.domi.furnace.server.plugin.library.ServerLibraries;
import org.domi.furnace.server.plugin.library.SharedLibraryLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLibrariesBootstrapTest {

    @TempDir Path tmp;
    private Path dir;
    private Path cache;
    private byte[] libJar;
    private String libSha;
    private PluginBootstrapper boot;

    private static final String ALPHA = "name=Alpha\nmain=org.domi.furnace.server.plugin.TestPlugins$Alpha\n";
    private static final String BETA = "name=Beta\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n";

    @BeforeEach
    void setUp() throws Exception {
        dir = tmp.resolve("plugins");
        cache = tmp.resolve("cache");
        libJar = compiledJar("fakelib.Shared", "package fakelib; public class Shared { public static String hi() { return \"hi\"; } }");
        libSha = sha256(libJar);
    }

    @AfterEach
    void tearDown() {
        if (boot == null) return;
        for (String n : List.of("Alpha", "Beta", "Gamma")) {
            PluginBootstrapper.Prepared p = boot.find(n);
            if (p != null) PluginManagerImpl.closeQuietly(p.loader());
        }
        boot.closeSharedLibraries();
    }

    // ------------------------------------------------------------------ 도구

    /** 표지 fakelib/ 를 가진 묶음(Central)과 Mojang 묶음 하나. */
    private ServerLibraries catalog() {
        return new ServerLibraries(List.of(
                new ServerLibraries.Group("fakelib", "시험 라이브러리",
                        List.of(new ServerLibraries.Artifact("org.test:fakelib:1.0", libSha, ServerLibraries.Repo.CENTRAL)), List.of("fakelib/"), List.of()),
                new ServerLibraries.Group("mojanglib", "시험 Mojang 라이브러리",
                        List.of(new ServerLibraries.Artifact("com.mojang:fakemojang:1.0", libSha, ServerLibraries.Repo.MOJANG)), List.of("com/mojang/fake/"),
                        List.of("fakelib"))));
    }

    private void putInCache(String g, String a, String v) throws IOException {
        Path f = cache.resolve(g.replace('.', '/')).resolve(a).resolve(v).resolve(a + "-" + v + ".jar");
        Files.createDirectories(f.getParent());
        Files.write(f, libJar);
    }

    private LibrarySettings offline() { return LibrarySettings.DEFAULT.withOffline(true).withCache(cache); }

    private PluginBootstrapper bootstrap(LibrarySettings ls) {
        PluginSettings ps = PluginSettings.DEFAULT.withLibraries(ls);
        List<PluginFormat> formats = List.of(new PluginFormatSpiTest.FakeFormat());
        boot = PluginBootstrapper.bootstrap(dir, ps, formats, catalog());
        return boot;
    }

    /** 설명 파일 + 표지 문자열을 상수로 가진 가짜 클래스 파일(내용은 클래스가 아니어도 된다 — 훑기만 한다). */
    private static void pluginJar(Path file, String descriptorName, String descriptor, String marker, Class<?>... classes) throws IOException {
        PluginSystemTest.jar(file, descriptorName, descriptor, classes);
        if (marker == null) return;
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var in = new java.util.jar.JarInputStream(Files.newInputStream(file));
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp))) {
            for (JarEntry e; (e = in.getNextJarEntry()) != null; ) {
                out.putNextEntry(new JarEntry(e.getName()));
                out.write(in.readAllBytes());
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("probe/Uses.class"));
            out.write(("Êþº¾...." + marker + "....").getBytes(StandardCharsets.ISO_8859_1));
            out.closeEntry();
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] compiledJar(String className, String source) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Path work = Files.createTempDirectory("fakelib");
        Path src = work.resolve(className.substring(className.lastIndexOf('.') + 1) + ".java");
        Files.writeString(src, source);
        assertEquals(0, javac.run(null, null, null, "-d", work.resolve("out").toString(), src.toString()));
        String path = className.replace('.', '/') + ".class";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JarOutputStream o = new JarOutputStream(bo)) {
            o.putNextEntry(new JarEntry(path));
            o.write(Files.readAllBytes(work.resolve("out").resolve(path)));
            o.closeEntry();
        }
        return bo.toByteArray();
    }

    private static String sha256(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private ClassLoader loaderOf(String plugin) { return boot.find(plugin).loader(); }

    // ------------------------------------------------------------------ 시험

    @Test
    void neededGroupIsSharedByEveryCompatPlugin() throws Exception {
        putInCache("org.test", "fakelib", "1.0");
        pluginJar(dir.resolve("bukkit-plugins").resolve("a.jar"), "fake.cfg", ALPHA, "fakelib/Shared", TestPlugins.Alpha.class);
        pluginJar(dir.resolve("bukkit-plugins").resolve("b.jar"), "fake.cfg", BETA, null, TestPlugins.Beta.class);
        bootstrap(offline());

        assertEquals(java.util.Set.of("fakelib"), boot.sharedLibraryGroups());
        Class<?> a = Class.forName("fakelib.Shared", true, loaderOf("Alpha"));
        Class<?> b = Class.forName("fakelib.Shared", true, loaderOf("Beta"));
        assertSame(a, b, "두 호환 플러그인이 같은 Class 를 본다(Paper 처럼 서버 쪽에 한 벌)");
        assertInstanceOf(SharedLibraryLoader.class, a.getClassLoader());
        assertSame(boot.sharedLibraryLoader(), loaderOf("Alpha").getParent());
        assertEquals("hi", a.getMethod("hi").invoke(null));
        // 캐시 밖으로 나가지 않았다 — 잠금 파일도 쓰지 않는다(고정값이 기준)
        assertFalse(Files.exists(cache.resolve("libraries.lock")));
    }

    @Test
    void groupsNobodyUsesAreNotPreparedButAllModePreparesThem() throws Exception {
        putInCache("org.test", "fakelib", "1.0");
        putInCache("com.mojang", "fakemojang", "1.0");
        pluginJar(dir.resolve("bukkit-plugins").resolve("b.jar"), "fake.cfg", BETA, null, TestPlugins.Beta.class);
        bootstrap(offline());
        assertEquals(java.util.Set.of(), boot.sharedLibraryGroups());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("fakelib.Shared", false, loaderOf("Beta")));
        tearDown();

        bootstrap(offline().withCompat(LibrarySettings.CompatMode.ALL));
        assertEquals(java.util.Set.of("fakelib", "mojanglib"), boot.sharedLibraryGroups());
        assertNotNull(Class.forName("fakelib.Shared", false, loaderOf("Beta")));
    }

    @Test
    void requiredGroupsComeAlongAndMojangCanBeRefused() throws Exception {
        putInCache("org.test", "fakelib", "1.0");
        putInCache("com.mojang", "fakemojang", "1.0");
        pluginJar(dir.resolve("bukkit-plugins").resolve("a.jar"), "fake.cfg", ALPHA, "com.mojang.fake.Thing", TestPlugins.Alpha.class);
        bootstrap(offline());
        assertEquals(java.util.Set.of("fakelib", "mojanglib"), boot.sharedLibraryGroups(), "점 표기(리플렉션 문자열)도 표지로 센다, 요구 묶음도 함께");
        tearDown();

        bootstrap(offline().withCompatMojang(false));
        assertEquals(java.util.Set.of("fakelib"), boot.sharedLibraryGroups(), "Mojang 저장소를 막으면 그 묶음만 빠진다");
        assertNotNull(boot.find("Alpha"), "플러그인은 그대로 올라간다");
    }

    @Test
    void missingOrWrongFilesOnlyWarnAndThePluginStillLoads() throws Exception {
        pluginJar(dir.resolve("bukkit-plugins").resolve("a.jar"), "fake.cfg", ALPHA, "fakelib/Shared", TestPlugins.Alpha.class);
        bootstrap(offline());   // 캐시 비어 있음 + offline
        assertEquals(java.util.Set.of(), boot.sharedLibraryGroups());
        assertNotNull(boot.find("Alpha"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("fakelib.Shared", false, loaderOf("Alpha")));
        tearDown();

        // 고정 sha256 과 다른 파일은 쓰지 않는다(offline 이라 다시 받지도 못한다)
        Path f = cache.resolve("org/test/fakelib/1.0/fakelib-1.0.jar");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "가짜");
        bootstrap(offline());
        assertEquals(java.util.Set.of(), boot.sharedLibraryGroups());
        assertFalse(Files.exists(f), "고정값과 다른 캐시 파일은 지운다");
    }

    @Test
    void offModeAndFurnacePluginsUseTheServerLoader() throws Exception {
        putInCache("org.test", "fakelib", "1.0");
        pluginJar(dir.resolve("bukkit-plugins").resolve("a.jar"), "fake.cfg", ALPHA, "fakelib/Shared", TestPlugins.Alpha.class);
        pluginJar(dir.resolve("b.jar"), "furnace-plugin.yml", "name: Beta\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Beta\n",
                "fakelib/Shared", TestPlugins.Beta.class);
        bootstrap(offline());
        assertSame(PluginHost.class.getClassLoader(), loaderOf("Beta").getParent(), "Furnace 플러그인은 서버 제공 라이브러리를 받지 않는다");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("fakelib.Shared", false, loaderOf("Beta")));
        tearDown();

        bootstrap(offline().withCompat(LibrarySettings.CompatMode.OFF));
        assertNull(boot.sharedLibraryLoader());
        assertSame(PluginHost.class.getClassLoader(), loaderOf("Alpha").getParent());
    }

    @Test
    void pluginYmlLibrariesAreResolvedForCompatPluginsLikePaper() throws Exception {
        putInCache("org.test", "fakelib", "1.0");
        // 잠금 파일 기록이 있으면 offline 캐시로 풀린다(R1 규칙 그대로)
        Files.writeString(cache.resolve("libraries.lock"), "org.test:fakelib:1.0=" + libSha + "\n");
        pluginJar(dir.resolve("bukkit-plugins").resolve("a.jar"), "fake.cfg", ALPHA + "lib=org.test:fakelib:1.0\n", null, TestPlugins.Alpha.class);
        bootstrap(offline().withTransitive(false));
        assertNotNull(Class.forName("fakelib.Shared", false, loaderOf("Alpha")), "plugins.libraries.enabled=false 여도 호환 플러그인의 libraries 는 받는다");
        tearDown();

        bootstrap(offline().withTransitive(false).withCompatDeclared(false));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("fakelib.Shared", false, loaderOf("Alpha")));
    }

    @Test
    void defaultCatalogIsClosedAndPinned() {
        ServerLibraries d = ServerLibraries.DEFAULT;
        for (ServerLibraries.Group g : d.groups()) {
            assertFalse(g.artifacts().isEmpty(), g.id());
            assertFalse(g.markers().isEmpty(), g.id());
            for (ServerLibraries.Artifact a : g.artifacts()) assertNotNull(a.sha256(), a.coordinate());
            for (String m : g.markers()) assertTrue(m.endsWith("/") && !m.startsWith("/"), m);
        }
        assertEquals(List.of("guava", "commons-lang3", "commons-io", "authlib"),
                d.closure(List.of("authlib")).stream().map(ServerLibraries.Group::id).toList());
        // BungeeChat 의 ChatColor·ComponentBuilder 는 Guava Preconditions 를 쓴다(검토 지적)
        assertEquals(List.of("guava", "bungeecord-chat"), d.closure(List.of("bungeecord-chat")).stream().map(ServerLibraries.Group::id).toList());
        assertEquals(List.of("brigadier", "authlib", "datafixerupper"),
                d.groups().stream().filter(ServerLibraries.Group::mojang).map(ServerLibraries.Group::id).toList());
        assertEquals(URI.create("https://libraries.minecraft.net/"), ServerLibraries.MOJANG);
        // 서버 클래스패스에 이미 있는 라이브러리(Gson·slf4j·netty)는 목록에 없다 — 두 벌이 되면 플러그인과 서버가 다른 Class 를 본다
        for (ServerLibraries.Group g : d.groups()) {
            for (ServerLibraries.Artifact a : g.artifacts()) {
                String c = a.coordinate();
                assertFalse(c.startsWith("com.google.code.gson:") || c.startsWith("org.slf4j:") || c.startsWith("io.netty:"), c);
            }
        }
        Map<String, Boolean> onServer = Map.of("com.google.gson.Gson", true, "org.slf4j.Logger", true);
        onServer.forEach((cls, v) -> assertDoesNotThrow(() -> Class.forName(cls, false, PluginHost.class.getClassLoader())));
    }

    @Test
    void bungeeChatColorAloneNeedsBungeeChatAndGuava() throws Exception {
        Path jar = tmp.resolve("color.jar");
        pluginJar(jar, "fake.cfg", ALPHA, "net/md_5/bungee/api/ChatColor");
        java.util.Set<String> found = ServerLibraries.DEFAULT.scan(jar, java.util.Set.of());
        assertEquals(java.util.Set.of("bungeecord-chat"), found, "헥스 색(ChatColor)만 써도 BungeeChat 이 필요하다");
        assertEquals(List.of("guava", "bungeecord-chat"), ServerLibraries.DEFAULT.closure(found).stream().map(ServerLibraries.Group::id).toList());
    }
}
