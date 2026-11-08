// 서버 핵심·공개 API 의 클래스 파일이 호환 모듈(Bukkit·Paper API, 어댑터, ASM)의 클래스를 이름으로 가리키지 않는다 — 정적 의존이 없다는 증거.
// 서버 모듈은 호환 모듈에 의존하지 않는다(그 반대 방향만 있다). 이 시험은 누가 실수로 `org.bukkit.*` 를 import 하면 바로 깨진다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.domi.furnace.api.plugin.PluginFormat;
import org.junit.jupiter.api.Test;

class CompatIsolationStaticTest {

    /** 호환 모듈이 가진 패키지의 JVM 내부 이름 접두. (net/md_5 는 서버 라이브러리 표지 문자열로만 나오므로 뺀다.) */
    private static final List<String> FORBIDDEN = List.of("org/bukkit/", "io/papermc/", "com/destroystokyo/", "org/domi/furnace/compat/", "org/objectweb/asm/");

    @Test
    void serverAndApiClassFilesNeverNameACompatClass() throws Exception {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (Class<?> anchor : List.of(PluginBootstrapper.class, PluginFormat.class)) {
            scanned += scan(anchor, offenders);
        }
        assertTrue(scanned > 1000, "스캔한 클래스가 너무 적다(" + scanned + ") — 위치를 못 찾았을 수 있다");
        assertEquals(List.of(), offenders, "서버·API 클래스가 호환 모듈 클래스를 가리킨다");
    }

    @Test
    void theServerTestClasspathCannotResolveCompatClasses() {
        for (String n : List.of("org.bukkit.Bukkit", "org.domi.furnace.compat.paper.CompatFormat", "org.objectweb.asm.ClassReader")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(n, false, getClass().getClassLoader()), n);
        }
    }

    private static int scan(Class<?> anchor, List<String> offenders) throws IOException, URISyntaxException {
        Path root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        int[] n = {0};
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".class"))::iterator) {
                    check(root.relativize(p).toString(), Files.readAllBytes(p), offenders);
                    n[0]++;
                }
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                for (JarEntry e : (Iterable<JarEntry>) jar.stream()::iterator) {
                    if (!e.getName().endsWith(".class")) continue;
                    try (InputStream in = jar.getInputStream(e)) {
                        check(e.getName(), in.readAllBytes(), offenders);
                    }
                    n[0]++;
                }
            }
        }
        return n[0];
    }

    private static void check(String name, byte[] bytes, List<String> offenders) {
        // 상수 풀의 클래스·서술자 문자열은 내부 이름(슬래시)으로 들어 있다. latin1 로 읽으면 바이트 그대로 문자열 검색이 된다.
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String f : FORBIDDEN) {
            if (text.contains(f)) {
                offenders.add(name + " → " + f);
                return;
            }
        }
    }
}
