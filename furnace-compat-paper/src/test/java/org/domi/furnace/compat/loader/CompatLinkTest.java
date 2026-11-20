package org.domi.furnace.compat.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * N-lib-1: 호환 계층의 모든 클래스가 선택 라이브러리(authlib·DFU·Brigadier·Adventure·Guava 등) 없이 링크되는가. 시험 클래스패스에는 그 라이브러리가 없다
 * (authlib 대역만 있어서 그것을 뺀 로더로 본다).
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
        // 시험 클래스패스에는 authlib 대역(src/authlib)이 있다 — 그것을 뺀 새 로더로 호환 계층 클래스를 다시 올려 본다
        List<String> stub = List.of(System.getProperty("furnace.test.authlibStub", "").split(java.io.File.pathSeparator));
        List<java.net.URL> urls = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (!e.isEmpty() && !stub.contains(e)) urls.add(Path.of(e).toUri().toURL());
        }
        ClassLoader loader = new java.net.URLClassLoader(urls.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader());
        assertTrue(stub.stream().anyMatch(s -> !s.isEmpty()), "authlib 대역 경로를 받았다");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.mojang.authlib.GameProfile", false, loader), "새 로더에는 authlib 이 없다");
        List<String> failures = new ArrayList<>();
        int linked = 0;
        for (String n : names) {
            if (n.endsWith("$Lib") || n.contains("$Lib$")) continue;
            // DataFixerUpper 의 DynamicOps 를 구현 — 그 라이브러리를 쓰는 플러그인(FAWE 등)이 있어 서버가 묶음을 붙인 때만 올라간다(T-191·T-200)
            if (n.equals("net.minecraft.nbt.NbtOps") || n.startsWith("net.minecraft.nbt.NbtOps$") || n.equals("net.minecraft.resources.DelegatingOps") || n.equals("net.minecraft.resources.RegistryOps")) continue;
            // Brigadier 의 ArgumentType 을 구현 — 그 라이브러리가 있을 때(Paper 서버·명령 라이브러리를 쓰는 플러그인)만 올라간다
            if (n.startsWith("net.minecraft.commands.arguments.")) continue;
            if (n.equals("org.domi.furnace.compat.nms.NmsStateCodec")) continue;   // DataFixerUpper 의 MapCodec 을 상속 — NmsDfu.visible() 일 때만 올린다
            try {
                Class<?> c = Class.forName(n, false, loader);   // 상위 타입이 라이브러리면 여기서 실패한다
                // 없는 이름을 찾으면 대상 클래스를 링크(검증)한 뒤 NoSuchMethodException 이 난다 — 초기화는 하지 않는다.
                // 검증이 라이브러리 타입을 읽다 실패하면 NoClassDefFoundError 를 원인으로 단 IllegalAccessException 이 난다(JDK 21 실험).
                MethodHandles.privateLookupIn(c, MethodHandles.lookup()).findStatic(c, "__furnace_link__", MethodType.methodType(void.class));
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
