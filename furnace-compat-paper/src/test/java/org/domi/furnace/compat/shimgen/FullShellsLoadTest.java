package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-260: 실제 서명표로 만든 전체 껍데기(furnace-compat-shells.jar)를 실행 때와 같은 따로 된 로더(호환 jar → 껍데기 jar → 호환 실행 의존 →
 * 서버 제공 라이브러리 → 바닐라 라이브러리)에 전부 올리고, 초기화까지 해 본다. 크기·시간은 build/full-shells/load-report.md 에 쓴다.
 * 서명표 캐시가 있어야 해서 일반 시험에서는 빠지고 {@code :furnace-compat-paper:fullShellsTest}(CI vanilla-gates)가 돈다.
 *
 * <p>합격 기준: 올리기(정의·상위 해석·final 재정의 검사)에서 연결 오류 0. 초기화는 껍데기 자신의 정적 초기화(열거형 상수)가 오류 0 —
 * 손 shim 상위의 정적 초기화가 서버 없이 실패하는 것은 껍데기 탓이 아니므로 따로 세어 보고만 한다(그 상위를 가진 것 수).
 */
@Tag("full-shells")
class FullShellsLoadTest {

    @Test
    void everyShellLoadsAndInitializes() throws Exception {
        Path jar = Path.of(System.getProperty("furnace.fullShells.jar"));
        Path report = Path.of(System.getProperty("furnace.fullShells.report"));
        List<URL> urls = new ArrayList<>();
        for (String p : System.getProperty("furnace.fullShells.classpath").split(File.pathSeparator)) if (!p.isBlank()) urls.add(Path.of(p).toUri().toURL());

        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (JarEntry e : java.util.Collections.list(jf.entries())) {
                if (e.getName().endsWith(".class")) names.add(e.getName().substring(0, e.getName().length() - 6).replace('/', '.'));
            }
        }
        assertTrue(names.size() > 5000, "expected thousands of shells, got " + names.size());
        long jarBytes = Files.size(jar);

        Map<String, List<String>> loadErrors = new TreeMap<>();
        Map<String, List<String>> initErrors = new TreeMap<>();
        int initBlockedByHandSuper = 0;
        int initTrapped = 0;
        long t0, loadNanos, initNanos;
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            t0 = System.nanoTime();
            List<Class<?>> loaded = new ArrayList<>();
            for (String n : names) {
                try {
                    loaded.add(Class.forName(n, false, cl));
                } catch (Throwable t) {
                    loadErrors.computeIfAbsent(t.getClass().getSimpleName(), k -> new ArrayList<>()).add(n + " - " + t.getMessage());
                }
            }
            loadNanos = System.nanoTime() - t0;
            t0 = System.nanoTime();
            for (Class<?> c : loaded) {
                try {
                    Class.forName(c.getName(), true, cl);
                } catch (Throwable t) {
                    Throwable root = t;
                    while (root.getCause() != null) root = root.getCause();
                    // 검증·형식·연결 오류는 상위 정적 초기화 탓일 수 없다 — 늘 오류로 센다
                    boolean linkage = t instanceof VerifyError || t instanceof ClassFormatError || t instanceof IncompatibleClassChangeError;
                    if (!linkage && hasInitTrap(c, cl, new java.util.HashSet<>())) initTrapped++;   // NMS 밖 껍데기의 의도된 초기화 덫(값 없는 필드)
                    // 원인이 손 shim(껍데기 jar 밖) 상위의 정적 초기화면 따로 센다
                    else if (!linkage && fromHandSuper(c, jar, cl)) initBlockedByHandSuper++;
                    else initErrors.computeIfAbsent(t.getClass().getSimpleName() + "/" + root.getClass().getSimpleName(), k -> new ArrayList<>())
                            .add(c.getName() + " - " + root.getMessage());
                }
            }
            initNanos = System.nanoTime() - t0;
        }

        StringBuilder sb = new StringBuilder("# T-260 full shells load report (generated; contains names - do not commit)\n\n");
        int head0 = sb.length();
        sb.append("shells: ").append(names.size()).append("\njar bytes: ").append(jarBytes).append(" (").append(String.format("%.1f", jarBytes / 1048576.0)).append(" MiB)\n");
        sb.append("load all (define + resolve supertypes, no init): ").append(loadNanos / 1_000_000).append(" ms\n");
        sb.append("initialize all loaded: ").append(initNanos / 1_000_000).append(" ms\n");
        sb.append("init failures caused by a hand-shim superclass static init (needs a running server): ").append(initBlockedByHandSuper).append('\n');
        sb.append("intended init traps (non-NMS shells with fields that have no value): ").append(initTrapped).append('\n');
        // 표준 출력(CI 로그)에는 숫자만 — 클래스 이름은 보고서 파일에만 둔다
        System.out.println("[full-shells] " + sb.substring(head0).replace('\n', ';') + " load errors=" + loadErrors.size() + " init errors=" + initErrors.size());
        dump(sb, "load errors", loadErrors);
        dump(sb, "init errors", initErrors);
        Files.createDirectories(report.getParent());
        Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);

        assertEquals(Map.of(), loadErrors, "every shell must load (define + resolve its supertypes)");
        assertEquals(Map.of(), initErrors, "shell static init (enum constants) must not fail");
    }

    /**
     * 의도된 초기화 덫인가: 이 클래스나 상위 형식(클래스·인터페이스, 초기화가 따라 올라가는 곳)의 정적 초기화가 {@code NmsShell.unsupportedClass} 를 부른다.
     * 클래스 파일만 본다(실패 메시지는 두 번째부터 원인이 바뀌어 믿을 수 없다).
     */
    private static boolean hasInitTrap(Class<?> c, ClassLoader cl, java.util.Set<Class<?>> seen) {
        if (c == null || c == Object.class || !seen.add(c) || c.getName().startsWith("java.")) return false;
        try (java.io.InputStream in = cl.getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
            if (in != null && new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains("unsupportedClass")) return true;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        if (hasInitTrap(c.getSuperclass(), cl, seen)) return true;
        for (Class<?> i : c.getInterfaces()) if (hasInitTrap(i, cl, seen)) return true;
        return false;
    }

    /** 이 클래스의 상위 사슬에 껍데기 jar 밖(손 shim) 클래스가 있나. */
    private static boolean fromHandSuper(Class<?> c, Path shellJar, ClassLoader cl) {
        for (Class<?> s = c.getSuperclass(); s != null && s != Object.class; s = s.getSuperclass()) {
            if (s.getName().startsWith("java.")) return false;
            URL u = cl.getResource(s.getName().replace('.', '/') + ".class");
            if (u != null && !u.toString().contains(shellJar.getFileName().toString())) return true;
        }
        return false;
    }

    private static void dump(StringBuilder sb, String title, Map<String, List<String>> m) {
        sb.append("\n## ").append(title).append(" (").append(m.values().stream().mapToInt(List::size).sum()).append(")\n");
        m.forEach((k, v) -> {
            sb.append("### ").append(k).append(" (").append(v.size()).append(")\n");
            v.stream().limit(30).forEach(x -> sb.append(x).append('\n'));
        });
    }
}
