package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.block.state.properties.Tilt;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.domi.furnace.api.world.BlockType;

/**
 * 서버 블록 종류 표의 속성 정의(이름·종류·값 문자열)를 바닐라 Property shim 으로 바꾼다. 같은 (이름, 값 종류, 값 목록)은 같은 객체라
 * 바닐라 BlockStateProperties 처럼 블록 사이에서 나눠 쓴다. 열거 값 종류는 값 이름 집합으로 고른다 — 값이 정확히 같은 열거가 있으면 그것,
 * 아니면 값을 모두 담는 열거가 하나뿐일 때 그것(난간 레일 6방향 → RailShape, 호퍼 방향 → Direction, 지옥 문 축 → Axis). 둘 이상이면 실패한다.
 */
public final class NmsBlockProperties {

    private NmsBlockProperties() {}

    private static final List<Class<? extends Enum<?>>> ENUMS = List.of(
            Direction.class, Direction.Axis.class, FrontAndTop.class, AttachFace.class, BambooLeaves.class, BedPart.class, BellAttachType.class,
            ChestType.class, ComparatorMode.class, DoorHingeSide.class, DoubleBlockHalf.class, DripstoneThickness.class, Half.class,
            NoteBlockInstrument.class, PistonType.class, RailShape.class, RedstoneSide.class, SculkSensorPhase.class, SlabType.class,
            StairsShape.class, StructureMode.class, Tilt.class, WallSide.class, TrialSpawnerState.class, VaultState.class);

    private static final Map<String, Property<?>> INTERN = new ConcurrentHashMap<>();

    /** 속성 정의 하나의 shim. 값 순서는 정의의 순서(바닐라 순서)를 그대로 쓴다. */
    public static Property<?> of(BlockType.PropertyInfo info) {
        return INTERN.computeIfAbsent(info.kind() + "|" + info.name() + "|" + String.join(",", info.values()), k -> create(info));
    }

    private static Property<?> create(BlockType.PropertyInfo info) {
        return switch (info.kind()) {
            case BOOLEAN -> BooleanProperty.create(info.name());
            case INTEGER -> integer(info);
            case ENUM -> enumProperty(info);
        };
    }

    private static Property<?> integer(BlockType.PropertyInfo info) {
        List<String> vs = info.values();
        int min = Integer.parseInt(vs.get(0)), max = Integer.parseInt(vs.get(vs.size() - 1));
        IntegerProperty p = IntegerProperty.create(info.name(), min, max);
        if (!names(p).equals(vs)) throw new IllegalStateException("정수 속성 값이 연속이 아니다: " + info);
        return p;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Property<?> enumProperty(BlockType.PropertyInfo info) {
        Set<String> want = new HashSet<>(info.values());
        Class<? extends Enum<?>> exact = null, only = null;
        int containing = 0;
        for (Class<? extends Enum<?>> c : ENUMS) {
            Set<String> have = new HashSet<>();
            for (Enum<?> e : c.getEnumConstants()) have.add(((StringRepresentable) e).getSerializedName());
            if (have.equals(want)) exact = c;
            if (have.containsAll(want)) {
                containing++;
                only = c;
            }
        }
        Class<? extends Enum<?>> c = exact != null ? exact : containing == 1 ? only : null;
        if (c == null) throw new IllegalStateException("값 종류를 정할 수 없는 열거 속성: " + info);
        List values = new ArrayList<>();
        for (String v : info.values()) {
            for (Enum<?> e : c.getEnumConstants()) if (((StringRepresentable) e).getSerializedName().equals(v)) values.add(e);
        }
        Property<?> p = c == Direction.class ? DirectionProperty.create(info.name(), (List<Direction>) values)
                : EnumProperty.create(info.name(), (Class) c, (java.util.Collection) values);
        if (!names(p).equals(info.values())) throw new IllegalStateException("열거 속성 값이 어긋난다: " + info);
        return p;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> names(Property p) {
        List<String> out = new ArrayList<>();
        for (Object v : p.getPossibleValues()) out.add(p.getName((Comparable) v));
        return out;
    }
}
