// 설정 경로 해석 — 사용자 지정 구분자(options().pathSeparator)일 때 Paper MemorySection 의 규칙(PR #254 검토 R4).
// 구분자 사이의 빈 단계는 "현재 구역"이라 건너뛰고, 끝 구분자는 "그 구역 자신"(조회) 또는 IllegalArgumentException(쓰기)이다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;

public final class ConfigPaths {

    private ConfigPaths() {}

    /** 서버 옵션(furnace.properties): 켜면 addDefault 기본값 트리를 Paper 처럼 언제나 '.' 로 쪼갠다(기본 꺼짐 = 개발자가 정한 구분자로 쪼갬). */
    public static final String PAPER_DEFAULT_SEPARATOR_OPTION = "plugins.compat.config.paper-default-separator";

    /** 서버 하나의 옵션 값(서버 수명 동안 바뀌지 않는다 — 값 조회마다 맵을 뒤지지 않게 한 번만 읽어 둔다). */
    private record Cached(org.domi.furnace.api.Furnace server, boolean on) {}

    private static volatile Cached cached;

    /** 위 옵션이 켜졌는가. 서버가 없으면(설정만 쓰는 도구·시험) 꺼짐. */
    public static boolean paperDefaultSeparator() {
        org.domi.furnace.api.Furnace f = org.domi.furnace.api.Furnace.serverOrNull();
        if (f == null) return false;
        Cached c = cached;
        if (c == null || c.server != f) cached = c = new Cached(f, "true".equals(f.compatOption(PAPER_DEFAULT_SEPARATOR_OPTION)));
        return c.on;
    }

    /**
     * @param keys 구역을 따라 내려갈 키 단계(빈 경로면 비어 있다 = 이 구역 자신)
     * @param endsAtSection 경로가 끝 구분자(또는 빈 경로)로 끝나 keys 가 가리키는 것이 "구역 자신"인가
     */
    public record Parsed(List<String> keys, boolean endsAtSection) {}

    public static Parsed parse(String path, char sep) {
        if (path.isEmpty()) return new Parsed(List.of(), true);
        List<String> keys = new ArrayList<>();
        int start = 0;
        for (int i = path.indexOf(sep); i >= 0; i = path.indexOf(sep, start)) {
            if (i > start) keys.add(path.substring(start, i));
            start = i + 1;
        }
        String last = path.substring(start);
        if (!last.isEmpty()) {
            keys.add(last);
            return new Parsed(keys, false);
        }
        // 마지막 단계가 비었다. 앞 단계가 하나도 없으면(구분자뿐인 경로) Paper 는 빈 이름 키에 그대로 쓴다.
        return keys.isEmpty() ? new Parsed(List.of(""), false) : new Parsed(keys, true);
    }
}
