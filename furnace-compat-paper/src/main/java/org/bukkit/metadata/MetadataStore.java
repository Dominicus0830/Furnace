package org.bukkit.metadata;

import java.util.List;
import org.bukkit.plugin.Plugin;

/** 한 종류의 대상(엔티티·플레이어·월드·블록)에 붙은 메타데이터 저장소. */
public interface MetadataStore<T> {

    void setMetadata(T subject, String metadataKey, MetadataValue newMetadataValue);

    List<MetadataValue> getMetadata(T subject, String metadataKey);

    boolean hasMetadata(T subject, String metadataKey);

    void removeMetadata(T subject, String metadataKey, Plugin owningPlugin);

    /** 그 플러그인이 붙인 모든 값을 다시 계산하게 한다. */
    void invalidateAll(Plugin owningPlugin);
}
