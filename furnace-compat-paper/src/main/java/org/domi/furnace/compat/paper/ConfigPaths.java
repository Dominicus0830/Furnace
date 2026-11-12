// 설정 경로 해석 — 사용자 지정 구분자(options().pathSeparator)일 때 Paper MemorySection 의 규칙(PR #254 검토 R4).
// 구분자 사이의 빈 단계는 "현재 구역"이라 건너뛰고, 끝 구분자는 "그 구역 자신"(조회) 또는 IllegalArgumentException(쓰기)이다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;

public final class ConfigPaths {

    private ConfigPaths() {}

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
