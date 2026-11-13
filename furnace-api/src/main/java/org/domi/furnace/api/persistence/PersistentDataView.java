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

    /**
     * 저장된 값을 종류를 묻지 않고 그대로 읽는다(없으면 null): 단일 값은 Byte·Short·Integer·Long·Float·Double·String·배열 사본,
     * 중첩 컨테이너는 {@link PersistentDataView}, 목록은 {@link java.util.List}(원소는 같은 규칙, 안쪽 목록의 원소 종류는 서로 달라도 된다).
     * 호환 계층이 값의 모양을 한 번에 알아내는 길이다 — 종류를 하나씩 물어보는 것보다 싸고 깊이 한도가 없다.
     */
    default Object raw(String key) { throw new UnsupportedOperationException("raw"); }

    /** 키 목록(사본). */
    Set<String> keys();

    boolean isEmpty();
}
