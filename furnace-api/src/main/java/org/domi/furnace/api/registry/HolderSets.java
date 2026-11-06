// 레지스트리 항목 집합 문자열의 검사(바닐라 HolderSet 코덱의 꼴): "#namespace:tag" 하나, 또는 id 목록.
package org.domi.furnace.api.registry;

import java.util.List;

final class HolderSets {
    private HolderSets() {}

    private static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_./-]+");

    /** 비어 있지 않고, 태그면 하나뿐이며, 모든 항목이 리소스 위치 꼴인가. 틀리면 IllegalArgumentException. */
    static List<String> check(String what, List<String> set, boolean allowEmpty) {
        if (set == null) throw new NullPointerException(what);
        List<String> copy = List.copyOf(set);
        if (copy.isEmpty() && !allowEmpty) throw new IllegalArgumentException(what + " 이(가) 비었다");
        for (String s : copy) {
            String id = s.startsWith("#") ? s.substring(1) : s;
            if (!KEY.matcher(id).matches()) throw new IllegalArgumentException(what + ": 잘못된 id " + s);
            if (s.startsWith("#") && copy.size() != 1) throw new IllegalArgumentException(what + ": 태그(#)는 혼자만 쓸 수 있다");
        }
        return copy;
    }
}
