// 규칙 ①(T-249): 호환 계층 주 코드(furnace-compat-paper main)는 서버 구현(org.domi.furnace.server..)을 이름으로 쓰지 않는다.
// 어댑터는 공개 API(furnace-api, 서버 내부는 api.internal 다리)만 본다 — 반대 방향의 짝은 furnace-server 의 CompatIsolationStaticTest·AdapterBoundaryStaticTest.
// (이 모듈의 시험은 서버를 클래스패스에 올리지만 주 코드는 서버에 컴파일 의존하지 않아야 한다.)
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AdapterDirectionStaticTest {

    private static final List<String> SERVER = List.of("org/domi/furnace/server/", "org.domi.furnace.server.");

    /** 서버 이름이 상수 풀에 있어도 되는 호환 주 코드(클래스 파일 이름 끝) — 이유 없이 늘리지 않는다. 비어 있어야 정상. */
    private static final List<String> ALLOWED = List.of();

    @Test
    void compatMainCodeNeverNamesAServerClass() throws Exception {
        Path root = Path.of(CompatFormat.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "호환 주 코드가 클래스 폴더에 있어야 한다: " + root);
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".class"))::iterator) {
                scanned++;
                String rel = root.relativize(p).toString();
                if (ALLOWED.stream().anyMatch(rel::endsWith)) continue;
                String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
                for (String f : SERVER) {
                    if (text.contains(f)) {
                        offenders.add(rel + " → " + f);
                        break;
                    }
                }
            }
        }
        assertTrue(scanned > 500, "스캔한 클래스가 너무 적다(" + scanned + ")");
        assertEquals(List.of(), offenders, "호환 계층 주 코드가 서버 구현을 가리킨다 — 공개 API(api.internal 다리)로 돌린다");
    }
}
