package org.domi.furnace.compat.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * N-lib-1: 호환 계층의 모든 클래스가 선택 라이브러리(authlib·DFU·Brigadier·Adventure·Guava 등) 없이 링크되는가.
 * 시험 클래스패스에는 authlib·Guava 가 있으므로(N-lib-2 시험용) 그 jar 를 뺀 전용 로더(부모 = 플랫폼 로더)에서 다시 올려 링크한다.
 * 서명에만 나오는 라이브러리 타입은 괜찮고(부를 때 해석), 본문이 하위 타입 대입·catch 로 건드리거나 상위 타입이 라이브러리면 클래스 전체가 링크에 실패한다 —
 * 그런 본문은 {@code …$Lib} 도우미로 뗀다(그 이름만 실패를 허용한다). 메서드 조회는 정적 초기화를 돌리지 않고 링크(검증)를 일으킨다.
 * 반사로 서명을 푸는 것({@code getDeclaredMethods})은 라이브러리가 있어야 하므로 여기서 하지 않는다 — 그래서 서명에 쓰는 묶음은 서버가 늘 준비한다
 * (호환 jar 를 표지로 훑은 묶음, {@code PluginBootstrapper.compatCore}).
 */
class CompatLinkTest {

    @Test
    void everyCompatClassLinksWithoutOptionalLibraries() throws Exception {
        Path root = Path.of(org.domi.furnace.compat.nms.NmsBlocks.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "호환 모듈 클래스 폴더: " + root);
        List<String> names;
        try (Stream<Path> files = Files.walk(root)) {
            names = files.filter(p -> p.toString().endsWith(".class")).map(p -> root.relativize(p).toString().replace(java.io.File.separatorChar, '/'))
                    .map(n -> n.substring(0, n.length() - 6).replace('/', '.')).filter(n -> !n.endsWith("module-info")).sorted().toList();
        }
        assertTrue(names.size() > 500, "클래스가 너무 적다: " + names.size());
        try (URLClassLoader loader = withoutLibraries()) {
            // 전용 로더가 정말 라이브러리를 가리는지, 그리고 시험이 도우미의 실패를 알아보는지 먼저 확인한다
            assertEquals(ClassNotFoundException.class, failureOf(() -> Class.forName("com.mojang.authlib.GameProfile", false, loader)));
            assertEquals(ClassNotFoundException.class, failureOf(() -> Class.forName("com.google.common.collect.Multimap", false, loader)));
            Class.forName("com.mojang.authlib.GameProfile", false, getClass().getClassLoader());
            assertEquals(IllegalAccessException.class, failureOf(() -> link(Class.forName(LinkProbe.class.getName(), false, loader))));
            linkAll(loader, names);
        }
    }

    /** 선택 라이브러리 jar 를 뺀 시험 클래스패스. */
    private static final List<String> LIBRARIES = List.of("authlib-", "guava-", "failureaccess-", "datafixerupper-", "brigadier-", "adventure-", "examination-");

    private static URLClassLoader withoutLibraries() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (e.isEmpty()) continue;
            String file = Path.of(e).getFileName().toString();
            if (LIBRARIES.stream().anyMatch(file::startsWith)) continue;
            urls.add(Path.of(e).toUri().toURL());
        }
        return new URLClassLoader("compat-link-test", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    /** 검증기가 라이브러리 클래스를 읽어야 하는 본문(하위 클래스를 상위 클래스로 돌려줌) — 라이브러리가 없으면 링크에 실패해야 한다. */
    static final class LinkProbe {
        static com.google.common.collect.ForwardingMultimap<String, com.mojang.authlib.properties.Property> map() {
            return new com.mojang.authlib.properties.PropertyMap();
        }
    }

    private interface Step { void run() throws Throwable; }

    private static Class<?> failureOf(Step s) {
        try {
            s.run();
            return null;
        } catch (Throwable t) {
            return t.getClass();
        }
    }

    /** 없는 이름을 찾으면 대상 클래스를 링크(검증)한 뒤 NoSuchMethodException 이 난다 — 초기화는 하지 않는다. */
    private static void link(Class<?> c) throws Throwable {
        MethodHandles.privateLookupIn(c, MethodHandles.lookup()).findStatic(c, "__furnace_link__", MethodType.methodType(void.class));
    }

    private static void linkAll(ClassLoader loader, List<String> names) {
        List<String> failures = new ArrayList<>();
        int linked = 0;
        for (String n : names) {
            if (n.endsWith("$Lib") || n.contains("$Lib$")) continue;
            try {
                Class<?> c = Class.forName(n, false, loader);   // 상위 타입이 라이브러리면 여기서 실패한다
                // 검증이 라이브러리 타입을 읽다 실패하면 NoClassDefFoundError 를 원인으로 단 IllegalAccessException 이 난다(JDK 21 실험).
                link(c);
            } catch (NoSuchMethodException expected) {
                linked++;
            } catch (Throwable t) {
                failures.add(n + ": " + t + (t.getCause() != null ? " ← " + t.getCause() : ""));
            }
        }
        assertEquals(List.of(), failures.subList(0, Math.min(20, failures.size())), "링크 실패 " + failures.size() + "개");
        assertTrue(linked > 500, "링크한 클래스: " + linked);
    }
}
