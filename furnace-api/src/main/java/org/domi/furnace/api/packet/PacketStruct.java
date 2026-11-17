// 이름 붙은 필드 묶음(불변, 순서 있음). GenericPacket 의 내용과 패킷 안에 내포된 구조(목록 원소 등)의 값으로 쓴다.
// 필드 이름은 Mojang 필드 이름을 snake_case 로 바꾼 것이다(cameraId 는 camera_id). 값은 GenericPacket 머리말의 값 종류를 따른다.
package org.domi.furnace.api.packet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.GlobalPos;

public final class PacketStruct {

    /** 값으로 쓸 수 있는 종류. 값의 "종류"는 이 목록에서 처음 맞는 것으로 정한다. */
    static final List<Class<?>> VALUE_KINDS = List.of(Integer.class, Long.class, Short.class, Byte.class, Float.class, Double.class,
            Boolean.class, String.class, UUID.class, Text.class, BlockPos.class, GlobalPos.class, BlockData.class, ItemStack.class, NbtCompound.class,
            byte[].class, int[].class, long[].class, List.class, Optional.class, PacketStruct.class);

    private static final PacketStruct EMPTY = new PacketStruct(new LinkedHashMap<>());

    private final Map<String, Object> fields;   // 바꾸지 않는다

    private PacketStruct(LinkedHashMap<String, Object> fields) { this.fields = Collections.unmodifiableMap(fields); }

    /** 빈 묶음. */
    public static PacketStruct of() { return EMPTY; }

    /** 이름과 값으로 만든다. 넣은 순서를 지키고, 배열·목록 값은 복사해 둔다. */
    public static PacketStruct of(Map<String, ?> fields) {
        Objects.requireNonNull(fields, "fields");
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        fields.forEach((k, v) -> m.put(Objects.requireNonNull(k, "name"), copyIn(k, v)));
        return new PacketStruct(m);
    }

    /** 필드 이름(순서대로). */
    public List<String> names() { return List.copyOf(fields.keySet()); }

    public boolean has(String name) { return fields.containsKey(name); }

    /** 필드 값. 없는 이름이면 IllegalArgumentException 을 던진다. 배열은 복사본을 준다. */
    public Object get(String name) {
        Objects.requireNonNull(name, "name");
        if (!fields.containsKey(name)) throw new IllegalArgumentException("no such field: " + name + " (available: " + fields.keySet() + ")");
        return copyOut(fields.get(name));
    }

    /** 타입 있는 키로 읽는다. 값 종류가 다르면 IllegalArgumentException 을 던진다. */
    public <T> T get(PacketField<T> field) {
        Object v = get(field.name());
        if (!field.valueClass().isInstance(v)) {
            throw new IllegalArgumentException(field.name() + " is a " + kindName(v) + " value (tried to read it as " + field.valueClass().getSimpleName() + ")");
        }
        return field.valueClass().cast(v);
    }

    /**
     * 그 필드만 바꾼 새 묶음. 이미 있는 필드면 같은 종류의 값이어야 한다(Integer 자리에는 Integer). 없는 필드면 끝에 붙는다.
     * 값이 null 이거나 쓸 수 없는 종류면 IllegalArgumentException 을 던진다.
     */
    public PacketStruct with(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Object v = copyIn(name, value);
        if (fields.containsKey(name)) {
            Class<?> was = kindOf(fields.get(name)), now = kindOf(v);
            if (was != now) throw new IllegalArgumentException(name + " must be a " + was.getSimpleName() + " value: " + now.getSimpleName());
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>(fields);
        m.put(name, v);
        return new PacketStruct(m);
    }

    public <T> PacketStruct with(PacketField<T> field, T value) { return with(field.name(), value); }

    /** 읽기 전용 이름-값 보기. 배열과 NbtCompound 는 원본이므로 바꾸지 않는다. 바꿀 값은 get 으로 복사본을 받아 쓴다. */
    public Map<String, Object> asMap() { return fields; }

    // ------------------------------------------------------------------ 값

    /** 값의 종류(VALUE_KINDS 중 하나). 쓸 수 없는 값이면 IllegalArgumentException 을 던진다. */
    static Class<?> kindOf(Object v) {
        for (Class<?> c : VALUE_KINDS) if (c.isInstance(v)) return c;
        throw new IllegalArgumentException("kind that cannot be a packet field value: " + (v == null ? "null" : v.getClass().getName()));
    }

    private static String kindName(Object v) {
        try {
            return kindOf(v).getSimpleName();
        } catch (IllegalArgumentException e) {
            return String.valueOf(v);
        }
    }

    private static Object copyIn(String name, Object v) {
        if (v == null) throw new IllegalArgumentException(name + ": null is not allowed (use Optional for values that may be absent)");
        try {
            kindOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + ": " + e.getMessage());
        }
        return switch (v) {
            case byte[] a -> a.clone();
            case int[] a -> a.clone();
            case long[] a -> a.clone();
            case NbtCompound n -> n.copy();   // 가변이라 패킷 밖과 나누지 않는다
            case Collection<?> c -> {
                List<Object> l = new ArrayList<>(c.size());
                int i = 0;
                for (Object o : c) l.add(copyIn(name + "[" + i++ + "]", o));
                yield Collections.unmodifiableList(l);
            }
            case Optional<?> o -> o.isEmpty() ? o : Optional.of(copyIn(name, o.get()));
            default -> v;
        };
    }

    /** 꺼낼 때 바꿀 수 있는 값(배열·NbtCompound)은 복사본을 준다. 목록이나 Optional 안에 있어도 마찬가지다. */
    private static Object copyOut(Object v) {
        return switch (v) {
            case byte[] a -> a.clone();
            case int[] a -> a.clone();
            case long[] a -> a.clone();
            case NbtCompound n -> n.copy();
            case List<?> l -> {
                if (l.stream().noneMatch(PacketStruct::mutable)) yield l;
                List<Object> c = new ArrayList<>(l.size());
                for (Object o : l) c.add(copyOut(o));
                yield Collections.unmodifiableList(c);
            }
            case Optional<?> o -> o.isPresent() && mutable(o.get()) ? Optional.of(copyOut(o.get())) : o;
            default -> v;
        };
    }

    private static boolean mutable(Object v) {
        return switch (v) {
            case byte[] a -> true;
            case int[] a -> true;
            case long[] a -> true;
            case NbtCompound n -> true;
            case List<?> l -> l.stream().anyMatch(PacketStruct::mutable);
            case Optional<?> o -> o.isPresent() && mutable(o.get());
            default -> false;
        };
    }

    static boolean same(Object a, Object b) {
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!same(x.get(i), y.get(i))) return false;
            return true;
        }
        if (a instanceof Optional<?> x && b instanceof Optional<?> y) return x.isPresent() == y.isPresent() && (x.isEmpty() || same(x.get(), y.get()));
        return Objects.deepEquals(a, b);
    }

    static int hash(Object v) {
        return switch (v) {
            case byte[] a -> Arrays.hashCode(a);
            case int[] a -> Arrays.hashCode(a);
            case long[] a -> Arrays.hashCode(a);
            case List<?> l -> {
                int h = 1;
                for (Object o : l) h = 31 * h + hash(o);
                yield h;
            }
            case Optional<?> o -> o.isEmpty() ? 0 : 31 + hash(o.get());
            default -> v.hashCode();
        };
    }

    static String show(Object v) {
        return switch (v) {
            case byte[] a -> "bytes[" + a.length + "]";
            case int[] a -> Arrays.toString(a);
            case long[] a -> Arrays.toString(a);
            default -> String.valueOf(v);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PacketStruct s) || !s.fields.keySet().equals(fields.keySet())) return false;
        if (!List.copyOf(s.fields.keySet()).equals(List.copyOf(fields.keySet()))) return false;
        for (var e : fields.entrySet()) if (!same(e.getValue(), s.fields.get(e.getKey()))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (var e : fields.entrySet()) h = 31 * h + (e.getKey().hashCode() ^ hash(e.getValue()));
        return h;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("{");
        for (var e : fields.entrySet()) {
            if (b.length() > 1) b.append(", ");
            b.append(e.getKey()).append('=').append(show(e.getValue()));
        }
        return b.append('}').toString();
    }
}
