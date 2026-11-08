package org.bukkit.craftbukkit.metadata;

import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.metadata.MetadataStore;
import org.bukkit.metadata.MetadataStoreBase;

/** 플레이어 메타데이터 — 이름(소문자)으로 가른다(Paper 와 같다: 나갔다 들어와도 남는다). */
public final class PlayerMetadataStore extends MetadataStoreBase<OfflinePlayer> implements MetadataStore<OfflinePlayer> {

    @Override
    protected String disambiguate(OfflinePlayer player, String metadataKey) { return player.getName().toLowerCase(Locale.ROOT) + ":" + metadataKey; }
}
