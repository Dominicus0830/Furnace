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

    private static volatile org.domi.furnace.server.net.registry.TagSet tags;

    /** 데이터 팩 리로드마다 지금 태그(아이템·블록 등)를 건다. */
    public static void installTags(org.domi.furnace.server.net.registry.TagSet t) { tags = t; }

    /** 그 레지스트리 태그에 id 가 들어 있는가(태그가 없거나 아직 안 걸렸으면 거짓). */
    public static boolean isInTag(String registry, String tag, int id) {
        var t = tags;
        if (t == null) return false;
        int[] ids = t.ids(registry, tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
        if (ids == null) return false;
        for (int v : ids) if (v == id) return true;
        return false;
    }

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
