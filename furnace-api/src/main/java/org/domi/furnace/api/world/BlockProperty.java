// 타입 있는 블록 속성 키. BlockData 의 문자열 맵을 직접 만지지 않고 get/with 로 값 종류(불리언·정수·열거)를 지키며 읽고 쓴다.
// 같은 이름의 속성이라도 블록마다 값 범위가 다르다(age 는 작물마다 최대가 다르다). 어느 블록에 어떤 값이 되는지는 {@link BlockType} 이 알려 준다.
// 이름과 값 문자열은 바닐라 명령과 같다(minecraft:oak_stairs[facing=north]) — 버전이 바뀌어도 속성 이름이 그대로면 같은 키로 쓴다.
package org.domi.furnace.api.world;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class BlockProperty<T> {

    private final String name;
    private final Class<T> type;
    private final Function<String, T> parser;
    private final Function<T, String> formatter;

    private BlockProperty(String name, Class<T> type, Function<String, T> parser, Function<T, String> formatter) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = type;
        this.parser = parser;
        this.formatter = formatter;
        if (name.isEmpty()) throw new IllegalArgumentException("속성 이름이 비었다");
    }

    public static BlockProperty<Boolean> bool(String name) {
        return new BlockProperty<>(name, Boolean.class, s -> {
            if (s.equals("true")) return true;
            if (s.equals("false")) return false;
            throw new IllegalArgumentException("true/false 가 아니다: " + s);
        }, b -> b ? "true" : "false");
    }

    public static BlockProperty<Integer> integer(String name) {
        return new BlockProperty<>(name, Integer.class, Integer::valueOf, String::valueOf);
    }

    /** 값 이름을 그대로 쓰는 열거형 속성(half·shape·part 처럼 블록마다 값 집합이 다른 것). */
    public static BlockProperty<String> text(String name) {
        return new BlockProperty<>(name, String.class, s -> s, s -> s);
    }

    /** 자바 열거형 속성 — 값 이름은 소문자 상수 이름. */
    public static <E extends Enum<E>> BlockProperty<E> of(String name, Class<E> enumType) {
        return new BlockProperty<>(name, enumType, s -> Enum.valueOf(enumType, s.toUpperCase(Locale.ROOT)),
                e -> e.name().toLowerCase(Locale.ROOT));
    }

    public String name() { return name; }

    public Class<T> type() { return type; }

    /** 값 문자열 → 값. 형식이 틀리면 IllegalArgumentException. */
    public T parse(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " 속성 값이 잘못됐다: " + text, e);
        }
    }

    /** 값 → 값 문자열(BlockData 에 들어가는 꼴). */
    public String format(T value) {
        Objects.requireNonNull(value, "value");
        return formatter.apply(value);
    }

    @Override
    public boolean equals(Object o) { return o instanceof BlockProperty<?> p && p.name.equals(name) && p.type == type; }

    @Override
    public int hashCode() { return name.hashCode() * 31 + type.hashCode(); }

    @Override
    public String toString() { return name + ":" + type.getSimpleName(); }
}
