package net.minecraft.world.level.levelgen.structure.templatesystem;

/**
 * 구조물 템플릿 관리자(Mojang 이름 shim, T-211 2단계) — {@code ServerLevel#getStructureManager()}·{@code MinecraftServer#getStructureManager()}. 바닐라처럼 서버에
 * 하나다. 값은 서버가 가진 템플릿(데이터 팩의 structure/*.nbt)을 가리키는 손잡이다: 구조물 생성이 받은 관리자가 서버 것인지 확인하는 데 쓴다.
 * 템플릿 읽기(get·getOrCreate)는 아직 없다.
 */
public final class StructureTemplateManager {

    private static volatile StructureTemplateManager current;

    private final org.domi.furnace.api.Furnace server;

    private StructureTemplateManager(org.domi.furnace.api.Furnace server) { this.server = server; }

    /** 호환 모듈 전용: 지금 서버의 관리자(서버마다 하나). */
    public static StructureTemplateManager of() {
        var s = org.domi.furnace.api.Furnace.server();
        StructureTemplateManager m = current;
        if (m != null && m.server == s) return m;
        synchronized (StructureTemplateManager.class) {
            m = current;
            if (m == null || m.server != s) current = m = new StructureTemplateManager(s);
            return m;
        }
    }

    /** 호환 모듈 전용: 지금 서버의 관리자인가. */
    public boolean furnaceCurrent() { return server == org.domi.furnace.api.Furnace.server(); }
}
