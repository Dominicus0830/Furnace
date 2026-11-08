package org.bukkit.craftbukkit.metadata;

import org.bukkit.entity.Entity;
import org.bukkit.metadata.MetadataStore;
import org.bukkit.metadata.MetadataStoreBase;

/** 엔티티 메타데이터 — UUID 로 가른다(Paper 와 같다: 청크가 내려갔다 다시 올라온 같은 엔티티도 같은 값, 엔티티가 사라져도 남는다). */
public final class EntityMetadataStore extends MetadataStoreBase<Entity> implements MetadataStore<Entity> {

    @Override
    protected String disambiguate(Entity entity, String metadataKey) { return entity.getUniqueId() + ":" + metadataKey; }
}
