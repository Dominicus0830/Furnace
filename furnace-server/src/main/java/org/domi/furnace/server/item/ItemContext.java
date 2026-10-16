// 아이템 코덱이 쓰는 레지스트리(이름 ↔ 숫자 id). 서버 시작 때 데이터 팩 매니저가 설치한다(바닐라 RegistryAccess 대응).
package org.domi.furnace.server.item;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;

public final class ItemContext {
    private ItemContext() {}

    private static volatile TagRegistries regs;
    private static final Map<String, List<ResourceId>> names = new ConcurrentHashMap<>();

    public static void install(TagRegistries r) {
        regs = r;
        names.clear();
    }

    public static TagRegistries regs() {
        TagRegistries r = regs;
        if (r == null) throw new IllegalStateException("아이템 레지스트리가 설치되지 않음");
        return r;
    }

    public static boolean installed() { return regs != null; }

    /** 이름 → 숫자 id(없으면 null). */
    public static Integer id(String registry, String name) {
        ResourceId rid = ResourceId.tryParse(name);
        return rid == null ? null : regs().id(registry, rid);
    }

    /** 숫자 id → 이름(범위 밖이면 null). */
    public static String name(String registry, int id) {
        List<ResourceId> l = names.computeIfAbsent(registry, k -> regs().names(k));
        return id >= 0 && id < l.size() ? l.get(id).toString() : null;
    }

    public static int size(String registry) { return names.computeIfAbsent(registry, k -> regs().names(k)).size(); }
}
