package org.bukkit.craftbukkit.tag;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.domi.furnace.compat.paper.CompatTags;

/** 블록 태그(Paper 와 같은 이름 — T-264). */
public class CraftBlockTag extends org.bukkit.craftbukkit.tag.CraftTag<org.bukkit.Material> {
    public CraftBlockTag(CompatTags.Kind kind, NamespacedKey key) { super(kind, key); }

    @Override
    public boolean isTagged(org.bukkit.Material item) {
        if (item == null) return false;   // Paper: EnumMap 조회라 null 이면 false
        return tagged(item);
    }

    @Override
    public Set<org.bukkit.Material> getValues() { return values(); }
}
