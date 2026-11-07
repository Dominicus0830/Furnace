// 리소스 위치(namespace:path) 검사. 레지스트리 키는 바닐라 ResourceLocation 규칙을 따른다.
package org.domi.furnace.api.worldgen;

import java.util.regex.Pattern;

final class ResourceKeys {

    private static final Pattern FULL = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private ResourceKeys() {}

    /** namespace 가 없으면 minecraft: 를 붙인다(소리·몹 종류 같은 참조용). 형식이 틀리면 IllegalArgumentException. */
    static String reference(String what, String id) {
        if (id == null) throw new NullPointerException(what);
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (!FULL.matcher(full).matches()) throw new IllegalArgumentException(what + " 형식이 잘못됐다(namespace:path): " + id);
        return full;
    }

    static float finite(String what, float v) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException(what + " 는 유한한 수여야 한다: " + v);
        return v;
    }

    static double finite(String what, double v) {
        if (!Double.isFinite(v)) throw new IllegalArgumentException(what + " 는 유한한 수여야 한다: " + v);
        return v;
    }
}
