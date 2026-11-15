package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmallPartsTest {
    static final String VERSION_JSON = """
            {"id":"1.21.1","protocol_version":767,"javaVersion":{"component":"java-runtime-delta","majorVersion":21},
             "downloads":{"server":{"sha1":"aaa","size":1,"url":"https://x/server.jar"},
                          "server_mappings":{"sha1":"bbb","size":2,"url":"https://x/server.txt"}}}""";
    static final String VERSION_JSON_UNOBFUSCATED = """
            {"id":"26.1","downloads":{"server":{"sha1":"ccc","size":1,"url":"https://x/s.jar"}}}""";

    @Test
    void versionInfoReadsMappingsWhenPresent() {
        VersionInfo v = VersionInfo.parse(VERSION_JSON);
        assertEquals("1.21.1", v.id());
        assertEquals(767, v.protocol());
        assertEquals(21, v.javaMajor());
        assertEquals("https://x/server.txt", v.mappings().orElseThrow().url());
        VersionInfo u = VersionInfo.parse(VERSION_JSON_UNOBFUSCATED);
        assertTrue(u.mappings().isEmpty(), "대응표 없는 판 = 난독화 없음");
        assertEquals(-1, u.protocol());
        assertThrows(IllegalArgumentException.class, () -> VersionInfo.parse("{\"id\":\"x\",\"downloads\":{}}"));
    }

    @Test
    void manifestLookup() {
        String m = "{\"versions\":[{\"id\":\"1.21.2\",\"url\":\"u2\"},{\"id\":\"1.21.1\",\"url\":\"u1\"}]}";
        assertEquals("u1", VersionInfo.manifestUrl(m, "1.21.1").orElseThrow());
        assertTrue(VersionInfo.manifestUrl(m, "9.9").isEmpty());
    }

    @Test
    void bundleUnpacksInnerJarAndLibraries(@TempDir Path dir) throws IOException {
        Path bundler = dir.resolve("server.jar");
        try (OutputStream os = Files.newOutputStream(bundler); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "META-INF/versions.list", "deadbeef\t1.21.1\t1.21.1/server-1.21.1.jar\n");
            put(z, "META-INF/versions/1.21.1/server-1.21.1.jar", "INNER");
            put(z, "META-INF/libraries/com/google/guava/guava/33/guava-33.jar", "G");
            put(z, "META-INF/libraries/io/netty/netty/4/netty-4.jar", "N");
            put(z, "META-INF/libraries.list", "ignored");
        }
        ServerBundle.Unpacked u = ServerBundle.unpack(bundler, dir.resolve("work"));
        assertEquals("INNER", Files.readString(u.serverJar()));
        assertEquals(2, u.libraries().size());
        assertTrue(u.libraries().get(0).toString().endsWith("guava-33.jar"));
    }

    @Test
    void bundleRejectsPlainJar(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("plain.jar");
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream jo = new JarOutputStream(os)) {
            jo.putNextEntry(new JarEntry("a.class"));
            jo.closeEntry();
        }
        assertThrows(IOException.class, () -> ServerBundle.unpack(jar, dir.resolve("w")));
    }

    @Test
    void verifierComparesGzipByContent(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("a.txt");
        Files.writeString(plain, "x\ny\n");
        Path same = dir.resolve("b.txt");
        Files.writeString(same, "x\ny\n");
        assertEquals(Verifier.Kind.IDENTICAL, Verifier.compare(plain, same).kind());
        Files.writeString(same, "x\nz\n");
        Verifier.Outcome diff = Verifier.compare(plain, same);
        assertEquals(Verifier.Kind.DIFFERENT, diff.kind());
        assertTrue(diff.detail().contains("줄 2"), diff.detail());
        assertEquals(Verifier.Kind.MISSING_COMMITTED, Verifier.compare(plain, dir.resolve("none")).kind());

        Path gz1 = dir.resolve("1.gz"), gz2 = dir.resolve("2.gz");
        Runner.gzip(plain, gz1);
        // 다른 헤더(시각 있음)로 같은 내용을 압축
        try (OutputStream os = Files.newOutputStream(gz2); var g = new java.util.zip.GZIPOutputStream(os)) {
            g.write("x\ny\n".getBytes(StandardCharsets.UTF_8));
        }
        byte[] b2 = Files.readAllBytes(gz2);
        b2[4] = 1; b2[5] = 2; b2[6] = 3; b2[7] = 4;   // MTIME 을 바꿔 압축 바이트를 다르게
        Files.write(gz2, b2);
        Verifier.Outcome o = Verifier.compare(gz1, gz2);
        assertEquals(Verifier.Kind.IDENTICAL_UNZIPPED, o.kind());
        assertTrue(o.ok());
    }

    @Test
    void gzipIsDeterministic(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("p.txt");
        Files.writeString(plain, "hello\n".repeat(100));
        Runner.gzip(plain, dir.resolve("a.gz"));
        Runner.gzip(plain, dir.resolve("b.gz"));
        assertTrue(java.util.Arrays.equals(Files.readAllBytes(dir.resolve("a.gz")), Files.readAllBytes(dir.resolve("b.gz"))));
        assertEquals(dir.resolve("p.txt"), Runner.stripGz(dir.resolve("p.txt.gz")));
    }

    @Test
    void optionsParse() {
        Options o = Options.parse(new String[] {"--mc", "1.21.2", "--mode", "verify", "--tools", "a,b", "--tool-arg", "shape-vectors=x", "--tool-arg", "shape-vectors=y", "--protocol", "768"});
        assertEquals("1.21.2", o.mc);
        assertEquals(Options.Mode.VERIFY, o.mode);
        assertEquals(Options.Mode.PORT_CHECK, Options.parse(new String[] {"--mc", "1", "--mode", "port-check"}).mode);
        assertEquals(List.of("a", "b"), o.tools);
        assertEquals(List.of("x", "y"), o.toolArgs.get("shape-vectors"));
        assertEquals(768, o.protocol);
        assertTrue(o.out.endsWith(Path.of("1.21.2")));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {}));
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {"--mc", "1", "--nope"}));
        assertTrue(Options.parse(new String[] {"--list"}).list);
    }

    @Test
    void selectsDefaultToolsOnly() {
        Options o = Options.parse(new String[] {"--mc", "1.21.1"});
        List<Tool> sel = Main.select(o);
        assertTrue(sel.stream().noneMatch(t -> t.id().equals("shape-vectors")));
        assertTrue(sel.stream().anyMatch(t -> t.id().equals("block-attrs")));
        assertThrows(IllegalArgumentException.class, () -> Main.select(Options.parse(new String[] {"--mc", "1", "--tools", "nope"})));
    }

    private static void put(ZipOutputStream z, String name, String body) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(body.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
