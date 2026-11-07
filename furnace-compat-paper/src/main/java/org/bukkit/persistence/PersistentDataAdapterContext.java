package org.bukkit.persistence;

/** 사용자 정의 {@link PersistentDataType} 이 중첩 컨테이너를 만들 때 쓰는 입구. */
public interface PersistentDataAdapterContext {

    PersistentDataContainer newPersistentDataContainer();
}
