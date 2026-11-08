// 호환 모듈(furnace-compat-paper)이 서버 jar 에 싣는 패키지들. Furnace 네이티브 플러그인의 클래스로더는 이 이름들을 서버 jar(부모)에서 찾지 않는다 —
// 네이티브 플러그인이 쓰는 것은 Furnace API 뿐이어야 한다. (net.md_5 는 호환 플러그인용 라이브러리 로더에만 있어 서버 jar 에는 없다.)
package org.domi.furnace.server.plugin;

import java.util.List;

final class CompatPackages {

    private CompatPackages() {}

    /** 이름 접두(점 표기). */
    static final List<String> PREFIXES = List.of(
            "org.bukkit.", "io.papermc.", "com.destroystokyo.",
            "org.domi.furnace.compat.",
            "org.objectweb.asm.",
            "net.minecraft.");   // 호환 jar 의 getHandle NMS shim(H6). 서버 본체 클래스는 다른 이름이다

    static boolean isCompat(String className) {
        for (String p : PREFIXES) if (className.startsWith(p)) return true;
        return false;
    }
}
