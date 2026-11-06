// 전리품 스택 → 서버 아이템 스택. 컴포넌트 패치는 종류별 저장 코덱으로 정규화해 담는다(네트워크·저장은 ComponentPatch 가 맡는다).
// 만들 때 아이템 코덱이 쓸 레지스트리를 설치한다.
package org.domi.furnace.server.loot;

import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.CodecException;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkComponents {

    private static final Logger log = LoggerFactory.getLogger(NetworkComponents.class);

    private final TagRegistries regs;

    public NetworkComponents(TagRegistries regs) {
        this.regs = regs;
        ItemContext.install(regs);
    }

    /** 전리품 스택 → 서버 아이템 스택(아이템 id·개수·컴포넌트 패치). */
    public ItemStack toItemStack(LootStack s) {
        if (s.isEmpty()) return ItemStack.EMPTY;
        Integer id = regs.id("minecraft:item", ResourceId.parse(s.item()));
        if (id == null) return ItemStack.EMPTY;
        return new ItemStack(id, s.count(), patch(s.item(), s.patch()));
    }

    /** 이름 → 값(null = 지움) 패치를 정규화. 받을 수 없는 컴포넌트는 경고를 남기고 뺀다. */
    public static ComponentPatch patch(String item, Map<String, Object> raw) {
        if (raw.isEmpty()) return ComponentPatch.EMPTY;
        try {
            return ComponentPatch.of(raw).normalizedFor(item);
        } catch (CodecException e) {
            java.util.LinkedHashMap<String, Object> ok = new java.util.LinkedHashMap<>();
            for (var en : raw.entrySet()) {
                try {
                    ComponentPatch.of(java.util.Collections.singletonMap(en.getKey(), en.getValue()));
                    ok.put(en.getKey(), en.getValue());
                } catch (CodecException ex) {
                    log.warn("아이템 {} 의 컴포넌트 {} 를 받지 못함: {}", item, en.getKey(), ex.getMessage());
                }
            }
            return ComponentPatch.of(ok).normalizedFor(item);
        }
    }
}
