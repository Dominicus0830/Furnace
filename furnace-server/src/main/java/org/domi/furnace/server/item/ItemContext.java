// 아이템 코덱이 쓰는 레지스트리(이름 ↔ 숫자 id). 서버 시작 때 데이터 팩 매니저가 설치한다(바닐라 RegistryAccess 대응).
package org.domi.furnace.server.item;

import org.domi.furnace.api.GameVersion;
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
        maxStackCache = new int[0];
    }

    /** 아이템 번호 → 기본 최대 수량 기억(ItemStackCodecs.defaultMaxStackSize). 설치 때 새로 만든다. 0 = 아직 안 구함. */
    private static volatile int[] maxStackCache = new int[0];

    public static int[] maxStackCache() {
        int[] c = maxStackCache;
        if (c.length == 0 && regs != null) {
            int n = size("minecraft:item");
            if (n > 0) {
                c = new int[n];
                maxStackCache = c;
            }
        }
        return c;
    }

    public static TagRegistries regs() {
        TagRegistries r = regs;
        if (r == null) throw new IllegalStateException("the item registry is not installed");
        return r;
    }

    public static boolean installed() { return regs != null; }

    private static volatile org.domi.furnace.server.net.registry.TagSet tags;

    /** 데이터 팩 리로드마다 지금 태그(아이템·블록 등)를 건다. */
    public static void installTags(org.domi.furnace.server.net.registry.TagSet t) { tags = t; }

    /** 지금 걸린 태그(아직 안 걸렸으면 null). */
    public static org.domi.furnace.server.net.registry.TagSet tagSet() { return tags; }

    private static final class VanillaTags {
        static final org.domi.furnace.server.net.registry.TagSet SET = org.domi.furnace.server.net.registry.TagSet.load("registries/tags-" + GameVersion.RESOURCE_TAG + ".txt");
    }

    /**
     * 게임 규칙(블록·엔티티·아이템·피해·게임 이벤트 태그 질문)이 읽는 태그 표. 데이터 팩 관리자가 건 표(데이터 팩·플러그인이 바꾼 것 포함)이고,
     * 아직 안 걸렸으면(데이터 팩 없이 도는 시험 서버) jar 에 든 바닐라 1.21.1 태그다. 바닐라는 `/reload` 때마다 이 표가 새로 바뀐다.
     * 호출처는 이 표를 오래 들고 있지 말고 표 객체가 바뀌었는지(`!=`)로 자기 캐시를 새로 만든다.
     */
    public static org.domi.furnace.server.net.registry.TagSet gameplayTags() {
        var t = tags;
        return t != null ? t : VanillaTags.SET;
    }

    /** 그 레지스트리 태그에 id 가 들어 있는가(태그가 없거나 아직 안 걸렸으면 거짓). */
    public static boolean isInTag(String registry, String tag, int id) {
        var t = tags;
        if (t == null) return false;
        int[] ids = t.ids(registry, tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
        if (ids == null) return false;
        for (int v : ids) if (v == id) return true;
        return false;
    }

    /** 그 레지스트리에 그 태그가 있는가(태그가 아직 안 걸렸으면 거짓). */
    public static boolean hasTag(String registry, String tag) {
        var t = tags;
        return t != null && t.ids(registry, tag.indexOf(':') < 0 ? "minecraft:" + tag : tag) != null;
    }

    /** 태그의 엔트리 숫자 id들(태그 파일에 적힌 순서, 없으면 빈 배열). */
    public static int[] tagEntries(String registry, String tag) {
        var t = tags;
        if (t == null) return new int[0];
        int[] ids = t.ids(registry, tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
        return ids == null ? new int[0] : ids.clone();
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
