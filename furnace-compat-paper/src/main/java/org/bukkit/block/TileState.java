package org.bukkit.block;

/**
 * Paper 1.21.1 TileState: 블록 엔티티가 있는 블록의 상태. 영속 데이터는 그 상태(스냅숏)의 것이라 {@link #update} 해야 블록에 실린다.
 */
public interface TileState extends BlockState, org.bukkit.persistence.PersistentDataHolder {

    @Override
    org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer();

    /** Paper: 스냅숏이면 true(Furnace 블록 상태는 늘 스냅숏). */
    boolean isSnapshot();
}
