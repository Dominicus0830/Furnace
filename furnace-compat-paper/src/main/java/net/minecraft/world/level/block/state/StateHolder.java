package net.minecraft.world.level.block.state;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 속성 값 묶음을 가진 상태(Mojang 이름 shim, N0). 예외 문구·toString({@code Block{minecraft:oak_stairs}[facing=north,half=bottom,…]})·
 * {@code cycle} 의 다음 값 고르기는 바닐라 1.21.1 {@code dte} 와 같다. 값 바꾸기는 바닐라처럼 미리 만든 상태 표에서 찾아 같은 객체를 돌려준다.
 */
public abstract class StateHolder<O, S> {

    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";

    protected final O owner;
    private final Map<Property<?>, Comparable<?>> values;

    protected StateHolder(O owner, Map<Property<?>, Comparable<?>> values) {
        this.owner = owner;
        this.values = org.domi.furnace.compat.nms.NmsCollections.identityOrderedMap(values);   // 바닐라처럼 동일 객체로만 찾는다
    }

    /** 이 상태에서 한 속성만 바꾼 상태(같은 주인의 표에서 찾는다). 값이 허용되는지는 부르는 쪽이 이미 확인했다. */
    protected abstract S withValue(Property<?> property, Comparable<?> value);

    public <T extends Comparable<T>> S cycle(Property<T> property) { return setValue(property, findNextInCollection(property.getPossibleValues(), getValue(property))); }

    protected static <T> T findNextInCollection(Collection<T> values, T current) {
        Iterator<T> it = values.iterator();
        while (it.hasNext()) {
            if (it.next().equals(current)) return it.hasNext() ? it.next() : values.iterator().next();
        }
        return it.next();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(owner);
        if (!values.isEmpty()) {
            sb.append('[');
            sb.append(values.entrySet().stream().map(StateHolder::entry).collect(Collectors.joining(",")));
            sb.append(']');
        }
        return sb.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String entry(Map.Entry<Property<?>, Comparable<?>> e) {
        Property p = e.getKey();
        return p.getName() + "=" + p.getName((Comparable) e.getValue());
    }

    public Collection<Property<?>> getProperties() { return Collections.unmodifiableCollection(values.keySet()); }

    public <T extends Comparable<T>> boolean hasProperty(Property<T> property) { return values.containsKey(property); }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        Comparable<?> v = values.get(property);
        if (v == null) throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + owner);
        return property.getValueClass().cast(v);
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        Comparable<?> v = values.get(property);
        return v == null ? Optional.empty() : Optional.of(property.getValueClass().cast(v));
    }

    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        Comparable<?> current = values.get(property);
        if (current == null) throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + owner);
        if (current.equals(value)) return (S) this;
        if (!property.getPossibleValues().contains(value)) {
            throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + owner + ", it is not an allowed value");
        }
        return withValue(property, value);
    }

    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        return hasProperty(property) ? setValue(property, value) : (S) this;
    }

    public Map<Property<?>, Comparable<?>> getValues() { return values; }
}
