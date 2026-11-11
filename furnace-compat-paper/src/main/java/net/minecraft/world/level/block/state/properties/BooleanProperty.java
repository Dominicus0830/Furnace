package net.minecraft.world.level.block.state.properties;

import java.util.Collection;
import java.util.Optional;

/** 참/거짓 속성(Mojang 이름 shim, N0). 값 순서 [true, false]·equals·hashCode 는 바닐라 {@code dtt} 와 같다. */
public class BooleanProperty extends Property<Boolean> {

    private final Collection<Boolean> values = org.domi.furnace.compat.nms.NmsCollections.orderedSet(java.util.List.of(true, false));

    protected BooleanProperty(String name) { super(name, Boolean.class); }

    public static BooleanProperty create(String name) { return new BooleanProperty(name); }

    @Override
    public Collection<Boolean> getPossibleValues() { return values; }

    @Override
    public Optional<Boolean> getValue(String name) {
        return "true".equals(name) || "false".equals(name) ? Optional.of(Boolean.valueOf(name)) : Optional.empty();
    }

    @Override
    public String getName(Boolean value) { return value.toString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BooleanProperty p && super.equals(o) && values.equals(p.values);
    }

    @Override
    public int generateHashCode() { return 31 * super.generateHashCode() + values.hashCode(); }
}
