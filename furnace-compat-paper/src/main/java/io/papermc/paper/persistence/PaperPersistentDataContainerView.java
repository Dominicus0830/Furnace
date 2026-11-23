package io.papermc.paper.persistence;

/**
 * Paper PaperPersistentDataContainerView — 영속 데이터 묶음과 읽기 전용 보기의 공통 상위(T-264 P2c-4). Paper 는 여기서 NBT 위에 읽기 멤버를
 * 구현하지만 Furnace 는 하위 클래스가 각자 자기 몸으로 구현한다 — 상위 사슬만 Paper 와 같다.
 */
public abstract class PaperPersistentDataContainerView implements PersistentDataContainerView {

    protected PaperPersistentDataContainerView() {}
}
