// 사망 이벤트의 아이템 목록 변환: 서버 스택 → API 값으로 내보내고, 이벤트 뒤 API 값 → 서버 스택으로 되돌린다.
// 플러그인이 손대지 않은 아이템(같은 API 값)은 변환하지 않고 원래 서버 스택 객체를 그대로 돌려준다 — API 값으로 갔다 오면
// 달라질 수 있는 컴포넌트를 지키고, 부른 쪽이 신원(==)으로 원래 드롭(이미 만든 아이템 엔티티 등)과 짝지을 수 있게.
package org.domi.furnace.server.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.ItemRegistry;

public final class ApiDrops {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Server");

    private final ItemRegistry items;
    private final List<org.domi.furnace.api.item.ItemStack> api;
    private final Map<org.domi.furnace.api.item.ItemStack, ArrayDeque<ItemStack>> originals = new HashMap<>();

    public ApiDrops(List<ItemStack> drops, ItemRegistry items) {
        this.items = items;
        this.api = new ArrayList<>(drops.size());
        for (ItemStack s : drops) {
            var a = ApiConvert.item(s, items);
            api.add(a);
            originals.computeIfAbsent(a, k -> new ArrayDeque<>()).add(s);
        }
    }

    /** 이벤트에 실을 API 값 목록(새 목록 — 이벤트가 따로 복사한다). */
    public List<org.domi.furnace.api.item.ItemStack> api() { return api; }

    /**
     * 이벤트 뒤 목록 → 서버 스택. 원래 값과 같은 항목은 원래 스택을 한 번씩만 꺼내 쓴다(같은 값이 두 번 남으면 두 번째부터는 새로 변환).
     * 비었거나 null 인 칸은 건너뛰고, 알 수 없는 아이템·컴포넌트는 경고 로그를 남기고 버린다.
     */
    public List<ItemStack> back(List<org.domi.furnace.api.item.ItemStack> now, String eventName) {
        List<ItemStack> out = new ArrayList<>(now.size());
        for (var a : now) {
            if (a == null || a.isEmpty()) continue;
            var queue = originals.get(a);
            if (queue != null && !queue.isEmpty()) {
                out.add(queue.poll());
                continue;
            }
            ItemStack s = convert(a, eventName);
            if (s != null) out.add(s);
        }
        return out;
    }

    /** back 과 같지만 원래 스택을 꺼내 가지 않는다(값만 필요한 목록 — getItemsToKeep). */
    public List<ItemStack> values(List<org.domi.furnace.api.item.ItemStack> now, String eventName) {
        List<ItemStack> out = new ArrayList<>(now.size());
        for (var a : now) {
            if (a == null || a.isEmpty()) continue;
            var queue = originals.get(a);
            if (queue != null && !queue.isEmpty()) {
                out.add(queue.peek());
                continue;
            }
            ItemStack s = convert(a, eventName);
            if (s != null) out.add(s);
        }
        return out;
    }

    private ItemStack convert(org.domi.furnace.api.item.ItemStack a, String eventName) {
        try {
            ItemStack s = ApiConvert.item(a, items);
            return s.isEmpty() ? null : s;
        } catch (IllegalArgumentException ex) {
            log.warn("{} 아이템 목록의 아이템을 버린다(알 수 없는 아이템·컴포넌트): {} — {}", eventName, a.type(), ex.getMessage());
            return null;
        }
    }
}
