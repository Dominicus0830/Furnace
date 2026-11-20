// 아이템 컴포넌트 패치(바닐라 DataComponentPatch): 기본값에서 바뀐 컴포넌트(id → SNBT)와 지운 컴포넌트(id). 불변.
// 스택의 components·removedComponents 와 같은 모양이라 스택 없이 따로 들고 다닐 수 있다.
package org.domi.furnace.api.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record ItemComponentPatch(Map<String, String> set, Set<String> removed) {

    public static final ItemComponentPatch EMPTY = new ItemComponentPatch(Map.of(), Set.of());

    public ItemComponentPatch {
        set = set.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(set));
        removed = removed.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(removed));
    }

    public boolean isEmpty() { return set.isEmpty() && removed.isEmpty(); }
}
