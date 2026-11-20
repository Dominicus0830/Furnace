package org.bukkit.inventory.meta.trim;

import java.util.Objects;

/** 방어구에 붙이는 장식(Paper 1.21.1 ArmorTrim): 재료와 무늬. 같음은 두 값이 레지스트리의 같은 객체인지로 본다. */
public class ArmorTrim {

    private final TrimMaterial material;
    private final TrimPattern pattern;

    public ArmorTrim(TrimMaterial material, TrimPattern pattern) {
        if (material == null) throw new IllegalArgumentException("material must not be null");
        if (pattern == null) throw new IllegalArgumentException("pattern must not be null");
        this.material = material;
        this.pattern = pattern;
    }

    public TrimMaterial getMaterial() { return material; }

    public TrimPattern getPattern() { return pattern; }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(material);
        hash = 31 * hash + Objects.hashCode(pattern);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ArmorTrim other)) return false;
        return material == other.material && pattern == other.pattern;
    }
}
