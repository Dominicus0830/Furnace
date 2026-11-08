package org.bukkit.craftbukkit.metadata;

import org.bukkit.block.Block;
import org.bukkit.metadata.MetadataStore;
import org.bukkit.metadata.MetadataStoreBase;

/** 블록 메타데이터 — 월드 UID 와 칸 좌표로 가른다(블록이 바뀌어도 칸에 남는다 — Paper 와 같다). */
public final class BlockMetadataStore extends MetadataStoreBase<Block> implements MetadataStore<Block> {

    @Override
    protected String disambiguate(Block block, String metadataKey) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ() + ":" + metadataKey;
    }
}
