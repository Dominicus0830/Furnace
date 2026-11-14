package org.domi.furnace.compat.nms;

import java.util.List;

/**
 * shim 에 없는 NMS 심볼의 이유 문구(설계 H5 2.1 의 등급 C 줄). 알려진 "만들 수 없는" 표면이면 등급 C 와 대체 Furnace API 를,
 * 아니면 "아직 shim 이 없다"를 돌려준다. 미지원 호출 예외·로드 때 호환 보고가 같은 문구를 쓴다.
 */
public final class NmsGrades {

    private NmsGrades() {}

    /** 등급 C 표면(내부 이름 접두어) → 이유·대체. 위에서부터 처음 맞는 것. */
    private static final List<String[]> C = List.of(
            new String[] {"net/minecraft/world/level/chunk/", "청크 내부의 필드·배열 수준 접근(섹션·팔레트 직접 만들기·쓰기, ProtoChunk)은 Furnace 의 청크 저장·region 분할과 달라 아직 없다 — 메서드 수준 읽기(LevelChunk·LevelChunkSection·PalettedContainer 보기)는 있다. 대체: Level#setBlock·Furnace 블록 API·Internals#withLevel·W3 월드젠 훅"},
            new String[] {"net/minecraft/world/level/lighting/", "빛 엔진 내부가 다르다. 대체: Furnace 빛 API(docs/api/light-sources.md)"},
            new String[] {"net/minecraft/server/level/ChunkMap", "청크 적재·추적 내부가 다르다. 대체: 청크 티켓 API(docs/api/chunk-tickets.md)"},
            new String[] {"net/minecraft/server/level/ServerChunkCache", "청크 적재·추적 내부가 다르다(로드된 청크 얻기 getChunk·getChunkNow 는 있다). 대체: 청크 티켓 API(docs/api/chunk-tickets.md)"},
            new String[] {"net/minecraft/world/level/levelgen/", "월드 생성 내부가 다르다. 대체: W3 월드젠 훅(docs/api/worldgen-hooks.md)"},
            new String[] {"net/minecraft/world/entity/ai/", "몹 AI 내부(목표·뇌)가 다르다. 대체: Furnace 몹 AI API(docs/api/mob-ai.md)"},
            new String[] {"net/minecraft/world/inventory/", "메뉴 내부가 Furnace 메뉴 프레임워크와 모양이 다르다. 대체: Furnace 메뉴 API"},
            new String[] {"net/minecraft/server/ReloadableServerResources", "데이터 팩 재적재 내부가 다르다. 대체: C2 함수 훅·W2 레지스트리(docs/api/function-hooks.md)"},
            new String[] {"net/minecraft/server/packs/", "데이터 팩 재적재 내부가 다르다. 대체: C2 함수 훅·W2 레지스트리(docs/api/function-hooks.md)"},
            new String[] {"net/minecraft/world/level/entity/", "엔티티 저장·틱 목록 내부가 멀티스레드 region 모델과 충돌한다. 대체: Furnace 엔티티 API"}
    );

    /** 등급 C 로 알려진 표면인가. */
    public static boolean isGradeC(String internalName) { return gradeC(internalName) != null; }

    static String gradeC(String internalName) {
        String n = NmsSymbols.element(internalName);
        if (n == null) return null;
        for (String[] c : C) if (n.startsWith(c[0])) return c[1];
        return null;
    }

    /** 사람에게 보일 이유 문구. */
    public static String reason(String internalName) {
        String c = gradeC(internalName);
        if (c != null) return "등급 C — " + c;
        String n = NmsSymbols.element(internalName);
        if (n != null && n.startsWith("org/bukkit/craftbukkit/")) return "CraftBukkit 어댑터에 아직 없다(docs/feature-parity/compat-h6-2026-11-07.md)";
        return "NMS shim 에 아직 없다(docs/feature-parity/compat-h7-2026-11-08.md). Spigot 이름이면 Spigot → Mojang 대응표가 필요하다(plugins.compat.nms.spigot-mappings=download, 기본 — 시작 로그에서 받기 실패 경고를 보라)";
    }
}
