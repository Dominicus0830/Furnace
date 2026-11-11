package net.minecraft.world.level.block.state.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 정수 속성(Mojang 이름 shim, N0). 범위 검사 문구·getValue 규칙·equals·hashCode 는 바닐라 {@code duc} 와 같다. */
public class IntegerProperty extends Property<Integer> {

    private final Collection<Integer> values;
    private final int min;
    private final int max;

    protected IntegerProperty(String name, int min, int max) {
        super(name, Integer.class);
        if (min < 0) throw new IllegalArgumentException("Min value of " + name + " must be 0 or greater");
        if (max <= min) throw new IllegalArgumentException("Max value of " + name + " must be greater than min (" + min + ")");
        this.min = min;
        this.max = max;
        List<Integer> vs = new ArrayList<>();
        for (int i = min; i <= max; i++) vs.add(i);
        this.values = org.domi.furnace.compat.nms.NmsCollections.orderedSet(vs);
    }

    public static IntegerProperty create(String name, int min, int max) { return new IntegerProperty(name, min, max); }

    @Override
    public Collection<Integer> getPossibleValues() { return values; }

    @Override
    public Optional<Integer> getValue(String name) {
        try {
            Integer v = Integer.valueOf(name);
            return v >= min && v <= max ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String getName(Integer value) { return value.toString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof IntegerProperty p && super.equals(o) && values.equals(p.values);
    }

    @Override
    public int generateHashCode() { return 31 * super.generateHashCode() + values.hashCode(); }
}
