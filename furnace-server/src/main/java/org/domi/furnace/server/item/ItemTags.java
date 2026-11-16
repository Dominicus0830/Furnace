// 아이템 태그 조회(태그 리소스의 숫자 id 와 ItemStack.itemId 가 같은 레지스트리 번호다).
// 태그 표는 데이터 팩이 바꾼 것(ItemContext.gameplayTags)을 읽는다. 표 객체가 바뀌면(/reload) 캐시를 새로 만든다.
package org.domi.furnace.server.item;

import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.registry.TagSet;

@VanillaPort("net.minecraft.tags.ItemTags")
public final class ItemTags {

    /** 한 태그 표에서 만든 태그별 멤버 표. */
    private record Cache(TagSet src, ConcurrentHashMap<String, boolean[]> byTag) {}

    private static volatile Cache cache = new Cache(null, new ConcurrentHashMap<>());

    private ItemTags() {}

    public static boolean is(ItemStack s, String tag) {
        if (s.isEmpty()) return false;
        TagSet live = ItemContext.gameplayTags();
        Cache c = cache;
        if (c.src != live) cache = c = new Cache(live, new ConcurrentHashMap<>());
        boolean[] set = c.byTag.get(tag);
        if (set == null) {
            TagSet src = c.src;
            set = c.byTag.computeIfAbsent(tag, t -> {
                int[] ids = src.ids("minecraft:item", t);
                int max = 4095;
                if (ids != null) for (int i : ids) max = Math.max(max, i);
                boolean[] b = new boolean[max + 1];
                if (ids != null) for (int i : ids) if (i >= 0) b[i] = true;
                return b;
            });
        }
        return s.itemId() < set.length && set[s.itemId()];
    }
}
