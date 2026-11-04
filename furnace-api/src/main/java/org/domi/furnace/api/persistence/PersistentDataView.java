// 영속 데이터 읽기 전용 보기. 키는 namespace:path(플러그인 이름을 namespace 로 쓴다).
// 엔티티·청크 핸들이 돌려주는 보기는 핸들을 만든 시점의 사본이다(낡을 수 있다) — 최신 값과 쓰기는 editPersistentData 로.
package org.domi.furnace.api.persistence;

import java.util.Set;

public interface PersistentDataView {

    /** 그 키가 있고 type 으로 읽을 수 있는가. */
    <T> boolean has(String key, PersistentDataType<T> type);

    /** 종류와 상관없이 그 키가 있는가. */
    boolean has(String key);

    /** 없으면 null. 있는데 종류가 다르면 IllegalArgumentException(Bukkit 과 같다). */
    <T> T get(String key, PersistentDataType<T> type);

    default <T> T getOrDefault(String key, PersistentDataType<T> type, T fallback) {
        T v = has(key, type) ? get(key, type) : null;
        return v != null ? v : fallback;
    }

    /** 키 목록(사본). */
    Set<String> keys();

    boolean isEmpty();
}
