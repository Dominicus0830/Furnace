// 엔티티 메타데이터 값 종류(바닐라 EntityDataSerializers 31종). 이름은 바닐라 필드 이름을 소문자로 쓴 것이고 버전과 상관없다.
// 와이어 번호(직렬화기 id)는 서버가 버전마다 정한다 — API 에는 드러나지 않는다.
//
// 자바 값: 레지스트리 값(고양이·늑대·개구리·그림 종류)은 이름 문자열("minecraft:tabby"), 스니퍼·아르마딜로 상태는 소문자 상태 이름("idling"·"scared").
package org.domi.furnace.api.entity.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.math.Quaternionf;
import org.domi.furnace.api.math.Rotations;
import org.domi.furnace.api.math.Vector3f;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.particle.Particle;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Direction;
import org.domi.furnace.api.world.GlobalPos;

public final class EntityDataType<T> {

    private static final Map<String, EntityDataType<?>> BY_NAME = new LinkedHashMap<>();

    public static final EntityDataType<Byte> BYTE = of("byte", Byte.class);
    public static final EntityDataType<Integer> INT = of("int", Integer.class);
    public static final EntityDataType<Long> LONG = of("long", Long.class);
    public static final EntityDataType<Float> FLOAT = of("float", Float.class);
    public static final EntityDataType<String> STRING = of("string", String.class);
    public static final EntityDataType<Text> COMPONENT = of("component", Text.class);
    public static final EntityDataType<Optional<Text>> OPTIONAL_COMPONENT = optional("optional_component", Text.class);
    /** 빈 스택(ItemStack.EMPTY)도 된다. */
    public static final EntityDataType<ItemStack> ITEM_STACK = of("item_stack", ItemStack.class);
    public static final EntityDataType<Boolean> BOOLEAN = of("boolean", Boolean.class);
    public static final EntityDataType<Rotations> ROTATIONS = of("rotations", Rotations.class);
    public static final EntityDataType<BlockPos> BLOCK_POS = of("block_pos", BlockPos.class);
    public static final EntityDataType<Optional<BlockPos>> OPTIONAL_BLOCK_POS = optional("optional_block_pos", BlockPos.class);
    public static final EntityDataType<Direction> DIRECTION = of("direction", Direction.class);
    public static final EntityDataType<Optional<UUID>> OPTIONAL_UUID = optional("optional_uuid", UUID.class);
    public static final EntityDataType<BlockData> BLOCK_STATE = of("block_state", BlockData.class);
    /** 비어 있으면 Optional.empty()(공기와 같다 — 바닐라도 공기를 "없음"으로 보낸다). */
    public static final EntityDataType<Optional<BlockData>> OPTIONAL_BLOCK_STATE = optional("optional_block_state", BlockData.class);
    public static final EntityDataType<NbtCompound> COMPOUND_TAG = of("compound_tag", NbtCompound.class);
    public static final EntityDataType<Particle> PARTICLE = of("particle", Particle.class);
    public static final EntityDataType<List<Particle>> PARTICLES = list("particles", Particle.class);
    public static final EntityDataType<VillagerData> VILLAGER_DATA = of("villager_data", VillagerData.class);
    /** 음수가 아닌 정수 또는 없음(엔티티 번호 등). */
    public static final EntityDataType<OptionalInt> OPTIONAL_UNSIGNED_INT = new EntityDataType<>("optional_unsigned_int", OptionalInt.class,
            v -> v instanceof OptionalInt o && (o.isEmpty() || o.getAsInt() >= 0));
    public static final EntityDataType<Pose> POSE = of("pose", Pose.class);
    public static final EntityDataType<String> CAT_VARIANT = of("cat_variant", String.class);
    public static final EntityDataType<String> WOLF_VARIANT = of("wolf_variant", String.class);
    public static final EntityDataType<String> FROG_VARIANT = of("frog_variant", String.class);
    public static final EntityDataType<Optional<GlobalPos>> OPTIONAL_GLOBAL_POS = optional("optional_global_pos", GlobalPos.class);
    public static final EntityDataType<String> PAINTING_VARIANT = of("painting_variant", String.class);
    public static final EntityDataType<String> SNIFFER_STATE = of("sniffer_state", String.class);
    public static final EntityDataType<String> ARMADILLO_STATE = of("armadillo_state", String.class);
    public static final EntityDataType<Vector3f> VECTOR3 = of("vector3", Vector3f.class);
    public static final EntityDataType<Quaternionf> QUATERNION = of("quaternion", Quaternionf.class);

    private final String name;
    private final Class<?> valueClass;
    private final Predicate<Object> accepts;

    private EntityDataType(String name, Class<?> valueClass, Predicate<Object> accepts) {
        this.name = name;
        this.valueClass = valueClass;
        this.accepts = accepts;
        BY_NAME.put(name, this);
    }

    private static <T> EntityDataType<T> of(String name, Class<T> c) { return new EntityDataType<>(name, c, c::isInstance); }

    private static <T> EntityDataType<Optional<T>> optional(String name, Class<T> inner) {
        return new EntityDataType<>(name, Optional.class, v -> v instanceof Optional<?> o && (o.isEmpty() || inner.isInstance(o.get())));
    }

    private static <T> EntityDataType<List<T>> list(String name, Class<T> inner) {
        return new EntityDataType<>(name, List.class, v -> {
            if (!(v instanceof List<?> l)) return false;
            for (Object e : l) if (!inner.isInstance(e)) return false;
            return true;
        });
    }

    /** "vector3" 같은 이름. */
    public String name() { return name; }

    /** 값의 자바 클래스(Optional·List 는 바깥 클래스). */
    public Class<?> valueClass() { return valueClass; }

    /**
     * 이 종류의 값으로 맞는지 확인하고 돌려준다. 아니면 IllegalArgumentException.
     * optional_block_state 의 공기(minecraft:air)는 Optional.empty() 로 바꾼다 — 바닐라 와이어에서 공기(0)와 없음이 같다.
     */
    @SuppressWarnings("unchecked")
    public T check(Object value) {
        Objects.requireNonNull(value, () -> name + " 값이 null 이다(없음은 Optional.empty() 로)");
        if (!accepts.test(value)) throw new IllegalArgumentException(name + " 종류에 맞지 않는 값: " + value);
        if (this == OPTIONAL_BLOCK_STATE && value instanceof Optional<?> o && o.isPresent() && o.get() instanceof BlockData b
                && b.type().equals("minecraft:air")) return (T) Optional.empty();
        return (T) value;
    }

    public static EntityDataType<?> byName(String name) { return BY_NAME.get(name); }

    /** 31종 전부(바닐라 등록 순서). */
    public static java.util.Collection<EntityDataType<?>> all() { return Collections.unmodifiableCollection(BY_NAME.values()); }

    @Override
    public String toString() { return name; }
}
