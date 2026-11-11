// 블록 종류 하나의 정의 — 가진 속성과 가능한 값, 기본 상태. 서버가 가진 상태 표를 그대로 보여 준다(읽기 전용, 아무 스레드).
package org.domi.furnace.api.world;

import java.util.List;
import java.util.Optional;

public interface BlockType {

    /** 레지스트리 키(minecraft:oak_stairs). */
    String id();

    /** 이 블록이 가진 속성(이름순). 없으면 빈 목록. */
    List<PropertyInfo> properties();

    default Optional<PropertyInfo> property(String name) {
        for (PropertyInfo p : properties()) if (p.name().equals(name)) return Optional.of(p);
        return Optional.empty();
    }

    default boolean hasProperty(BlockProperty<?> property) { return property(property.name()).isPresent(); }

    /** 속성을 하나도 적지 않았을 때의 상태. */
    BlockData defaultData();

    /** 가능한 상태의 수. */
    int stateCount();

    /** 이 블록의 아이템 키(바닐라 {@code Block.asItem} — 벽 횃불은 minecraft:torch). 아이템이 없는 블록이면 minecraft:air. */
    default String item() { throw new UnsupportedOperationException("item"); }

    /** data 가 이 종류의 올바른 상태인가(종류가 같고, 모든 속성이 있으며 값이 가능한 값). */
    default boolean isValid(BlockData data) {
        if (!data.type().equals(id())) return false;
        for (var e : data.properties().entrySet()) {
            var info = property(e.getKey());
            if (info.isEmpty() || !info.get().values().contains(e.getValue())) return false;
        }
        return true;
    }

    /** 속성 하나의 정의. values 는 가능한 값 문자열 전부(바닐라 순서). */
    record PropertyInfo(String name, Kind kind, List<String> values) {

        public enum Kind { BOOLEAN, INTEGER, ENUM }

        public PropertyInfo {
            values = List.copyOf(values);
        }

        public boolean accepts(String value) { return values.contains(value); }

        /** 타입 있는 키로 읽을 수 있는가(이름이 같고 종류가 맞는다). */
        public boolean matches(BlockProperty<?> property) {
            if (!name.equals(property.name())) return false;
            Class<?> t = property.type();
            return switch (kind) {
                case BOOLEAN -> t == Boolean.class;
                case INTEGER -> t == Integer.class;
                case ENUM -> t != Boolean.class && t != Integer.class;
            };
        }
    }
}
