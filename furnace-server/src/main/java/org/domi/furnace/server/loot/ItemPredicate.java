// 바닐라 ItemPredicate(전리품 조건·발전 과제 쪽) — 판정은 명령 인자와 같은 공용 코덱(item/ItemPredicates)에 맡긴다.
// 잘못된 술어(모르는 하위 술어 종류·컴포넌트, 없는 레지스트리 키, 잘못된 값)는 바닐라처럼 그 항목의 로드 오류다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import org.domi.furnace.server.item.CodecException;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.item.ItemPredicates;
import org.domi.furnace.server.item.ItemView;
import org.domi.furnace.server.item.RegistryView;

public final class ItemPredicate {

    private final ItemPredicates.Test test;

    private ItemPredicate(ItemPredicates.Test test) { this.test = test; }

    public static ItemPredicate parse(JsonElement e, LootRegistries regs) {
        try {
            return new ItemPredicate(ItemPredicates.parse(Components.fromJson(e), view(regs)));
        } catch (CodecException ex) {
            throw new LootParseException(ex.getMessage());
        }
    }

    public boolean test(LootStack s, LootRegistries regs) { return test.test(view(s)); }

    /** 전리품 레지스트리: 태그는 검사하지 않는다(예전 HolderSet 과 같게 — 없는 태그는 빈 태그). */
    static RegistryView view(LootRegistries regs) {
        return new RegistryView() {
            public boolean exists(String registry, String id) { return regs.exists(registry, id); }
            public boolean hasTag(String registry, String tag) { return true; }
            public boolean inTag(String registry, String tag, String id) { return regs.tag(registry, tag).contains(id); }
        };
    }

    /** 전리품 스택을 술어가 보는 꼴로: 컴포넌트 값은 그 종류의 코덱으로 정규화(못 하면 그대로). */
    static ItemView view(LootStack s) {
        if (s.isEmpty()) return ItemView.EMPTY;
        return new ItemView() {
            public String item() { return s.item(); }
            public int count() { return s.count(); }
            public Object get(String component) {
                Object v = s.get(component);
                if (v == null) return null;
                var t = DataComponents.byName(component);
                if (t == null || t.isTransient()) return v;
                try {
                    return t.normalize(v);
                } catch (CodecException e) {
                    return v;
                }
            }
        };
    }
}
