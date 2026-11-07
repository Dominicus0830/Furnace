package org.bukkit.persistence;

import java.util.Set;
import org.bukkit.NamespacedKey;

/** 키-값 영속 데이터. 키는 네임스페이스 키, 값은 {@link PersistentDataType} 이 정한 종류. */
public interface PersistentDataContainer extends PersistentDataAdapterContext {

    <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value);

    <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type);

    /** 종류와 상관없이 그 키가 있는가. */
    boolean has(NamespacedKey key);

    /** 없으면 null. 있는데 종류가 다르면 IllegalArgumentException. */
    <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type);

    <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue);

    Set<NamespacedKey> getKeys();

    void remove(NamespacedKey key);

    boolean isEmpty();

    /** 이 컨테이너의 내용을 다른 컨테이너로 복사한다(replace 가 거짓이면 이미 있는 키는 남긴다). */
    void copyTo(PersistentDataContainer other, boolean replace);

    PersistentDataAdapterContext getAdapterContext();

    @Override
    default PersistentDataContainer newPersistentDataContainer() { return getAdapterContext().newPersistentDataContainer(); }
}
