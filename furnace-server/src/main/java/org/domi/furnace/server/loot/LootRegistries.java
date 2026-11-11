// 전리품이 보는 레지스트리·태그·인챈트 정의 — 데이터 팩을 읽을 때 한 번 만든다.
package org.domi.furnace.server.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;

public final class LootRegistries {

    private final Map<String, List<String>> names = new HashMap<>();
    private final Map<String, Map<String, List<String>>> tags = new HashMap<>();
    private final Map<String, Enchantment> enchantments = new LinkedHashMap<>();
    private final TagRegistries all;

    /** @param tagIds TagRegistries.loadAll 결과(레지스트리 → 태그 → 숫자 id) */
    public LootRegistries(TagRegistries regs, Map<String, Map<String, int[]>> tagIds) {
        this.all = regs;
        for (String reg : TagRegistries.SYNCED) {
            List<String> n = new ArrayList<>();
            for (ResourceId id : regs.names(reg)) n.add(id.toString());
            names.put(reg, n);
            Map<String, List<String>> t = new HashMap<>();
            for (var e : tagIds.getOrDefault(reg, Map.of()).entrySet()) {
                List<String> members = new ArrayList<>(e.getValue().length);
                for (int i : e.getValue()) members.add(n.get(i));
                t.put(ResourceId.parse(e.getKey()).toString(), Collections.unmodifiableList(members));
            }
            tags.put(reg, t);
        }
    }

    /** 태그 내용(바닐라 태그 순서). 없는 태그면 빈 목록. */
    public List<String> tag(String registry, String tag) {
        Map<String, List<String>> t = tags.get(registry);
        List<String> l = t == null ? null : t.get(LootJson.id(tag));
        return l == null ? List.of() : l;
    }

    /** 태그 이름들(동기화 레지스트리만). */
    public java.util.Set<String> tagNames(String registry) { return tags.getOrDefault(registry, Map.of()).keySet(); }

    public boolean hasTag(String registry, String tag) {
        Map<String, List<String>> t = tags.get(registry);
        return t != null && t.containsKey(LootJson.id(tag));
    }

    /** 레지스트리 순서의 엔트리(동기화 레지스트리만). */
    public List<String> entries(String registry) { return names.getOrDefault(registry, List.of()); }

    public boolean exists(String registry, String id) {
        if (registry.equals("minecraft:item")) return ItemData.vanilla().exists(id);
        List<String> n = names.get(registry);
        if (n != null) return n.contains(id);
        // 동기화하지 않는 레지스트리(물약·속성·효과·장식 재료/무늬·음반 등)는 내장·동적 레지스트리 이름표로. 이름표가 없는 레지스트리만 검사하지 않는다.
        if (all.names(registry).isEmpty()) return true;
        ResourceId rid = ResourceId.tryParse(id);
        return rid != null && all.id(registry, rid) != null;
    }

    void putEnchantment(String id, Enchantment e) { enchantments.put(id, e); }

    public Enchantment enchantment(String id) { return enchantments.get(id); }

    /** 레지스트리 순서(바닐라: 파일 id 의 path·namespace 순). */
    public Map<String, Enchantment> enchantments() { return enchantments; }
}
