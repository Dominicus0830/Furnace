// 라이브러리 자동 로딩(B5): 내려받기·체크섬 고정·잠금 파일·캐시·offline·require-checksum·전이 의존(pom 상속·BOM·제외·범위).
// 네트워크 대신 메모리 저장소(Fetcher)로 시험하고, HttpFetcher 는 로컬 HTTP 서버로 시험한다.
package org.domi.furnace.server.plugin.library;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.domi.furnace.api.plugin.PluginDescription.Library;
import org.domi.furnace.server.plugin.library.LibraryResolver.LibraryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LibraryResolverTest {

    private static final String REPO = "https://repo.test/maven2/";
    private static final Logger LOG = LoggerFactory.getLogger("LibraryResolverTest");

    @TempDir Path tmp;
    private Path cache;
    private final Map<String, byte[]> remote = new HashMap<>();
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cache = tmp.resolve("cache");
    }

    // ------------------------------------------------------------------ 도구

    private LibraryResolver resolver(LibrarySettings s) {
        return new LibraryResolver(s, cache, URI.create(REPO), (uri, max) -> {
            requests.add(uri.toString());
            byte[] b = remote.get(uri.toString());
            if (b != null && b.length > max) throw new LibraryResolver.HttpStatusException("크기 상한");
            return b;
        }, LOG);
    }

    private static LibrarySettings on() { return LibrarySettings.DEFAULT.withEnabled(true).withTransitive(false); }

    private static byte[] jarBytes(String marker) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JarOutputStream o = new JarOutputStream(bo)) {
            o.putNextEntry(new JarEntry("marker.txt"));
            o.write(marker.getBytes(StandardCharsets.UTF_8));
            o.closeEntry();
        }
        return bo.toByteArray();
    }

    private static String url(String g, String a, String v, String ext) {
        return REPO + g.replace('.', '/') + "/" + a + "/" + v + "/" + a + "-" + v + "." + ext;
    }

    private static String hex(String alg, byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance(alg).digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 저장소에 jar(+ .sha1)를 올린다. */
    private byte[] publishJar(String g, String a, String v, String marker) throws Exception {
        byte[] jar = jarBytes(marker);
        remote.put(url(g, a, v, "jar"), jar);
        remote.put(url(g, a, v, "jar.sha1"), hex("SHA-1", jar).getBytes(StandardCharsets.UTF_8));
        return jar;
    }

    private void publishPom(String g, String a, String v, String xml) throws Exception {
        byte[] b = xml.getBytes(StandardCharsets.UTF_8);
        remote.put(url(g, a, v, "pom"), b);
        remote.put(url(g, a, v, "pom.sha1"), hex("SHA-1", b).getBytes(StandardCharsets.UTF_8));
    }

    private static String pom(String g, String a, String v, String body) {
        return "<project><groupId>" + g + "</groupId><artifactId>" + a + "</artifactId><version>" + v + "</version>" + body + "</project>";
    }

    private static String dep(String g, String a, String v) { return dep(g, a, v, ""); }

    private static String dep(String g, String a, String v, String extra) {
        return "<dependency><groupId>" + g + "</groupId><artifactId>" + a + "</artifactId>" + (v == null ? "" : "<version>" + v + "</version>") + extra + "</dependency>";
    }

    private static String deps(String... d) { return "<dependencies>" + String.join("", d) + "</dependencies>"; }

    private static String marker(Path jar) throws IOException {
        try (var zf = new java.util.zip.ZipFile(jar.toFile())) {
            return new String(zf.getInputStream(zf.getEntry("marker.txt")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String lockFile() throws IOException { return Files.readString(cache.resolve(LibraryResolver.LOCK_FILE)); }

    // ------------------------------------------------------------------ 내려받기·캐시·잠금

    @Test
    void downloadsVerifiesAndCaches() throws Exception {
        byte[] jar = publishJar("com.acme", "lib", "1.0", "one");
        List<Path> out = resolver(on()).resolve("P", List.of(new Library("com.acme:lib:1.0")));
        assertEquals(1, out.size());
        assertEquals("one", marker(out.get(0)));
        assertEquals(cache.resolve("com/acme/lib/1.0/lib-1.0.jar"), out.get(0));
        assertTrue(lockFile().contains("com.acme:lib:1.0=" + hex("SHA-256", jar)));
        // 두 번째: 네트워크 없이 캐시
        requests.clear();
        assertEquals(out, resolver(on()).resolve("P", List.of(new Library("com.acme:lib:1.0"))));
        assertTrue(requests.isEmpty(), "캐시에서 읽어야 한다: " + requests);
    }

    @Test
    void nothingDeclaredIsFree() throws Exception {
        assertTrue(resolver(on()).resolve("P", List.of()).isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void classifierSelectsOtherFile() throws Exception {
        byte[] jar = jarBytes("native");
        remote.put(url("a", "b", "1", "jar").replace("b-1.jar", "b-1-linux.jar"), jar);
        remote.put(url("a", "b", "1", "jar").replace("b-1.jar", "b-1-linux.jar") + ".sha1", hex("SHA-1", jar).getBytes(StandardCharsets.UTF_8));
        List<Path> out = resolver(on()).resolve("P", List.of(new Library("a:b:1:linux")));
        assertEquals("native", marker(out.get(0)));
        assertTrue(out.get(0).endsWith("b-1-linux.jar"));
    }

    @Test
    void missingDirectLibraryFails() {
        LibraryException e = assertThrows(LibraryException.class, () -> resolver(on()).resolve("P", List.of(new Library("a:b:1"))));
        assertTrue(e.getMessage().contains("a:b:1"), e.getMessage());
    }

    @Test
    void sha1MismatchIsRejected() throws Exception {
        publishJar("a", "b", "1", "real");
        remote.put(url("a", "b", "1", "jar"), jarBytes("evil"));   // jar 만 바뀌고 .sha1 은 그대로
        LibraryException e = assertThrows(LibraryException.class, () -> resolver(on()).resolve("P", List.of(new Library("a:b:1"))));
        assertTrue(e.getMessage().contains(".sha1"), e.getMessage());
        assertFalse(Files.exists(cache.resolve("a/b/1/b-1.jar")), "검증 실패한 파일은 남기지 않는다");
    }

    @Test
    void missingSha1IsRejected() throws Exception {
        publishJar("a", "b", "1", "x");
        remote.remove(url("a", "b", "1", "jar.sha1"));
        assertThrows(LibraryException.class, () -> resolver(on()).resolve("P", List.of(new Library("a:b:1"))));
    }

    @Test
    void manifestPinAcceptsMatchAndRejectsMismatch() throws Exception {
        byte[] jar = publishJar("a", "b", "1", "x");
        String good = hex("SHA-256", jar);
        assertEquals(1, resolver(on()).resolve("P", List.of(new Library("a:b:1", good))).size());

        Path other = tmp.resolve("other");
        cache = other;
        LibraryException e = assertThrows(LibraryException.class,
                () -> resolver(on()).resolve("P", List.of(new Library("a:b:1", "0".repeat(64)))));
        assertTrue(e.getMessage().contains("sha256"), e.getMessage());
        assertFalse(Files.exists(other.resolve("a/b/1/b-1.jar")));
    }

    @Test
    void pinNeedsNoSha1File() throws Exception {
        byte[] jar = publishJar("a", "b", "1", "x");
        remote.remove(url("a", "b", "1", "jar.sha1"));
        assertEquals(1, resolver(on()).resolve("P", List.of(new Library("a:b:1", hex("SHA-256", jar)))).size());
    }

    @Test
    void lockFilePinsLaterDownloads() throws Exception {
        publishJar("a", "b", "1", "first");
        resolver(on()).resolve("P", List.of(new Library("a:b:1")));
        // 캐시 파일을 지우고 저장소가 다른 내용(.sha1 도 맞춰서)을 주면 잠금 파일 때문에 거부
        Files.delete(cache.resolve("a/b/1/b-1.jar"));
        publishJar("a", "b", "1", "second");
        LibraryException e = assertThrows(LibraryException.class, () -> resolver(on()).resolve("P", List.of(new Library("a:b:1"))));
        assertTrue(e.getMessage().contains("잠금 파일"), e.getMessage());
        assertFalse(Files.exists(cache.resolve("a/b/1/b-1.jar")));
    }

    @Test
    void tamperedCacheIsReplacedByVerifiedDownload() throws Exception {
        publishJar("a", "b", "1", "good");
        Path jar = resolver(on()).resolve("P", List.of(new Library("a:b:1"))).get(0);
        Files.write(jar, jarBytes("tampered"));
        Path again = resolver(on()).resolve("P", List.of(new Library("a:b:1"))).get(0);
        assertEquals("good", marker(again));
    }

    @Test
    void tamperedCacheOfflineIsRejected() throws Exception {
        publishJar("a", "b", "1", "good");
        Path jar = resolver(on()).resolve("P", List.of(new Library("a:b:1"))).get(0);
        Files.write(jar, jarBytes("tampered"));
        assertThrows(LibraryException.class, () -> resolver(on().withOffline(true)).resolve("P", List.of(new Library("a:b:1"))));
    }

    @Test
    void manifestPinDifferentFromLockIsRejected() throws Exception {
        publishJar("a", "b", "1", "x");
        resolver(on()).resolve("P", List.of(new Library("a:b:1")));
        LibraryException e = assertThrows(LibraryException.class,
                () -> resolver(on()).resolve("P", List.of(new Library("a:b:1", "1".repeat(64)))));
        assertTrue(e.getMessage().contains("잠금"), e.getMessage());
    }

    @Test
    void oversizeDownloadIsRejected() throws Exception {
        remote.put(url("a", "b", "1", "jar"), new byte[2 * 1024 * 1024]);
        LibrarySettings s = new LibrarySettings(true, false, false, false, null, 30, 1);
        assertThrows(LibraryException.class, () -> resolver(s).resolve("P", List.of(new Library("a:b:1"))));
    }

    // ------------------------------------------------------------------ require-checksum·offline

    @Test
    void requireChecksumRefusesUnrecorded() throws Exception {
        publishJar("a", "b", "1", "x");
        LibrarySettings s = on().withRequireChecksum(true);
        LibraryException e = assertThrows(LibraryException.class, () -> resolver(s).resolve("P", List.of(new Library("a:b:1"))));
        assertTrue(e.getMessage().contains("require-checksum"), e.getMessage());
        assertTrue(requests.isEmpty(), "기록이 없으면 받기 전에 거부한다");
    }

    @Test
    void requireChecksumAcceptsPinOrLockEntry() throws Exception {
        byte[] jar = publishJar("a", "b", "1", "x");
        String sha = hex("SHA-256", jar);
        LibrarySettings s = on().withRequireChecksum(true);
        assertEquals(1, resolver(s).resolve("P", List.of(new Library("a:b:1", sha))).size());

        // 잠금 파일만 손으로 채운 새 캐시
        cache = tmp.resolve("cache2");
        Files.createDirectories(cache);
        Files.writeString(cache.resolve(LibraryResolver.LOCK_FILE), "# 손으로\na:b:1=" + sha + "\n");
        assertEquals(1, resolver(s).resolve("P", List.of(new Library("a:b:1"))).size());
    }

    @Test
    void requireChecksumRefusesUnrecordedCachedFile() throws Exception {
        byte[] jar = jarBytes("x");
        Path f = cache.resolve("a/b/1/b-1.jar");
        Files.createDirectories(f.getParent());
        Files.write(f, jar);
        assertThrows(LibraryException.class, () -> resolver(on().withRequireChecksum(true)).resolve("P", List.of(new Library("a:b:1"))));
    }

    @Test
    void offlineUsesCacheOnly() throws Exception {
        publishJar("a", "b", "1", "x");
        LibraryException e = assertThrows(LibraryException.class, () -> resolver(on().withOffline(true)).resolve("P", List.of(new Library("a:b:1"))));
        assertTrue(e.getMessage().contains("offline"), e.getMessage());
        assertTrue(requests.isEmpty());

        resolver(on()).resolve("P", List.of(new Library("a:b:1")));
        requests.clear();
        assertEquals(1, resolver(on().withOffline(true)).resolve("P", List.of(new Library("a:b:1"))).size());
        assertTrue(requests.isEmpty());
    }

    // ------------------------------------------------------------------ 전이 의존

    private LibrarySettings transitive() { return LibrarySettings.DEFAULT.withEnabled(true); }

    @Test
    void transitiveFollowsCompileAndRuntimeOnly() throws Exception {
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(
                dep("g", "compiled", "1"),
                dep("g", "run", "1", "<scope>runtime</scope>"),
                dep("g", "tst", "1", "<scope>test</scope>"),
                dep("g", "prov", "1", "<scope>provided</scope>"),
                dep("g", "opt", "1", "<optional>true</optional>"),
                dep("g", "noVersion", null))));
        for (String n : List.of("compiled", "run", "tst", "prov", "opt")) {
            publishJar("g", n, "1", n);
            publishPom("g", n, "1", pom("g", n, "1", ""));
        }
        List<Path> out = resolver(transitive()).resolve("P", List.of(new Library("g:root:1")));
        List<String> names = out.stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("root-1.jar", "compiled-1.jar", "run-1.jar"), names);
    }

    @Test
    void parentPropertiesAndManagementAreInherited() throws Exception {
        publishPom("g", "parent", "5", pom("g", "parent", "5",
                "<properties><dep.v>2.0</dep.v></properties><dependencyManagement>" + deps(dep("g", "managed", "${dep.v}")) + "</dependencyManagement>"));
        publishJar("g", "child", "5", "child");
        publishPom("g", "child", "5", "<project><parent><groupId>g</groupId><artifactId>parent</artifactId><version>5</version></parent>"
                + "<artifactId>child</artifactId>" + deps(dep("g", "managed", null), dep("g", "same", "${project.version}")) + "</project>");
        publishJar("g", "managed", "2.0", "managed");
        publishPom("g", "managed", "2.0", pom("g", "managed", "2.0", ""));
        publishJar("g", "same", "5", "same");
        publishPom("g", "same", "5", pom("g", "same", "5", ""));
        List<String> names = resolver(transitive()).resolve("P", List.of(new Library("g:child:5"))).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("child-5.jar", "managed-2.0.jar", "same-5.jar"), names);
    }

    @Test
    void parentDependenciesAreInherited() throws Exception {
        publishPom("g", "parent", "1", pom("g", "parent", "1", deps(dep("g", "common", "1"))));
        publishJar("g", "child", "1", "child");
        publishPom("g", "child", "1", "<project><parent><groupId>g</groupId><artifactId>parent</artifactId><version>1</version></parent>"
                + "<artifactId>child</artifactId></project>");
        publishJar("g", "common", "1", "common");
        publishPom("g", "common", "1", pom("g", "common", "1", ""));
        assertEquals(2, resolver(transitive()).resolve("P", List.of(new Library("g:child:1"))).size());
    }

    @Test
    void bomImportSuppliesVersions() throws Exception {
        publishPom("g", "bom", "1", pom("g", "bom", "1", "<packaging>pom</packaging><dependencyManagement>" + deps(dep("g", "part", "3.1")) + "</dependencyManagement>"));
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", "<dependencyManagement>"
                + deps(dep("g", "bom", "1", "<type>pom</type><scope>import</scope>")) + "</dependencyManagement>" + deps(dep("g", "part", null))));
        publishJar("g", "part", "3.1", "part");
        publishPom("g", "part", "3.1", pom("g", "part", "3.1", ""));
        List<String> names = resolver(transitive()).resolve("P", List.of(new Library("g:root:1"))).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("root-1.jar", "part-3.1.jar"), names);
    }

    @Test
    void pomPackagingHasNoJarButItsDependenciesAreFollowed() throws Exception {
        publishPom("g", "aggregate", "1", pom("g", "aggregate", "1", "<packaging>pom</packaging>" + deps(dep("g", "real", "1"))));
        publishJar("g", "real", "1", "real");
        publishPom("g", "real", "1", pom("g", "real", "1", ""));
        List<String> names = resolver(transitive()).resolve("P", List.of(new Library("g:aggregate:1"))).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("real-1.jar"), names);
    }

    @Test
    void exclusionsAndNearestWins() throws Exception {
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(
                dep("g", "mid", "1", "<exclusions><exclusion><groupId>g</groupId><artifactId>skipme</artifactId></exclusion></exclusions>"),
                dep("g", "shared", "2"))));
        publishJar("g", "mid", "1", "mid");
        publishPom("g", "mid", "1", pom("g", "mid", "1", deps(dep("g", "skipme", "1"), dep("g", "shared", "1"))));
        for (String[] a : new String[][]{{"skipme", "1"}, {"shared", "1"}, {"shared", "2"}}) {
            publishJar("g", a[0], a[1], a[0] + a[1]);
            publishPom("g", a[0], a[1], pom("g", a[0], a[1], ""));
        }
        List<String> names = resolver(transitive()).resolve("P", List.of(new Library("g:root:1"))).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("root-1.jar", "mid-1.jar", "shared-2.jar"), names);   // skipme 제외, shared 는 가까운 2
    }

    @Test
    void declaredVersionBeatsTransitive() throws Exception {
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(dep("g", "lib", "1"))));
        for (String v : List.of("1", "9")) {
            publishJar("g", "lib", v, "lib" + v);
            publishPom("g", "lib", v, pom("g", "lib", v, ""));
        }
        List<String> names = resolver(transitive()).resolve("P", List.of(new Library("g:root:1"), new Library("g:lib:9"))).stream()
                .map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("root-1.jar", "lib-9.jar"), names);
    }

    @Test
    void cyclesTerminate() throws Exception {
        publishJar("g", "a", "1", "a");
        publishPom("g", "a", "1", pom("g", "a", "1", deps(dep("g", "b", "1"))));
        publishJar("g", "b", "1", "b");
        publishPom("g", "b", "1", pom("g", "b", "1", deps(dep("g", "a", "1"))));
        assertEquals(2, resolver(transitive()).resolve("P", List.of(new Library("g:a:1"))).size());
    }

    @Test
    void versionRangesUseLowerBoundAndUnresolvableAreSkipped() throws Exception {
        assertEquals("1.0", LibraryResolver.lowerBound("1.0"));
        assertEquals("1.0", LibraryResolver.lowerBound("[1.0]"));
        assertEquals("1.0", LibraryResolver.lowerBound("[1.0,2.0)"));
        assertNull(LibraryResolver.lowerBound("(,2.0]"));
        assertNull(LibraryResolver.lowerBound("(1.0,2.0)"));

        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(dep("g", "ranged", "[1.0,2.0)"), dep("g", "open", "(,3.0]"), dep("g", "undefined", "${nope}"))));
        publishJar("g", "ranged", "1.0", "ranged");
        publishPom("g", "ranged", "1.0", pom("g", "ranged", "1.0", ""));
        List<String> names = resolver(transitive()).resolve("P", List.of(new Library("g:root:1"))).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of("root-1.jar", "ranged-1.0.jar"), names);
    }

    @Test
    void missingTransitiveJarIsSkippedButMissingDirectFails() throws Exception {
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(dep("g", "ghost", "1"))));
        publishPom("g", "ghost", "1", pom("g", "ghost", "1", ""));   // jar 는 없다
        assertEquals(1, resolver(transitive()).resolve("P", List.of(new Library("g:root:1"))).size());
        assertThrows(LibraryException.class, () -> resolver(transitive()).resolve("P", List.of(new Library("g:ghost:1"))));
    }

    @Test
    void transitiveOffIgnoresPoms() throws Exception {
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(dep("g", "other", "1"))));
        assertEquals(1, resolver(on()).resolve("P", List.of(new Library("g:root:1"))).size());
        assertTrue(requests.stream().noneMatch(r -> r.endsWith(".pom")), requests.toString());
    }

    @Test
    void transitiveRequiresChecksumToo() throws Exception {
        byte[] root = publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", pom("g", "root", "1", deps(dep("g", "dep", "1"))));
        publishJar("g", "dep", "1", "dep");
        publishPom("g", "dep", "1", pom("g", "dep", "1", ""));
        LibrarySettings s = transitive().withRequireChecksum(true);
        LibraryException e = assertThrows(LibraryException.class, () -> resolver(s).resolve("P", List.of(new Library("g:root:1", hex("SHA-256", root)))));
        assertTrue(e.getMessage().contains("g:dep:1"), e.getMessage());
    }

    @Test
    void pomWithDoctypeEntityDoesNotLeakFiles() throws Exception {
        Path secret = tmp.resolve("secret.txt");
        Files.writeString(secret, "TOPSECRET");
        publishJar("g", "root", "1", "root");
        publishPom("g", "root", "1", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"" + secret.toUri() + "\">]>"
                + pom("g", "root", "1", "<description>&e;</description>"));
        // 외부 엔티티는 풀리지 않는다(읽기 실패든 빈 값이든 비밀이 로그·결과에 새지 않으면 된다)
        try {
            resolver(transitive()).resolve("P", List.of(new Library("g:root:1")));
        } catch (LibraryException e) {
            assertFalse(e.getMessage().contains("TOPSECRET"));
        }
    }

    // ------------------------------------------------------------------ HttpFetcher

    @Test
    void httpFetcherHandlesStatusRetryAndSizeLimit() throws Exception {
        AtomicInteger flaky = new AtomicInteger();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/ok", ex -> reply(ex, 200, "hello".getBytes(StandardCharsets.UTF_8)));
        srv.createContext("/big", ex -> reply(ex, 200, new byte[5000]));
        srv.createContext("/flaky", ex -> reply(ex, flaky.incrementAndGet() < 3 ? 503 : 200, "late".getBytes(StandardCharsets.UTF_8)));
        srv.createContext("/denied", ex -> reply(ex, 403, new byte[0]));
        srv.start();
        try {
            String base = "http://127.0.0.1:" + srv.getAddress().getPort();
            LibraryResolver.HttpFetcher f = new LibraryResolver.HttpFetcher(5, 1);
            assertEquals("hello", new String(f.get(URI.create(base + "/ok"), 100), StandardCharsets.UTF_8));
            assertNull(f.get(URI.create(base + "/missing"), 100));
            assertThrows(IOException.class, () -> f.get(URI.create(base + "/big"), 1000));
            assertEquals("late", new String(f.get(URI.create(base + "/flaky"), 100), StandardCharsets.UTF_8));
            assertEquals(3, flaky.get());
            assertThrows(IOException.class, () -> f.get(URI.create(base + "/denied"), 100));
        } finally {
            srv.stop(0);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, byte[] body) throws IOException {
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) ex.getResponseBody().write(body);
        ex.close();
    }

    // ------------------------------------------------------------------ 서버 제공 라이브러리(고정 sha256, H4 묶음 B)

    private static final String MOJANG_REPO = "https://mojang.test/libs/";

    private LibraryResolver resolverWithMojang(LibrarySettings s) {
        return new LibraryResolver(s, cache, URI.create(REPO), URI.create(MOJANG_REPO), (uri, max) -> {
            requests.add(uri.toString());
            return remote.get(uri.toString());
        }, LOG);
    }

    @Test
    void pinnedArtifactsComeFromTheirRepositoryAndMustMatchThePin() throws Exception {
        byte[] central = jarBytes("central");
        byte[] mojang = jarBytes("mojang");
        remote.put(url("org.test", "lib", "1.0", "jar"), central);
        remote.put(MOJANG_REPO + "com/mojang/brig/1.0/brig-1.0.jar", mojang);
        LibraryResolver r = resolverWithMojang(LibrarySettings.DEFAULT);
        Path a = r.ensurePinned(new ServerLibraries.Artifact("org.test:lib:1.0", hex("SHA-256", central), ServerLibraries.Repo.CENTRAL));
        Path b = r.ensurePinned(new ServerLibraries.Artifact("com.mojang:brig:1.0", hex("SHA-256", mojang), ServerLibraries.Repo.MOJANG));
        assertArrayEquals(central, Files.readAllBytes(a));
        assertArrayEquals(mojang, Files.readAllBytes(b));
        assertEquals(List.of(url("org.test", "lib", "1.0", "jar"), MOJANG_REPO + "com/mojang/brig/1.0/brig-1.0.jar"), requests, ".sha1 은 묻지 않는다(고정값이 기준)");
        assertFalse(Files.exists(cache.resolve(LibraryResolver.LOCK_FILE)), "잠금 파일을 쓰지 않는다");

        // 캐시에 있으면 다시 받지 않는다(offline 이어도 된다)
        requests.clear();
        resolverWithMojang(LibrarySettings.DEFAULT.withOffline(true))
                .ensurePinned(new ServerLibraries.Artifact("org.test:lib:1.0", hex("SHA-256", central), ServerLibraries.Repo.CENTRAL));
        assertEquals(List.of(), requests);

        // 다른 바이트는 거부(파일도 남기지 않는다)
        remote.put(url("org.test", "evil", "1.0", "jar"), jarBytes("evil"));
        LibraryException e = assertThrows(LibraryException.class, () -> r.ensurePinned(
                new ServerLibraries.Artifact("org.test:evil:1.0", hex("SHA-256", central), ServerLibraries.Repo.CENTRAL)));
        assertTrue(e.getMessage().contains("sha256"), e.getMessage());
        assertFalse(Files.exists(cache.resolve("org/test/evil/1.0/evil-1.0.jar")));
    }

    @Test
    void pinnedArtifactsIgnoreTheLockFileAndRepairTheCache() throws Exception {
        byte[] good = jarBytes("good");
        String pin = hex("SHA-256", good);
        remote.put(url("org.test", "lib", "1.0", "jar"), good);
        Files.createDirectories(cache);
        Files.writeString(cache.resolve(LibraryResolver.LOCK_FILE), "org.test:lib:1.0=" + "0".repeat(64) + "\n");
        Path f = cache.resolve("org/test/lib/1.0/lib-1.0.jar");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "망가진 파일");
        Path got = resolverWithMojang(LibrarySettings.DEFAULT.withRequireChecksum(true))
                .ensurePinned(new ServerLibraries.Artifact("org.test:lib:1.0", pin, ServerLibraries.Repo.CENTRAL));
        assertArrayEquals(good, Files.readAllBytes(got), "망가진 캐시는 지우고 다시 받는다 — 잠금 파일·require-checksum 은 고정값 앞에서 상관없다");

        Files.writeString(f, "또 망가짐");
        LibraryException e = assertThrows(LibraryException.class, () -> resolverWithMojang(LibrarySettings.DEFAULT.withOffline(true))
                .ensurePinned(new ServerLibraries.Artifact("org.test:lib:1.0", pin, ServerLibraries.Repo.CENTRAL)));
        assertTrue(e.getMessage().contains("offline"), e.getMessage());
        LibraryResolver r = resolverWithMojang(LibrarySettings.DEFAULT);
        assertThrows(LibraryException.class, () -> r.ensurePinned(new ServerLibraries.Artifact("org.test:none:1.0", pin, ServerLibraries.Repo.MOJANG)),
                "저장소에 없으면(404) 실패");
        assertEquals(URI.create(MOJANG_REPO + "org/test/none/1.0/none-1.0.jar"),
                r.sourceOf(new ServerLibraries.Artifact("org.test:none:1.0", pin, ServerLibraries.Repo.MOJANG)));
    }

    // ------------------------------------------------------------------ 서버 jar 에 실린 라이브러리(Guava·SnakeYAML, 사용자 결정 5 정정)

    /** 서버 jar 대신: 폴더 하나를 META-INF/furnace/libraries/ 자원으로 보이는 로더. */
    private ClassLoader bundle(Map<String, byte[]> files) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("bundle-" + files.hashCode()));
        for (var e : files.entrySet()) {
            Path f = root.resolve(ServerLibraries.BUNDLE_DIR + e.getKey());
            Files.createDirectories(f.getParent());
            Files.write(f, e.getValue());
        }
        return new java.net.URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, null);
    }

    private LibraryResolver bundled(LibrarySettings s, ClassLoader source) {
        return new LibraryResolver(s, cache, URI.create(REPO), URI.create(MOJANG_REPO), (uri, max) -> {
            requests.add(uri.toString());
            return remote.get(uri.toString());
        }, source, LOG);
    }

    @Test
    void bundledArtifactsAreExtractedFromTheServerJarWithoutTheNetwork() throws Exception {
        byte[] jar = jarBytes("bundled");
        String pin = hex("SHA-256", jar);
        ServerLibraries.Artifact a = new ServerLibraries.Artifact("org.test:lib:1.0", pin, ServerLibraries.Repo.CENTRAL, true);
        assertEquals("META-INF/furnace/libraries/lib-1.0.jar", a.bundleResource());
        // offline 이고 저장소가 비어 있어도 서버 jar 에서 꺼낸다
        Path got = bundled(LibrarySettings.DEFAULT.withOffline(true), bundle(Map.of("lib-1.0.jar", jar))).ensurePinned(a);
        assertArrayEquals(jar, Files.readAllBytes(got));
        assertEquals(cache.resolve("org/test/lib/1.0/lib-1.0.jar"), got, "내려받은 것과 같은 자리(캐시 공유)");
        assertEquals(List.of(), requests, "네트워크에 나가지 않는다");
        // 망가진 캐시도 서버 jar 에서 고친다
        Files.writeString(got, "망가짐");
        assertArrayEquals(jar, Files.readAllBytes(bundled(LibrarySettings.DEFAULT.withOffline(true), bundle(Map.of("lib-1.0.jar", jar))).ensurePinned(a)));
        assertEquals(List.of(), requests);
    }

    @Test
    void bundledBytesMustMatchThePinOrTheResolverFallsBackToTheRepository() throws Exception {
        byte[] good = jarBytes("good");
        String pin = hex("SHA-256", good);
        ServerLibraries.Artifact a = new ServerLibraries.Artifact("org.test:lib:1.0", pin, ServerLibraries.Repo.CENTRAL, true);
        ClassLoader wrong = bundle(Map.of("lib-1.0.jar", jarBytes("다른 바이트")));
        assertThrows(LibraryException.class, () -> bundled(LibrarySettings.DEFAULT.withOffline(true), wrong).ensurePinned(a),
                "다른 바이트는 쓰지 않는다(offline 이면 실패)");
        assertFalse(Files.exists(cache.resolve("org/test/lib/1.0/lib-1.0.jar")));
        remote.put(url("org.test", "lib", "1.0", "jar"), good);
        assertArrayEquals(good, Files.readAllBytes(bundled(LibrarySettings.DEFAULT, wrong).ensurePinned(a)), "온라인이면 저장소에서 받는다");
        assertEquals(List.of(url("org.test", "lib", "1.0", "jar")), requests);
    }

    @Test
    void serverJarWithoutTheBundleStillDownloadsAndUnbundledArtifactsIgnoreIt() throws Exception {
        byte[] good = jarBytes("good");
        String pin = hex("SHA-256", good);
        remote.put(url("org.test", "lib", "1.0", "jar"), good);
        // 개발 실행·시험: 자원이 없으면 예전처럼 받는다
        assertArrayEquals(good, Files.readAllBytes(bundled(LibrarySettings.DEFAULT, bundle(Map.of()))
                .ensurePinned(new ServerLibraries.Artifact("org.test:lib:1.0", pin, ServerLibraries.Repo.CENTRAL, true))));
        assertEquals(1, requests.size());
        // bundled 가 아닌 것은 같은 이름의 자원이 있어도 보지 않는다(Adventure 등은 늘 받는다)
        Files.delete(cache.resolve("org/test/lib/1.0/lib-1.0.jar"));
        requests.clear();
        bundled(LibrarySettings.DEFAULT, bundle(Map.of("lib-1.0.jar", good)))
                .ensurePinned(new ServerLibraries.Artifact("org.test:lib:1.0", pin, ServerLibraries.Repo.CENTRAL));
        assertEquals(List.of(url("org.test", "lib", "1.0", "jar")), requests);
    }

    @Test
    void onlyGuavaAndSnakeYamlAreBundled() {
        java.util.Set<String> bundledCoords = new java.util.TreeSet<>();
        for (ServerLibraries.Group g : ServerLibraries.DEFAULT.groups()) {
            for (ServerLibraries.Artifact a : g.artifacts()) if (a.bundled()) bundledCoords.add(a.coordinate());
            if (g.id().equals("guava") || g.id().equals("snakeyaml")) assertTrue(g.bundled(), g.id());
            else assertFalse(g.bundled(), g.id() + " 는 필요할 때 받는다");
        }
        assertEquals(java.util.Set.of("com.google.guava:guava:32.1.2-jre", "com.google.guava:failureaccess:1.0.1", "org.yaml:snakeyaml:2.2"), bundledCoords,
                "서버 jar 에 싣는 것은 Guava·failureaccess·SnakeYAML 뿐(Adventure 는 받는다, 2026-11-12)");
    }

    @Test
    void anUnreachableHostIsNotRetriedForTheRestOfTheStart() throws Exception {
        byte[] good = jarBytes("good");
        String pin = hex("SHA-256", good);
        LibraryResolver r = new LibraryResolver(LibrarySettings.DEFAULT, cache, URI.create(REPO), URI.create(MOJANG_REPO), (uri, max) -> {
            requests.add(uri.toString());
            if (uri.toString().startsWith(MOJANG_REPO)) throw new java.net.ConnectException("연결 거부");
            return good;
        }, LOG);
        LibraryException first = assertThrows(LibraryException.class,
                () -> r.ensurePinned(new ServerLibraries.Artifact("com.mojang:a:1.0", pin, ServerLibraries.Repo.MOJANG)));
        assertFalse(first instanceof LibraryResolver.Unreachable, "처음 실패는 진짜 이유를 보인다");
        assertThrows(LibraryResolver.Unreachable.class,
                () -> r.ensurePinned(new ServerLibraries.Artifact("com.mojang:b:1.0", pin, ServerLibraries.Repo.MOJANG)));
        assertEquals(1, requests.stream().filter(u -> u.startsWith(MOJANG_REPO)).count(), "같은 호스트는 다시 묻지 않는다(제한 시간을 묶음마다 기다리지 않는다)");
        assertNotNull(r.ensurePinned(new ServerLibraries.Artifact("org.test:c:1.0", pin, ServerLibraries.Repo.CENTRAL)), "다른 저장소는 그대로 받는다");
    }

    @Test
    void anHttpErrorDoesNotMarkTheHostDown() throws Exception {
        byte[] good = jarBytes("good");
        String pin = hex("SHA-256", good);
        LibraryResolver r = new LibraryResolver(LibrarySettings.DEFAULT, cache, URI.create(REPO), URI.create(MOJANG_REPO), (uri, max) -> {
            requests.add(uri.toString());
            if (uri.toString().contains("/big/")) throw new LibraryResolver.HttpStatusException("크기 상한");
            return good;
        }, LOG);
        assertThrows(LibraryException.class, () -> r.ensurePinned(new ServerLibraries.Artifact("org.test:big:1.0", pin, ServerLibraries.Repo.CENTRAL)));
        assertNotNull(r.ensurePinned(new ServerLibraries.Artifact("org.test:ok:1.0", pin, ServerLibraries.Repo.CENTRAL)));
    }
}
