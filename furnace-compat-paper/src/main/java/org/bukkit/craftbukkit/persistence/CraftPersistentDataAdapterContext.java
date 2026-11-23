package org.bukkit.craftbukkit.persistence;

import org.bukkit.persistence.PersistentDataAdapterContext;

/** Paper CraftPersistentDataAdapterContext — 사용자 데이터 종류가 중첩 묶음을 만들 때 받는 문맥(T-264 P2c-4). 새 묶음은 빈 메모리 맵이다. */
public final class CraftPersistentDataAdapterContext implements PersistentDataAdapterContext {

    CraftPersistentDataAdapterContext() {}

    @Override
    public CraftPersistentDataContainer newPersistentDataContainer() {
        return new CraftPersistentDataContainer(new org.domi.furnace.compat.paper.ItemPdc(null));
    }
}
