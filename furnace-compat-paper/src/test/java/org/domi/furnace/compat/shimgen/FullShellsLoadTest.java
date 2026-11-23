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
 * T-260: 실제 서명표로 만든 전체 껍데기(furnace-compat-shells.jar)를 실행 때와 같은 따로 된 로더(껍데기 jar → 호환 jar → 호환 실행 의존 →
 * 서버 제공 라이브러리 → 바닐라 라이브러리)에 전부 올리고, 초기화까지 해 본다. 크기·시간은 build/full-shells/load-report.md 에 쓴다.
 * 서명표 캐시가 있어야 해서 일반 시험에서는 빠지고 {@code :furnace-compat-paper:fullShellsTest}(CI vanilla-gates)가 돈다.
 *
 * <p>합격 기준: 올리기(정의·상위 해석·final 재정의 검사)에서 연결 오류 0. 초기화는 껍데기 자신의 정적 초기화(열거형 상수)가 오류 0 —
 * 손 shim 상위의 정적 초기화가 서버 없이 실패하는 것은 껍데기 탓이 아니므로 따로 세어 보고만 한다(그 상위를 가진 것 수).
 * T-261 로 빠진 멤버를 합친 손 shim(같은 jar, 목록 {@code merged-classes.txt})도 올린다 — 검증·연결 오류는 늘 실패, 자기 정적 초기화가 서버 없이
 * 실패하는 것은 껍데기 탓이 아니므로 따로 센다. 합친 클래스가 정말 호환 jar 의 원래 클래스를 이기는지(로더 순서)도 본다.
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
        java.util.Set<String> merged = new java.util.HashSet<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            JarEntry me = jf.getJarEntry("META-INF/furnace/full-shells/merged-classes.txt");
            assertTrue(me != null, "merged class list missing (T-261)");
            for (String l : new String(jf.getInputStream(me).readAllBytes(), StandardCharsets.UTF_8).split("\n")) if (!l.isBlank()) merged.add(l.replace('/', '.'));
        }
        assertTrue(merged.size() > 1000, "expected the hand shims to get their missing members, got " + merged.size());
        // 껍데기 jar 가 로더 맨 앞이다 — 합친 목록 밖의 클래스가 호환 jar 에도 있으면 손 shim 이 조용히 가려진다(T-261 검토)
        List<String> shadowing = new ArrayList<>();
        try (JarFile compat = new JarFile(System.getProperty("furnace.fullShells.compatJar"))) {
            for (String n : names) if (!merged.contains(n) && compat.getJarEntry(n.replace('.', '/') + ".class") != null) shadowing.add(n);
        }
        assertEquals(List.of(), shadowing, "classes in both the shells jar and the compat jar must be merged hand shims");
        long jarBytes = Files.size(jar);

        Map<String, List<String>> loadErrors = new TreeMap<>();
        Map<String, List<String>> initErrors = new TreeMap<>();
        int initBlockedByHandSuper = 0;
        int mergedInitNeedsServer = 0;
        int mergedNotWinning = 0;
        int initTrapped = 0;
        long t0, loadNanos, initNanos;
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            t0 = System.nanoTime();
            List<Class<?>> loaded = new ArrayList<>();
            for (String n : names) {
                try {
                    Class<?> c = Class.forName(n, false, cl);
                    loaded.add(c);
                    // 합친 클래스는 껍데기 jar 에서 와야 한다(호환 jar 의 원래 클래스를 이긴다)
                    if (merged.contains(n) && !c.getProtectionDomain().getCodeSource().getLocation().toString().endsWith(jar.getFileName().toString())) mergedNotWinning++;
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
                    if (!linkage && merged.contains(c.getName())) mergedInitNeedsServer++;   // 손 shim 자신의 정적 초기화(서버 필요) — 합친 멤버 탓이 아니다
                    else if (!linkage && hasInitTrap(c, cl, new java.util.HashSet<>())) initTrapped++;   // NMS 밖 껍데기의 의도된 초기화 덫(값 없는 필드)
                    // 원인이 손 shim(껍데기 jar 밖) 상위의 정적 초기화면 따로 센다
                    else if (!linkage && fromHandSuper(c, jar, cl, merged)) initBlockedByHandSuper++;
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
        sb.append("merged hand shims (T-261): ").append(merged.size()).append(", own static init needs a running server: ").append(mergedInitNeedsServer)
                .append(", not served from the shells jar: ").append(mergedNotWinning).append('\n');
        sb.append("intended init traps (non-NMS shells with fields that have no value): ").append(initTrapped).append('\n');
        // 표준 출력(CI 로그)에는 숫자만 — 클래스 이름은 보고서 파일에만 둔다
        System.out.println("[full-shells] " + sb.substring(head0).replace('\n', ';') + " load errors=" + loadErrors.size() + " init errors=" + initErrors.size());
        dump(sb, "load errors", loadErrors);
        dump(sb, "init errors", initErrors);
        Files.createDirectories(report.getParent());
        Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);

        assertEquals(Map.of(), loadErrors, "every shell must load (define + resolve its supertypes)");
        assertEquals(Map.of(), initErrors, "shell static init (enum constants) must not fail");
        assertEquals(0, mergedNotWinning, "merged hand shims must win over the originals in the compat jar (shells jar first)");
    }

    /**
     * 엔티티 데이터 설계 D3: 합치기가 손 shim 에 더한 정적 칸 필드(EntityDataAccessor)가 클래스 초기화 뒤 칸 번호표(entity_data/ids 의 A 줄)와 같은
     * 칸 번호를 갖는다. 이름은 지문으로만 견준다(바닐라 private 이름을 시험에 적지 않는다). 정적 초기화가 서버 없이 실패하는 손 shim 은 셀 수 없어 빼고 센다.
     */
    @Test
    void entityDataSlotFieldsMatchTheIdTable() throws Exception {
        Path jar = Path.of(System.getProperty("furnace.fullShells.jar"));
        List<URL> urls = new ArrayList<>();
        for (String p : System.getProperty("furnace.fullShells.classpath").split(File.pathSeparator)) if (!p.isBlank()) urls.add(Path.of(p).toUri().toURL());
        java.util.Set<String> merged = new java.util.TreeSet<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (String l : new String(jf.getInputStream(jf.getJarEntry("META-INF/furnace/full-shells/merged-classes.txt")).readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!l.isBlank()) merged.add(l.replace('/', '.'));
            }
        }
        int checked = 0, notInitialized = 0;
        List<String> wrong = new ArrayList<>();
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            var res = cl.getResourceAsStream("entity_data/ids-1.21.1.txt");
            assertTrue(res != null, "entity data id table missing from the loader");
            // shim 에 값 종류 상수가 없는 칸(JOML 값 — VECTOR3·QUATERNION)은 생성기가 채우지 않는다(초기화가 실패하지 않게) — 견주지 않는다
            java.util.Set<String> kinds = new java.util.HashSet<>();
            for (java.lang.reflect.Field f : Class.forName("net.minecraft.network.syncher.EntityDataSerializers", true, cl).getDeclaredFields()) {
                // 합친 껍데기 필드(값 없음)는 상수가 아니다
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && java.lang.reflect.Modifier.isPublic(f.getModifiers()) && f.get(null) != null) kinds.add(f.getName());
            }
            Map<String, Integer> slots = new java.util.HashMap<>();
            int unsupported = 0;
            for (String l : new String(res.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] f = l.trim().split(" ");
                if (f.length != 5 || !f[0].equals("A")) continue;
                if (kinds.contains(f[4])) slots.put(f[1] + " " + f[2], Integer.parseInt(f[3]));
                else unsupported++;
            }
            System.out.println("[full-shells] entity data slots with a value kind the shim lacks (left empty): " + unsupported);
            // 지금 비는 칸은 JOML 값(VECTOR3·QUATERNION) 4칸뿐 — 값 종류를 더하거나 빼면 이 숫자를 함께 고친다(검토 권장)
            assertEquals(4, unsupported, "entity data slots left empty because the shim lacks their value kind");
            Class<?> accessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor", false, cl);
            java.lang.reflect.Method id = accessor.getMethod("id");
            for (String n : merged) {
                Class<?> c = Class.forName(n, false, cl);
                List<java.lang.reflect.Field> fs = new ArrayList<>();
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == accessor
                            && slots.containsKey(n + " " + Links.fingerprint(n.replace('.', '/'), f.getName()).substring(1))) fs.add(f);
                }
                if (fs.isEmpty()) continue;
                try {
                    Class.forName(n, true, cl);
                } catch (Throwable t) {
                    notInitialized++;
                    continue;
                }
                for (java.lang.reflect.Field f : fs) {
                    f.setAccessible(true);
                    Object v = f.get(null);
                    int want = slots.get(n + " " + Links.fingerprint(n.replace('.', '/'), f.getName()).substring(1));
                    if (v == null || (int) id.invoke(v) != want) wrong.add(Links.fingerprint(n.replace('.', '/'), f.getName()) + " = " + v + " (want " + want + ")");
                    else checked++;
                }
            }
        }
        System.out.println("[full-shells] entity data slot fields checked: " + checked + ", classes whose init needs a server: " + notInitialized);
        assertEquals(List.of(), wrong, "slot fields must carry the id table's slot number");
        assertTrue(checked >= 100, "expected the merged entity hand shims to get their slot fields, checked " + checked);
        assertEquals(0, notInitialized, "every class with slot fields must initialize here (the id table is on the loader)");
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

    /** 이 클래스의 상위 사슬에 껍데기 jar 밖(손 shim) 클래스나 합친 손 shim 이 있나. */
    private static boolean fromHandSuper(Class<?> c, Path shellJar, ClassLoader cl, java.util.Set<String> merged) {
        for (Class<?> s = c.getSuperclass(); s != null && s != Object.class; s = s.getSuperclass()) {
            if (s.getName().startsWith("java.")) return false;
            if (merged.contains(s.getName())) return true;
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
