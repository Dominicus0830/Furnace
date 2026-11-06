// 자주 쓰는 블록 속성 키. 블록이 그 속성을 가졌는지와 값 범위는 {@link BlockType} 으로 확인한다.
package org.domi.furnace.api.world;

public final class BlockProperties {

    private BlockProperties() {}

    // ---- 불리언
    public static final BlockProperty<Boolean> WATERLOGGED = BlockProperty.bool("waterlogged");
    public static final BlockProperty<Boolean> LIT = BlockProperty.bool("lit");
    public static final BlockProperty<Boolean> POWERED = BlockProperty.bool("powered");
    public static final BlockProperty<Boolean> OPEN = BlockProperty.bool("open");
    public static final BlockProperty<Boolean> SNOWY = BlockProperty.bool("snowy");
    public static final BlockProperty<Boolean> TRIGGERED = BlockProperty.bool("triggered");
    public static final BlockProperty<Boolean> ATTACHED = BlockProperty.bool("attached");
    public static final BlockProperty<Boolean> HANGING = BlockProperty.bool("hanging");
    public static final BlockProperty<Boolean> PERSISTENT = BlockProperty.bool("persistent");
    public static final BlockProperty<Boolean> ENABLED = BlockProperty.bool("enabled");
    public static final BlockProperty<Boolean> EXTENDED = BlockProperty.bool("extended");
    public static final BlockProperty<Boolean> LOCKED = BlockProperty.bool("locked");
    public static final BlockProperty<Boolean> INVERTED = BlockProperty.bool("inverted");
    public static final BlockProperty<Boolean> CONDITIONAL = BlockProperty.bool("conditional");
    public static final BlockProperty<Boolean> OCCUPIED = BlockProperty.bool("occupied");
    public static final BlockProperty<Boolean> UNSTABLE = BlockProperty.bool("unstable");
    public static final BlockProperty<Boolean> BERRIES = BlockProperty.bool("berries");

    // ---- 정수(범위는 블록마다 다르다)
    public static final BlockProperty<Integer> AGE = BlockProperty.integer("age");
    public static final BlockProperty<Integer> LEVEL = BlockProperty.integer("level");
    public static final BlockProperty<Integer> POWER = BlockProperty.integer("power");
    public static final BlockProperty<Integer> DISTANCE = BlockProperty.integer("distance");
    public static final BlockProperty<Integer> MOISTURE = BlockProperty.integer("moisture");
    public static final BlockProperty<Integer> BITES = BlockProperty.integer("bites");
    public static final BlockProperty<Integer> CHARGES = BlockProperty.integer("charges");
    public static final BlockProperty<Integer> CANDLES = BlockProperty.integer("candles");
    public static final BlockProperty<Integer> LAYERS = BlockProperty.integer("layers");
    public static final BlockProperty<Integer> PICKLES = BlockProperty.integer("pickles");
    public static final BlockProperty<Integer> ROTATION = BlockProperty.integer("rotation");
    public static final BlockProperty<Integer> DELAY = BlockProperty.integer("delay");
    public static final BlockProperty<Integer> STAGE = BlockProperty.integer("stage");
    public static final BlockProperty<Integer> EGGS = BlockProperty.integer("eggs");
    public static final BlockProperty<Integer> HATCH = BlockProperty.integer("hatch");
    public static final BlockProperty<Integer> HONEY_LEVEL = BlockProperty.integer("honey_level");
    public static final BlockProperty<Integer> NOTE = BlockProperty.integer("note");

    // ---- 열거
    public static final BlockProperty<Direction> FACING = BlockProperty.of("facing", Direction.class);
    public static final BlockProperty<Axis> AXIS = BlockProperty.of("axis", Axis.class);

    // 값 집합이 블록마다 다른 열거 — 값 이름을 그대로 쓴다(half: top/bottom 또는 upper/lower, shape, part, type, face …)
    public static final BlockProperty<String> HALF = BlockProperty.text("half");
    public static final BlockProperty<String> SHAPE = BlockProperty.text("shape");
    public static final BlockProperty<String> PART = BlockProperty.text("part");
    public static final BlockProperty<String> TYPE = BlockProperty.text("type");
    public static final BlockProperty<String> FACE = BlockProperty.text("face");
    public static final BlockProperty<String> MODE = BlockProperty.text("mode");
    public static final BlockProperty<String> INSTRUMENT = BlockProperty.text("instrument");
}
