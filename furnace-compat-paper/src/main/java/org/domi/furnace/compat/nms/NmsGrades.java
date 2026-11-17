package org.domi.furnace.compat.nms;

import java.util.List;
import org.domi.furnace.compat.i18n.CompatLang;

/**
 * shim 에 없는 NMS 심볼의 이유 문구(설계 H5 2.1 의 등급 C 줄). 알려진 "만들 수 없는" 표면이면 등급 C 와 대체 Furnace API 를,
 * 아니면 "아직 shim 이 없다"를 돌려준다. 미지원 호출 예외·로드 때 호환 보고가 같은 문구를 쓴다.
 */
public final class NmsGrades {

    private NmsGrades() {}

    /** 등급 C 표면(내부 이름 접두어) → 이유·대체 문구의 호환 번들 키(nms.grade-c.…). 위에서부터 처음 맞는 것. */
    private static final List<String[]> C = List.of(
            new String[] {"net/minecraft/world/level/chunk/", "nms.grade-c.chunk"},
            new String[] {"net/minecraft/world/level/lighting/", "nms.grade-c.lighting"},
            new String[] {"net/minecraft/server/level/ChunkMap", "nms.grade-c.chunk-map"},
            new String[] {"net/minecraft/server/level/ServerChunkCache", "nms.grade-c.chunk-cache"},
            new String[] {"net/minecraft/world/level/levelgen/", "nms.grade-c.levelgen"},
            new String[] {"net/minecraft/world/entity/ai/", "nms.grade-c.mob-ai"},
            new String[] {"net/minecraft/world/inventory/", "nms.grade-c.menu"},
            new String[] {"net/minecraft/server/ReloadableServerResources", "nms.grade-c.reload"},
            new String[] {"net/minecraft/server/packs/", "nms.grade-c.packs"},
            new String[] {"net/minecraft/world/level/entity/", "nms.grade-c.entity-storage"}
    );

    /** 등급 C 로 알려진 표면인가. */
    public static boolean isGradeC(String internalName) { return gradeC(internalName) != null; }

    static String gradeC(String internalName) {
        String n = NmsSymbols.element(internalName);
        if (n == null) return null;
        for (String[] c : C) if (n.startsWith(c[0])) return CompatLang.t(c[1]);
        return null;
    }

    /** 사람에게 보일 이유 문구. */
    public static String reason(String internalName) {
        String c = gradeC(internalName);
        if (c != null) return CompatLang.t("nms.grade-c", c);
        String n = NmsSymbols.element(internalName);
        if (n != null && n.startsWith("org/bukkit/craftbukkit/")) return CompatLang.t("nms.no-craftbukkit");
        return CompatLang.t("nms.no-shim");
    }
}
