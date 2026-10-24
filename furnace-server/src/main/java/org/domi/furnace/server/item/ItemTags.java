// 아이템 태그 조회(태그 리소스의 숫자 id 와 ItemStack.itemId 가 같은 레지스트리 번호다).
package org.domi.furnace.server.item;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.registry.TagSet;

public final class ItemTags {
    private static final TagSet SET = TagSet.load("registries/tags-1.21.1.txt");
    private static final Map<String, boolean[]> CACHE = new HashMap<>();

    private ItemTags() {}

    public static synchronized boolean is(ItemStack s, String tag) {
        if (s.isEmpty()) return false;
        boolean[] set = CACHE.computeIfAbsent(tag, t -> {
            int[] ids = SET.ids("minecraft:item", t);
            boolean[] b = new boolean[4096];
            if (ids != null) for (int i : ids) b[i] = true;
            return b;
        });
        return s.itemId() < set.length && set[s.itemId()];
    }
}
