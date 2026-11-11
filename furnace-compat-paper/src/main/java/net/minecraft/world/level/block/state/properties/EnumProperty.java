package net.minecraft.world.level.block.state.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.StringRepresentable;

/** 열거 속성(Mojang 이름 shim, N0). 값 순서는 만든 순서, 같은 직렬화 이름 거부 문구·equals·hashCode 는 바닐라 {@code dua} 와 같다. */
public class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> {

    private final Collection<T> values;
    private final Map<String, T> names = new HashMap<>();

    protected EnumProperty(String name, Class<T> clazz, Collection<T> values) {
        super(name, clazz);
        this.values = org.domi.furnace.compat.nms.NmsCollections.orderedSet(List.copyOf(values));
        for (T v : values) {
            String key = v.getSerializedName();
            if (names.containsKey(key)) throw new IllegalArgumentException("Multiple values have the same name '" + key + "'");
            names.put(key, v);
        }
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, (Predicate<T>) v -> true);
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Predicate<T> filter) {
        return create(name, clazz, Arrays.stream(clazz.getEnumConstants()).filter(filter).toList());
    }

    @SafeVarargs
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, T... values) {
        return create(name, clazz, List.of(values));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Collection<T> values) {
        return new EnumProperty<>(name, clazz, values);
    }

    @Override
    public Collection<T> getPossibleValues() { return values; }

    @Override
    public Optional<T> getValue(String name) { return Optional.ofNullable(names.get(name)); }

    @Override
    public String getName(T value) { return value.getSerializedName(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof EnumProperty<?> p && super.equals(o) && values.equals(p.values) && names.equals(p.names);
    }

    @Override
    public int generateHashCode() {
        int h = super.generateHashCode();
        h = 31 * h + values.hashCode();
        return 31 * h + names.hashCode();
    }
}
