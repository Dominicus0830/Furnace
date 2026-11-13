// 고칠 수 있는 영속 데이터. 엔티티·청크의 것은 editPersistentData 콜백 안(소유 region 스레드)에서만 받는다 — 콜백 밖으로 들고 나가 쓰지 않는다.
// 저장 위치는 Paper 와 같다: 엔티티 NBT 의 BukkitValues, 청크 NBT 의 ChunkBukkitValues.
package org.domi.furnace.api.persistence;

public interface PersistentDataContainer extends PersistentDataView {

    /** value 가 null 이면 IllegalArgumentException(지우려면 remove). */
    <T> void set(String key, PersistentDataType<T> type, T value);

    /**
     * {@link #raw} 의 짝: 값을 모양 그대로 넣는다(단일 값은 Byte·Short·Integer·Long·Float·Double·String·배열, 중첩 컨테이너는 {@link #newContainer()} 로 만든 것,
     * 목록은 {@link java.util.List} — 안쪽 목록의 원소 종류가 서로 달라도 된다). 바깥 목록의 원소 종류가 섞였거나 지원하지 않는 값이면 IllegalArgumentException.
     */
    default void setRaw(String key, Object value) { throw new UnsupportedOperationException("setRaw"); }

    void remove(String key);

    /** 중첩 값(TAG_CONTAINER)으로 넣을 빈 컨테이너. */
    PersistentDataContainer newContainer();
}
