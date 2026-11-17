package org.bukkit.util;

/**
 * Paper 1.21.1 OldEnum: 열거형에서 레지스트리 값으로 바뀐 형(무늬 종류 등)이 옛 열거형 메서드(name·ordinal·compareTo)를 이어 주는 바탕.
 * Paper 도 없앨 예정으로 표시해 두었다 — 새 코드는 getKey 를 쓴다.
 */
@Deprecated(since = "1.21", forRemoval = true)
public interface OldEnum<T extends OldEnum<T>> extends Comparable<T> {

    @Deprecated(since = "1.21", forRemoval = true)
    @Override
    int compareTo(T other);

    /** 옛 열거형 이름(minecraft 값은 대문자 경로, 예: STRIPE_BOTTOM — 다른 이름공간은 키 전체). */
    @Deprecated(since = "1.21", forRemoval = true)
    String name();

    /** 만들어진 차례(서버마다 다를 수 있어 저장하지 말 것). */
    @Deprecated(since = "1.21", forRemoval = true)
    int ordinal();
}
