// 엔티티의 Bukkit PersistentDataContainer — 호출마다 그 엔티티를 가진 region 스레드에서 지금 값을 읽고 쓴다(editPersistentDataNow).
// 소유 스레드가 아니면 읽기는 ThreadOwnershipException, 쓰기(set·remove)는 소유 스레드로 예약된다.
package org.domi.furnace.compat.paper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class EntityPdc extends org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer {

    private final org.domi.furnace.api.entity.Entity entity;

    public EntityPdc(org.domi.furnace.api.entity.Entity entity) { this.entity = entity; }

    @Override
    protected boolean editNow(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) { return entity.editPersistentDataNow(edit); }

    @Override
    protected CompletableFuture<Boolean> editLater(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) { return entity.editPersistentData(edit); }

    /** 엔티티가 사라졌으면 IllegalStateException. */
    @Override
    protected <T> T whenGone(Function<ItemPdc, T> op, boolean write) { throw new IllegalStateException("the entity is gone: " + entity.uniqueId()); }
}
