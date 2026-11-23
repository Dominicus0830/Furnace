package org.bukkit.craftbukkit.tag;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.domi.furnace.compat.paper.CompatTags;

/** 유체 태그(Paper 와 같은 이름 — T-264). */
public class CraftFluidTag extends org.bukkit.craftbukkit.tag.CraftTag<org.bukkit.Fluid> {
    public CraftFluidTag(CompatTags.Kind kind, NamespacedKey key) { super(kind, key); }

    @Override
    public boolean isTagged(org.bukkit.Fluid item) {
        if (item == null) throw new IllegalArgumentException("item cannot be null");   // Paper: 레지스트리 조회라 null 이면 예외
        return tagged(item);
    }

    @Override
    public Set<org.bukkit.Fluid> getValues() { return values(); }
}
