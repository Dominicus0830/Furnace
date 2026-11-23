package io.papermc.paper;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.domi.furnace.compat.paper.CompatTags;

/** 게임 이벤트 태그(Paper 와 같은 이름·자리 — T-264). */
public class CraftGameEventTag extends org.bukkit.craftbukkit.tag.CraftTag<org.bukkit.GameEvent> {
    public CraftGameEventTag(CompatTags.Kind kind, NamespacedKey key) { super(kind, key); }

    @Override
    public boolean isTagged(org.bukkit.GameEvent item) {
        if (item == null) throw new IllegalArgumentException("item cannot be null");   // Paper: 레지스트리 조회라 null 이면 예외
        return tagged(item);
    }

    @Override
    public Set<org.bukkit.GameEvent> getValues() { return values(); }
}
