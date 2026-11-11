package net.minecraft.world.level.block.state.properties;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.StateHolder;

/**
 * 블록 상태 속성(Mojang 이름 shim, N0). equals(같은 값 종류·이름)·hashCode(31·종류 + 이름, 처음 한 번 계산)·toString(Guava
 * toStringHelper 꼴 {@code BooleanProperty{name=lit, clazz=class java.lang.Boolean, values=[true, false]}})은 바닐라 1.21.1
 * {@code duf} 바이트코드와 같다. 코덱({@code codec()}·{@code valueCodec()}·{@code parseValue})은 DataFixerUpper 가 서버에 없어 두지 않았다.
 */
public abstract class Property<T extends Comparable<T>> {

    private final Class<T> clazz;
    private final String name;
    private Integer hashCode;

    protected Property(String name, Class<T> clazz) {
        this.clazz = clazz;
        this.name = name;
    }

    public Value<T> value(T value) { return new Value<>(this, value); }

    public Value<T> value(StateHolder<?, ?> holder) { return new Value<>(this, holder.getValue(this)); }

    public Stream<Value<T>> getAllValues() { return getPossibleValues().stream().map(this::value); }

    public String getName() { return name; }

    public Class<T> getValueClass() { return clazz; }

    public abstract Collection<T> getPossibleValues();

    public abstract String getName(T value);

    public abstract Optional<T> getValue(String name);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name=" + name + ", clazz=" + clazz + ", values=" + getPossibleValues() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Property<?> p && clazz.equals(p.clazz) && name.equals(p.name);
    }

    @Override
    public final int hashCode() {
        if (hashCode == null) hashCode = generateHashCode();
        return hashCode;
    }

    public int generateHashCode() { return 31 * clazz.hashCode() + name.hashCode(); }

    /** 속성과 값 한 쌍. */
    public record Value<T extends Comparable<T>>(Property<T> property, T value) {

        public Value {
            if (!property.getPossibleValues().contains(value)) {
                throw new IllegalArgumentException("Value " + value + " does not belong to property " + property);
            }
        }

        @Override
        public String toString() { return property.getName() + "=" + property.getName(value); }
    }
}
