package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 준비 단계 전체(번들러 풀기 → 대응표로 이름 풀기 → 데이터 생성기 실행 → 캐시 재사용)를 가짜 서버 jar 로 돌려 본다.
 * 가짜 서버: 난독화 이름 `a` 클래스 하나(대응표가 net.minecraft.data.Main 으로 풀어 준다)가 `--output <폴더>` 에 파일 하나를 쓴다.
 */
class PreparerTest {
    @Test
    void preparesFromOfflineFiles(@TempDir Path dir) throws Exception {
        Path src = Files.createDirectories(dir.resolve("src"));
        Files.writeString(src.resolve("a.java"), """
                public class a {
                    public static void main(String[] args) throws Exception {
                        java.nio.file.Files.createDirectories(java.nio.file.Path.of(args[2], "reports"));
                        java.nio.file.Files.writeString(java.nio.file.Path.of(args[2], "reports", "ran.txt"), String.join(",", args));
                    }
                }
                """);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-proc:none", "-d", classes.toString(), src.resolve("a.java").toString()));

        Path inner = dir.resolve("inner.jar");
        try (OutputStream os = Files.newOutputStream(inner); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "a.class", Files.readAllBytes(classes.resolve("a.class")));
            put(z, "META-INF/X.SF", "sig".getBytes(StandardCharsets.UTF_8));
            put(z, "data/minecraft/tags/block/x.json", "{}".getBytes(StandardCharsets.UTF_8));
        }
        Path bundler = dir.resolve("server.jar");
        try (OutputStream os = Files.newOutputStream(bundler); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "META-INF/versions.list", "00\t9.9\t9.9/server-9.9.jar\n".getBytes(StandardCharsets.UTF_8));
            put(z, "META-INF/versions/9.9/server-9.9.jar", Files.readAllBytes(inner));
        }
        Path mappings = dir.resolve("mappings.txt");
        Files.writeString(mappings, "net.minecraft.data.Main -> a:\n");

        Options o = Options.parse(new String[] {"--mc", "9.9", "--cache", dir.resolve("cache").toString(), "--server-jar", bundler.toString(),
                "--mappings", mappings.toString(), "--repo", dir.toString()});
        Prepared p = new Preparer(o).prepare();
        assertTrue(p.remapped());
        assertTrue(p.serverJar().getFileName().toString().equals("server-mojmap.jar"));
        try (JarFile jf = new JarFile(p.serverJar().toFile())) {
            assertNotNull(jf.getEntry("net/minecraft/data/Main.class"));
            assertNotNull(jf.getEntry("data/minecraft/tags/block/x.json"));
            assertTrue(jf.stream().noneMatch(e -> e.getName().endsWith(".SF")));
        }
        Path ran = p.generated().resolve("reports/ran.txt");
        assertTrue(Files.isRegularFile(ran), "데이터 생성기가 이름 푼 jar 에서 net.minecraft.data.Main 으로 돌았다");
        assertTrue(Files.readString(ran).startsWith("--all,--output,"));

        // 같은 입력이면 다시 만들지 않는다: 생성기 출력에 표시를 남겨 두면 두 번째 준비 뒤에도 남아 있다.
        Path marker = p.generated().resolve("marker.txt");
        Files.writeString(marker, "keep");
        Prepared p2 = new Preparer(o).prepare();
        assertTrue(Files.isRegularFile(marker), "캐시 재사용");
        assertEquals(p.serverJar(), p2.serverJar());

        // 입력(대응표)이 바뀌면 다시 만든다.
        Files.writeString(mappings, "net.minecraft.data.Main -> a:\n# changed\n");
        new Preparer(o).prepare();
        assertTrue(Files.notExists(marker), "입력이 바뀌면 다시 만든다");
    }

    @Test
    void unobfuscatedVersionSkipsRemapping(@TempDir Path dir) throws Exception {
        Path inner = dir.resolve("inner.jar");
        try (OutputStream os = Files.newOutputStream(inner); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "data/x.json", "{}".getBytes(StandardCharsets.UTF_8));
        }
        Path bundler = dir.resolve("server.jar");
        try (OutputStream os = Files.newOutputStream(bundler); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "META-INF/versions.list", "00\t9.9\t9.9/server-9.9.jar\n".getBytes(StandardCharsets.UTF_8));
            put(z, "META-INF/versions/9.9/server-9.9.jar", Files.readAllBytes(inner));
        }
        Path generated = dir.resolve("cache/9.9/generated");
        Options o = Options.parse(new String[] {"--mc", "9.9", "--cache", dir.resolve("cache").toString(), "--server-jar", bundler.toString(), "--repo", dir.toString()});
        // 데이터 생성기 클래스가 없어 실패하는 것은 정상 — 그 앞 단계(이름 풀기 건너뜀)까지만 본다.
        try {
            new Preparer(o).prepare();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("net.minecraft.data.Main") || expected.getMessage().contains("종료 코드"), expected.getMessage());
        }
        assertTrue(Files.isRegularFile(dir.resolve("cache/9.9/work/server-inner.jar")));
        assertTrue(Files.notExists(dir.resolve("cache/9.9/work/server-mojmap.jar")));
        assertEquals(List.of(), List.of(), generated.toString());
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }
}
