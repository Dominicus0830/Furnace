package org.domi.furnace.compat.paper.fixtures;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 시험 쪽과 NMS 시험 플러그인 사이의 공용 칠판(이름 → 값). 플러그인 클래스로더가 부모(시험 클래스패스)에서 이 클래스를 찾는다. */
public final class NmsProbe {

    public static final Map<String, Object> VALUES = new ConcurrentHashMap<>();

    private NmsProbe() {}

    public static void put(String key, Object value) { VALUES.put(key, value == null ? NULL : value); }

    public static Object get(String key) {
        Object v = VALUES.get(key);
        return v == NULL ? null : v;
    }

    private static final Object NULL = new Object() {
        @Override
        public String toString() { return "null"; }
    };
}
