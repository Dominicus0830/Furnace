package org.bukkit.persistence;

/** 영속 데이터 컨테이너를 가진 대상(아이템 메타 등). */
public interface PersistentDataHolder {

    PersistentDataContainer getPersistentDataContainer();
}
