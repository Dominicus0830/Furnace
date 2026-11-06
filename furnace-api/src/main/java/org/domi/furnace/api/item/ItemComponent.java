// 아이템 컴포넌트 키 — 컴포넌트 id(레지스트리 키)와 API 값 종류를 묶은 것. 값은 마인크래프트 버전과 무관한 API 타입이고,
// 버전별 저장 꼴과의 변환은 서버가 맡는다(그래서 같은 코드가 이후 버전 서버에서도 돈다).
// 상수는 ItemComponents. 목록에 없는 컴포넌트는 raw(id) 로 SNBT 문자열 그대로 다룬다.
package org.domi.furnace.api.item;

import java.util.Objects;

public final class ItemComponent<T> {

    private final String id;
    private final Class<?> valueType;
    private final boolean raw;

    ItemComponent(String id, Class<?> valueType) { this(id, valueType, false); }

    private ItemComponent(String id, Class<?> valueType, boolean raw) {
        this.id = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.raw = raw;
    }

    /** 어떤 컴포넌트든 SNBT 문자열 그대로 읽고 쓰는 키. 서버가 모르는 id 면 읽고 쓸 때 IllegalArgumentException. */
    public static ItemComponent<String> raw(String id) {
        return new ItemComponent<>(Objects.requireNonNull(id, "id"), String.class, true);
    }

    /** "minecraft:custom_name" 꼴. */
    public String id() { return id; }

    /** raw(id) 로 만든 SNBT 문자열 키인가. */
    public boolean isRaw() { return raw; }

    /** 값의 자바 타입(목록이면 List.class). */
    public Class<?> valueType() { return valueType; }

    @Override
    public boolean equals(Object o) { return o instanceof ItemComponent<?> c && c.id.equals(id) && c.valueType == valueType && c.raw == raw; }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "ItemComponent[" + id + "]"; }
}
