package org.bukkit.craftbukkit.metadata;

import org.bukkit.OfflinePlayer;
import org.bukkit.metadata.MetadataStore;
import org.bukkit.metadata.MetadataStoreBase;

/** 플레이어 메타데이터 — UUID 로 가른다(Paper 와 같다: 나갔다 들어와도 남고, 이름을 바꿔도 따라간다). */
public final class PlayerMetadataStore extends MetadataStoreBase<OfflinePlayer> implements MetadataStore<OfflinePlayer> {

    @Override
    protected String disambiguate(OfflinePlayer player, String metadataKey) { return player.getUniqueId() + ":" + metadataKey; }
}
