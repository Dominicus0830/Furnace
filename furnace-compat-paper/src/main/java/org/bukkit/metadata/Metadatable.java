package org.bukkit.metadata;

import java.util.List;
import org.bukkit.plugin.Plugin;

/** 메타데이터를 붙일 수 있는 대상. 같은 열쇠에 플러그인마다 값 하나. */
public interface Metadatable {

    /** 값을 붙인다(같은 플러그인의 같은 열쇠는 바꾼다). 값·값의 플러그인이 null 이면 IllegalArgumentException. */
    void setMetadata(String metadataKey, MetadataValue newMetadataValue);

    /** 그 열쇠의 값들(플러그인마다 하나). 없으면 빈 목록. */
    List<MetadataValue> getMetadata(String metadataKey);

    boolean hasMetadata(String metadataKey);

    /** 그 플러그인이 붙인 값을 뗀다. */
    void removeMetadata(String metadataKey, Plugin owningPlugin);
}
