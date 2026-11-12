package io.papermc.paper.persistence;

import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** 영속 데이터를 읽기만 하는 보기(Paper {@code PersistentDataContainerView}). {@link PersistentDataContainer} 가 이를 잇는다. */
public interface PersistentDataContainerView {

    <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type);

    boolean has(NamespacedKey key);

    /** 없으면 null. 있는데 종류가 다르면 IllegalArgumentException. */
    <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type);

    <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue);

    Set<NamespacedKey> getKeys();

    boolean isEmpty();

    /** 이 컨테이너의 내용을 다른 컨테이너로 복사한다(replace 가 거짓이면 이미 있는 키는 남긴다). */
    void copyTo(PersistentDataContainer other, boolean replace);

    PersistentDataAdapterContext getAdapterContext();

    /**
     * 내용을 NBT 바이트로 내보낸다 — 압축하지 않은 이름 없는 compound(Paper 와 같은 꼴: 종류 10, 빈 이름, 내용, 끝 0).
     * {@link PersistentDataContainer#readFromBytes} 로 되읽는다.
     */
    byte[] serializeToBytes() throws java.io.IOException;
}
