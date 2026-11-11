package net.minecraft.world.level.block.state.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Direction;

/** 방향 속성(Mojang 이름 shim, N0, 1.21.1 에는 아직 따로 있다). */
public class DirectionProperty extends EnumProperty<Direction> {

    protected DirectionProperty(String name, Collection<Direction> values) { super(name, Direction.class, values); }

    public static DirectionProperty create(String name) { return create(name, (Predicate<Direction>) d -> true); }

    public static DirectionProperty create(String name, Predicate<Direction> filter) {
        return create(name, Arrays.stream(Direction.values()).filter(filter).toList());
    }

    public static DirectionProperty create(String name, Direction... values) { return create(name, List.of(values)); }

    public static DirectionProperty create(String name, Collection<Direction> values) { return new DirectionProperty(name, values); }
}
