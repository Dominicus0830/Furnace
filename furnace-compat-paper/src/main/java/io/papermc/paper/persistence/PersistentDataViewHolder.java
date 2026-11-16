package io.papermc.paper.persistence;

/** 영속 데이터를 읽기만 하는 보기를 가진 대상(Paper {@code PersistentDataViewHolder}). {@code PersistentDataHolder}·ItemStack·OfflinePlayer 가 잇는다. */
public interface PersistentDataViewHolder {

    /**
     * 이 대상의 영속 데이터 보기. 호출할 때마다가 아니라 읽을 때마다 지금 값을 본다(Paper 와 같다 — 아이템은 지금 칸의 데이터,
     * 오프라인 플레이어는 디스크의 저장 데이터). 이 컨테이너의 키는 모두 플러그인 네임스페이스 아래라 기본 태그는 건드릴 수 없다.
     */
    PersistentDataContainerView getPersistentDataContainer();
}
