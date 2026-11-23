package org.bukkit.craftbukkit.tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.domi.furnace.compat.paper.CompatTags;

/**
 * Bukkit 태그(Paper 와 같은 이름 — T-264). Furnace 게임 레지스트리의 태그를 그때그때 읽는다: 태그를 잡고 있다가 데이터 팩 리로드로 내용이 바뀌면
 * 다음 물음부터 새 내용이 보이고(태그 키를 들고 레지스트리를 매번 본다), 값이 그 레지스트리의 것이 아니면(예: 아이템 전용 Material 을 블록 태그에 물음)
 * isTagged 는 false.
 */
public abstract class CraftTag<T extends Keyed> implements Tag<T> {

    private final CompatTags.Kind kind;
    private final NamespacedKey key;
    /** getValues 가 마지막으로 만든 값 — 같은 항목 목록 객체(레지스트리 보기가 안 바뀜)면 다시 만들지 않는다. */
    private volatile CompatTags.ValuesCache cache;

    protected CraftTag(CompatTags.Kind kind, NamespacedKey key) {
        this.kind = kind;
        this.key = key;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    /** null 이 아닌 값이 이 태그에 있는가(그 레지스트리의 값이 아니면 false). */
    protected final boolean tagged(T item) {
        if (!kind.inRegistry().test(item)) return false;
        return CompatTags.registries().isTagged(kind.vanillaRegistry(), key.toString(), item.getKey().toString());
    }

    @SuppressWarnings("unchecked")
    protected final Set<T> values() {
        List<String> ids = CompatTags.registries().tag(kind.vanillaRegistry(), key.toString()).orElse(List.of());
        CompatTags.ValuesCache c = cache;
        if (c != null && c.source() == ids) return (Set<T>) c.values();
        Set<T> out = new LinkedHashSet<>();
        for (String id : ids) {
            NamespacedKey k = NamespacedKey.fromString(id);
            Keyed v = k == null ? null : kind.byKey().apply(k);
            if (v != null) out.add((T) v);
        }
        Set<T> frozen = Collections.unmodifiableSet(out);
        cache = new CompatTags.ValuesCache(ids, frozen);
        return frozen;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof CraftTag<?> t && t.kind == kind && t.key.equals(key);
    }

    @Override
    public int hashCode() { return key.hashCode(); }

    @Override
    public String toString() { return "Tag{" + kind.bukkitRegistry() + ":" + key + "}"; }
}
