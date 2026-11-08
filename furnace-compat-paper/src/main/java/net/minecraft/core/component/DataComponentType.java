package net.minecraft.core.component;

/**
 * 아이템 데이터 컴포넌트 종류(Mojang 이름 shim, H7b, 등급 B). 바닐라처럼 인터페이스이고, 상수는 {@link DataComponents} 에 있다.
 * shim 이 값 변환을 가진 종류만 상수로 있다 — 나머지 상수(DataComponents.ENCHANTMENTS 등)는 없는 필드라 읽는 자리에서 실패한다.
 */
public interface DataComponentType<T> {

    /** 호환 모듈 전용: 레지스트리 키(minecraft:custom_data). */
    String furnaceKey();
}
