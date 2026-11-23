package org.bukkit.craftbukkit.persistence;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Paper CraftPersistentDataContainer — 플러그인이 받는 영속 데이터 묶음은 아이템 메타·엔티티·블록 엔티티·중첩 묶음 모두 이 한 클래스다
 * (Paper 관측표 itemmeta.*.pdc·entity.*.pdc, T-264 P2c-4). Furnace 는 저장 방식이 둘이라 몸을 따로 둔다: 메모리 맵(ItemPdc — 메타·스냅숏·중첩)과
 * 주인 스레드에서 지금 값을 읽고 쓰는 살아 있는 몸(LivePdc — 엔티티·놓인 블록 엔티티). 이 클래스는 그 몸에 그대로 맡기고 어댑터 문맥만 Paper 처럼
 * 따로 쥔다. 생성자·{@link #furnaceBody} 는 Furnace 전용이라 패키지 전용이고 호환 계층은 PdcViews 로 만든다.
 */
public class CraftPersistentDataContainer extends io.papermc.paper.persistence.PaperPersistentDataContainerView implements PersistentDataContainer {

    private final PersistentDataContainer body;
    private final CraftPersistentDataAdapterContext adapterContext = new CraftPersistentDataAdapterContext();

    CraftPersistentDataContainer(PersistentDataContainer body) {
        this.body = body;
        if (body instanceof org.domi.furnace.compat.paper.PdcBody b) b.useContext(adapterContext);
    }

    PersistentDataContainer furnaceBody() { return body; }

    private static PersistentDataContainer unwrap(PersistentDataContainer c) { return c instanceof CraftPersistentDataContainer w ? w.body : c; }

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) { body.set(key, type, value); }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) { return body.has(key, type); }

    @Override
    public boolean has(NamespacedKey key) { return body.has(key); }

    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) { return body.get(key, type); }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) { return body.getOrDefault(key, type, defaultValue); }

    @Override
    public Set<NamespacedKey> getKeys() { return body.getKeys(); }

    @Override
    public void remove(NamespacedKey key) { body.remove(key); }

    @Override
    public boolean isEmpty() { return body.isEmpty(); }

    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) { body.copyTo(unwrap(other), replace); }

    @Override
    public PersistentDataAdapterContext getAdapterContext() { return adapterContext; }

    @Override
    public byte[] serializeToBytes() throws java.io.IOException { return body.serializeToBytes(); }

    @Override
    public void readFromBytes(byte[] bytes, boolean clear) throws java.io.IOException { body.readFromBytes(bytes, clear); }

    @Override
    public boolean equals(Object o) { return o instanceof CraftPersistentDataContainer c && body.equals(c.body); }

    @Override
    public int hashCode() { return body.hashCode(); }
}
