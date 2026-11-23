// Bukkit 영속 데이터 묶음의 Furnace 몸(ItemPdc·LivePdc) — 플러그인에게는 CraftPersistentDataContainer 로 감싸 건넨다(T-264 P2c-4).
package org.domi.furnace.compat.paper;

import org.bukkit.persistence.PersistentDataAdapterContext;

public interface PdcBody {

    /** 감싼 묶음의 어댑터 문맥 — 사용자 데이터 종류의 toPrimitive·fromPrimitive 가 받는다(Paper 와 같은 CraftPersistentDataAdapterContext). */
    void useContext(PersistentDataAdapterContext context);
}
