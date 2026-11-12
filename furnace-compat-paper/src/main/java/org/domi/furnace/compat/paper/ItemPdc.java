// Bukkit PersistentDataContainer 를 Furnace 영속 데이터 위에 덮어씌우는 층 — 읽기는 원본(읽기 전용 보기)을 보고, 쓰기는 모아 두었다가
// 아이템 메타를 ItemStack 에 돌려놓을 때(applyTo) 한꺼번에 적용한다. 원본이 없으면(새 컨테이너·중첩) 메모리 안 값만 쓴다.
package org.domi.furnace.compat.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.persistence.PersistentDataView;

final class ItemPdc implements PersistentDataContainer, PersistentDataAdapterContext {

    /** 바뀐 값: Furnace 종류와 기본 종류 값. 지운 키는 null. */
    private record Value(org.domi.furnace.api.persistence.PersistentDataType<?> type, Object value) {}

    /** 단일 값 종류(NBT 태그 번호 순서 — 1~12 중 목록(9)을 뺀 것). */
    private static final org.domi.furnace.api.persistence.PersistentDataType<?>[] SCALARS = {
        org.domi.furnace.api.persistence.PersistentDataType.BYTE, org.domi.furnace.api.persistence.PersistentDataType.SHORT,
        org.domi.furnace.api.persistence.PersistentDataType.INTEGER, org.domi.furnace.api.persistence.PersistentDataType.LONG,
        org.domi.furnace.api.persistence.PersistentDataType.FLOAT, org.domi.furnace.api.persistence.PersistentDataType.DOUBLE,
        org.domi.furnace.api.persistence.PersistentDataType.STRING, org.domi.furnace.api.persistence.PersistentDataType.BYTE_ARRAY,
        org.domi.furnace.api.persistence.PersistentDataType.INTEGER_ARRAY, org.domi.furnace.api.persistence.PersistentDataType.LONG_ARRAY,
        org.domi.furnace.api.persistence.PersistentDataType.TAG_CONTAINER};

    /** 원본 값의 종류를 찾아볼 순서: 단일 값 먼저, 그다음 각 원소 종류의 목록(빈 목록은 첫 목록 종류로 잡힌다). */
    private static final org.domi.furnace.api.persistence.PersistentDataType<?>[] PROBE = probe();

    private static org.domi.furnace.api.persistence.PersistentDataType<?>[] probe() {
        var out = new org.domi.furnace.api.persistence.PersistentDataType<?>[SCALARS.length * 2];
        for (int i = 0; i < SCALARS.length; i++) {
            out[i] = SCALARS[i];
            out[SCALARS.length + i] = org.domi.furnace.api.persistence.PersistentDataType.list(SCALARS[i]);
        }
        return out;
    }

    /** 이 종류로 저장될 값 — 호출 스레드에서 미리 만들어 두었다가(prepare) 소유 스레드에서 넣는다. */
    record Prepared(org.domi.furnace.api.persistence.PersistentDataType<?> type, Object stored) {}

    private final PersistentDataView view;
    private final Map<String, Value> changes = new LinkedHashMap<>();

    ItemPdc(PersistentDataView view) { this.view = view; }

    ItemPdc copy() {
        ItemPdc c = new ItemPdc(view);
        for (var e : changes.entrySet()) c.changes.put(e.getKey(), e.getValue() == null ? null : copyValue(e.getValue()));
        return c;
    }

    private static Value copyValue(Value v) { return new Value(v.type(), copyPrim(v.value())); }

    /** 값의 깊은 복사: 중첩 컨테이너·배열·목록을 새로 만든다(꺼낸 값을 고쳐도 저장된 값이 안 바뀌게). */
    private static Object copyPrim(Object o) {
        if (o instanceof ItemPdc p) return p.copy();
        if (o instanceof PersistentDataContainer c) return copyNested(c);
        if (o instanceof byte[] a) return a.clone();
        if (o instanceof int[] a) return a.clone();
        if (o instanceof long[] a) return a.clone();
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object e : l) out.add(copyPrim(Objects.requireNonNull(e, "목록에 null 을 넣을 수 없다")));
            return out;
        }
        return o;
    }

    boolean dirty() { return !changes.isEmpty(); }

    // ------------------------------------------------------------------ 종류 대응

    /** Bukkit 종류 → 저장 종류. 목록 종류는 원소의 기본 종류로 따진다(BOOLEAN 목록은 byte 목록). */
    static org.domi.furnace.api.persistence.PersistentDataType<?> furnaceType(PersistentDataType<?, ?> type) {
        if (type instanceof ListPersistentDataType<?, ?> l) {
            return org.domi.furnace.api.persistence.PersistentDataType.list(furnaceType(l.elementType()));
        }
        Class<?> primitive = type.getPrimitiveType();
        if (primitive == PersistentDataContainer[].class) {
            return org.domi.furnace.api.persistence.PersistentDataType.list(org.domi.furnace.api.persistence.PersistentDataType.TAG_CONTAINER);
        }
        if (List.class.isAssignableFrom(primitive)) throw new IllegalArgumentException("목록 종류는 PersistentDataType.LIST 로 만든 것만 쓸 수 있다: " + type);
        return furnaceType(primitive);
    }

    private static org.domi.furnace.api.persistence.PersistentDataType<?> furnaceType(Class<?> primitive) {
        if (primitive == Byte.class) return org.domi.furnace.api.persistence.PersistentDataType.BYTE;
        if (primitive == Short.class) return org.domi.furnace.api.persistence.PersistentDataType.SHORT;
        if (primitive == Integer.class) return org.domi.furnace.api.persistence.PersistentDataType.INTEGER;
        if (primitive == Long.class) return org.domi.furnace.api.persistence.PersistentDataType.LONG;
        if (primitive == Float.class) return org.domi.furnace.api.persistence.PersistentDataType.FLOAT;
        if (primitive == Double.class) return org.domi.furnace.api.persistence.PersistentDataType.DOUBLE;
        if (primitive == String.class) return org.domi.furnace.api.persistence.PersistentDataType.STRING;
        if (primitive == byte[].class) return org.domi.furnace.api.persistence.PersistentDataType.BYTE_ARRAY;
        if (primitive == int[].class) return org.domi.furnace.api.persistence.PersistentDataType.INTEGER_ARRAY;
        if (primitive == long[].class) return org.domi.furnace.api.persistence.PersistentDataType.LONG_ARRAY;
        if (PersistentDataContainer.class.isAssignableFrom(primitive)) return org.domi.furnace.api.persistence.PersistentDataType.TAG_CONTAINER;
        throw new IllegalArgumentException("기본 종류로 저장할 수 없다: " + primitive.getName());
    }

    private static PersistentDataType<?, ?> bukkitType(org.domi.furnace.api.persistence.PersistentDataType<?> t) {
        if (t.elementType() != null) return PersistentDataType.LIST.listTypeFrom(bukkitType(t.elementType()));
        if (t == org.domi.furnace.api.persistence.PersistentDataType.BYTE) return PersistentDataType.BYTE;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.SHORT) return PersistentDataType.SHORT;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.INTEGER) return PersistentDataType.INTEGER;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.LONG) return PersistentDataType.LONG;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.FLOAT) return PersistentDataType.FLOAT;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.DOUBLE) return PersistentDataType.DOUBLE;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.STRING) return PersistentDataType.STRING;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.BYTE_ARRAY) return PersistentDataType.BYTE_ARRAY;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.INTEGER_ARRAY) return PersistentDataType.INTEGER_ARRAY;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.LONG_ARRAY) return PersistentDataType.LONG_ARRAY;
        return PersistentDataType.TAG_CONTAINER;
    }

    /** 키가 뜻하는 값(바뀐 것 → 원본). 없으면 null. 원본 값의 종류는 하나씩 읽어 보며 찾는다. 알 수 없는 종류(목록의 목록 등)는 null. */
    private Value lookup(String key) {
        if (changes.containsKey(key)) return changes.get(key);
        if (view == null || !view.has(key)) return null;
        for (var t : PROBE) {
            if (view.has(key, t)) {
                return new Value(t, fromView(view.get(key, t)));
            }
        }
        return null;
    }

    /** 원본에서 읽은 값을 메모리 값으로 — 중첩 컨테이너는 복사한다. */
    private static Object fromView(Object v) {
        if (v instanceof PersistentDataView c) return detached(c);
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object e : l) out.add(fromView(e));
            return out;
        }
        return v;
    }

    /** 저장된 값이 want 종류로 읽히는가(빈 목록은 어떤 목록 종류로도 읽힌다 — NBT 의 빈 목록은 원소 종류가 없다). */
    private static boolean matches(Value v, org.domi.furnace.api.persistence.PersistentDataType<?> want) {
        if (v.type().equals(want)) return true;
        return want.elementType() != null && v.type().elementType() != null && v.value() instanceof List<?> l && l.isEmpty();
    }

    /** 중첩 컨테이너를 메모리로 복사한다 — 원본(엔티티 콜백 안의 살아 있는 값)을 콜백 밖으로 들고 나가지 않게. */
    private static ItemPdc detached(PersistentDataView nested) {
        ItemPdc copy = new ItemPdc(null);
        ItemPdc src = new ItemPdc(nested);
        for (String k : nested.keys()) {
            Value v = src.lookup(k);
            if (v != null) copy.changes.put(k, copyValue(v));
        }
        return copy;
    }

    private boolean exists(String key) {
        if (changes.containsKey(key)) return changes.get(key) != null;
        return view != null && view.has(key);
    }

    // ------------------------------------------------------------------ Bukkit PersistentDataContainer

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        put(key.toString(), prepare(type, value, this));
    }

    /** 호출 스레드에서 값을 저장 꼴로 바꿔 둔다(사용자 종류의 코드·종류 검사가 소유 스레드에서 돌지 않게). 값은 복사된다. */
    static <P, C> Prepared prepare(PersistentDataType<P, C> type, C value, PersistentDataAdapterContext context) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        var ft = furnaceType(type);
        P prim = type.toPrimitive(value, context);
        Object stored = prim instanceof PersistentDataContainer[] arr ? java.util.Arrays.asList(arr) : prim;
        return new Prepared(ft, copyPrim(Objects.requireNonNull(stored, "toPrimitive 가 null")));
    }

    void put(String key, Prepared p) { changes.put(key, new Value(p.type(), p.stored())); }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        Value v = lookup(key.toString());
        return v != null && matches(v, furnaceType(type));
    }

    @Override
    public boolean has(NamespacedKey key) { return exists(key.toString()); }

    @SuppressWarnings("unchecked")
    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        Value v = lookup(key.toString());
        if (v == null) {
            if (exists(key.toString())) throw new IllegalArgumentException("The found object is of a type Furnace's compat layer does not read (" + key + ")");
            return null;
        }
        if (!matches(v, furnaceType(type))) {
            throw new IllegalArgumentException("The found object is of the type " + v.type() + ", which is not the expected " + type.getPrimitiveType().getSimpleName());
        }
        Object prim = copyPrim(v.value());
        if (type.getPrimitiveType() == PersistentDataContainer[].class) prim = ((List<?>) prim).toArray(new PersistentDataContainer[0]);
        return type.fromPrimitive((P) prim, this);
    }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
        C v = get(key, type);
        return v != null ? v : defaultValue;
    }

    /** 지금 있는 키(키 형식과 상관없이). */
    private Set<String> rawKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (view != null) keys.addAll(view.keys());
        for (var e : changes.entrySet()) {
            if (e.getValue() == null) keys.remove(e.getKey());
            else keys.add(e.getKey());
        }
        return keys;
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        Set<NamespacedKey> out = new LinkedHashSet<>();
        for (String k : rawKeys()) {
            NamespacedKey nk = NamespacedKey.fromString(k);
            if (nk != null) out.add(nk);
        }
        return out;
    }

    @Override
    public void remove(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        if (exists(key.toString())) changes.put(key.toString(), null);
    }

    @Override
    public boolean isEmpty() {
        if (view != null) {
            for (String k : view.keys()) if (!changes.containsKey(k) || changes.get(k) != null) return false;
        }
        for (var e : changes.entrySet()) if (e.getValue() != null) return false;
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        Objects.requireNonNull(other, "other");
        for (NamespacedKey k : getKeys()) {
            if (!replace && other.has(k)) continue;
            Value v = lookup(k.toString());
            if (v == null) throw new org.domi.furnace.compat.paper.UnsupportedFeature("PersistentDataContainer#copyTo(" + k + ")", "이 키의 값 종류(목록 등)를 호환 계층이 아직 읽지 못한다");
            other.set(k, (PersistentDataType) bukkitType(v.type()), v.value());
        }
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() { return this; }

    @Override
    public PersistentDataContainer newPersistentDataContainer() { return new ItemPdc(null); }

    // ------------------------------------------------------------------ Furnace 로 쓰기

    /** 바뀐 것을 Furnace 컨테이너에 적용한다(ItemStack#withPersistentData 콜백 안에서). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void applyTo(org.domi.furnace.api.persistence.PersistentDataContainer target) {
        for (var e : changes.entrySet()) {
            Value v = e.getValue();
            if (v == null) {
                target.remove(e.getKey());
                continue;
            }
            target.set(e.getKey(), (org.domi.furnace.api.persistence.PersistentDataType) v.type(), toFurnace(v.value(), target));
        }
    }

    /** 메모리 값 → Furnace 값(중첩 컨테이너는 target 이 만든 빈 컨테이너에 채운다). */
    private static Object toFurnace(Object o, org.domi.furnace.api.persistence.PersistentDataContainer target) {
        if (o instanceof PersistentDataContainer nested) {
            var child = target.newContainer();
            ((ItemPdc) (nested instanceof ItemPdc ip ? ip : copyNested(nested))).applyTo(child);
            return child;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object e : l) out.add(toFurnace(e, target));
            return out;
        }
        return o;
    }

    /** 사용자가 직접 구현한 컨테이너(드묾)는 복사해서 쓴다. */
    private static ItemPdc copyNested(PersistentDataContainer other) {
        ItemPdc c = new ItemPdc(null);
        other.copyTo(c, true);
        return c;
    }


    // ------------------------------------------------------------------ NBT 바이트 (serializeToBytes / readFromBytes)

    private static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4, TAG_FLOAT = 5, TAG_DOUBLE = 6,
            TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9, TAG_COMPOUND = 10, TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;
    private static final int MAX_DEPTH = 512;

    /** SCALARS 와 같은 순서의 NBT 태그 번호. */
    private static final int[] SCALAR_TAGS = {TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_STRING,
            TAG_BYTE_ARRAY, TAG_INT_ARRAY, TAG_LONG_ARRAY, TAG_COMPOUND};

    private static int tagId(org.domi.furnace.api.persistence.PersistentDataType<?> t) {
        if (t.elementType() != null) return TAG_LIST;
        for (int i = 0; i < SCALARS.length; i++) if (SCALARS[i] == t) return SCALAR_TAGS[i];
        throw new IllegalArgumentException("알 수 없는 종류: " + t);
    }

    /**
     * 내용을 Paper 와 같은 바이트로 낸다: 압축 없는 이름 없는 compound(종류 10, 빈 이름, 항목들, 끝 0).
     * 항목 순서는 Paper 가 쓰는 해시 맵 순서를 따른다. 호환 계층이 읽지 못하는 값 종류가 있으면 IOException.
     */
    @Override
    public byte[] serializeToBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("");
            writeCompound(out, 0);
        }
        return bytes.toByteArray();
    }

    @Override
    public void readFromBytes(byte[] bytes, boolean clear) throws IOException { mergeFrom(parse(bytes), clear); }

    /** parsed 의 내용을 넣는다(clear 면 먼저 비운다). */
    void mergeFrom(ItemPdc parsed, boolean clear) {
        if (clear) for (String k : rawKeys()) changes.put(k, null);
        for (var e : parsed.changes.entrySet()) changes.put(e.getKey(), copyValue(e.getValue()));
    }

    private void writeCompound(DataOutputStream out, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("컨테이너가 너무 깊게 중첩됐다");
        Map<String, Value> ordered = new HashMap<>();
        for (String k : rawKeys()) {
            Value v = lookup(k);
            if (v == null) throw new IOException("호환 계층이 읽지 못하는 값 종류라 내보낼 수 없다: " + k);
            ordered.put(k, v);
        }
        for (var e : ordered.entrySet()) {
            out.writeByte(tagId(e.getValue().type()));
            out.writeUTF(e.getKey());
            writePayload(out, e.getValue().type(), e.getValue().value(), depth);
        }
        out.writeByte(TAG_END);
    }

    private static void writePayload(DataOutputStream out, org.domi.furnace.api.persistence.PersistentDataType<?> t, Object v, int depth) throws IOException {
        switch (tagId(t)) {
            case TAG_BYTE -> out.writeByte((Byte) v);
            case TAG_SHORT -> out.writeShort((Short) v);
            case TAG_INT -> out.writeInt((Integer) v);
            case TAG_LONG -> out.writeLong((Long) v);
            case TAG_FLOAT -> out.writeFloat((Float) v);
            case TAG_DOUBLE -> out.writeDouble((Double) v);
            case TAG_STRING -> out.writeUTF((String) v);
            case TAG_BYTE_ARRAY -> {
                byte[] a = (byte[]) v;
                out.writeInt(a.length);
                out.write(a);
            }
            case TAG_INT_ARRAY -> {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
            }
            case TAG_LONG_ARRAY -> {
                long[] a = (long[]) v;
                out.writeInt(a.length);
                for (long x : a) out.writeLong(x);
            }
            case TAG_COMPOUND -> ((ItemPdc) v).writeCompound(out, depth + 1);
            default -> {
                List<?> list = (List<?>) v;
                var elem = t.elementType();
                out.writeByte(list.isEmpty() ? TAG_END : tagId(elem));
                out.writeInt(list.size());
                for (Object e : list) writePayload(out, elem, e, depth + 1);
            }
        }
    }

    /** 바이트를 읽어 메모리 컨테이너로(IOException: 꼴이 틀렸거나 호환 계층이 다루지 못하는 값). */
    static ItemPdc parse(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (in.readByte() != TAG_COMPOUND) throw new IOException("Root tag must be a named compound tag");
            in.readUTF();
            return readCompound(in, 0);
        }
    }

    private static ItemPdc readCompound(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT 중첩이 너무 깊다");
        ItemPdc out = new ItemPdc(null);
        while (true) {
            int id = in.readByte();
            if (id == TAG_END) return out;
            String key = in.readUTF();
            if (id == TAG_LIST) {
                out.changes.put(key, readList(in, depth));
                continue;
            }
            out.changes.put(key, new Value(scalarType(id), readPayload(in, id, depth)));
        }
    }

    /** 목록: 원소 종류는 단일 값이어야 한다(목록의 목록은 호환 계층이 다루지 못한다). 빈 목록은 byte 목록으로 둔다. */
    private static Value readList(DataInputStream in, int depth) throws IOException {
        int elemId = in.readByte();
        int n = in.readInt();
        if (n < 0) throw new IOException("NBT 길이가 음수다");
        if (n == 0) return new Value(org.domi.furnace.api.persistence.PersistentDataType.list(SCALARS[0]), new ArrayList<>());
        if (elemId == TAG_END) throw new IOException("원소가 있는 목록의 종류가 END 다");
        if (elemId == TAG_LIST) throw new IOException("목록의 목록은 호환 계층이 다루지 못한다");
        var elem = scalarType(elemId);
        checkLength(in, n, 1);
        List<Object> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) items.add(readPayload(in, elemId, depth + 1));
        return new Value(org.domi.furnace.api.persistence.PersistentDataType.list(elem), items);
    }

    /** 단일 값 태그 번호 → 종류. 목록(9)은 따로 읽는다. */
    private static org.domi.furnace.api.persistence.PersistentDataType<?> scalarType(int id) throws IOException {
        for (int i = 0; i < SCALARS.length; i++) if (SCALAR_TAGS[i] == id) return SCALARS[i];
        throw new IOException("알 수 없는 NBT 태그 종류: " + id);
    }

    private static void checkLength(DataInputStream in, int length, int unit) throws IOException {
        if (length < 0 || (long) length * unit > in.available()) throw new IOException("NBT 길이가 남은 바이트보다 크다: " + length);
    }

    private static Object readPayload(DataInputStream in, int id, int depth) throws IOException {
        return switch (id) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_STRING -> in.readUTF();
            case TAG_BYTE_ARRAY -> {
                int n = in.readInt();
                checkLength(in, n, 1);
                byte[] a = new byte[n];
                in.readFully(a);
                yield a;
            }
            case TAG_INT_ARRAY -> {
                int n = in.readInt();
                checkLength(in, n, 4);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                yield a;
            }
            case TAG_LONG_ARRAY -> {
                int n = in.readInt();
                checkLength(in, n, 8);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                yield a;
            }
            default -> readCompound(in, depth + 1);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemPdc p)) return false;
        Set<NamespacedKey> a = getKeys();
        if (!a.equals(p.getKeys())) return false;
        for (NamespacedKey k : a) {
            Value x = lookup(k.toString()), y = p.lookup(k.toString());
            if (x == null || y == null) return false;
            if (!(matches(x, y.type()) && matches(y, x.type())) || !valueEquals(x.value(), y.value())) return false;
        }
        return true;
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof int[] x && b instanceof int[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof long[] x && b instanceof long[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!valueEquals(x.get(i), y.get(i))) return false;
            return true;
        }
        return Objects.equals(a, b);
    }

    @Override
    public int hashCode() { return getKeys().hashCode(); }
}
