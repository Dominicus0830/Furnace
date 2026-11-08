package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 격리 약속: Bukkit·Paper 플러그인이 없으면 호환 모듈(org.bukkit.*, org.domi.furnace.compat.*, ASM)의 클래스는 JVM 에 한 개도 올라오지 않고,
 * 호환 모듈이 클래스패스에 아예 없어도 서버는 똑같이 돈다. 별도 JVM 을 {@code -Xlog:class+load} 로 띄워 실제로 올라온 클래스를 센다
 * (같은 JVM 의 다른 시험이 이미 올린 클래스에 흔들리지 않는다).
 */
class CompatIsolationTest {

    /** 호환 모듈이 가진 패키지 접두(플러그인 API 클래스·어댑터·바이트코드 변환기). */
    private static final List<String> COMPAT_PREFIXES = List.of(
            "org.bukkit.", "io.papermc.", "com.destroystokyo.", "net.md_5.", "org.domi.furnace.compat.", "org.objectweb.asm.");

    @TempDir
    Path tmp;

    private record Run(int exit, String output, List<String> loaded) {
        List<String> compatClasses() {
            return loaded.stream().filter(n -> COMPAT_PREFIXES.stream().anyMatch(n::startsWith)).toList();
        }
    }

    /** classpathFilter 가 true 인 항목만 남긴 클래스패스로 {@link org.domi.furnace.isolationcheck.IsolationMain} 을 새 JVM 에서 돌린다. */
    private Run run(Path serverDir, java.util.function.Predicate<String> classpathFilter) throws Exception {
        List<String> cp = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) if (classpathFilter.test(e)) cp.add(e);
        Path classLog = tmp.resolve("class-load-" + System.nanoTime() + ".log");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> cmd = List.of(java.toString(), "-Xmx512m", "-Xlog:class+load=info:file=" + classLog,
                "-cp", String.join(File.pathSeparator, cp), "org.domi.furnace.isolationcheck.IsolationMain", serverDir.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("자식 JVM 이 끝나지 않는다:\n" + new String(out, StandardCharsets.UTF_8));
        }
        List<String> loaded = new ArrayList<>();
        Pattern line = Pattern.compile("\\] (\\S+) source:");
        for (String l : Files.readAllLines(classLog, StandardCharsets.UTF_8)) {
            Matcher m = line.matcher(l);
            if (m.find()) loaded.add(m.group(1));
        }
        return new Run(p.exitValue(), new String(out, StandardCharsets.UTF_8), loaded);
    }

    private static boolean isCompatModuleEntry(String entry) {
        String e = entry.replace('\\', '/');
        boolean compatMain = e.contains("furnace-compat-paper/build/classes/java/main") || e.contains("furnace-compat-paper/build/resources/main")
                || e.contains("furnace-compat-paper/build/libs/");
        boolean asm = e.contains("/org.ow2.asm/") || e.matches(".*/asm(-[a-z]+)?-\\d.*\\.jar");
        return compatMain || asm;
    }

    private Path bukkitJar(Path dir) throws Exception {
        return Jars.bukkit(dir.resolve("plugins").resolve("bukkit-plugins").resolve("recorder.jar"), """
                name: Recorder
                version: 1.0
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, java.util.Map.of(), RecorderPlugin.class);
    }

    @Test
    void sanityTheCompatModuleIsOnThisTestsClasspath() throws Exception {
        assertTrue(getClass().getClassLoader().getResource("META-INF/services/org.domi.furnace.api.plugin.PluginFormat") != null);
        assertTrue(java.util.Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).anyMatch(CompatIsolationTest::isCompatModuleEntry),
                "필터가 호환 모듈 클래스패스 항목을 찾지 못한다 — 아래 격리 시험이 아무것도 거르지 않는다");
    }

    @Test
    void noBukkitPluginMeansNoCompatClassIsEverLoaded() throws Exception {
        Run r = run(Files.createDirectories(tmp.resolve("s1")), e -> true);   // 호환 모듈이 클래스패스에 있다
        assertEquals(0, r.exit(), r.output());
        assertTrue(r.output().contains("RESULT players=1 tpsOk=true plugins=[]"), r.output());
        assertTrue(r.loaded().stream().anyMatch(n -> n.equals("org.domi.furnace.server.plugin.PluginBootstrapper")), "기록이 서버 클래스를 담고 있어야 의미가 있다");
        assertEquals(List.of(), r.compatClasses(), "Bukkit·Paper 플러그인이 없는데 호환 모듈 클래스가 올라왔다");
    }

    @Test
    void anEmptyBukkitFolderAndAFurnaceOnlyServerStillLoadNothingFromTheCompatModule() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("s2"));
        Files.createDirectories(dir.resolve("plugins").resolve("bukkit-plugins"));
        Run r = run(dir, e -> true);
        assertEquals(0, r.exit(), r.output());
        assertEquals(List.of(), r.compatClasses());
    }

    @Test
    void aBukkitPluginDoesLoadTheCompatModule_detectorControl() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("s3"));
        bukkitJar(dir);
        Run r = run(dir, e -> true);
        assertEquals(0, r.exit(), r.output());
        assertTrue(r.output().contains("plugins=[Recorder]"), r.output());
        assertTrue(r.compatClasses().contains("org.domi.furnace.compat.paper.CompatFormat"), "대조군: 플러그인이 있으면 어댑터가 올라와야 한다");
        assertTrue(r.compatClasses().contains("org.bukkit.Bukkit"));
    }

    @Test
    void theServerRunsIdenticallyWhenTheCompatModuleIsNotOnTheClasspathAtAll() throws Exception {
        Run with = run(Files.createDirectories(tmp.resolve("s4")), e -> true);
        Run without = run(Files.createDirectories(tmp.resolve("s5")), e -> !isCompatModuleEntry(e));
        assertEquals(0, without.exit(), without.output());
        assertEquals(result(with.output()), result(without.output()), "호환 모듈 유무로 서버의 관측 결과가 달라진다");
        assertEquals(List.of(), without.compatClasses());
        assertFalse(without.output().contains("Exception"), without.output());
    }

    @Test
    void aBukkitJarWithoutTheCompatModuleIsSkippedWithAWarningNotACrash() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("s6"));
        bukkitJar(dir);
        Run r = run(dir, e -> !isCompatModuleEntry(e));
        assertEquals(0, r.exit(), r.output());
        assertTrue(r.output().contains("plugins=[]"), r.output());
        assertTrue(r.output().contains("클래스패스에 없어"), "호환 모듈이 없어 Bukkit 플러그인을 못 불러온다는 안내가 있어야 한다:\n" + r.output());
        assertEquals(List.of(), r.compatClasses());
    }

    private static String result(String output) {
        return output.lines().filter(l -> l.startsWith("RESULT")).reduce("", (a, b) -> a + b + "\n");
    }
}
