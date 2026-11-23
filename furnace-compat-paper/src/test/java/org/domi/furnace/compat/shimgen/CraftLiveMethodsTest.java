package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * T-264 검토 2: 상위 클래스를 Paper 대로 옮기거나 이름을 옮겨도 동작이 조용히 사라지지 않는가. 실행 때와 같은 로더(껍데기 jar → 호환 jar → …)에서
 * Craft API 구현 클래스마다, 그 클래스가 구현하는 Bukkit/Paper API 인터페이스 메서드를 JVM 규칙(공개 메서드 해석)대로 풀어 **진짜 몸통**
 * (추상 아님·껍데기 표지 {@code @NmsShellMember} 없음)으로 가는 것의 집합을 만든다. 커밋된 표({@code craft-live-api-methods-1.21.1.txt},
 * 클래스마다 "수 + 정렬한 집합의 지문")와 비교해 수가 줄거나 같은 수인데 집합이 바뀌면 실패, 늘면 표를 고치라며 실패(늘기만 한다).
 * 서명표 캐시가 있어야 해서 {@code :furnace-compat-paper:fullShellsTest}(CI vanilla-gates)가 돈다.
 */
@Tag("full-shells")
class CraftLiveMethodsTest {
    static final Path TABLE = Path.of("src/test/resources/craft-live-api-methods-1.21.1.txt");

    @Test
    void liveApiMethodsOnlyGrow() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String p : System.getProperty("furnace.fullShells.classpath").split(File.pathSeparator)) if (!p.isBlank()) urls.add(Path.of(p).toUri().toURL());
        Map<String, String> now = new TreeMap<>();
        Map<String, Set<String>> liveSets = new TreeMap<>();
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Map<String, Set<String>> shellMembers = new HashMap<>();
            for (CraftHierarchyLock.Entry e : CraftHierarchyLock.read(CraftHierarchyTest.LOCK).values()) {
                if (!e.role().equals("impl") || (e.access() & Opcodes.ACC_INTERFACE) != 0) continue;
                Class<?> c;
                try {
                    c = Class.forName(e.name().replace('/', '.'), false, cl);
                } catch (Throwable t) {
                    continue;   // 못 만드는 클래스(전체 껍데기 4장) — fullShellsTest 가 따로 센다
                }
                Set<String> live = new TreeSet<>();
                for (Class<?> api : apiInterfaces(c)) {
                    for (Method m : api.getMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() != api) continue;
                        Method impl;
                        try {
                            impl = c.getMethod(m.getName(), m.getParameterTypes());
                        } catch (Throwable t) {
                            continue;
                        }
                        if (Modifier.isAbstract(impl.getModifiers())) continue;
                        if (isShell(cl, impl, shellMembers)) continue;
                        live.add(m.getName() + Type.getMethodDescriptor(m));
                    }
                }
                now.put(e.name(), live.size() + " " + fingerprint(String.join("\n", live)));
                liveSets.put(e.name(), live);
            }
        }
        if (Boolean.getBoolean("furnace.craftLive.write")) {
            StringBuilder sb = new StringBuilder("# T-264 Craft API classes: live (non-abstract, non-shell) API methods - count and sha256/16 of the sorted set. Only grows.\n"
                    + "# Rewrite: ./gradlew -PskipOracle=true :furnace-compat-paper:fullShellsTest -Dfurnace.craftLive.write=true (only after checking nothing went away)\n");
            now.forEach((k, v) -> sb.append(k).append(' ').append(v).append('\n'));
            Files.writeString(TABLE, sb.toString(), StandardCharsets.UTF_8);
            return;
        }
        Map<String, String> was = new TreeMap<>();
        for (String raw : Files.readAllLines(TABLE, StandardCharsets.UTF_8)) {
            String l = raw.strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int sp = l.indexOf(' ');
            was.put(l.substring(0, sp), l.substring(sp + 1));
        }
        List<String> lost = new ArrayList<>();
        List<String> grown = new ArrayList<>();
        for (Map.Entry<String, String> w : was.entrySet()) {
            String n = now.get(w.getKey());
            if (n == null) {
                lost.add(w.getKey() + " no longer loads (was " + w.getValue() + ")");
                continue;
            }
            int before = Integer.parseInt(w.getValue().split(" ")[0]);
            int after = Integer.parseInt(n.split(" ")[0]);
            // 표에는 수·지문만 있어 무엇이 빠졌는지는 지금 목록으로 본다(API 서술자뿐 — Mojang 이름 없음)
            if (after < before || (after == before && !n.equals(w.getValue()))) lost.add(w.getKey() + " " + w.getValue() + " -> " + n + "\n    live now: " + String.join(" ", liveSets.get(w.getKey())));
            else if (after > before) grown.add(w.getKey() + " " + w.getValue() + " -> " + n);
        }
        for (String k : now.keySet()) if (!was.containsKey(k)) grown.add(k + " new " + now.get(k));
        StringBuilder msg = new StringBuilder();
        if (!lost.isEmpty()) msg.append("live API methods went away (a body became a shell or abstract):\n  ").append(String.join("\n  ", lost)).append('\n');
        if (!grown.isEmpty()) msg.append("live API methods grew - record them with -Dfurnace.craftLive.write=true:\n  ").append(String.join("\n  ", grown)).append('\n');
        if (!msg.isEmpty()) fail(msg.toString());
        assertTrue(now.size() > 300, "expected hundreds of Craft API classes, got " + now.size());
    }

    /** 클래스가 (상위 사슬로) 구현하는 Bukkit/Paper API 인터페이스(org.bukkit·io.papermc·com.destroystokyo, CraftBukkit 밖). */
    static Set<Class<?>> apiInterfaces(Class<?> c) {
        Set<Class<?>> out = new LinkedHashSet<>();
        List<Class<?>> todo = new ArrayList<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) todo.addAll(List.of(k.getInterfaces()));
        while (!todo.isEmpty()) {
            Class<?> i = todo.remove(todo.size() - 1);
            String n = i.getName();
            boolean api = (n.startsWith("org.bukkit.") && !n.startsWith("org.bukkit.craftbukkit.")) || n.startsWith("io.papermc.") || n.startsWith("com.destroystokyo.");
            if (api && out.add(i)) todo.addAll(List.of(i.getInterfaces()));
            else if (!api) todo.addAll(List.of(i.getInterfaces()));
        }
        return out;
    }

    /** 몸통이 껍데기 멤버인가(CLASS 보존 표지라 리플렉션에 안 보인다 — 선언한 클래스 바이트를 읽는다). */
    static boolean isShell(ClassLoader cl, Method m, Map<String, Set<String>> cache) throws IOException {
        String owner = m.getDeclaringClass().getName().replace('.', '/');
        Set<String> shells = cache.get(owner);
        if (shells == null) {
            Set<String> found = new TreeSet<>();
            try (InputStream in = cl.getResourceAsStream(owner + ".class")) {
                if (in != null) {
                    new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                                    if (d.equals(FullShells.SHELL_MEMBER)) found.add(name + desc);
                                    return null;
                                }
                            };
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
            cache.put(owner, shells = found);
        }
        return shells.contains(m.getName() + Type.getMethodDescriptor(m));
    }

    static String fingerprint(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
    }
}
