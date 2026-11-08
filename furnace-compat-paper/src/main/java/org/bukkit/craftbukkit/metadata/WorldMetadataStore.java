package org.bukkit.craftbukkit.metadata;

import org.bukkit.World;
import org.bukkit.metadata.MetadataStore;
import org.bukkit.metadata.MetadataStoreBase;

/** 월드 메타데이터 — 월드 UID 로 가른다. */
public final class WorldMetadataStore extends MetadataStoreBase<World> implements MetadataStore<World> {

    @Override
    protected String disambiguate(World world, String metadataKey) { return world.getUID() + ":" + metadataKey; }
}
