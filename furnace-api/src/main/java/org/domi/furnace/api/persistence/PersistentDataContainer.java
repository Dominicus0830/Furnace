// 고칠 수 있는 영속 데이터. 엔티티·청크의 것은 editPersistentData 콜백 안(소유 region 스레드)에서만 받는다 — 콜백 밖으로 들고 나가 쓰지 않는다.
// 저장 위치는 Paper 와 같다: 엔티티 NBT 의 BukkitValues, 청크 NBT 의 ChunkBukkitValues.
package org.domi.furnace.api.persistence;

public interface PersistentDataContainer extends PersistentDataView {

    /** value 가 null 이면 IllegalArgumentException(지우려면 remove). */
    <T> void set(String key, PersistentDataType<T> type, T value);

    void remove(String key);

    /** 중첩 값(TAG_CONTAINER)으로 넣을 빈 컨테이너. */
    PersistentDataContainer newContainer();
}
